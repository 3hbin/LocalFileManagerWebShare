package com.localfm.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.RemoteViews

class ShareWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_share)
            val running = ServerBridge.server?.isAlive == true
            views.setTextViewText(R.id.widgetLabel, if (running) "Tắt Share" else "Bật Share")
            val i = Intent(context, ShareWidget::class.java).setAction(ACTION)
            val pi = PendingIntent.getBroadcast(
                context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pi)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION) return
        if (ServerBridge.server?.isAlive == true) {
            WebShareService.stop(context)
        } else {
            val root = Environment.getExternalStorageDirectory() ?: context.filesDir
            WebShareService.start(context, root, FmSettings.port(context))
        }
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(android.content.ComponentName(context, ShareWidget::class.java))
        onUpdate(context, AppWidgetManager.getInstance(context), ids)
    }

    companion object { const val ACTION = "localfm.WIDGET_TOGGLE" }
}
