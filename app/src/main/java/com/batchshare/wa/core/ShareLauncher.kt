package com.batchshare.wa.core

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

object ShareLauncher {

    private const val AUTHORITY = "com.batchshare.wa.fileprovider"

    private fun getMimeType(file: File): String {
        val ext = file.extension.lowercase(Locale.US)
        if (ext == "apk") return "application/vnd.android.package-archive"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    private fun resolveCommonMimeType(files: List<File>): String {
        if (files.isEmpty()) return "*/*"
        val mimes = files.map { getMimeType(it) }
        val first = mimes.first()
        if (mimes.all { it == first }) return first
        val prefix = first.substringBefore('/')
        if (prefix.isNotEmpty() && mimes.all { it.startsWith("$prefix/") }) {
            return "$prefix/*"
        }
        return "*/*"
    }

    fun zipAndShare(
        context: Context,
        files: List<File>,
        customZipName: String? = null,
        password: String? = null,
        clearAfter: Boolean = true
    ): Boolean {
        if (files.isEmpty()) {
            Toast.makeText(context, "No files to zip", Toast.LENGTH_SHORT).show()
            return false
        }

        val zipUri = ZipEngine.zipFiles(context, files, customZipName, password)
        if (zipUri == null) {
            Toast.makeText(context, "Failed to create zip archive", Toast.LENGTH_SHORT).show()
            return false
        }

        val shared = shareDocument(context, zipUri)
        if (shared && clearAfter) {
            FileCollector.clearStagedFiles(context)
        }
        return shared
    }

    fun shareUnzipped(
        context: Context,
        files: List<File>,
        clearAfter: Boolean = true
    ): Boolean {
        if (files.isEmpty()) {
            Toast.makeText(context, "No files to share", Toast.LENGTH_SHORT).show()
            return false
        }

        val rawOutputDir = File(context.cacheDir, "shared_raw/output").apply {
            if (!exists()) {
                mkdirs()
            }
        }

        // Clean up older shared raw files older than 2 hours
        try {
            rawOutputDir.listFiles()?.forEach { oldFile ->
                if (System.currentTimeMillis() - oldFile.lastModified() > 2 * 60 * 60 * 1000) {
                    oldFile.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }

        // IMPORTANT: Copy files to cacheDir so they remain alive on disk when staged files are cleared
        val copiedFiles = mutableListOf<File>()
        val uris = ArrayList<Uri>()

        for (file in files) {
            if (file.exists() && file.length() > 0L) {
                try {
                    val targetFile = File(rawOutputDir, file.name)
                    file.copyTo(targetFile, overwrite = true)
                    copiedFiles.add(targetFile)
                    val uri = FileProvider.getUriForFile(context, AUTHORITY, targetFile)
                    uris.add(uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (uris.isEmpty()) {
            Toast.makeText(context, "Could not prepare files to share", Toast.LENGTH_SHORT).show()
            return false
        }

        val mimeType = resolveCommonMimeType(copiedFiles)

        val clipData = ClipData.newUri(context.contentResolver, copiedFiles[0].name, uris[0])
        for (i in 1 until uris.size) {
            clipData.addItem(ClipData.Item(uris[i]))
        }

        val shareIntent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris[0])
                this.clipData = clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                this.clipData = clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        // Grant URI read permissions to all potential receiver apps
        try {
            val resInfoList = context.packageManager.queryIntentActivities(
                shareIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resInfoList) {
                val pkgName = resolveInfo.activityInfo.packageName
                for (uri in uris) {
                    context.grantUriPermission(pkgName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Explicitly grant permissions to WhatsApp and Telegram packages
        listOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thunderdog.challegram").forEach { pkg ->
            try {
                for (uri in uris) {
                    context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) {
                // Ignore if not present
            }
        }

        val chooserIntent = Intent.createChooser(
            shareIntent,
            if (uris.size == 1) "Share File" else "Share ${uris.size} Files"
        ).apply {
            this.clipData = clipData
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(chooserIntent)
            if (clearAfter) {
                FileCollector.clearStagedFiles(context)
            }
            true
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to share to", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun shareDocument(context: Context, uri: Uri): Boolean {
        val clipData = ClipData.newUri(context.contentResolver, "Zip Archive", uri)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            this.clipData = clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            val resInfoList = context.packageManager.queryIntentActivities(
                shareIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        listOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thunderdog.challegram").forEach { pkg ->
            try {
                context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Ignore if not present
            }
        }

        val chooserIntent = Intent.createChooser(shareIntent, "Share Zip Archive").apply {
            this.clipData = clipData
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(chooserIntent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to share to", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
