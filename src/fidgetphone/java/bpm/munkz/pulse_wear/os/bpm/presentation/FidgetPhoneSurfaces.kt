package bpm.munkz.pulse_wear.os.bpm.presentation

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.edit
import bpm.munkz.pulse_wear.os.bpm.BuildConfig

internal const val ACTION_REFRESH_FIDGET_DOCK =
    "bpm.munkz.pulse_wear.os.fidgettoy.action.REFRESH_FIDGET_DOCK"

internal data class FidgetPhoneSurfaceSnapshot(
    val favoriteToyId: Int,
    val favoriteToyName: String,
    val mainColorArgb: Int,
    val backgroundColorArgb: Int,
    val ringColorArgb: Int,
    val spinnerStyleIndex: Int,
    val spinnerMultiEnabled: Boolean,
    val hapticEnabled: Boolean,
    val fidgetCount: Int,
)

internal fun Context.loadFidgetPhoneSurfaceSnapshot(): FidgetPhoneSurfaceSnapshot {
    val preferences = getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE)
    val languageIndex = preferences.getInt(
        FIDGET_LANGUAGE_KEY,
        AppLanguages.indexOf(AppLanguage.English),
    )
    val language = AppLanguages.getOrElse(languageIndex) { AppLanguage.English }
    val favoriteToyId = preferences.getString(FIDGET_PINNED_TOYS_KEY, "")
        .orEmpty()
        .toPinnedToyIds()
        .firstOrNull()
        ?: FIDGET_SPINNER_INDEX
    val favoriteToy = FIDGET_TOY_INFOS.firstOrNull { toy -> toy.id == favoriteToyId }
        ?: FIDGET_TOY_INFOS.first()
    return FidgetPhoneSurfaceSnapshot(
        favoriteToyId = favoriteToy.id,
        favoriteToyName = favoriteToy.nameFor(language),
        mainColorArgb = preferences.getInt(FIDGET_MAIN_COLOR_KEY, NEON_GREEN_COLOR)
            .normalizedFidgetSurfaceColor(NEON_GREEN_COLOR),
        backgroundColorArgb = preferences.getInt(
            FIDGET_BACKGROUND_COLOR_KEY,
            DEFAULT_FIDGET_SURFACE_BACKGROUND,
        ).normalizedFidgetSurfaceColor(DEFAULT_FIDGET_SURFACE_BACKGROUND),
        ringColorArgb = preferences.getInt(FIDGET_RING_COLOR_KEY, NEON_GREEN_COLOR)
            .normalizedFidgetSurfaceColor(NEON_GREEN_COLOR, preserveRainbow = true),
        spinnerStyleIndex = fidgetSpinnerStyle(
            preferences.getInt(
                FIDGET_SPINNER_STYLE_KEY,
                DEFAULT_FIDGET_SPINNER_STYLE_INDEX,
            ),
        ).index,
        spinnerMultiEnabled = preferences.getBoolean(FIDGET_SPINNER_MULTI_KEY, true),
        hapticEnabled = preferences.getBoolean(FIDGET_HAPTIC_ENABLED_KEY, true),
        fidgetCount = preferences.getInt(FIDGET_COUNT_KEY, 0).coerceAtLeast(0),
    )
}

internal fun Context.incrementFidgetSurfaceCount(): Int {
    val preferences = getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE)
    val currentCount = preferences.getInt(FIDGET_COUNT_KEY, 0).coerceAtLeast(0)
    val nextCount = if (currentCount == Int.MAX_VALUE) currentCount else currentCount + 1
    preferences.edit { putInt(FIDGET_COUNT_KEY, nextCount) }
    return nextCount
}

internal fun Context.refreshFidgetPhoneSurfaces(preferredToyId: Int? = null) {
    if (BuildConfig.APP_EDITION != FIDGET_PHONE_EDITION) return

    val favoriteToyId = preferredToyId
        ?.takeIf { requestedId -> FIDGET_TOY_INFOS.any { toy -> toy.id == requestedId } }
        ?: loadFidgetPhoneSurfaceSnapshot().favoriteToyId
    updateFidgetLauncherIcon(favoriteToyId)
    refreshFidgetDockWidget()
}

