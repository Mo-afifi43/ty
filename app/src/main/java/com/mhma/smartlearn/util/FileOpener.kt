package com.mhma.smartlearn.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/** Hands files to whatever the student uses for PDFs, slides and so on. */
object FileOpener {

    /** @return false when the file is gone or nothing on the phone can open it. */
    fun open(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeTypeOf(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(context, Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun share(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mimeTypeOf(file))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(context, Intent.createChooser(intent, "Share file").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    /** Opens a web address in the phone's browser. */
    fun openInBrowser(context: Context, url: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun start(context: Context, intent: Intent): Boolean {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun mimeTypeOf(file: File): String {
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }
}
