package com.batchshare.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.batchshare.desktop.state.TrayState
import com.batchshare.desktop.ui.theme.BatchShareDesktopTheme
import com.batchshare.desktop.ui.theme.PrimaryBlue
import com.batchshare.desktop.ui.theme.Slate700
import com.batchshare.desktop.ui.theme.Slate900
import java.awt.Dimension
import java.awt.Toolkit
import kotlin.math.roundToInt

@Composable
fun FloatingBubbleWindow(
    onToggleMainWindow: () -> Unit
) {
    val screenSize = remember { Toolkit.getDefaultToolkit().screenSize }
    // Position bubble near the right edge of screen by default
    val initialX = (screenSize.width - 90).coerceAtLeast(100)
    val initialY = (screenSize.height / 3).coerceAtLeast(100)

    val windowState = remember {
        WindowState(
            position = WindowPosition(initialX.dp, initialY.dp),
            size = androidx.compose.ui.unit.DpSize(80.dp, 80.dp)
        )
    }

    Window(
        onCloseRequest = { TrayState.isFloatingBubbleVisible.value = false },
        state = windowState,
        title = "BatchShare Bubble",
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false
    ) {
        // Register native AWT DropTarget so files dropped onto the bubble are added!
        LaunchedEffect(window) {
            window.minimumSize = Dimension(80, 80)
            DragAndDropHelper.registerDropTarget(window.contentPane) { files ->
                TrayState.addFiles(files)
            }
        }

        BatchShareDesktopTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val curPos = windowState.position
                            if (curPos is WindowPosition.Absolute) {
                                val newX = (curPos.x.value + dragAmount.x).coerceIn(0f, (screenSize.width - 80).toFloat())
                                val newY = (curPos.y.value + dragAmount.y).coerceIn(0f, (screenSize.height - 80).toFloat())
                                windowState.position = WindowPosition(newX.dp, newY.dp)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // 56dp Circular Bubble Container (Matching Android)
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .shadow(10.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Slate900)
                        .border(1.5.dp, Slate700, CircleShape)
                        .clickable {
                            onToggleMainWindow()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource("icons/icon.png"),
                        contentDescription = "BatchShare Drop Target",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                    )
                }

                // Badge Counter Pill with Notch Ring
                val count = TrayState.stagedFiles.size
                AnimatedVisibility(
                    visible = count > 0,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue)
                            .border(1.5.dp, Slate900, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (count > 99) "99+" else count.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
