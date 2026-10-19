package com.batchshare.wa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.batchshare.wa.core.BubblePreferences
import com.batchshare.wa.core.FileCollector
import com.batchshare.wa.core.FloatingBubbleService
import com.batchshare.wa.core.NotificationActionReceiver
import com.batchshare.wa.core.NotificationHelper
import com.batchshare.wa.core.ShareLauncher
import com.batchshare.wa.ui.SharePreviewScreen
import com.batchshare.wa.ui.theme.BatchShareTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class BatchShareActivity : ComponentActivity() {

    private val filesList = mutableStateListOf<File>()
    private val isLoading = mutableStateOf(false)
    private val isBubbleEnabled = mutableStateOf(false)

    private val pickFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            stageUris(uris, isExternalShare = false)
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            NotificationHelper.updateNotification(this, filesList)
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = BubblePreferences.canDrawOverlays(this)
        isBubbleEnabled.value = granted && BubblePreferences.isBubbleEnabled(this)
        if (granted) {
            Toast.makeText(this, "Floating Bubble enabled 🫧", Toast.LENGTH_SHORT).show()
            if (filesList.isNotEmpty()) {
                FloatingBubbleService.start(this)
            }
        } else {
            Toast.makeText(this, "Overlay permission not granted", Toast.LENGTH_SHORT).show()
            BubblePreferences.setBubbleEnabled(this, false)
            isBubbleEnabled.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        refreshStagedFiles()
        isBubbleEnabled.value = BubblePreferences.isBubbleEnabled(this) && BubblePreferences.canDrawOverlays(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        NotificationActionReceiver.onFilesClearedListener = {
            refreshStagedFiles()
        }

        setContent {
            BatchShareTheme {
                SharePreviewScreen(
                    files = filesList,
                    onConfirmZip = { filesToZip, customZipName, password ->
                        performZipAndShare(filesToZip, customZipName, password)
                    },
                    onConfirmUnzipped = { filesToShare ->
                        performShareUnzipped(filesToShare)
                    },
                    onPickFiles = {
                        pickFilesLauncher.launch(arrayOf("*/*"))
                    },
                    onOpenFile = { file ->
                        openFile(file)
                    },
                    onRemoveFile = { fileToRemove ->
                        val updated = FileCollector.removeStagedFile(this, fileToRemove)
                        filesList.clear()
                        filesList.addAll(updated)
                        NotificationHelper.updateNotification(this, filesList)
                        FloatingBubbleService.update(this)
                    },
                    onClearAll = {
                        FileCollector.clearStagedFiles(this)
                        filesList.clear()
                        NotificationHelper.cancelNotification(this)
                        FloatingBubbleService.stop(this)
                        Toast.makeText(this, "Cleared holding box", Toast.LENGTH_SHORT).show()
                    },
                    isBubbleEnabled = isBubbleEnabled.value,
                    onToggleBubble = { enable ->
                        if (enable) {
                            if (BubblePreferences.canDrawOverlays(this)) {
                                BubblePreferences.setBubbleEnabled(this, true)
                                isBubbleEnabled.value = true
                                Toast.makeText(this, "Floating Bubble enabled 🫧", Toast.LENGTH_SHORT).show()
                                if (filesList.isNotEmpty()) {
                                    FloatingBubbleService.start(this)
                                }
                            } else {
                                overlayPermissionLauncher.launch(BubblePreferences.requestOverlayPermissionIntent(this))
                            }
                        } else {
                            BubblePreferences.setBubbleEnabled(this, false)
                            isBubbleEnabled.value = false
                            FloatingBubbleService.stop(this)
                            Toast.makeText(this, "Floating Bubble disabled", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isLoading = isLoading.value
                )
            }
        }

        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshStagedFiles()
        isBubbleEnabled.value = BubblePreferences.isBubbleEnabled(this) && BubblePreferences.canDrawOverlays(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        NotificationActionReceiver.onFilesClearedListener = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(NotificationHelper.EXTRA_AUTO_ZIP, false)) {
            val staged = FileCollector.getStagedFiles(this)
            if (staged.isNotEmpty()) {
                performZipAndShare(staged, null, null)
                return
            }
        }

        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val uris = extractUrisFromIntent(intent)
                if (uris.isNotEmpty()) {
                    stageUris(uris, isExternalShare = true)
                }
            }
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "com.batchshare.wa.fileprovider", file)
            val ext = file.extension.lowercase(Locale.US)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(viewIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app available to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performZipAndShare(filesToZip: List<File>, customZipName: String?, password: String?) {
        isLoading.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            val success = ShareLauncher.zipAndShare(
                this@BatchShareActivity,
                filesToZip,
                customZipName = customZipName,
                password = password,
                clearAfter = true
            )
            withContext(Dispatchers.Main) {
                isLoading.value = false
                if (success) {
                    filesList.clear()
                    NotificationHelper.cancelNotification(this@BatchShareActivity)
                    FloatingBubbleService.stop(this@BatchShareActivity)
                    finishAndRemoveTask()
                }
            }
        }
    }

    private fun performShareUnzipped(filesToShare: List<File>) {
        isLoading.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            val success = ShareLauncher.shareUnzipped(
                this@BatchShareActivity,
                filesToShare,
                clearAfter = true
            )
            withContext(Dispatchers.Main) {
                isLoading.value = false
                if (success) {
                    filesList.clear()
                    NotificationHelper.cancelNotification(this@BatchShareActivity)
                    FloatingBubbleService.stop(this@BatchShareActivity)
                    finishAndRemoveTask()
                }
            }
        }
    }

    private fun stageUris(uris: List<Uri>, isExternalShare: Boolean = false) {
        isLoading.value = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                FileCollector.stageUris(this@BatchShareActivity, uris)
            }
            filesList.clear()
            filesList.addAll(result.stagedFiles)
            isLoading.value = false

            NotificationHelper.updateNotification(this@BatchShareActivity, filesList)

            val canBubble = BubblePreferences.isBubbleEnabled(this@BatchShareActivity) &&
                    BubblePreferences.canDrawOverlays(this@BatchShareActivity)

            if (canBubble) {
                FloatingBubbleService.update(this@BatchShareActivity)
            }

            val toastText = when {
                result.duplicateCount > 0 && result.addedCount == 0 -> {
                    if (result.duplicateCount == 1) {
                        "\"${result.duplicateNames.firstOrNull() ?: "File"}\" is already held"
                    } else {
                        "${result.duplicateCount} files are already held"
                    }
                }
                result.duplicateCount > 0 && result.addedCount > 0 -> {
                    "Staged ${result.addedCount} file${if (result.addedCount == 1) "" else "s"}${if (canBubble) " to bubble 🫧" else ""} (${result.duplicateCount} duplicate skipped)"
                }
                result.addedCount > 0 -> {
                    "Staged ${filesList.size} file${if (filesList.size == 1) "" else "s"}${if (canBubble) " to bubble 🫧" else ""}"
                }
                else -> {
                    "Holding ${filesList.size} file${if (filesList.size == 1) "" else "s"}"
                }
            }

            Toast.makeText(this@BatchShareActivity, toastText, Toast.LENGTH_SHORT).show()

            if (isExternalShare && canBubble) {
                // Immediately return user to WhatsApp / Telegram / source app!
                finishAndRemoveTask()
            }
        }
    }

    private fun refreshStagedFiles() {
        val existing = FileCollector.getStagedFiles(this)
        filesList.clear()
        filesList.addAll(existing)
        NotificationHelper.updateNotification(this, filesList)
    }

    private fun extractUrisFromIntent(intent: Intent): List<Uri> {
        val uris = linkedSetOf<Uri>()

        val streamUris: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        if (!streamUris.isNullOrEmpty()) {
            uris.addAll(streamUris.filterNotNull())
        }

        intent.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i).uri?.let { uris.add(it) }
            }
        }

        if (uris.isEmpty() && intent.data != null) {
            intent.data?.let { uris.add(it) }
        }

        return uris.toList()
    }
}
