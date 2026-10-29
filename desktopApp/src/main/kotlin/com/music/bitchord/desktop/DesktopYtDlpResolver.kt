package com.music.bitchord.desktop

import com.music.bitchord.data.innertube.InnerTubeXResolver
import com.music.bitchord.data.model.Song
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * YouTube stream resolution through an external `yt-dlp` process, Meiro-style.
 *
 * The in-process path ([StreamResolver]) mints a PO-token in a JavaFX WebEngine and solves
 * YouTube's cipher with GraalVM/QuickJS. Neither has a dispose: the first to run leaves WebCore
 * and the Truffle runtime resident for the life of the app — a couple of hundred megabytes of
 * native memory for a feature that only needs a playable URL.
 *
 * `yt-dlp` is a separate process that returns the very same `googlevideo` URL and request headers,
 * so the existing FFmpeg decode is untouched and the JVM never loads the browser or the JS engine.
 *
 * A Windows build carries `yt-dlp.exe` inside its own jar and unpacks it on first use; anywhere
 * else, or with `-Dbitchord.ytdlp=<path>`, it runs whatever the user points at. When neither is
 * available the whole thing is simply off and YouTube resolves in-process, as before.
 */
internal object DesktopYtDlpResolver {

    /** The Windows-only resource the packaged jar keeps the extractor in. */
    private const val BUNDLED_EXE = "/yt-dlp/yt-dlp.exe"

    /** The persisted user choice, mirrored from the Settings sheet. Default on. */
    const val KEY_ENABLED = "ytdlp_extractor"

    /** Whether this build has any way to run `yt-dlp` — a bundled binary or an explicit path. */
    val supported: Boolean
        get() = System.getProperty("bitchord.ytdlp", "").isNotBlank() ||
            javaClass.getResource(BUNDLED_EXE) != null

    private val enabledPref = MutableStateFlow(DesktopPersistence().boolean(KEY_ENABLED, true))

    /** The toggle the Playback settings row shows, kept live so flipping it takes effect at once. */
    val enabledSetting: StateFlow<Boolean> = enabledPref

    /** Whether this build should resolve YouTube through `yt-dlp` instead of in-process. */
    val enabled: Boolean
        get() = supported && enabledPref.value

    fun setEnabled(value: Boolean) {
        val persistence = DesktopPersistence()
        persistence.saveBoolean(KEY_ENABLED, value)
        runCatching { persistence.preferences.flush() }
        enabledPref.value = value
        // Turning the extractor off means the in-process path may run again, so let InnerTubeX
        // prewarm; turning it on means it will not, so keep WebKit and QuickJS unloaded.
        InnerTubeXResolver.autoPrewarm = !enabled
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Where the bundled extractor is unpacked to, beside the analyser's own unpacked files. */
    private val toolHome: Path by lazy {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: "${System.getProperty("user.home")}/.local/share"
        Path.of(base, "bitchord", "tools").also { Files.createDirectories(it) }
    }

    /** A runnable path to `yt-dlp`, unpacking the bundled binary on first call. IO only. */
    private fun exePath(): String {
        System.getProperty("bitchord.ytdlp", "").let { if (it.isNotBlank()) return it }
        return unpackBundled().toString()
    }

    /**
     * Copies the bundled `yt-dlp.exe` out of the jar, skipping the copy when the file on disk
     * already matches the resource by size and is no older than it — the same contract the
     * analyser's native library is unpacked under.
     */
    private fun unpackBundled(): Path {
        val name = BUNDLED_EXE.substringAfterLast('/')
        val target = toolHome.resolve(name)
        val stream = javaClass.getResourceAsStream(BUNDLED_EXE)
            ?: error("$BUNDLED_EXE is missing from this build")
        stream.use { input ->
            val connection = javaClass.getResource(BUNDLED_EXE)?.openConnection()
            val expected = connection?.contentLengthLong ?: -1L
            val builtAt = connection?.lastModified ?: 0L
            if (Files.exists(target) && expected > 0 && Files.size(target) == expected &&
                Files.getLastModifiedTime(target).toMillis() >= builtAt
            ) {
                return target
            }
            val partial = toolHome.resolve("$name.partial")
            Files.copy(input, partial, StandardCopyOption.REPLACE_EXISTING)
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
            target.toFile().setExecutable(true)
        }
        return target
    }

    suspend fun resolve(song: Song, maxKbps: Int): Result<DesktopStream> = runCatching {
        val selector = if (maxKbps < Int.MAX_VALUE) {
            "bestaudio[abr<=$maxKbps]/bestaudio/best"
        } else {
            "bestaudio/best"
        }
        val output = runCatching {
            withContext(Dispatchers.IO) { query(exePath(), song.videoId, selector) }
        }.getOrElse {
            if (it is CancellationException) throw it
            throw IllegalStateException("yt-dlp failed for ${song.videoId}: ${it.message}", it)
        }
        val format = json.parseToJsonElement(output).jsonObject
        val url = format.string("url") ?: throw IllegalStateException("yt-dlp returned no URL")
        val headers = (format["http_headers"] as? JsonObject)
            ?.mapNotNull { (key, value) -> value.jsonPrimitive.contentOrNull?.let { header -> key to header } }
            ?.toMap()
            .orEmpty()
        val acodec = format.string("acodec")?.takeIf { it != "none" }
        val kbps = (format.string("abr")?.toDoubleOrNull() ?: format.string("tbr")?.toDoubleOrNull())
            ?.roundToInt()
        DesktopStream(
            url = url,
            format = DesktopStreamFormat(codec = acodec, kbps = kbps),
            headers = headers,
            sourceId = "youtube",
            // Google's media servers answer 403 to a whole-file request and 206 to a windowed one,
            // exactly as for the in-process path — FFmpeg must read it in ranges.
            windowedReads = true,
        )
    }

    private fun query(exe: String, videoId: String, selector: String): String {
        val process = ProcessBuilder(
            exe, "-j", "--no-playlist", "--no-warnings", "-f", selector,
            "https://www.youtube.com/watch?v=$videoId",
        ).redirectErrorStream(false).start()
        // Drain both pipes on their own threads before waiting: the `-j` payload — every format
        // plus the description — is bigger than the OS pipe buffer, so a child that is only waited
        // on and never read blocks writing and never exits, which reads as a timeout.
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val outThread = Thread {
            runCatching { process.inputStream.bufferedReader().forEachLine { stdout.append(it).append('\n') } }
        }
        val errThread = Thread {
            runCatching { process.errorStream.bufferedReader().forEachLine { stderr.append(it).append('\n') } }
        }
        outThread.isDaemon = true
        errThread.isDaemon = true
        outThread.start()
        errThread.start()
        val finished = process.waitFor(45, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw IllegalStateException("yt-dlp timed out")
        }
        outThread.join(2_000)
        errThread.join(2_000)
        val out = stdout.toString()
        if (process.exitValue() != 0 || out.isBlank()) {
            throw IllegalStateException("yt-dlp exit ${process.exitValue()}: ${stderr.toString().trim().take(300)}")
        }
        // `-j` for a single video prints one object; take the first non-empty line defensively.
        return out.lineSequence().first { it.isNotBlank() }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
}
