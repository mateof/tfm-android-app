package com.mateof.tfm.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

data class PickedFile(val uri: Uri, val name: String, val size: Long, val mimeType: String?)

object Uploads {

    fun describe(context: Context, uri: Uri): PickedFile {
        var name = uri.lastPathSegment ?: "file"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        return PickedFile(uri, name, size, context.contentResolver.getType(uri))
    }

    /** Streams a content Uri as a multipart part without loading it in memory. */
    fun filePart(context: Context, picked: PickedFile): MultipartBody.Part {
        val mediaType = (picked.mimeType ?: "application/octet-stream").toMediaTypeOrNull()
        val body = object : RequestBody() {
            override fun contentType(): MediaType? = mediaType
            override fun contentLength(): Long = picked.size
            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(picked.uri)?.use { input ->
                    sink.writeAll(input.source())
                } ?: throw java.io.IOException("Cannot open $picked")
            }
        }
        return MultipartBody.Part.createFormData("file", picked.name, body)
    }
}
