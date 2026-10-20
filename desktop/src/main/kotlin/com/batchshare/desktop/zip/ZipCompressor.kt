package com.batchshare.desktop.zip

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ZipCompressor {

    private val tempDir: File by lazy {
        val dir = File(System.getProperty("java.io.tmpdir"), "BatchShare_Windows")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /**
     * Compresses the provided files into a .zip archive.
     * If [customName] is provided, it is sanitized and used.
     * If [password] is non-blank, the archive is protected with AES-256 encryption.
     */
    fun createZip(
        files: List<File>,
        customName: String = "",
        password: String = "",
        outputDir: File = tempDir
    ): File {
        if (!outputDir.exists()) outputDir.mkdirs()

        val sanitizedName = if (customName.isNotBlank()) {
            customName.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        } else {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            "BatchShare_$timestamp"
        }

        val zipFile = File(outputDir, if (sanitizedName.endsWith(".zip", ignoreCase = true)) sanitizedName else "$sanitizedName.zip")
        if (zipFile.exists()) {
            zipFile.delete()
        }

        val zip = if (password.isNotBlank()) {
            ZipFile(zipFile, password.toCharArray())
        } else {
            ZipFile(zipFile)
        }

        val parameters = ZipParameters().apply {
            compressionMethod = CompressionMethod.DEFLATE
            compressionLevel = CompressionLevel.NORMAL
            if (password.isNotBlank()) {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
        }

        for (file in files) {
            if (!file.exists()) continue
            if (file.isDirectory) {
                zip.addFolder(file, parameters)
            } else {
                zip.addFile(file, parameters)
            }
        }

        return zipFile
    }

    /**
     * Cleans up temporary archives created by BatchShare older than 1 hour.
     */
    fun cleanOldTempFiles() {
        try {
            val oneHourAgo = System.currentTimeMillis() - 3600_000
            tempDir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < oneHourAgo) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
