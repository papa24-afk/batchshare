package com.batchshare.wa.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.batchshare.wa.BatchShareActivity
import com.batchshare.wa.ui.formatFileSize
import java.io.File

object NotificationHelper {

    const val CHANNEL_ID = "batchshare_holding_box"
    const val NOTIFICATION_ID = 1001
    const val EXTRA_AUTO_ZIP = "extra_auto_zip"
    const val ACTION_CLEAR = "com.batchshare.wa.ACTION_CLEAR"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Holding Box",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows files waiting in your holding box"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(context: Context, files: List<File>) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (files.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        ensureChannel(context)

        val totalBytes = files.sumOf { it.length() }
        val sizeStr = formatFileSize(totalBytes)
        val countStr = "${files.size} file${if (files.size == 1) "" else "s"}"

        // Tap notification to open app
        val openIntent = Intent(context, BatchShareActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Zip & Share directly
        val zipIntent = Intent(context, BatchShareActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_AUTO_ZIP, true)
        }
        val zipPendingIntent = PendingIntent.getActivity(
            context,
            1,
            zipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Clear holding box
        val clearIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_CLEAR
        }
        val clearPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            clearIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = buildNotification(context, files, openPendingIntent, zipPendingIntent, clearPendingIntent)
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun buildNotification(
        context: Context,
        files: List<File>,
        openIntent: PendingIntent? = null,
        zipIntent: PendingIntent? = null,
        clearIntent: PendingIntent? = null
    ): android.app.Notification {
        ensureChannel(context)
        val totalBytes = files.sumOf { it.length() }
        val sizeStr = formatFileSize(totalBytes)
        val countStr = "${files.size} file${if (files.size == 1) "" else "s"}"

        val op = openIntent ?: PendingIntent.getActivity(
            context,
            0,
            Intent(context, BatchShareActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val zp = zipIntent ?: PendingIntent.getActivity(
            context,
            1,
            Intent(context, BatchShareActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_AUTO_ZIP, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cp = clearIntent ?: PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_CLEAR
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("BatchShare • $countStr ($sizeStr)")
            .setContentText("Files waiting in holding box")
            .setSmallIcon(com.batchshare.wa.R.mipmap.ic_launcher)
            .setContentIntent(op)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_menu_share, "Zip & Share", zp)
            .addAction(android.R.drawable.ic_menu_delete, "Clear", cp)
            .build()
    }

    fun cancelNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
