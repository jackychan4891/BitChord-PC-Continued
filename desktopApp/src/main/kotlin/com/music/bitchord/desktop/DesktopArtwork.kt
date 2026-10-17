package com.music.bitchord.desktop

import com.music.bitchord.data.model.artworkAt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.decodeToImageBitmap
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest

/**
 * Decoded artwork, kept to a budget, with the encoded bytes kept on disk behind it.
 *
 * The in-memory part used to be an unbounded map: every distinct URL a session ever loaded stayed
 * decoded for the life of the process, so browsing Explore and Search for a while was hundreds of
 * megabytes of bitmaps nothing would ever ask for again — which is where the gigabyte people
 * reported was going.
 *
 * The disk part is what makes the second launch fast. A page is a hundred covers, and although one
 * is a short fetch, a hundred of them arriving on a cold connection is the several seconds people
 * saw before an Apple Music page filled in. Apple answers with a year of `max-age`, so the bytes
 * are worth keeping; only the decode is repeated.
 */
internal object DesktopArtworkCache {

    /**
     * A sixth of the heap, within reason.
     *
     * Derived rather than fixed so the budget still makes sense against whatever `-Xmx` the
     * launcher was given, and clamped at both ends so a small heap still caches something and a
     * large one does not decide it may hold half a gigabyte of thumbnails.
     */
    internal val budgetBytes: Long =
        (Runtime.getRuntime().maxMemory() / 6).coerceIn(32L * 1024 * 1024, 128L * 1024 * 1024)

    /** Four bytes a pixel, which is what every format here decodes to. */
    private val images = DesktopByteBudgetLru<ImageBitmap>(budgetBytes) {
        it.width.toLong() * it.height.toLong() * 4L
    }

    /**
     * Encoded copies, capped well below the song cache.
     *
     * A cover is tens of kilobytes, so this holds the artwork of a long browsing history; it is
     * bounded anyway because a cache that grows without limit is just a disk-filler.
     */
    private const val DISK_BUDGET_BYTES = 128L * 1024 * 1024

    private val directory: Path by lazy {
        val state = System.getenv("XDG_CACHE_HOME")?.takeIf(String::isNotBlank)
            ?: "${System.getProperty("user.home")}/.cache"
        Path.of(state, "bitchord", "artwork").also { runCatching { Files.createDirectories(it) } }
    }

    private val paths = java.util.concurrent.ConcurrentHashMap<String, Path>()

    private fun fileFor(url: String): Path? = runCatching {
        paths.getOrPut(url) {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(url.toByteArray(StandardCharsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
            directory.resolve("$digest.img")
        }
    }.getOrNull()

    private fun readDisk(url: String): ByteArray? = runCatching {
        val file = fileFor(url) ?: return@runCatching null
        if (!Files.isReadable(file)) return@runCatching null
        val bytes = Files.readAllBytes(file)
        if (bytes.isEmpty()) return@runCatching null
        // What pruning keeps is what was used least recently, and reading is the using.
        runCatching { Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis())) }
        bytes
    }.getOrNull()

    private fun writeDisk(url: String, bytes: ByteArray) = runCatching {
        val file = fileFor(url) ?: return@runCatching
        Files.write(file, bytes)
        trimDisk()
    }

    /**
     * Oldest first, until the cache fits again.
     *
     * A full directory walk is only worth doing just after a write, which is the only moment the
     * cap can have been passed.
     */
    private fun trimDisk() {
        val files = runCatching {
            Files.list(directory).use { stream -> stream.filter(Files::isRegularFile).toList() }
        }.getOrDefault(emptyList())
        var total = files.sumOf { runCatching { Files.size(it) }.getOrDefault(0L) }
        if (total <= DISK_BUDGET_BYTES) return
        files.sortedBy { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) }
            .forEach { file ->
                if (total <= DISK_BUDGET_BYTES) return
                val size = runCatching { Files.size(file) }.getOrDefault(0L)
                if (runCatching { Files.deleteIfExists(file) }.getOrDefault(false)) total -= size
            }
    }

    /** Everything held, dropped — what the Storage settings' "Clear image cache" does. */
    fun clear() {
        images.clear()
        paths.clear()
        runCatching {
            Files.list(directory).use { stream ->
                stream.filter(Files::isRegularFile).forEach { file -> runCatching { Files.deleteIfExists(file) } }
            }
        }
    }

    suspend fun load(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        images.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            val encoded = readDisk(url) ?: slots.withPermit { fetch(url) } ?: return@withContext null
            runCatching { encoded.decodeToImageBitmap() }.getOrNull()?.also { images.put(url, it) }
        }
    }

    /**
     * How many covers may be fetched at once.
     *
     * A page is a hundred of them. Asked for all at once they each pay for a connection and a TLS
     * handshake, which is what a page used to disappear into; held to a dozen they queue onto the
     * connections that are already open, and the page fills from the top down.
     */
    private const val IN_FLIGHT = 12
    private val slots = Semaphore(IN_FLIGHT)

    private fun fetch(url: String): ByteArray? {
        val bytes = runCatching {
            URI(url).toURL().openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }.getInputStream().use { stream -> stream.readBytes() }
        }.getOrNull() ?: return null
        if (bytes.isNotEmpty()) writeDisk(url, bytes)
        return bytes
    }
}

private val APPLE_ARTWORK_SIZE = Regex("""/\d+x\d+([a-z]{2})\.(jpg|jpeg|png|webp)$""")

/** Apple's artwork CDN names its size in the file name; everything else goes through the shared hint. */
internal fun String?.desktopArtworkAt(px: Int): String? {
    if (this == null) return null
    if (runCatching { URI(this).host.orEmpty().endsWith(".mzstatic.com") }.getOrDefault(false)) {
        return APPLE_ARTWORK_SIZE.replace(this) { "/${px}x$px${it.groupValues[1]}.${it.groupValues[2]}" }
    }
    return artworkAt(px)
}

/** Artwork, fetched at the size the surface drawing it actually needs. */
@Composable
fun DesktopArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    px: Int? = null,
) {
    val sized = if (px != null) url.desktopArtworkAt(px) else url
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = sized) {
        value = withContext(Dispatchers.IO) {
            DesktopArtworkCache.load(sized)
        }
    }
    if (bitmap == null) {
        Box(modifier.background(Color(0xFF2B2B35)))
    } else {
        Image(
            painter = BitmapPainter(bitmap!!),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}
