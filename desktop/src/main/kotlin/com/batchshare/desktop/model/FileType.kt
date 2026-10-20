package com.batchshare.desktop.model

import androidx.compose.ui.graphics.Color
import java.io.File
import java.util.Locale

data class FileTypeInfo(
    val typeLabel: String,
    val badgeColor: Color,
    val textColor: Color
)

fun getFileTypeInfo(file: File): FileTypeInfo {
    return when (file.extension.lowercase(Locale.US)) {
        "pdf" -> FileTypeInfo("PDF", Color(0xFFEF4444), Color(0xFFFFFFFF))
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg" ->
            FileTypeInfo("IMG", Color(0xFF06B6D4), Color(0xFFFFFFFF))
        "mp4", "mkv", "mov", "avi", "3gp", "webm", "flv" ->
            FileTypeInfo("VID", Color(0xFFA855F7), Color(0xFFFFFFFF))
        "mp3", "m4a", "wav", "ogg", "aac", "flac", "opus" ->
            FileTypeInfo("AUD", Color(0xFF10B981), Color(0xFFFFFFFF))
        "apk" -> FileTypeInfo("APK", Color(0xFFF97316), Color(0xFFFFFFFF))
        "zip", "rar", "7z", "tar", "gz" ->
            FileTypeInfo("ZIP", Color(0xFFF59E0B), Color(0xFFFFFFFF))
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "json", "xml", "md" ->
            FileTypeInfo("DOC", Color(0xFF3B82F6), Color(0xFFFFFFFF))
        "kt", "java", "py", "c", "cpp", "h", "cs", "js", "ts", "html", "css", "rs", "go" ->
            FileTypeInfo("CODE", Color(0xFF8B5CF6), Color(0xFFFFFFFF))
        else -> FileTypeInfo(
            if (file.extension.isNotBlank()) file.extension.uppercase().take(4) else "FILE",
            Color(0xFF64748B),
            Color(0xFFFFFFFF)
        )
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", maxOf(1.0, kb))
    }
}