internal fun Context.refreshFidgetDockWidget() {
    if (BuildConfig.APP_EDITION != FIDGET_PHONE_EDITION) return
    sendBroadcast(Intent(ACTION_REFRESH_FIDGET_DOCK).setPackage(packageName))
}

internal fun Context.requestFidgetDockPin(): Boolean {
    if (BuildConfig.APP_EDITION != FIDGET_PHONE_EDITION) return false
    val appWidgetManager = getSystemService(AppWidgetManager::class.java)
    if (!appWidgetManager.isRequestPinAppWidgetSupported) return false
    return appWidgetManager.requestPinAppWidget(
        ComponentName(this, FIDGET_DOCK_PROVIDER_CLASS),
        null,
        null,
    )
}

internal fun Context.openFidgetSpinnerWallpaper() {
    if (BuildConfig.APP_EDITION != FIDGET_PHONE_EDITION) return
    val wallpaperComponent = ComponentName(this, FIDGET_SPINNER_WALLPAPER_CLASS)
    val previewIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, wallpaperComponent)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        startActivity(previewIntent)
    }.recoverCatching {
        startActivity(
            Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun Context.updateFidgetLauncherIcon(favoriteToyId: Int) {
    val selectedAlias = FIDGET_LAUNCHER_ALIASES[favoriteToyId]
        ?: FIDGET_LAUNCHER_ALIASES.getValue(FIDGET_SPINNER_INDEX)
    val packageManager = packageManager

    runCatching {
        FIDGET_LAUNCHER_ALIASES.values.forEach { alias ->
            val component = ComponentName(this, alias)
            val shouldBeEnabled = alias == selectedAlias
            if (component.isFidgetLauncherAliasEnabled(packageManager) != shouldBeEnabled) {
                packageManager.setComponentEnabledSetting(
                    component,
                    if (shouldBeEnabled) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    },
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

private fun ComponentName.isFidgetLauncherAliasEnabled(packageManager: PackageManager): Boolean {
    return when (packageManager.getComponentEnabledSetting(this)) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
        -> false
        else -> className == FIDGET_DEFAULT_LAUNCHER_ALIAS
    }
}

private fun Int.normalizedFidgetSurfaceColor(
    fallback: Int,
    preserveRainbow: Boolean = false,
): Int {
    if (preserveRainbow && this == RAINBOW_COLOR) return this
    val selected = if (this == RAINBOW_COLOR) fallback else this
    return selected or 0xFF000000.toInt()
}

private const val FIDGET_PHONE_EDITION = "fidgetphone"
private const val FIDGET_PRESENTATION_PACKAGE =
    "bpm.munkz.pulse_wear.os.bpm.presentation"
private const val FIDGET_DOCK_PROVIDER_CLASS =
    "$FIDGET_PRESENTATION_PACKAGE.FidgetDockWidgetProvider"
private const val FIDGET_SPINNER_WALLPAPER_CLASS =
    "$FIDGET_PRESENTATION_PACKAGE.FidgetSpinnerWallpaperService"
private const val FIDGET_LAUNCHER_PACKAGE =
    "$FIDGET_PRESENTATION_PACKAGE.launcher"
private const val FIDGET_DEFAULT_LAUNCHER_ALIAS =
    "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherSpinStorm"
private const val DEFAULT_FIDGET_SURFACE_BACKGROUND = 0xFF061112.toInt()

private val FIDGET_LAUNCHER_ALIASES = mapOf(
    1 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherSpinStorm",
    2 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherFlipStack",
    3 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherGridStepper",
    4 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherButtonDrift",
    5 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherColorPopHunt",
    6 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherBounceShot",
    7 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherMazeShuffle",
    8 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherSquishPop",
    9 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherMagSnap",
    10 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherPopGrid",
    11 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherInfinityFlip",
    12 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherRatchetRing",
    13 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherLiquidMaze",
    14 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherGearJam",
    15 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherWorryStone",
    16 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherKeyClicks",
    17 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherZenTrace",
    18 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherBeatMachine",
    19 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherWindowSlide",
    20 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherDoorSwing",
    21 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherLightFlick",
    22 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherFanBreeze",
    23 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherSinkFlow",
    24 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherSymbolDock",
    25 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherCenterDrop",
    26 to "$FIDGET_LAUNCHER_PACKAGE.FidgetLauncherBallSort",
)
