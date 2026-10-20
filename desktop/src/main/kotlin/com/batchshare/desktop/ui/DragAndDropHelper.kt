package com.batchshare.desktop.ui

import com.batchshare.desktop.platform.FileListTransferable
import java.awt.Component
import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragGestureEvent
import java.awt.dnd.DragGestureListener
import java.awt.dnd.DragSource
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import javax.swing.JComponent

object DragAndDropHelper {

    /**
     * Registers a component to receive dropped files from Windows Explorer, Chrome, etc.
     */
    fun registerDropTarget(component: Component, onFilesDropped: (List<File>) -> Unit) {
        component.dropTarget = DropTarget(component, object : DropTargetAdapter() {
            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    val transferable = dtde.transferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                        if (!files.isNullOrEmpty()) {
                            onFilesDropped(files)
                            dtde.dropComplete(true)
                            return
                        }
                    }
                    dtde.dropComplete(false)
                } catch (e: Exception) {
                    e.printStackTrace()
                    dtde.dropComplete(false)
                }
            }
        })
    }

    /**
     * Makes an AWT/Swing component a drag source so the user can click and drag
     * files OUT of the application and drop them directly into chat apps (WhatsApp, Telegram, Discord).
     */
    fun attachDragOutSource(component: JComponent, getFilesToDrag: () -> List<File>) {
        val dragSource = DragSource.getDefaultDragSource()
        dragSource.createDefaultDragGestureRecognizer(
            component,
            DnDConstants.ACTION_COPY_OR_MOVE,
            DragGestureListener { dge: DragGestureEvent ->
                val files = getFilesToDrag().filter { it.exists() }
                if (files.isNotEmpty()) {
                    val transferable = FileListTransferable(files)
                    dragSource.startDrag(
                        dge,
                        Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                        transferable,
                        null
                    )
                }
            }
        )
    }
}
