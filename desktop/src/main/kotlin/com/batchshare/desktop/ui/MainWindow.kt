package com.batchshare.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.batchshare.desktop.model.formatFileSize
import com.batchshare.desktop.model.getFileTypeInfo
import com.batchshare.desktop.platform.WindowsIntegration
import com.batchshare.desktop.state.TrayState
import com.batchshare.desktop.ui.theme.BatchShareDesktopTheme
import com.batchshare.desktop.ui.theme.DangerRed
import com.batchshare.desktop.ui.theme.PrimaryBlue
import com.batchshare.desktop.ui.theme.PrimaryBlueLight
import com.batchshare.desktop.ui.theme.Slate200
import com.batchshare.desktop.ui.theme.Slate400
import com.batchshare.desktop.ui.theme.Slate600
import com.batchshare.desktop.ui.theme.Slate700
import com.batchshare.desktop.ui.theme.Slate800
import com.batchshare.desktop.ui.theme.Slate900
import com.batchshare.desktop.ui.theme.Slate950
import com.batchshare.desktop.ui.theme.SuccessGreen
import com.batchshare.desktop.zip.ZipCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

@Composable
fun MainWindow(
    onCloseToTray: () -> Unit
) {
    val windowState = remember {
        WindowState(
            position = WindowPosition(Alignment.Center),
            size = androidx.compose.ui.unit.DpSize(580.dp, 740.dp)
        )
    }

    Window(
        onCloseRequest = onCloseToTray,
        state = windowState,
        title = "BatchShare for Windows",
        icon = painterResource("icons/icon.png"),
        alwaysOnTop = TrayState.isAlwaysOnTop.value,
        resizable = true
    ) {
        // Register drop target for the whole window
        LaunchedEffect(window) {
            DragAndDropHelper.registerDropTarget(window.contentPane) { files ->
                TrayState.addFiles(files)
            }
        }

        BatchShareDesktopTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Slate950
            ) {
                MainContent(
                    onBrowseFiles = {
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Select files to stage in BatchShare"
                            isMultiSelectionEnabled = true
                            fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
                        }
                        if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
                            TrayState.addFiles(chooser.selectedFiles.toList())
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MainContent(
    onBrowseFiles: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val files = TrayState.stagedFiles
    val totalSizeStr = remember(files.size, files.sumOf { it.length() }) {
        formatFileSize(TrayState.totalBytes())
    }

    // Auto-clear status banner after 3 seconds
    val statusMsg = TrayState.statusMessage.value
    LaunchedEffect(statusMsg) {
        if (statusMsg != null) {
            delay(3500)
            TrayState.statusMessage.value = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // -------------------------------------------------------------
        // Header
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource("icons/icon.png"),
                    contentDescription = "Logo",
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "BatchShare",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PrimaryBlue.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "v1.0.0 Windows",
                                color = PrimaryBlueLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = if (files.isEmpty()) "Universal staging tray & zip bundler" else "${files.size} ${if (files.size == 1) "item" else "items"} staged • $totalSizeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }
            }

            // Window Header Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pin / Always-On-Top Toggle
                IconButton(
                    onClick = {
                        TrayState.isAlwaysOnTop.value = !TrayState.isAlwaysOnTop.value
                        TrayState.showStatus(if (TrayState.isAlwaysOnTop.value) "Pinned on top" else "Unpinned")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Always on top",
                        tint = if (TrayState.isAlwaysOnTop.value) PrimaryBlueLight else Slate600
                    )
                }

                // Floating Bubble Toggle
                IconButton(
                    onClick = {
                        TrayState.isFloatingBubbleVisible.value = !TrayState.isFloatingBubbleVisible.value
                        TrayState.showStatus(if (TrayState.isFloatingBubbleVisible.value) "Floating drop target enabled" else "Floating drop target hidden")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Toggle Floating Drop Target",
                        tint = if (TrayState.isFloatingBubbleVisible.value) PrimaryBlueLight else Slate600
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // Status Message Banner (Toast)
        // -------------------------------------------------------------
        if (statusMsg != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Slate800,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate700)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusMsg,
                        color = Slate200,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // -------------------------------------------------------------
        // Center Content Area: Empty State or File List
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (files.isEmpty()) {
                // Empty Drop Zone Card
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Slate800)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderZip,
                                contentDescription = null,
                                tint = PrimaryBlueLight,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Staging Tray is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Drag & drop files or folders anywhere onto this window\nor onto the floating bubble at your screen edge.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onBrowseFiles,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse Files...")
                        }
                    }
                }
            } else {
                // Staged Files Scrollable List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(files, key = { it.absolutePath }) { file ->
                        FileItemRow(
                            file = file,
                            onRemove = { TrayState.removeFile(file) },
                            onPreview = { WindowsIntegration.openFile(file) }
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Bottom Configuration & Actions Panel
        // -------------------------------------------------------------
        if (files.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {

                    // 1. Archive Name Input & Quick Suggestion Chips
                    Text(
                        text = "Archive Name",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = TrayState.customZipName.value,
                        onValueChange = { TrayState.customZipName.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Work_Files (optional)", color = Slate600) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlueLight,
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Slate200
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Preset Name Suggestion Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf("Documents", "Invoices", "Project_Files", "Photos", "Archive")
                        items(presets) { preset ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (TrayState.customZipName.value == preset) PrimaryBlue else Slate800,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
                                modifier = Modifier.clickable {
                                    TrayState.customZipName.value = if (TrayState.customZipName.value == preset) "" else preset
                                }
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 11.5.sp,
                                    color = if (TrayState.customZipName.value == preset) Color.White else Slate400,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. AES-256 Password Encryption Field
                    Text(
                        text = "Password Protection (AES-256)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = TrayState.password.value,
                        onValueChange = { TrayState.password.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Leave blank for unencrypted zip", color = Slate600) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = Slate400
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlueLight,
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Slate200
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Primary Actions: [ Zip & Copy ] and [ Share Raw ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Primary: Zip & Copy to Clipboard
                        Button(
                            onClick = {
                                if (isProcessing) return@Button
                                isProcessing = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val zip = ZipCompressor.createZip(
                                        files = files.toList(),
                                        customName = TrayState.customZipName.value,
                                        password = TrayState.password.value
                                    )
                                    val copied = WindowsIntegration.copyZipToClipboard(zip)
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        if (copied) {
                                            TrayState.showStatus("Archive copied to clipboard! Paste (Ctrl+V) anywhere.")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.3f).height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Zip & Copy (Ctrl+V)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Secondary: Share Raw (Copies all unzipped files to clipboard)
                        FilledTonalButton(
                            onClick = {
                                val copied = WindowsIntegration.copyFilesToClipboard(files.toList())
                                if (copied) {
                                    TrayState.showStatus("All ${files.size} raw files copied to clipboard!")
                                }
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Slate800,
                                contentColor = Slate200
                            )
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Raw", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. Secondary Row: [ Save Zip To... ] and [ Clear Queue ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val defaultName = TrayState.customZipName.value.ifBlank { "BatchShare_Archive" }
                                val saveTarget = WindowsIntegration.promptSaveZipDialog(defaultName)
                                if (saveTarget != null) {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        ZipCompressor.createZip(
                                            files = files.toList(),
                                            customName = saveTarget.nameWithoutExtension,
                                            password = TrayState.password.value,
                                            outputDir = saveTarget.parentFile
                                        )
                                        withContext(Dispatchers.Main) {
                                            TrayState.showStatus("Saved archive to ${saveTarget.name}")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Zip To...", fontSize = 12.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = onBrowseFiles,
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add More", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = { TrayState.clear() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear Queue",
                                    tint = DangerRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileItemRow(
    file: File,
    onRemove: () -> Unit,
    onPreview: () -> Unit
) {
    val fileInfo = remember(file.name) { getFileTypeInfo(file) }
    val sizeStr = remember(file.length()) { formatFileSize(file.length()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Slate900,
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type Badge Chip
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = fileInfo.badgeColor.copy(alpha = 0.16f)
            ) {
                Text(
                    text = fileInfo.typeLabel,
                    color = fileInfo.badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // File Name & Path
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$sizeStr • ${file.parent ?: ""}",
                    color = Slate400,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Preview in Default App
            IconButton(
                onClick = onPreview,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Preview",
                    tint = Slate400,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Remove Button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Slate600,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
