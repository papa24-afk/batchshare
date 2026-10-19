package com.batchshare.wa.core

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.batchshare.wa.BatchShareActivity
import com.batchshare.wa.R
import com.batchshare.wa.ui.formatFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class FloatingBubbleService : Service() {

    companion object {
        const val ACTION_START = "com.batchshare.wa.BUBBLE_START"
        const val ACTION_UPDATE = "com.batchshare.wa.BUBBLE_UPDATE"
        const val ACTION_STOP = "com.batchshare.wa.BUBBLE_STOP"

        var isRunning = false
            private set

        fun start(context: Context) {
            if (!BubblePreferences.canDrawOverlays(context) || !BubblePreferences.isBubbleEnabled(context)) {
                return
            }
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun update(context: Context) {
            if (!isRunning) {
                start(context)
                return
            }
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = ACTION_UPDATE
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            if (!isRunning) return
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null

    // Views
    private var bubbleContainer: FrameLayout? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var dismissContainer: FrameLayout? = null
    private var dismissParams: WindowManager.LayoutParams? = null

    private var trayContainer: FrameLayout? = null
    private var trayParams: WindowManager.LayoutParams? = null

    private var badgeView: TextView? = null
    private var isTrayExpanded = false

    // Staged files cache
    private var currentFiles = listOf<File>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val initialFiles = FileCollector.getStagedFiles(this)
        val notif = NotificationHelper.buildNotification(this, initialFiles)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.NOTIFICATION_ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notif)
        }

        // Hook up listener so external clear notifies the bubble
        NotificationActionReceiver.onFilesClearedListener = {
            serviceScope.launch {
                refreshFilesAndUI()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdownService()
                return START_NOT_STICKY
            }
            ACTION_UPDATE, ACTION_START, null -> {
                refreshFilesAndUI()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        removeBubbleView()
        removeDismissView()
        removeTrayView()
    }

    private fun refreshFilesAndUI() {
        if (!BubblePreferences.canDrawOverlays(this) || !BubblePreferences.isBubbleEnabled(this)) {
            shutdownService()
            return
        }

        currentFiles = FileCollector.getStagedFiles(this)
        if (currentFiles.isEmpty()) {
            shutdownService()
            return
        }

        // Keep foreground service notification active
        NotificationHelper.updateNotification(this, currentFiles)

        if (bubbleContainer == null) {
            createBubbleView()
            createDismissView()
        }

        updateBadgeCount(currentFiles.size)

        if (isTrayExpanded) {
            updateTrayContent()
        }
    }

    private fun shutdownService() {
        removeBubbleView()
        removeDismissView()
        removeTrayView()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    // -------------------------------------------------------------
    // Bubble View Creation & Touch Handling
    // -------------------------------------------------------------
    @SuppressLint("ClickableViewAccessibility")
    private fun createBubbleView() {
        val wm = windowManager ?: return

        val bubbleSize = dpToPx(62)
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = getScreenWidth() - bubbleSize - dpToPx(12)
            y = getScreenHeight() / 3
        }
        bubbleParams = params

        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(bubbleSize, bubbleSize)
        }

        // Circular background with subtle glow border
        val iconSize = dpToPx(52)
        val iconImageView = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0A0F1D"))
                setStroke(dpToPx(2), Color.parseColor("#00D2FF"))
            }
            background = iconBg
            val p = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = p
            elevation = dpToPx(8).toFloat()
        }
        container.addView(iconImageView)

        // Count Badge
        val badgeSize = dpToPx(22)
        val badge = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val badgeBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E5FF"))
                setStroke(dpToPx(1.5f), Color.WHITE)
            }
            background = badgeBg
            val bp = FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                gravity = Gravity.TOP or Gravity.END
                marginEnd = dpToPx(2)
                topMargin = dpToPx(2)
            }
            layoutParams = bp
            elevation = dpToPx(10).toFloat()
        }
        badgeView = badge
        container.addView(badge)

        // Dragging & Click Listener
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        container.setOnTouchListener { _, event ->
            val curParams = bubbleParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = curParams.x
                    initialY = curParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                        showDismissView(true)
                    }

                    if (isDragging) {
                        curParams.x = initialX + dx
                        curParams.y = initialY + dy
                        try {
                            wm.updateViewLayout(container, curParams)
                        } catch (e: Exception) {
                            // View might be detached
                        }

                        // Check dismiss target proximity
                        val inDismiss = isOverDismissTarget(curParams.x, curParams.y)
                        highlightDismissTarget(inDismiss)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        showDismissView(false)
                        val inDismiss = isOverDismissTarget(curParams.x, curParams.y)
                        if (inDismiss) {
                            // Dismissed!
                            FileCollector.clearStagedFiles(this)
                            NotificationHelper.cancelNotification(this)
                            Toast.makeText(this, "Cleared holding box", Toast.LENGTH_SHORT).show()
                            shutdownService()
                        } else {
                            // Snap to nearest edge
                            snapBubbleToEdge(curParams.x)
                        }
                    } else {
                        // Tap / Click!
                        toggleMiniTray()
                    }
                    true
                }
                else -> false
            }
        }

        bubbleContainer = container
        try {
            wm.addView(container, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateBadgeCount(count: Int) {
        badgeView?.text = if (count > 99) "99+" else count.toString()
        badgeView?.visibility = if (count > 0) View.VISIBLE else View.GONE
    }

    private fun snapBubbleToEdge(currentX: Int) {
        val wm = windowManager ?: return
        val container = bubbleContainer ?: return
        val params = bubbleParams ?: return

        val screenW = getScreenWidth()
        val bubbleSize = dpToPx(62)
        val margin = dpToPx(8)
        val targetX = if (currentX + bubbleSize / 2 < screenW / 2) {
            margin
        } else {
            screenW - bubbleSize - margin
        }

        val anim = ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                params.x = va.animatedValue as Int
                try {
                    wm.updateViewLayout(container, params)
                } catch (e: Exception) {
                    // Detached
                }
            }
        }
        anim.start()
    }

    private fun removeBubbleView() {
        bubbleContainer?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        bubbleContainer = null
        bubbleParams = null
        badgeView = null
    }

    // -------------------------------------------------------------
    // Dismiss Target (Bottom Red ✕ Drop Zone)
    // -------------------------------------------------------------
    private fun createDismissView() {
        val wm = windowManager ?: return
        val size = dpToPx(64)
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(48)
        }
        dismissParams = params

        val container = FrameLayout(this).apply {
            visibility = View.GONE
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E11D48")) // Modern red
                setStroke(dpToPx(2), Color.WHITE)
            }
            background = bg
            elevation = dpToPx(12).toFloat()
        }

        val crossText = TextView(this).apply {
            text = "✕"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(crossText)

        dismissContainer = container
        try {
            wm.addView(container, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showDismissView(show: Boolean) {
        dismissContainer?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun isOverDismissTarget(bubbleX: Int, bubbleY: Int): Boolean {
        val screenW = getScreenWidth()
        val screenH = getScreenHeight()
        val dismissCenterX = screenW / 2
        val dismissCenterY = screenH - dpToPx(48) - dpToPx(32)

        val bubbleCenterX = bubbleX + dpToPx(31)
        val bubbleCenterY = bubbleY + dpToPx(31)

        val distSq = (bubbleCenterX - dismissCenterX) * (bubbleCenterX - dismissCenterX) +
                (bubbleCenterY - dismissCenterY) * (bubbleCenterY - dismissCenterY)

        val threshold = dpToPx(80)
        return distSq < (threshold * threshold)
    }

    private fun highlightDismissTarget(highlight: Boolean) {
        dismissContainer?.let { view ->
            val bg = view.background as? GradientDrawable ?: return
            if (highlight) {
                bg.setColor(Color.parseColor("#BE123C"))
                bg.setStroke(dpToPx(3), Color.YELLOW)
                view.scaleX = 1.2f
                view.scaleY = 1.2f
            } else {
                bg.setColor(Color.parseColor("#E11D48"))
                bg.setStroke(dpToPx(2), Color.WHITE)
                view.scaleX = 1.0f
                view.scaleY = 1.0f
            }
        }
    }

    private fun removeDismissView() {
        dismissContainer?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        dismissContainer = null
        dismissParams = null
    }

    // -------------------------------------------------------------
    // Expandable Mini-Tray Modal
    // -------------------------------------------------------------
    private fun toggleMiniTray() {
        if (isTrayExpanded) {
            closeMiniTray()
        } else {
            openMiniTray()
        }
    }

    private fun openMiniTray() {
        val wm = windowManager ?: return
        isTrayExpanded = true

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        trayParams = params

        // Scrim background
        val scrim = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#990B0F19"))
            setOnClickListener {
                closeMiniTray()
            }
        }

        // Card Container
        val cardW = minOf(getScreenWidth() - dpToPx(32), dpToPx(340))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                setColor(Color.parseColor("#111827"))
                setStroke(dpToPx(1.5f), Color.parseColor("#1F2937"))
            }
            background = cardBg
            elevation = dpToPx(20).toFloat()
            setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(18))
            isClickable = true
            isFocusable = true
            val lp = FrameLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = lp
        }

        scrim.addView(card)
        trayContainer = scrim

        try {
            wm.addView(scrim, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updateTrayContent()
    }

    private fun closeMiniTray() {
        isTrayExpanded = false
        removeTrayView()
    }

    private fun removeTrayView() {
        trayContainer?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        trayContainer = null
        trayParams = null
    }

    private fun updateTrayContent() {
        val scrim = trayContainer ?: return
        val card = (scrim.getChildAt(0) as? LinearLayout) ?: return
        card.removeAllViews()

        currentFiles = FileCollector.getStagedFiles(this)
        if (currentFiles.isEmpty()) {
            closeMiniTray()
            shutdownService()
            return
        }

        val totalBytes = currentFiles.sumOf { it.length() }
        val sizeStr = formatFileSize(totalBytes)

        // 1. Header (BatchShare • 3 files • 4.2 MB)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12)
            }
        }

        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            val ip = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                marginEnd = dpToPx(10)
            }
            layoutParams = ip
        }
        headerRow.addView(icon)

        val headerTextLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleText = TextView(this).apply {
            text = "BatchShare"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val subText = TextView(this).apply {
            text = "${currentFiles.size} files held • $sizeStr"
            textSize = 12f
            setTextColor(Color.parseColor("#9CA3AF"))
        }
        headerTextLayout.addView(titleText)
        headerTextLayout.addView(subText)
        headerRow.addView(headerTextLayout)

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.parseColor("#9CA3AF"))
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(4), dpToPx(4), dpToPx(4))
            setOnClickListener { closeMiniTray() }
        }
        headerRow.addView(closeBtn)
        card.addView(headerRow)

        // 2. Scrollable File List (max 4 files tall)
        val scrollView = ScrollView(this).apply {
            val maxListH = dpToPx(180)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(14)
            }
            isVerticalScrollBarEnabled = true
        }

        val fileListCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        for (file in currentFiles) {
            val fileRow = createFileRow(file)
            fileListCol.addView(fileRow)
        }
        scrollView.addView(fileListCol)
        card.addView(scrollView)

        // 3. Action Buttons Row: [ ⚡ Zip & Share ] [ 📤 Raw ]
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(10)
            }
        }

        // Primary Zip & Share button
        val zipBtn = TextView(this).apply {
            text = "⚡ Zip & Share"
            textSize = 14f
            setTextColor(Color.parseColor("#0A0F1D"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.parseColor("#00D2FF"))
            }
            background = btnBg
            setPadding(0, dpToPx(12), 0, dpToPx(12))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f).apply {
                marginEnd = dpToPx(8)
            }
            layoutParams = lp

            setOnClickListener {
                closeMiniTray()
                serviceScope.launch(Dispatchers.IO) {
                    val success = ShareLauncher.zipAndShare(this@FloatingBubbleService, currentFiles, clearAfter = true)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            NotificationHelper.cancelNotification(this@FloatingBubbleService)
                            shutdownService()
                        }
                    }
                }
            }
        }
        actionsRow.addView(zipBtn)

        // Share Raw button
        val rawBtn = TextView(this).apply {
            text = "📤 Raw"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.parseColor("#1F2937"))
                setStroke(dpToPx(1), Color.parseColor("#374151"))
            }
            background = btnBg
            setPadding(0, dpToPx(12), 0, dpToPx(12))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f)
            layoutParams = lp

            setOnClickListener {
                closeMiniTray()
                serviceScope.launch(Dispatchers.IO) {
                    val success = ShareLauncher.shareUnzipped(this@FloatingBubbleService, currentFiles, clearAfter = true)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            NotificationHelper.cancelNotification(this@FloatingBubbleService)
                            shutdownService()
                        }
                    }
                }
            }
        }
        actionsRow.addView(rawBtn)
        card.addView(actionsRow)

        // 4. Secondary actions row: [ 🗑️ Clear ] and [ ↗️ More Options / Encryption ]
        val subActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val clearBtn = TextView(this).apply {
            text = "🗑️ Clear"
            textSize = 12f
            setTextColor(Color.parseColor("#EF4444"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(4), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener {
                FileCollector.clearStagedFiles(this@FloatingBubbleService)
                NotificationHelper.cancelNotification(this@FloatingBubbleService)
                Toast.makeText(this@FloatingBubbleService, "Cleared holding box", Toast.LENGTH_SHORT).show()
                closeMiniTray()
                shutdownService()
            }
        }
        subActionsRow.addView(clearBtn)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        subActionsRow.addView(spacer)

        val openAppBtn = TextView(this).apply {
            text = "⚙️ Passwords & Options ↗"
            textSize = 12f
            setTextColor(Color.parseColor("#60A5FA"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(8), dpToPx(4), dpToPx(4), dpToPx(4))
            setOnClickListener {
                closeMiniTray()
                val openIntent = Intent(this@FloatingBubbleService, BatchShareActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(openIntent)
            }
        }
        subActionsRow.addView(openAppBtn)
        card.addView(subActionsRow)
    }

    private fun createFileRow(file: File): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val rowBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10).toFloat()
                setColor(Color.parseColor("#1F2937").and(0x66FFFFFF))
            }
            background = rowBg
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }
            layoutParams = lp
        }

        // Badge
        val ext = file.extension.uppercase()
        val badgeColor = when (ext) {
            "PDF" -> Color.parseColor("#EF4444")
            "JPG", "JPEG", "PNG", "WEBP", "GIF" -> Color.parseColor("#06B6D4")
            "MP4", "MKV", "MOV" -> Color.parseColor("#A855F7")
            "MP3", "M4A", "WAV" -> Color.parseColor("#10B981")
            "APK" -> Color.parseColor("#F97316")
            "ZIP", "RAR", "7Z" -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#3B82F6")
        }

        val badge = TextView(this).apply {
            text = if (ext.length > 3) ext.take(3) else ext.ifEmpty { "FILE" }
            textSize = 9f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val bBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(badgeColor)
            }
            background = bBg
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            val bp = LinearLayout.LayoutParams(dpToPx(34), dpToPx(20)).apply {
                marginEnd = dpToPx(8)
            }
            layoutParams = bp
        }
        row.addView(badge)

        // File info
        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(this).apply {
            text = file.name
            textSize = 12f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val sizeView = TextView(this).apply {
            text = formatFileSize(file.length())
            textSize = 10f
            setTextColor(Color.parseColor("#9CA3AF"))
        }
        infoCol.addView(nameView)
        infoCol.addView(sizeView)
        row.addView(infoCol)

        // Remove (X) button
        val removeBtn = TextView(this).apply {
            text = "✕"
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
            setOnClickListener {
                FileCollector.removeStagedFile(this@FloatingBubbleService, file)
                refreshFilesAndUI()
            }
        }
        row.addView(removeBtn)

        return row
    }

    // -------------------------------------------------------------
    // Display Utilities
    // -------------------------------------------------------------
    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }

    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())

    private fun getScreenWidth(): Int {
        return resources.displayMetrics.widthPixels
    }

    private fun getScreenHeight(): Int {
        return resources.displayMetrics.heightPixels
    }
}
