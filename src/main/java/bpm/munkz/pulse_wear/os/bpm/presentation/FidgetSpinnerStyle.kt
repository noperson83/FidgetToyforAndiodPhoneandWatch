package bpm.munkz.pulse_wear.os.bpm.presentation

internal enum class FidgetSpinnerCenterPiece {
    Disc,
    Ring,
    Hex,
    Square,
    Diamond,
    Target,
    Bolt,
}

internal data class FidgetSpinnerStyle(
    val index: Int,
    val englishName: String,
    val spanishName: String,
    val armColorArgb: Int?,
    val ballColorsArgb: List<Int>,
    val centerColorArgb: Int?,
    val ballScale: Float,
    val stemLengthScale: Float,
    val stemWidthScale: Float,
    val centerScale: Float,
    val centerPiece: FidgetSpinnerCenterPiece,
) {
    fun nameFor(language: AppLanguage): String {
        return when (language) {
            AppLanguage.English -> englishName
            AppLanguage.Spanish -> spanishName
        }
    }

    fun ballColor(index: Int): Int {
        return ballColorsArgb[index % ballColorsArgb.size]
    }
}

internal const val DEFAULT_FIDGET_SPINNER_STYLE_INDEX = 0

internal val FIDGET_SPINNER_STYLES = listOf(
    FidgetSpinnerStyle(
        index = 0,
        englishName = "Classic",
        spanishName = "Clasico",
        armColorArgb = null,
        ballColorsArgb = listOf(
            0xFFFFC857.toInt(),
            0xFFEF476F.toInt(),
            0xFF8D6BFF.toInt(),
        ),
        centerColorArgb = null,
        ballScale = 1f,
        stemLengthScale = 1f,
        stemWidthScale = 1f,
        centerScale = 1f,
        centerPiece = FidgetSpinnerCenterPiece.Disc,
    ),
    FidgetSpinnerStyle(
        index = 1,
        englishName = "Neon",
        spanishName = "Neon",
        armColorArgb = 0xFF13E8D4.toInt(),
        ballColorsArgb = listOf(
            0xFF00F5D4.toInt(),
            0xFF00BBF9.toInt(),
            0xFFFF2AD4.toInt(),
        ),
        centerColorArgb = 0xFFB8FF00.toInt(),
        ballScale = 0.82f,
        stemLengthScale = 1.14f,
        stemWidthScale = 0.78f,
        centerScale = 0.9f,
        centerPiece = FidgetSpinnerCenterPiece.Ring,
    ),
    FidgetSpinnerStyle(
        index = 2,
        englishName = "Chrome",
        spanishName = "Cromo",
        armColorArgb = 0xFFB8C5D6.toInt(),
        ballColorsArgb = listOf(
            0xFFF3F7FA.toInt(),
            0xFF8FA3B8.toInt(),
            0xFF5BD5FF.toInt(),
        ),
        centerColorArgb = 0xFFE7EDF4.toInt(),
        ballScale = 0.88f,
        stemLengthScale = 1.08f,
        stemWidthScale = 0.92f,
        centerScale = 0.86f,
        centerPiece = FidgetSpinnerCenterPiece.Bolt,
    ),
    FidgetSpinnerStyle(
        index = 3,
        englishName = "Ember",
        spanishName = "Brasa",
        armColorArgb = 0xFFFF5A1F.toInt(),
        ballColorsArgb = listOf(
            0xFFFF9F1C.toInt(),
            0xFFFF3D00.toInt(),
            0xFFFFD166.toInt(),
        ),
        centerColorArgb = 0xFFFFEB3B.toInt(),
        ballScale = 1.08f,
        stemLengthScale = 0.94f,
        stemWidthScale = 1.15f,
        centerScale = 1.06f,
        centerPiece = FidgetSpinnerCenterPiece.Hex,
    ),
    FidgetSpinnerStyle(
        index = 4,
        englishName = "Candy",
        spanishName = "Dulce",
        armColorArgb = 0xFFFF8CC8.toInt(),
        ballColorsArgb = listOf(
            0xFFFF71CE.toInt(),
            0xFFFFCE56.toInt(),
            0xFF5CE1E6.toInt(),
        ),
        centerColorArgb = 0xFFFFFFFF.toInt(),
        ballScale = 1.17f,
        stemLengthScale = 0.86f,
        stemWidthScale = 0.82f,
        centerScale = 0.88f,
        centerPiece = FidgetSpinnerCenterPiece.Diamond,
    ),
    FidgetSpinnerStyle(
        index = 5,
        englishName = "Orbit",
        spanishName = "Orbita",
        armColorArgb = 0xFF5470FF.toInt(),
        ballColorsArgb = listOf(
            0xFF52B6FF.toInt(),
            0xFF8D6BFF.toInt(),
            0xFF56F1C8.toInt(),
        ),
        centerColorArgb = 0xFF56F1C8.toInt(),
        ballScale = 0.7f,
        stemLengthScale = 1.22f,
        stemWidthScale = 0.65f,
        centerScale = 1.12f,
        centerPiece = FidgetSpinnerCenterPiece.Target,
    ),
    FidgetSpinnerStyle(
        index = 6,
        englishName = "Heavy",
        spanishName = "Pesado",
        armColorArgb = 0xFF3A3A46.toInt(),
        ballColorsArgb = listOf(
            0xFFB8FF00.toInt(),
            0xFFFFC857.toInt(),
            0xFFEF476F.toInt(),
        ),
        centerColorArgb = 0xFF8D6BFF.toInt(),
        ballScale = 1.25f,
        stemLengthScale = 0.88f,
        stemWidthScale = 1.34f,
        centerScale = 1.18f,
        centerPiece = FidgetSpinnerCenterPiece.Square,
    ),
)

internal fun fidgetSpinnerStyle(index: Int): FidgetSpinnerStyle {
    return FIDGET_SPINNER_STYLES.firstOrNull { style -> style.index == index }
        ?: FIDGET_SPINNER_STYLES.first()
}
