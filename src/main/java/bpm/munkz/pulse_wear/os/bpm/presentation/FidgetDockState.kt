package bpm.munkz.pulse_wear.os.bpm.presentation

import android.content.Context

internal const val FIDGET_DOCK_PAD_COUNT = 4
internal const val FIDGET_DOCK_PAD_STATES_KEY = "fidget_dock_pad_states"

internal val FIDGET_DOCK_SYMBOLS = listOf(
    "●",
    "◆",
    "X",
    "-",
    "#",
    "@",
    "*",
    "=",
    "+",
    "?",
)

internal fun Context.loadFidgetDockPadStates(): List<Int> {
    val preferences = getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE)
    val persistedStates = preferences.getString(FIDGET_DOCK_PAD_STATES_KEY, null)
    if (persistedStates != null) {
        return persistedStates
            .split(",")
            .mapNotNull { value -> value.toIntOrNull() }
            .map(::normalizeFidgetDockSymbolIndex)
            .let(::completeFidgetDockPadStates)
    }

    if (preferences.contains(LEGACY_FIDGET_DOCK_PAD_MASK_KEY)) {
        val legacyMask = preferences.getInt(LEGACY_FIDGET_DOCK_PAD_MASK_KEY, 0)
        return List(FIDGET_DOCK_PAD_COUNT) { padIndex ->
            if (legacyMask and (1 shl padIndex) != 0) 1 else 0
        }
    }

    return DEFAULT_FIDGET_DOCK_PAD_STATES
}

internal fun Context.advanceFidgetDockPad(padIndex: Int): List<Int> {
    if (padIndex !in 0 until FIDGET_DOCK_PAD_COUNT) return loadFidgetDockPadStates()
    val updatedStates = loadFidgetDockPadStates().mapIndexed { index, symbolIndex ->
        if (index == padIndex) {
            (symbolIndex + 1) % FIDGET_DOCK_SYMBOLS.size
        } else {
            symbolIndex
        }
    }
    getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(FIDGET_DOCK_PAD_STATES_KEY, updatedStates.joinToString(","))
        .apply()
    return updatedStates
}

internal fun fidgetDockSymbol(symbolIndex: Int): String {
    return FIDGET_DOCK_SYMBOLS[normalizeFidgetDockSymbolIndex(symbolIndex)]
}

private fun normalizeFidgetDockSymbolIndex(symbolIndex: Int): Int {
    return ((symbolIndex % FIDGET_DOCK_SYMBOLS.size) + FIDGET_DOCK_SYMBOLS.size) %
        FIDGET_DOCK_SYMBOLS.size
}

private fun completeFidgetDockPadStates(states: List<Int>): List<Int> {
    return List(FIDGET_DOCK_PAD_COUNT) { padIndex ->
        states.getOrElse(padIndex) { DEFAULT_FIDGET_DOCK_PAD_STATES[padIndex] }
    }
}

private const val LEGACY_FIDGET_DOCK_PAD_MASK_KEY = "fidget_dock_pad_mask"
private val DEFAULT_FIDGET_DOCK_PAD_STATES = listOf(0, 1, 2, 3)
