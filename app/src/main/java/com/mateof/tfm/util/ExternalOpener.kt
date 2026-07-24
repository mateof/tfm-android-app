package com.mateof.tfm.util

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.mateof.tfm.data.prefs.ServerPreferences
import com.mateof.tfm.data.repo.MediaUrls
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads a file to the app's cache and launches [Intent.ACTION_VIEW] via
 * the system chooser, so the user can hand the file off to any installed
 * viewer (PDF readers, archive apps, third-party players, …).
 */
@Singleton
class ExternalOpener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val mediaUrls: MediaUrls,
    private val prefs: ServerPreferences
) {

    sealed interface Result {
        data object Success : Result
        data object NoUrl : Result
        data object NoAppFound : Result
        data class Failed(val message: String) : Result
    }

    /**
     * Streams [url] into `cacheDir/opened/<safeName>` and launches the chooser.
     * Runs on IO; call from a coroutine.
     */
    suspend fun open(url: String?, fileName: String, extensionHint: String?): Result =
        withContext(Dispatchers.IO) {
            val absolute = mediaUrls.withKey(url) ?: return@withContext Result.NoUrl
            val safeName = fileName.replace(Regex("[/\\\\]"), "_").ifBlank { "file" }
            val dir = File(context.cacheDir, "opened").apply { mkdirs() }
            val file = File(dir, safeName)
            try {
                val req = Request.Builder().url(absolute).apply {
                    val key = prefs.current.apiKey
                    if (key.isNotBlank()) addHeader("X-Api-Key", key)
                }.build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.Failed("HTTP ${resp.code}")
                    }
                    val body = resp.body ?: return@withContext Result.Failed("Cuerpo vacío")
                    body.byteStream().use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                }
            } catch (e: IOException) {
                return@withContext Result.Failed(e.message ?: "Error de red")
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mime = mimeFor(extensionHint, fileName)
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(view, "Abrir con").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(chooser)
                Result.Success
            } catch (e: android.content.ActivityNotFoundException) {
                Result.NoAppFound
            }
        }

    private fun mimeFor(extensionHint: String?, fileName: String): String {
        val ext = extensionHint?.trimStart('.')?.takeIf { it.isNotBlank() }
            ?: fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            ?: return "*/*"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "*/*"
    }
}
