package com.batchshare.desktop.platform

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

object WindowsIntegration {

    /**
     * Copies a single zip file to the Windows system clipboard as a file object
     * so it can be pasted with Ctrl+V into WhatsApp, Telegram, Discord, File Explorer, etc.
     */
    fun copyZipToClipboard(zipFile: File): Boolean {
        return copyFilesToClipboard(listOf(zipFile))
    }

    /**
     * Copies a list of files to the Windows system clipboard as file objects.
     */
    fun copyFilesToClipboard(files: List<File>): Boolean {
        return try {
            val validFiles = files.filter { it.exists() }
            if (validFiles.isEmpty()) return false

            val transferable = FileListTransferable(validFiles)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Opens native Windows Save File Dialog to let user pick where to save the .zip
     */
    fun promptSaveZipDialog(defaultName: String): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Save BatchShare Archive"
            selectedFile = File(if (defaultName.endsWith(".zip")) defaultName else "$defaultName.zip")
            fileFilter = FileNameExtensionFilter("Zip Archives (*.zip)", "zip")
        }

        val result = chooser.showSaveDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) {
            var selected = chooser.selectedFile
            if (!selected.name.endsWith(".zip", ignoreCase = true)) {
                selected = File(selected.parentFile, "${selected.name}.zip")
            }
            selected
        } else {
            null
        }
    }

    /**
     * Opens a file in its default Windows registered viewer (e.g. Photos, Acrobat, Notepad, VLC)
     */
    fun openFile(file: File) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Adds "Send to BatchShare" to Windows Explorer right-click context menu
     */
    fun registerExplorerContextMenu(currentExePath: String): Boolean {
        return try {
            val command = "\"$currentExePath\" \"%1\""

            // File right click
            ProcessBuilder("reg", "add", "HKCU\\Software\\Classes\\*\\shell\\BatchShare", "/ve", "/d", "Send to BatchShare", "/f").start().waitFor()
            ProcessBuilder("reg", "add", "HKCU\\Software\\Classes\\*\\shell\\BatchShare\\command", "/ve", "/d", command, "/f").start().waitFor()

            // Directory right click
            ProcessBuilder("reg", "add", "HKCU\\Software\\Classes\\Directory\\shell\\BatchShare", "/ve", "/d", "Send to BatchShare", "/f").start().waitFor()
            ProcessBuilder("reg", "add", "HKCU\\Software\\Classes\\Directory\\shell\\BatchShare\\command", "/ve", "/d", command, "/f").start().waitFor()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Removes "Send to BatchShare" from Windows Explorer context menu
     */
    fun unregisterExplorerContextMenu(): Boolean {
        return try {
            ProcessBuilder("reg", "delete", "HKCU\\Software\\Classes\\*\\shell\\BatchShare", "/f").start().waitFor()
            ProcessBuilder("reg", "delete", "HKCU\\Software\\Classes\\Directory\\shell\\BatchShare", "/f").start().waitFor()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Checks if explorer context menu is currently registered
     */
    fun isExplorerContextMenuRegistered(): Boolean {
        return try {
            val process = ProcessBuilder("reg", "query", "HKCU\\Software\\Classes\\*\\shell\\BatchShare").start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}

class FileListTransferable(private val files: List<File>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.javaFileListFlavor
    override fun getTransferData(flavor: DataFlavor?): Any = files
}
