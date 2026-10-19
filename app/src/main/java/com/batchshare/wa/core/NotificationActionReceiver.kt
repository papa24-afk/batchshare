package com.batchshare.wa.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        var onFilesClearedListener: (() -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == NotificationHelper.ACTION_CLEAR) {
            FileCollector.clearStagedFiles(context)
            NotificationHelper.cancelNotification(context)
            onFilesClearedListener?.invoke()
            Toast.makeText(context, "Holding box cleared", Toast.LENGTH_SHORT).show()
        }
    }
}
