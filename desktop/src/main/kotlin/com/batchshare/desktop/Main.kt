package com.batchshare.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import com.batchshare.desktop.state.TrayState
import com.batchshare.desktop.ui.FloatingBubbleWindow
import com.batchshare.desktop.ui.MainWindow
import com.batchshare.desktop.zip.ZipCompressor
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

fun main(args: Array<String>) {
    // 1. Process command line arguments (e.g. if files are passed from Explorer context menu)
    if (args.isNotEmpty()) {
        val files = args.map { File(it) }.filter { it.exists() }
        if (files.isNotEmpty()) {
            TrayState.addFiles(files)
        }
    }

    // 2. Clean up old temp files in background
    Thread {
        ZipCompressor.cleanOldTempFiles()
    }.start()

    // 3. Launch Compose Desktop Application
    application {
        val trayState = rememberTrayState()
        val appIcon = painterResource("icons/icon.png")

        // Windows System Tray Integration
        Tray(
            state = trayState,
            icon = appIcon,
            tooltip = "BatchShare - ${TrayState.stagedFiles.size} staged",
            onAction = {
                TrayState.isMainWindowVisible.value = true
            },
            menu = {
                Item(
                    text = if (TrayState.isMainWindowVisible.value) "Hide Staging Tray" else "Open Staging Tray",
                    onClick = {
                        TrayState.isMainWindowVisible.value = !TrayState.isMainWindowVisible.value
                    }
                )
                Item(
                    text = if (TrayState.isFloatingBubbleVisible.value) "Hide Floating Drop Target" else "Show Floating Drop Target",
                    onClick = {
                        TrayState.isFloatingBubbleVisible.value = !TrayState.isFloatingBubbleVisible.value
                    }
                )
                Item(
                    text = "Add Files...",
                    onClick = {
                        SwingUtilities.invokeLater {
                            val chooser = JFileChooser().apply {
                                dialogTitle = "Add files to BatchShare"
                                isMultiSelectionEnabled = true
                            }
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                TrayState.addFiles(chooser.selectedFiles.toList())
                                TrayState.isMainWindowVisible.value = true
                            }
                        }
                    }
                )
                if (TrayState.stagedFiles.isNotEmpty()) {
                    Item(
                        text = "Clear Queue (${TrayState.stagedFiles.size})",
                        onClick = {
                            TrayState.clear()
                        }
                    )
                }
                Separator()
                Item(
                    text = "Exit BatchShare",
                    onClick = {
                        exitApplication()
                    }
                )
            }
        )

        // Floating Drop Target (Desktop Bubble / Shelf)
        if (TrayState.isFloatingBubbleVisible.value) {
            FloatingBubbleWindow(
                onToggleMainWindow = {
                    TrayState.isMainWindowVisible.value = !TrayState.isMainWindowVisible.value
                }
            )
        }

        // Main Staging Tray Window
        if (TrayState.isMainWindowVisible.value) {
            MainWindow(
                onCloseToTray = {
                    TrayState.isMainWindowVisible.value = false
                    TrayState.showStatus("BatchShare minimized to system tray")
                }
            )
        }
    }
}
