package com.batchshare.desktop.state

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object TrayState {

    val stagedFiles = mutableStateListOf<File>()
    val customZipName = mutableStateOf("")
    val password = mutableStateOf("")

    // Window controls
    val isMainWindowVisible = mutableStateOf(true)
    val isFloatingBubbleVisible = mutableStateOf(true)
    val isAlwaysOnTop = mutableStateOf(false)

    // Status toast message for UI
    val statusMessage = mutableStateOf<String?>(null)

    // Cached SHA-256 hashes to prevent adding duplicate files
    private val knownHashes = mutableSetOf<String>()

    /**
     * Adds files/folders to the staging tray, skipping duplicates via SHA-256.
     * Returns the count of newly added files.
     */
    fun addFiles(incoming: List<File>): Int {
        var addedCount = 0
        val queue = ArrayDeque<File>()

        for (f in incoming) {
            if (f.exists()) {
                if (f.isDirectory) {
                    f.walkTopDown().filter { it.isFile }.forEach { queue.add(it) }
                } else {
                    queue.add(f)
                }
            }
        }

        while (queue.isNotEmpty()) {
            val file = queue.removeFirst()
            // Quick check by absolute path
            if (stagedFiles.any { it.absolutePath == file.absolutePath }) {
                continue
            }

            // Fingerprint hash check (only compute for files < 100MB to keep UI snappy)
            val hash = if (file.length() < 100 * 1024 * 1024) {
                computeSha256(file)
            } else {
                "${file.name}_${file.length()}"
            }

            if (hash != null && knownHashes.contains(hash)) {
                // Duplicate skipped
                continue
            }

            if (hash != null) {
                knownHashes.add(hash)
            }

            stagedFiles.add(file)
            addedCount++
        }

        if (addedCount > 0) {
            showStatus("Staged $addedCount ${if (addedCount == 1) "file" else "files"}")
        }

        return addedCount
    }

    fun removeFile(file: File) {
        stagedFiles.remove(file)
        if (stagedFiles.isEmpty()) {
            knownHashes.clear()
        }
    }

    fun clear() {
        stagedFiles.clear()
        knownHashes.clear()
        customZipName.value = ""
        password.value = ""
        showStatus("Staging tray cleared")
    }

    fun totalBytes(): Long {
        return stagedFiles.sumOf { it.length() }
    }

    fun showStatus(msg: String) {
        statusMessage.value = msg
    }

    private fun computeSha256(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
