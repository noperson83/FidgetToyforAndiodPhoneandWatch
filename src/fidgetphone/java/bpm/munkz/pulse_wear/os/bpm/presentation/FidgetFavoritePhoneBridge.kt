package bpm.munkz.pulse_wear.os.bpm.presentation

import android.content.Context
import android.content.Intent

internal const val ACTION_OPEN_FIDGET_FAVORITE =
    "bpm.munkz.pulse_wear.os.fidgettoy.action.OPEN_FAVORITE"
internal const val EXTRA_OPEN_FIDGET_TOY_ID =
    "bpm.munkz.pulse_wear.os.fidgettoy.extra.TOY_ID"

internal fun Intent?.requestedFidgetToyIndex(): Int? {
    if (this == null || action != ACTION_OPEN_FIDGET_FAVORITE) return null
    val requestedId = getIntExtra(EXTRA_OPEN_FIDGET_TOY_ID, -1)
    return requestedId.takeIf { id -> FIDGET_TOY_INFOS.any { toy -> toy.id == id } }
}

internal fun Context.requestFidgetFavoriteComplicationUpdates() = Unit
