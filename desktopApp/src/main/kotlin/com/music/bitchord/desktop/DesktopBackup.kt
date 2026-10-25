package com.music.bitchord.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A copy of the listener's preferences, as one readable JSON file.
 *
 * The whole preferences node is walked rather than an enumerated list, so a setting added in a
 * later build is carried without anyone having to remember to add it here. What is deliberately
 * left out is anything that is a credential rather than a preference: account sessions, Last.fm's
 * API secret and session key, the ListenBrainz and Discord tokens, and the Spotify cookie.
 */
internal object DesktopBackup {

    private val json = Json { prettyPrint = true }

    /** Any key carrying one of these is a secret or an account, and never goes in the file. */
    private val SECRET_KEY_PARTS = listOf(
        "token", "secret", "password", "cookie", "session", "credential",
        "api_key", "apikey", "auth", "account", "sp_dc",
    )

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    /** Writes the file and returns it. Throws when the folder cannot be written. */
    fun export(): File {
        val preferences = DesktopPersistence().preferences
        val settings = preferences.keys().sorted()
            .filterNot { key: String -> SECRET_KEY_PARTS.any { it in key.lowercase(Locale.ROOT) } }
            .mapNotNull { key: String ->
                preferences.get(key, null)?.let { value: String -> key to JsonPrimitive(value) }
            }
            .toMap()
        val document = buildJsonObject {
            put("app", "BitChord")
            put("format", 1)
            put("exportedAt", Instant.now().toString())
            put("settings", JsonObject(settings))
        }
        val directory = File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory }
            ?: File(System.getProperty("user.home"))
        val file = File(directory, "BitChord-settings-${Instant.now().atZone(ZoneId.systemDefault()).format(STAMP)}.json")
        file.writeText(json.encodeToString(JsonObject.serializer(), document))
        return file
    }
}

/** The small confirmation after a backup, carrying the path it was written to. */
@Composable
internal fun DesktopBackupDialog(message: String, failed: Boolean, onDismiss: () -> Unit) {
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 440, popup = true) {
        Column(
            Modifier.padding(horizontal = panelInset(22.dp), vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (failed) DesktopStrings["d_backup_failed", "Backup failed"] else DesktopStrings["d_backup_saved", "Backup saved"],
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(message, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(DesktopStrings["done", "Done"], color = DesktopAccent) }
            }
        }
    }
}
