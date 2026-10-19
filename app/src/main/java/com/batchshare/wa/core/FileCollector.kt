package com.batchshare.wa.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class StageResult(
    val stagedFiles: List<File>,
    val addedCount: Int,
    val duplicateCount: Int,
    val duplicateNames: List<String>
)

object FileCollector {

    private fun getStagingDirectory(context: Context): File {
        val dir = File(context.filesDir, "staged_files")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun calculateHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(16384)
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "${file.name}_${file.length()}"
        }
    }

    fun stageUris(context: Context, uris: List<Uri>): StageResult {
        val stagingDir = getStagingDirectory(context)
        var addedCount = 0
        var duplicateCount = 0
        val duplicateNames = mutableListOf<String>()

        val existingFiles = getStagedFiles(context)
        val currentFilesMap = existingFiles.map { file ->
            file to (file.length() to calculateHash(file))
        }.toMutableList()

        for ((index, uri) in uris.withIndex()) {
            val tempFile = File(stagingDir, ".temp_${System.currentTimeMillis()}_$index")
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    tempFile.delete()
                    continue
                }

                val rawName = getDisplayName(context, uri) ?: "document_${System.currentTimeMillis()}_${index + 1}"
                val sanitized = sanitizeFileName(rawName)
                val tempLength = tempFile.length()
                val tempHash = calculateHash(tempFile)

                // Check for duplicate:
                // 1. Same content hash (identical file content already held)
                // OR
                // 2. Same filename and same length
                val isDuplicate = currentFilesMap.any { (existingFile, lengthAndHash) ->
                    val (len, hash) = lengthAndHash
                    (len == tempLength && hash == tempHash) ||
                    (existingFile.name.equals(sanitized, ignoreCase = true) && len == tempLength)
                }

                if (isDuplicate) {
                    duplicateCount++
                    duplicateNames.add(sanitized)
                    tempFile.delete()
                } else {
                    var targetFile = File(stagingDir, sanitized)
                    if (targetFile.exists()) {
                        val nameWithoutExt = targetFile.nameWithoutExtension
                        val ext = targetFile.extension
                        val dotExt = if (ext.isNotEmpty()) ".$ext" else ""
                        var counter = 1
                        while (targetFile.exists()) {
                            targetFile = File(stagingDir, "${nameWithoutExt} ($counter)$dotExt")
                            counter++
                        }
                    }

                    val moved = tempFile.renameTo(targetFile)
                    val finalFile = if (moved) {
                        targetFile
                    } else {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                        targetFile
                    }

                    currentFilesMap.add(finalFile to (tempLength to tempHash))
                    addedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tempFile.delete()
            }
        }

        return StageResult(
            stagedFiles = getStagedFiles(context),
            addedCount = addedCount,
            duplicateCount = duplicateCount,
            duplicateNames = duplicateNames
        )
    }

    // Alias for stageUris
    fun resolve(context: Context, uris: List<Uri>): List<File> {
        return stageUris(context, uris).stagedFiles
    }

    fun getStagedFiles(context: Context): List<File> {
        val stagingDir = getStagingDirectory(context)
        return stagingDir.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".temp") && it.length() > 0L }
            ?.sortedBy { it.lastModified() }
            ?: emptyList()
    }

    fun removeStagedFile(context: Context, file: File): List<File> {
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return getStagedFiles(context)
    }

    fun clearStagedFiles(context: Context) {
        try {
            getStagingDirectory(context).deleteRecursively()
            getStagingDirectory(context).mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        return cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return uri.lastPathSegment
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }
}
