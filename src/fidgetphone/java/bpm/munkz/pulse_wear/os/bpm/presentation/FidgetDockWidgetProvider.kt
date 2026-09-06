package bpm.munkz.pulse_wear.os.bpm.presentation

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.net.toUri
import bpm.munkz.pulse_wear.os.bpm.R

class FidgetDockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, context.buildFidgetDockViews())
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_FIDGET_DOCK_PAD -> {
                val padIndex = intent.getIntExtra(EXTRA_FIDGET_DOCK_PAD, -1)
                if (padIndex in FIDGET_DOCK_PAD_IDS.indices) {
                    context.advanceFidgetDockPad(padIndex)
                    context.incrementFidgetSurfaceCount()
                    context.vibrateFidgetDock()
                }
                context.updateAllFidgetDockWidgets()
            }
            ACTION_REFRESH_FIDGET_DOCK -> context.updateAllFidgetDockWidgets()
        }
    }

    private fun Context.buildFidgetDockViews(): RemoteViews {
        val snapshot = loadFidgetPhoneSurfaceSnapshot()
        val views = RemoteViews(packageName, R.layout.fidget_dock_widget)
        val padStates = loadFidgetDockPadStates()

        views.setTextViewText(R.id.fidget_dock_favorite, snapshot.favoriteToyName)
        views.setTextViewText(R.id.fidget_dock_count, toDockCountLabel(snapshot.fidgetCount))
        views.setTextColor(R.id.fidget_dock_title, snapshot.mainColorArgb)
        views.setTextColor(R.id.fidget_dock_favorite, snapshot.ringColorArgb)
        views.setTextColor(R.id.fidget_dock_count, snapshot.mainColorArgb)

        val openFavoriteIntent = Intent(this, MainActivity::class.java)
            .setAction(ACTION_OPEN_FIDGET_FAVORITE)
            .putExtra(EXTRA_OPEN_FIDGET_TOY_ID, snapshot.favoriteToyId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openFavoritePendingIntent = PendingIntent.getActivity(
            this,
            FIDGET_DOCK_OPEN_REQUEST_CODE,
            openFavoriteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.fidget_dock_header, openFavoritePendingIntent)
        views.setOnClickPendingIntent(R.id.fidget_dock_logo, openFavoritePendingIntent)

        FIDGET_DOCK_PAD_IDS.forEachIndexed { padIndex, viewId ->
            val symbolIndex = padStates.getOrElse(padIndex) { padIndex }
            val symbol = fidgetDockSymbol(symbolIndex)
            views.setTextViewText(viewId, symbol)
            views.setTextViewTextSize(
                viewId,
                TypedValue.COMPLEX_UNIT_SP,
                if (symbolIndex == 0 || symbolIndex == 1) 38f else 32f,
            )
            views.setFloat(viewId, "setAlpha", if (symbolIndex == 0) 0.78f else 1f)
            views.setContentDescription(viewId, getString(R.string.fidget_dock_pad_symbol, symbol))
            views.setOnClickPendingIntent(viewId, dockPadPendingIntent(padIndex))
        }
        return views
    }

    private fun Context.dockPadPendingIntent(padIndex: Int): PendingIntent {
        val intent = Intent(this, FidgetDockWidgetProvider::class.java)
            .setAction(ACTION_FIDGET_DOCK_PAD)
            .setData("munkz-fidget://dock/pad/$padIndex".toUri())
            .putExtra(EXTRA_FIDGET_DOCK_PAD, padIndex)
        return PendingIntent.getBroadcast(
            this,
            FIDGET_DOCK_PAD_REQUEST_CODE + padIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun Context.vibrateFidgetDock() {
        if (!loadFidgetPhoneSurfaceSnapshot().hapticEnabled) return
        val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(22L, 108))
        }
    }

    private fun Context.updateAllFidgetDockWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val component = ComponentName(this, FidgetDockWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(component)
        onUpdate(this, appWidgetManager, widgetIds)
    }

    private fun Context.toDockCountLabel(count: Int): String {
        val compactCount = when {
            count >= 1_000_000_000 -> "${count / 1_000_000_000}B"
            count >= 1_000_000 -> "${count / 1_000_000}M"
            count >= 10_000 -> "${count / 1_000}K"
            else -> count.toString()
        }
        return getString(R.string.fidget_dock_count, compactCount)
    }

    private companion object {
        const val ACTION_FIDGET_DOCK_PAD =
            "bpm.munkz.pulse_wear.os.fidgettoy.action.FIDGET_DOCK_PAD"
        const val EXTRA_FIDGET_DOCK_PAD =
            "bpm.munkz.pulse_wear.os.fidgettoy.extra.FIDGET_DOCK_PAD"
        const val FIDGET_DOCK_OPEN_REQUEST_CODE = 43_100
        const val FIDGET_DOCK_PAD_REQUEST_CODE = 43_110

        val FIDGET_DOCK_PAD_IDS = intArrayOf(
            R.id.fidget_dock_pad_1,
            R.id.fidget_dock_pad_2,
            R.id.fidget_dock_pad_3,
            R.id.fidget_dock_pad_4,
        )
    }
}
