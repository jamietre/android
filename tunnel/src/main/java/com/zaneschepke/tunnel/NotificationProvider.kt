package com.zaneschepke.tunnel

import android.app.Notification
import android.content.Context

interface NotificationProvider {
    val vpnInitNotification: Notification
    val proxyInitNotification: Notification
    val vpnNotificationId: Int
    val proxyNotificationId: Int

    fun refreshTile(context: Context)
}
