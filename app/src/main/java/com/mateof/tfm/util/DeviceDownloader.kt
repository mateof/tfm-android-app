package com.mateof.tfm.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.mateof.tfm.data.prefs.ServerPreferences
import com.mateof.tfm.data.repo.MediaUrls
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads server files onto the device through the system [DownloadManager],
 * so the user gets notifications, resume-on-failure and the file lands in
 * `Download/TFM/`.
 */
@Singleton
class DeviceDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaUrls: MediaUrls,
    private val prefs: ServerPreferences
) {
    /** @return true when the download was handed to the system. */
    fun download(url: String?, fileName: String): Boolean {
        val absolute = mediaUrls.withKey(url) ?: return false
        val request = DownloadManager.Request(Uri.parse(absolute))
            .setTitle(fileName)
            .setDescription("TFM")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "TFM/${fileName.replace('/', '_')}"
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val key = prefs.current.apiKey
        if (key.isNotBlank()) request.addRequestHeader("X-Api-Key", key)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        return true
    }
}
