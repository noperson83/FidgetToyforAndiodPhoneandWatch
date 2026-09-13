package bpm.munkz.pulse_wear.os.bpm.presentation

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

enum class AppLanguage {
    English,
    Spanish,
}

internal val AppLanguages = listOf(
    AppLanguage.English,
    AppLanguage.Spanish,
)

enum class BeatSoundMode(val persistedValue: Int) {
    Clicks(0),
    Wood(1),
    Bell(2),
    ;

    companion object {
        fun fromPersistedValue(value: Int): BeatSoundMode =
            entries.firstOrNull { it.persistedValue == value } ?: Clicks
    }
}

enum class AccentIntensityMode(val persistedValue: Int) {
    Big(0),
    Medium(1),
    Little(2),
    Silent(3),
    ;

    companion object {
        fun fromPersistedValue(value: Int): AccentIntensityMode =
            entries.firstOrNull { it.persistedValue == value } ?: Big
    }
}

enum class FidgetRewardStyle(val persistedValue: Int) {
    Calm(0),
    Glow(1),
    Celebrate(2),
    ;

    companion object {
        fun fromPersistedValue(value: Int): FidgetRewardStyle =
            entries.firstOrNull { it.persistedValue == value } ?: Glow
    }
}

internal fun FidgetRewardStyle.labelFor(language: AppLanguage): String = when (this) {
    FidgetRewardStyle.Calm -> if (language == AppLanguage.English) "Calm" else "Calma"
    FidgetRewardStyle.Glow -> if (language == AppLanguage.English) "Glow" else "Brillo"
    FidgetRewardStyle.Celebrate -> if (language == AppLanguage.English) "Celebrate" else "Celebrar"
}

internal const val NEON_GREEN_COLOR = -6422784
internal const val RAINBOW_COLOR = 0x00ABCDEF

internal val PulseColorOptions = listOf(
    -47872,
    -16715777,
    -32512,
    -7667457,
    NEON_GREEN_COLOR,
    -1,
    -65281,
    RAINBOW_COLOR,
)

internal val ThemeMainColorOptions = listOf(
    -47872,
    -16715777,
    -32512,
    -7667457,
    NEON_GREEN_COLOR,
    -1,
    -65281,
)

internal val RainbowColors = listOf(
    Color(0xFFFF3B30),
    Color(0xFFFFD60A),
    Color(0xFF32D74B),
    Color(0xFF64D2FF),
    Color(0xFFBF5AF2),
    Color(0xFFFF2D55),
)

internal val ThemeBackgroundColorOptions = listOf(
    0xFF000000.toInt(),
    0xFF111827.toInt(),
    0xFF001F24.toInt(),
    0xFF1A1028.toInt(),
    0xFF24120A.toInt(),
    0xFF102016.toInt(),
)

internal data class AccentIntensityChoice(
    val mode: AccentIntensityMode,
    val label: String,
    val spanishLabel: String = label,
)

internal val AccentIntensityChoices = listOf(
    AccentIntensityChoice(AccentIntensityMode.Big, "Big", "Gran"),
    AccentIntensityChoice(AccentIntensityMode.Medium, "Mid", "Med"),
    AccentIntensityChoice(AccentIntensityMode.Little, "Lil", "Peq"),
    AccentIntensityChoice(AccentIntensityMode.Silent, "Sil", "Sil"),
)

internal fun AccentIntensityChoice.labelFor(language: AppLanguage): String {
    return when (language) {
        AppLanguage.English -> label
        AppLanguage.Spanish -> spanishLabel
    }
}

internal fun isRainbowColor(colorArgb: Int): Boolean = colorArgb == RAINBOW_COLOR

internal fun colorFromChoice(colorArgb: Int): Color {
    return if (isRainbowColor(colorArgb)) Color(NEON_GREEN_COLOR) else Color(colorArgb)
}

internal fun selectedSwatchMarkColor(colorArgb: Int): Color {
    return if (isRainbowColor(colorArgb) || relativeLuminance(colorArgb) > 0.46) {
        Color.Black
    } else {
        Color.White
    }
}

internal fun readableTextColorFor(colorArgb: Int): Color {
    return if (relativeLuminance(colorArgb) > 0.46) Color.Black else Color.White
}

private fun relativeLuminance(colorArgb: Int): Double {
    val red = linearColorChannel(colorArgb, 16)
    val green = linearColorChannel(colorArgb, 8)
    val blue = linearColorChannel(colorArgb, 0)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

private fun linearColorChannel(colorArgb: Int, shift: Int): Double {
    val channel = ((colorArgb shr shift) and 0xFF) / 255.0
    return if (channel <= 0.03928) {
        channel / 12.92
    } else {
        ((channel + 0.055) / 1.055).pow(2.4)
    }
}
