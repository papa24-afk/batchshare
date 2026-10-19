package com.batchshare.wa.core

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ZipEngine {

    private const val AUTHORITY = "com.batchshare.wa.fileprovider"

    fun zipFiles(
        context: Context,
        files: List<File>,
        customName: String? = null,
        password: String? = null
    ): Uri? {
        if (files.isEmpty()) return null

        val outputDir = File(context.cacheDir, "shared_zips/output").apply {
            if (!exists()) {
                mkdirs()
            }
        }

        // Clean up older zip files so cache doesn't grow indefinitely
        try {
            outputDir.listFiles()?.filter { it.isFile && it.extension == "zip" }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(2)
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            // Ignore cleanup failure
        }

        val zipFileName = if (!customName.isNullOrBlank()) {
            val sanitized = customName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            if (sanitized.lowercase(Locale.US).endsWith(".zip")) sanitized else "$sanitized.zip"
        } else {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
            "documents_$timestamp.zip"
        }
        val zipFile = File(outputDir, zipFileName)
        if (zipFile.exists()) {
            zipFile.delete()
        }

        return try {
            val zip = ZipFile(zipFile)
            val parameters = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                if (!password.isNullOrBlank()) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
            }

            if (!password.isNullOrBlank()) {
                zip.setPassword(password.toCharArray())
            }

            val validFiles = files.filter { it.exists() && it.length() > 0L }
            if (validFiles.isEmpty()) return null

            zip.addFiles(validFiles, parameters)

            FileProvider.getUriForFile(context, AUTHORITY, zipFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
