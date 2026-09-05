package com.orangefox.unofficial.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

data class DlState(
    val id: Long,
    val title: String,
    val status: Int,
    val bytes: Long,
    val total: Long
)

/**
 * Thin wrapper around the platform DownloadManager. Files land in
 * Downloads/OrangeFox/ and the system handles retries, Wi-Fi policy and the
 * completion notification.
 */
object DownloadHelper {

    private fun dm(context: Context) =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueue(context: Context, url: String, fileName: String): Long {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("OrangeFox Recovery download")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "OrangeFox/$fileName")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        return dm(context).enqueue(request)
    }

    fun snapshot(context: Context, ids: Set<Long>): Map<Long, DlState> {
        if (ids.isEmpty()) return emptyMap()
        return runCatching {
            val query = DownloadManager.Query().setFilterById(*ids.toLongArray())
            dm(context).query(query).use { cursor ->
                val out = mutableMapOf<Long, DlState>()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    out[id] = DlState(
                        id = id,
                        title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty(),
                        status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    )
                }
                out
            }
        }.getOrDefault(emptyMap())
    }
}
