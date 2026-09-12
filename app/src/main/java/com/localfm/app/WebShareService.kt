package com.localfm.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class WebShareService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServer()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val rootPath = intent?.getStringExtra(EXTRA_ROOT) ?: return START_NOT_STICKY
        val port = intent.getIntExtra(EXTRA_PORT, FmSettings.port(this))
        ensureChannel()
        startForeground(NOTIF, notification("Đang chia sẻ cổng $port"))
        stopServer()
        return try {
            val server = LocalWebServer(port, File(rootPath)).apply {
                sharePassword = FmSettings.webPassword(this@WebShareService)
                hideSizes = FmSettings.hideSizes(this@WebShareService)
                receiveOnly = FmSettings.receiveOnly(this@WebShareService)
            }
            server.start()
            ServerBridge.server = server
            ServerBridge.port = port
            val mins = FmSettings.autoOffMin(this)
            if (mins > 0) {
                android.os.Handler(mainLooper).postDelayed({
                    startService(Intent(this, WebShareService::class.java).setAction(ACTION_STOP))
                }, mins * 60_000L)
            }
            START_STICKY
        } catch (_: Exception) {
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun stopServer() {
        try { ServerBridge.server?.stop() } catch (_: Exception) {}
        ServerBridge.server = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH, "Web Share", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CH)
        .setSmallIcon(android.R.drawable.ic_menu_share)
        .setContentTitle("Web Share đang bật")
        .setContentText(text)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 1, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel, "Tắt",
            PendingIntent.getService(
                this, 2, Intent(this, WebShareService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    companion object {
        const val ACTION_STOP = "localfm.STOP_SHARE"
        const val EXTRA_ROOT = "root"
        const val EXTRA_PORT = "port"
        private const val CH = "web_share"
        private const val NOTIF = 4201
        fun start(ctx: Context, root: File, port: Int) {
            val i = Intent(ctx, WebShareService::class.java)
                .putExtra(EXTRA_ROOT, root.absolutePath)
                .putExtra(EXTRA_PORT, port)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, WebShareService::class.java).setAction(ACTION_STOP))
        }
    }
}

object ServerBridge {
    @Volatile var server: LocalWebServer? = null
    @Volatile var port: Int = 8080
}
