package com.batchshare.wa.core

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
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

    // Overlay Views
    private var bubbleContainer: FrameLayout? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var dismissContainer: FrameLayout? = null
    private var dismissParams: WindowManager.LayoutParams? = null

    private var trayContainer: FrameLayout? = null
    private var trayParams: WindowManager.LayoutParams? = null

    private var badgeView: TextView? = null
    private var isTrayExpanded = false
    private var isOverDismissZone = false

    // Files
    private var currentFiles = listOf<File>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val initialFiles = FileCollector.getStagedFiles(this)
        val notif = NotificationHelper.buildNotification(this, initialFiles)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notif)
        }

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
    // Bubble View: Native Material 3 Touch & Appearance
    // -------------------------------------------------------------
    @SuppressLint("ClickableViewAccessibility")
    private fun createBubbleView() {
        val wm = windowManager ?: return

        val bubbleSize = dpToPx(56)
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
            x = getScreenWidth() - bubbleSize - dpToPx(8)
            y = getScreenHeight() / 3
        }
        bubbleParams = params

        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(bubbleSize, bubbleSize)
        }

        // Circular background with subtle dark border
        val iconSize = dpToPx(50)
        val iconImageView = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0F172A")) // Slate 900
                setStroke(dpToPx(1f), Color.parseColor("#334155")) // Slate 700 subtle outline
            }
            background = iconBg
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER
            }
            elevation = dpToPx(6).toFloat()
        }
        container.addView(iconImageView)

        // Count Badge (Clean Material 3 Pill)
        val badge = TextView(this).apply {
            textSize = 10.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            val badgeBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2563EB")) // Primary Blue
                setStroke(dpToPx(1.5f), Color.parseColor("#0F172A")) // Notch cutout ring
            }
            background = badgeBg
            layoutParams = FrameLayout.LayoutParams(dpToPx(20), dpToPx(20)).apply {
                gravity = Gravity.TOP or Gravity.END
                marginEnd = dpToPx(0)
                topMargin = dpToPx(0)
            }
            elevation = dpToPx(8).toFloat()
        }
        badgeView = badge
        container.addView(badge)

        // Dragging & Edge Snapping
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        container.setOnTouchListener { v, event ->
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
                            // Detached
                        }

                        // Check dismiss target proximity
                        val inDismiss = isOverDismissTarget(curParams.x, curParams.y)
                        if (inDismiss != isOverDismissZone) {
                            isOverDismissZone = inDismiss
                            highlightDismissTarget(inDismiss)
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        showDismissView(false)
                        val inDismiss = isOverDismissTarget(curParams.x, curParams.y)
                        if (inDismiss) {
                            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            FileCollector.clearStagedFiles(this)
                            NotificationHelper.cancelNotification(this)
                            Toast.makeText(this, "Staging tray cleared", Toast.LENGTH_SHORT).show()
                            shutdownService()
                        } else {
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            snapBubbleToEdge(curParams.x)
                        }
                    } else {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
        val bubbleSize = dpToPx(56)
        val margin = dpToPx(6)
        val targetX = if (currentX + bubbleSize / 2 < screenW / 2) {
            margin
        } else {
            screenW - bubbleSize - margin
        }

        val anim = ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 180
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
    // Dismiss Target: Sleek Frosted Pill at Bottom Center
    // -------------------------------------------------------------
    private fun createDismissView() {
        val wm = windowManager ?: return
        val size = dpToPx(54)
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
            y = dpToPx(40)
        }
        dismissParams = params

        val container = FrameLayout(this).apply {
            visibility = View.GONE
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1C1917")) // Dark Charcoal
                setStroke(dpToPx(1f), Color.parseColor("#44403C"))
            }
            background = bg
            elevation = dpToPx(10).toFloat()
        }

        val closeIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble_close)
            setColorFilter(Color.parseColor("#A8A29E"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(16)
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(closeIcon)

        dismissContainer = container
        try {
            wm.addView(container, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showDismissView(show: Boolean) {
        val view = dismissContainer ?: return
        if (show) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate().alpha(1f).setDuration(120).start()
        } else {
            view.animate().alpha(0f).setDuration(120).withEndAction {
                view.visibility = View.GONE
            }.start()
        }
    }

    private fun isOverDismissTarget(bubbleX: Int, bubbleY: Int): Boolean {
        val screenW = getScreenWidth()
        val screenH = getScreenHeight()
        val dismissCenterX = screenW / 2
        val dismissCenterY = screenH - dpToPx(40) - dpToPx(27)

        val bubbleCenterX = bubbleX + dpToPx(28)
        val bubbleCenterY = bubbleY + dpToPx(28)

        val distSq = (bubbleCenterX - dismissCenterX) * (bubbleCenterX - dismissCenterX) +
                (bubbleCenterY - dismissCenterY) * (bubbleCenterY - dismissCenterY)

        val threshold = dpToPx(72)
        return distSq < (threshold * threshold)
    }

    private fun highlightDismissTarget(highlight: Boolean) {
        dismissContainer?.let { view ->
            val bg = view.background as? GradientDrawable ?: return
            val icon = view.getChildAt(0) as? ImageView ?: return
            if (highlight) {
                bg.setColor(Color.parseColor("#7F1D1D")) // Deep Crimson
                bg.setStroke(dpToPx(1.5f), Color.parseColor("#EF4444"))
                icon.setColorFilter(Color.WHITE)
                view.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start()
            } else {
                bg.setColor(Color.parseColor("#1C1917"))
                bg.setStroke(dpToPx(1f), Color.parseColor("#44403C"))
                icon.setColorFilter(Color.parseColor("#A8A29E"))
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
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
    // Mini-Tray: Material 3 Bottom Sheet / Card
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

        // Translucent background scrim
        val scrim = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#80000000"))
            setOnClickListener {
                closeMiniTray()
            }
        }

        // Bottom-anchored Sheet Container
        val sheetW = minOf(getScreenWidth() - dpToPx(24), dpToPx(380))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(20).toFloat()
                setColor(Color.parseColor("#0F172A")) // Deep Slate 900
                setStroke(dpToPx(1f), Color.parseColor("#1E293B")) // Slate 800
            }
            background = cardBg
            elevation = dpToPx(24).toFloat()
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(16))
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(sheetW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(24)
            }
        }

        scrim.addView(card)
        trayContainer = scrim

        try {
            wm.addView(scrim, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Animate slide up
        card.translationY = dpToPx(100).toFloat()
        card.alpha = 0f
        card.animate().translationY(0f).alpha(1f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()

        updateTrayContent()
    }

    private fun closeMiniTray() {
        val scrim = trayContainer ?: return
        val card = scrim.getChildAt(0) as? LinearLayout
        if (card != null) {
            card.animate()
                .translationY(dpToPx(80).toFloat())
                .alpha(0f)
                .setDuration(150)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        isTrayExpanded = false
                        removeTrayView()
                    }
                })
                .start()
        } else {
            isTrayExpanded = false
            removeTrayView()
        }
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

        // 0. Drag Handle Indicator
        val dragHandle = View(this).apply {
            val hBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(2).toFloat()
                setColor(Color.parseColor("#334155")) // Slate 700
            }
            background = hBg
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(12)
            }
        }
        card.addView(dragHandle)

        // 1. Header (Staged Files • X items • X MB)
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
            layoutParams = LinearLayout.LayoutParams(dpToPx(26), dpToPx(26)).apply {
                marginEnd = dpToPx(10)
            }
        }
        headerRow.addView(icon)

        val headerTextLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleText = TextView(this).apply {
            text = "Staged Queue"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subText = TextView(this).apply {
            text = "${currentFiles.size} ${if (currentFiles.size == 1) "file" else "files"} • $sizeStr"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8")) // Slate 400
        }
        headerTextLayout.addView(titleText)
        headerTextLayout.addView(subText)
        headerRow.addView(headerTextLayout)

        val closeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble_close)
            setColorFilter(Color.parseColor("#94A3B8"))
            background = makeRippleBackground(Color.parseColor("#1E293B"), dpToPx(16))
            val pad = dpToPx(8)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
            setOnClickListener { closeMiniTray() }
        }
        headerRow.addView(closeBtn)
        card.addView(headerRow)

        // 2. Scrollable File List (max 180dp height)
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12)
            }
            isVerticalScrollBarEnabled = false
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

        // 3. Primary & Secondary Action Row: [ Zip & Share ] [ Share Raw ]
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(10)
            }
        }

        // Primary: Zip & Share
        val zipBtn = TextView(this).apply {
            text = "Zip & Share"
            textSize = 13.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            background = makeRippleBackground(Color.parseColor("#2563EB"), dpToPx(10))
            setPadding(0, dpToPx(11), 0, dpToPx(11))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f).apply {
                marginEnd = dpToPx(8)
            }

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

        // Secondary: Share Raw
        val rawBtn = TextView(this).apply {
            text = "Share Raw"
            textSize = 13.5f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            background = makeRippleBackground(Color.parseColor("#1E293B"), dpToPx(10), strokeColor = Color.parseColor("#334155"))
            setPadding(0, dpToPx(11), 0, dpToPx(11))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

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

        // 4. Subtle Footer: [ Clear queue ] and [ Options ]
        val footerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val clearBtn = TextView(this).apply {
            text = "Clear queue"
            textSize = 12f
            setTextColor(Color.parseColor("#F87171")) // Soft Red
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dpToPx(4), dpToPx(6), dpToPx(8), dpToPx(6))
            setOnClickListener {
                FileCollector.clearStagedFiles(this@FloatingBubbleService)
                NotificationHelper.cancelNotification(this@FloatingBubbleService)
                Toast.makeText(this@FloatingBubbleService, "Queue cleared", Toast.LENGTH_SHORT).show()
                closeMiniTray()
                shutdownService()
            }
        }
        footerRow.addView(clearBtn)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        footerRow.addView(spacer)

        val optionsBtn = TextView(this).apply {
            text = "Archive options"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8")) // Slate 400
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dpToPx(8), dpToPx(6), dpToPx(4), dpToPx(6))
            setOnClickListener {
                closeMiniTray()
                val openIntent = Intent(this@FloatingBubbleService, BatchShareActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(openIntent)
            }
        }
        footerRow.addView(optionsBtn)
        card.addView(footerRow)
    }

    private fun createFileRow(file: File): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val rowBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#1E293B")) // Slate 800
            }
            background = rowBg
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }
        }

        // Tonal Chip for File Extension
        val ext = file.extension.uppercase()
        val (badgeBgColor, badgeTextColor) = when (ext) {
            "PDF" -> Pair(Color.parseColor("#3B1A1E"), Color.parseColor("#F87171"))
            "JPG", "JPEG", "PNG", "WEBP", "GIF" -> Pair(Color.parseColor("#0E2E3B"), Color.parseColor("#38BDF8"))
            "MP4", "MKV", "MOV" -> Pair(Color.parseColor("#2E1065"), Color.parseColor("#C084FC"))
            "MP3", "M4A", "WAV" -> Pair(Color.parseColor("#052E16"), Color.parseColor("#4ADE80"))
            "APK", "ZIP", "RAR", "7Z" -> Pair(Color.parseColor("#451A03"), Color.parseColor("#FBBF24"))
            else -> Pair(Color.parseColor("#172554"), Color.parseColor("#60A5FA"))
        }

        val badge = TextView(this).apply {
            text = if (ext.length > 3) ext.take(3) else ext.ifEmpty { "DOC" }
            textSize = 9.5f
            setTextColor(badgeTextColor)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            val bBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(4).toFloat()
                setColor(badgeBgColor)
            }
            background = bBg
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2))
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(18)).apply {
                marginEnd = dpToPx(10)
            }
        }
        row.addView(badge)

        // File info
        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(this).apply {
            text = file.name
            textSize = 12.5f
            setTextColor(Color.parseColor("#F1F5F9"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val sizeView = TextView(this).apply {
            text = formatFileSize(file.length())
            textSize = 10.5f
            setTextColor(Color.parseColor("#64748B")) // Slate 500
        }
        infoCol.addView(nameView)
        infoCol.addView(sizeView)
        row.addView(infoCol)

        // Delete button
        val removeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble_close)
            setColorFilter(Color.parseColor("#64748B"))
            val pad = dpToPx(6)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dpToPx(26), dpToPx(26))
            setOnClickListener {
                FileCollector.removeStagedFile(this@FloatingBubbleService, file)
                refreshFilesAndUI()
            }
        }
        row.addView(removeBtn)

        return row
    }

    // -------------------------------------------------------------
    // Ripple Background Helper
    // -------------------------------------------------------------
    private fun makeRippleBackground(bgColor: Int, cornerRadiusPx: Int, strokeColor: Int? = null): RippleDrawable {
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadiusPx.toFloat()
            setColor(bgColor)
            if (strokeColor != null) {
                setStroke(dpToPx(1f), strokeColor)
            }
        }
        val mask = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadiusPx.toFloat()
            setColor(Color.WHITE)
        }
        return RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#26FFFFFF")),
            shape,
            mask
        )
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
