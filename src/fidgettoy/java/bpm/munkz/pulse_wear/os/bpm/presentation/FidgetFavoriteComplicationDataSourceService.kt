package bpm.munkz.pulse_wear.os.bpm.presentation

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import bpm.munkz.pulse_wear.os.bpm.BuildConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val ACTION_OPEN_FIDGET_FAVORITE =
    "bpm.munkz.pulse_wear.os.fidgettoy.action.OPEN_FAVORITE"
internal const val EXTRA_OPEN_FIDGET_TOY_ID =
    "bpm.munkz.pulse_wear.os.fidgettoy.extra.TOY_ID"

internal fun Intent?.requestedFidgetToyIndex(): Int? {
    if (this == null || action != ACTION_OPEN_FIDGET_FAVORITE) return null
    val requestedId = getIntExtra(EXTRA_OPEN_FIDGET_TOY_ID, -1)
    return requestedId.takeIf { id -> FIDGET_TOY_INFOS.any { toy -> toy.id == id } }
}

internal fun Context.requestFidgetFavoriteComplicationUpdates() {
    if (BuildConfig.APP_EDITION != "fidgettoy") return
    ComplicationDataSourceUpdateRequester.create(
        context = this,
        complicationDataSourceComponent = ComponentName(
            this,
            FidgetFavoriteComplicationDataSourceService::class.java,
        ),
    ).requestUpdateAll()
}

class FidgetFavoriteComplicationDataSourceService :
    SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return previewSnapshot().toComplicationData(type)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val snapshot = loadFavoriteSnapshot()
        return snapshot.toComplicationData(request.complicationType)
            ?: snapshot.toShortTextComplicationData()
    }

    private fun FidgetFavoriteSnapshot.toComplicationData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.PHOTO_IMAGE -> toPhotoImageComplicationData()
            ComplicationType.SHORT_TEXT -> toShortTextComplicationData()
            else -> null
        }
    }

    private fun FidgetFavoriteSnapshot.toPhotoImageComplicationData(): ComplicationData {
        val description = PlainComplicationText.Builder("Favorite fidget $name").build()
        return PhotoImageComplicationData.Builder(
            photoImage = Icon.createWithBitmap(renderFidgetFavoriteBitmap(this)),
            contentDescription = description,
        )
            .setTapAction(openFavoritePendingIntent(toyId))
            .build()
    }

    private fun FidgetFavoriteSnapshot.toShortTextComplicationData(): ComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(name).build(),
            contentDescription = PlainComplicationText.Builder("Favorite fidget $name").build(),
        )
            .setTitle(PlainComplicationText.Builder("Fidget").build())
            .setTapAction(openFavoritePendingIntent(toyId))
            .build()
    }

    private fun openFavoritePendingIntent(toyId: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(ACTION_OPEN_FIDGET_FAVORITE)
            .putExtra(EXTRA_OPEN_FIDGET_TOY_ID, toyId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            FIDGET_FAVORITE_PENDING_INTENT_BASE + toyId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun Context.loadFavoriteSnapshot(): FidgetFavoriteSnapshot {
        val preferences = getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE)
        val languageIndex = preferences.getInt(
            FIDGET_LANGUAGE_KEY,
            AppLanguages.indexOf(AppLanguage.English),
        )
        val language = AppLanguages.getOrElse(languageIndex) { AppLanguage.English }
        val favoriteId = preferences.getString(FIDGET_PINNED_TOYS_KEY, "")
            .orEmpty()
            .toPinnedToyIds()
            .firstOrNull()
        val toy = FIDGET_TOY_INFOS.firstOrNull { it.id == favoriteId }
            ?: FIDGET_TOY_INFOS.first()
        return FidgetFavoriteSnapshot(
            toyId = toy.id,
            name = toy.nameFor(language),
            mainColorArgb = normalizeFidgetColor(
                preferences.getInt(FIDGET_MAIN_COLOR_KEY, NEON_GREEN_COLOR),
            ),
            backgroundColorArgb = normalizeFidgetColor(
                preferences.getInt(FIDGET_BACKGROUND_COLOR_KEY, DEFAULT_FIDGET_FACE_BACKGROUND),
            ),
            ringColorArgb = normalizeFidgetColor(
                preferences.getInt(FIDGET_RING_COLOR_KEY, NEON_GREEN_COLOR),
            ),
        )
    }

    private fun previewSnapshot(): FidgetFavoriteSnapshot {
        return FidgetFavoriteSnapshot(
            toyId = 1,
            name = "Spin Storm",
            mainColorArgb = NEON_GREEN_COLOR,
            backgroundColorArgb = DEFAULT_FIDGET_FACE_BACKGROUND,
            ringColorArgb = 0xFFFFC857.toInt(),
        )
    }
}

private data class FidgetFavoriteSnapshot(
    val toyId: Int,
    val name: String,
    val mainColorArgb: Int,
    val backgroundColorArgb: Int,
    val ringColorArgb: Int,
)

private fun normalizeFidgetColor(colorArgb: Int): Int {
    val normalized = if (colorArgb == RAINBOW_COLOR) NEON_GREEN_COLOR else colorArgb
    return normalized or 0xFF000000.toInt()
}

private fun renderFidgetFavoriteBitmap(snapshot: FidgetFavoriteSnapshot): Bitmap {
    val bitmap = Bitmap.createBitmap(FIDGET_FACE_IMAGE_SIZE, FIDGET_FACE_IMAGE_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = FIDGET_FACE_IMAGE_SIZE / 2f
    val background = snapshot.backgroundColorArgb
    val accent = snapshot.mainColorArgb
    val ring = snapshot.ringColorArgb
    val readable = readableColor(background)

    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            center,
            center * 0.86f,
            center,
            intArrayOf(withAlpha(accent, 70), background, Color.BLACK),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawCircle(center, center, center, backgroundPaint)

    val ringPaint = strokePaint(ring, 7f)
    canvas.drawCircle(center, center, center - 12f, ringPaint)
    canvas.drawCircle(center, center, center - 24f, strokePaint(withAlpha(readable, 65), 2f))

    drawFavoriteMotif(
        canvas = canvas,
        toyId = snapshot.toyId,
        accent = accent,
        secondary = ring,
        readable = readable,
    )

    val eyebrowPaint = textPaint(withAlpha(readable, 175), 18f)
    canvas.drawText("MUNKZ FIDGET", center, 39f, eyebrowPaint)

    val namePaint = textPaint(readable, 31f)
    while (namePaint.measureText(snapshot.name) > FIDGET_FACE_IMAGE_SIZE - 52f && namePaint.textSize > 20f) {
        namePaint.textSize -= 1f
    }
    canvas.drawText(snapshot.name, center, FIDGET_FACE_IMAGE_SIZE - 35f, namePaint)
    return bitmap
}

private fun drawFavoriteMotif(
    canvas: Canvas,
    toyId: Int,
    accent: Int,
    secondary: Int,
    readable: Int,
) {
    when (toyId) {
        1 -> drawSpinnerMotif(canvas, accent, secondary)
        2, 3 -> drawSwitchMotif(canvas, accent, secondary)
        4, 5, 10, 16, 18 -> drawButtonGridMotif(canvas, accent, secondary)
        6 -> drawSlingshotMotif(canvas, accent, secondary)
        7, 13, 17 -> drawFlowMotif(canvas, accent, secondary)
        8, 15 -> drawSquishyMotif(canvas, accent, secondary)
        9 -> drawMagnetMotif(canvas, accent, secondary)
        11 -> drawCubeMotif(canvas, accent, secondary)
        12 -> drawRingMotif(canvas, accent, secondary)
        14 -> drawGearMotif(canvas, accent, secondary)
        19 -> drawWindowMotif(canvas, accent, secondary)
        20 -> drawDoorMotif(canvas, accent, secondary)
        21 -> drawLightMotif(canvas, accent, secondary)
        22 -> drawFanMotif(canvas, accent, secondary)
        23 -> drawSinkMotif(canvas, accent, secondary)
        24 -> drawSymbolDockMotif(canvas, accent, secondary)
        25 -> drawRingMotif(canvas, accent, secondary)
        26 -> drawButtonGridMotif(canvas, accent, secondary)
        else -> drawSpinnerMotif(canvas, accent, readable)
    }
}

private fun drawSpinnerMotif(canvas: Canvas, accent: Int, secondary: Int) {
    val centerX = 160f
    val centerY = 157f
    repeat(3) { index ->
        val angle = -PI.toFloat() / 2f + index * 2f * PI.toFloat() / 3f
        val lobeX = centerX + cos(angle) * 62f
        val lobeY = centerY + sin(angle) * 62f
        canvas.drawLine(centerX, centerY, lobeX, lobeY, strokePaint(accent, 24f))
        canvas.drawCircle(lobeX, lobeY, 25f, fillPaint(if (index == 0) secondary else accent))
    }
    canvas.drawCircle(centerX, centerY, 31f, fillPaint(secondary))
    canvas.drawCircle(centerX, centerY, 13f, fillPaint(Color.BLACK))
}

private fun drawSwitchMotif(canvas: Canvas, accent: Int, secondary: Int) {
    repeat(4) { index ->
        val left = 65f + (index % 2) * 105f
        val top = 105f + (index / 2) * 77f
        canvas.drawRoundRect(RectF(left, top, left + 84f, top + 42f), 21f, 21f, fillPaint(withAlpha(accent, 105)))
        canvas.drawCircle(left + if (index % 2 == 0) 62f else 22f, top + 21f, 17f, fillPaint(if (index % 2 == 0) secondary else accent))
    }
}

private fun drawButtonGridMotif(canvas: Canvas, accent: Int, secondary: Int) {
    repeat(6) { index ->
        val x = 94f + (index % 3) * 66f
        val y = 119f + (index / 3) * 72f
        canvas.drawRoundRect(
            RectF(x - 23f, y - 23f, x + 23f, y + 23f),
            11f,
            11f,
            fillPaint(if (index % 2 == 0) accent else secondary),
        )
    }
}

private fun drawSymbolDockMotif(canvas: Canvas, accent: Int, secondary: Int) {
    val symbols = FIDGET_DOCK_SYMBOLS.take(FIDGET_DOCK_PAD_COUNT)
    symbols.forEachIndexed { index, symbol ->
        val centerX = 76f + index * 56f
        val padColor = if (index % 2 == 0) accent else secondary
        canvas.drawRoundRect(
            RectF(centerX - 23f, 116f, centerX + 23f, 196f),
            13f,
            13f,
            fillPaint(padColor),
        )
        val symbolPaint = textPaint(readableColor(padColor), if (index < 2) 39f else 34f)
        val baseline = 156f - (symbolPaint.ascent() + symbolPaint.descent()) / 2f
        canvas.drawText(symbol, centerX, baseline, symbolPaint)
    }
}

private fun drawSlingshotMotif(canvas: Canvas, accent: Int, secondary: Int) {
    canvas.drawLine(105f, 105f, 143f, 186f, strokePaint(accent, 12f))
    canvas.drawLine(215f, 105f, 177f, 186f, strokePaint(accent, 12f))
    canvas.drawLine(143f, 186f, 160f, 229f, strokePaint(secondary, 15f))
    canvas.drawLine(177f, 186f, 160f, 229f, strokePaint(secondary, 15f))
    canvas.drawCircle(160f, 182f, 24f, fillPaint(secondary))
}

private fun drawFlowMotif(canvas: Canvas, accent: Int, secondary: Int) {
    repeat(5) { row ->
        val path = Path()
        val y = 105f + row * 32f
        path.moveTo(62f, y)
        path.cubicTo(100f, y - 25f, 130f, y + 25f, 160f, y)
        path.cubicTo(195f, y - 25f, 225f, y + 25f, 258f, y)
        canvas.drawPath(path, strokePaint(if (row % 2 == 0) accent else secondary, 6f))
    }
    canvas.drawCircle(160f, 163f, 17f, fillPaint(secondary))
}

private fun drawSquishyMotif(canvas: Canvas, accent: Int, secondary: Int) {
    canvas.drawOval(RectF(74f, 98f, 246f, 226f), fillPaint(withAlpha(accent, 215)))
    canvas.drawOval(RectF(101f, 121f, 219f, 202f), strokePaint(secondary, 9f))
    canvas.drawCircle(134f, 143f, 13f, fillPaint(withAlpha(Color.WHITE, 150)))
}

private fun drawMagnetMotif(canvas: Canvas, accent: Int, secondary: Int) {
    canvas.drawCircle(112f, 161f, 49f, strokePaint(accent, 24f))
    canvas.drawCircle(208f, 161f, 49f, strokePaint(secondary, 24f))
    canvas.drawLine(145f, 130f, 175f, 130f, strokePaint(Color.WHITE, 9f))
    canvas.drawLine(145f, 192f, 175f, 192f, strokePaint(Color.WHITE, 9f))
}

private fun drawCubeMotif(canvas: Canvas, accent: Int, secondary: Int) {
    canvas.drawRoundRect(RectF(70f, 106f, 166f, 202f), 15f, 15f, fillPaint(accent))
    canvas.drawRoundRect(RectF(154f, 122f, 250f, 218f), 15f, 15f, fillPaint(withAlpha(secondary, 225)))
    canvas.drawLine(166f, 106f, 250f, 122f, strokePaint(Color.WHITE, 5f))
}

private fun drawRingMotif(canvas: Canvas, accent: Int, secondary: Int) {
    canvas.drawCircle(160f, 160f, 74f, strokePaint(accent, 26f))
    repeat(12) { index ->
        val angle = index * 2f * PI.toFloat() / 12f
        canvas.drawCircle(
            160f + cos(angle) * 74f,
            160f + sin(angle) * 74f,
            7f,
            fillPaint(secondary),
        )
    }
}

private fun drawGearMotif(canvas: Canvas, accent: Int, secondary: Int) {
    drawGear(canvas, 126f, 149f, 49f, accent)
    drawGear(canvas, 202f, 135f, 38f, secondary)
    drawGear(canvas, 190f, 207f, 31f, 0xFFEF476F.toInt())
}

private fun drawGear(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
    canvas.drawCircle(x, y, radius * 0.78f, fillPaint(color))
    repeat(8) { index ->
        val angle = index * PI.toFloat() / 4f
        val innerX = x + cos(angle) * radius * 0.62f
        val innerY = y + sin(angle) * radius * 0.62f
        val outerX = x + cos(angle) * radius
        val outerY = y + sin(angle) * radius
        canvas.drawLine(innerX, innerY, outerX, outerY, strokePaint(color, radius * 0.24f))
    }
    canvas.drawCircle(x, y, radius * 0.22f, fillPaint(Color.BLACK))
}

private fun drawWindowMotif(canvas: Canvas, accent: Int, secondary: Int) {
    canvas.drawRoundRect(RectF(70f, 92f, 250f, 225f), 9f, 9f, strokePaint(accent, 11f))
    canvas.drawRect(76f, 99f, 157f, 218f, fillPaint(withAlpha(0xFF39A7FF.toInt(), 185)))
    canvas.drawRect(163f, 99f, 244f, 218f, fillPaint(withAlpha(secondary, 190)))
    canvas.drawLine(160f, 98f, 160f, 219f, strokePaint(accent, 8f))
    canvas.drawCircle(218f, 123f, 14f, fillPaint(0xFFFFC857.toInt()))
}

private fun drawDoorMotif(canvas: Canvas, accent: Int, secondary: Int) {
    val path = Path().apply {
        moveTo(104f, 91f)
        lineTo(216f, 108f)
        lineTo(216f, 224f)
        lineTo(104f, 239f)
        close()
    }
    canvas.drawPath(path, fillPaint(accent))
    canvas.drawLine(104f, 91f, 104f, 239f, strokePaint(secondary, 10f))
    canvas.drawCircle(190f, 168f, 9f, fillPaint(secondary))
}

private fun drawLightMotif(canvas: Canvas, accent: Int, secondary: Int) {
    canvas.drawCircle(160f, 145f, 55f, fillPaint(withAlpha(secondary, 235)))
    repeat(8) { index ->
        val angle = index * PI.toFloat() / 4f
        canvas.drawLine(
            160f + cos(angle) * 68f,
            145f + sin(angle) * 68f,
            160f + cos(angle) * 91f,
            145f + sin(angle) * 91f,
            strokePaint(accent, 8f),
        )
    }
    canvas.drawRoundRect(RectF(133f, 196f, 187f, 224f), 9f, 9f, fillPaint(accent))
}

private fun drawFanMotif(canvas: Canvas, accent: Int, secondary: Int) {
    repeat(3) { index ->
        val angle = -PI.toFloat() / 2f + index * 2f * PI.toFloat() / 3f
        val path = Path().apply {
            moveTo(160f, 160f)
            lineTo(160f + cos(angle - 0.46f) * 28f, 160f + sin(angle - 0.46f) * 28f)
            lineTo(160f + cos(angle) * 88f, 160f + sin(angle) * 88f)
            lineTo(160f + cos(angle + 0.55f) * 45f, 160f + sin(angle + 0.55f) * 45f)
            close()
        }
        canvas.drawPath(path, fillPaint(if (index == 0) secondary else accent))
    }
    canvas.drawCircle(160f, 160f, 22f, fillPaint(Color.BLACK))
}

private fun drawSinkMotif(canvas: Canvas, accent: Int, secondary: Int) {
    canvas.drawRoundRect(RectF(73f, 169f, 247f, 224f), 24f, 24f, fillPaint(withAlpha(Color.WHITE, 190)))
    canvas.drawArc(RectF(122f, 82f, 198f, 174f), 180f, 180f, false, strokePaint(accent, 15f))
    canvas.drawLine(198f, 128f, 198f, 169f, strokePaint(accent, 15f))
    val waterPath = Path().apply {
        moveTo(198f, 171f)
        cubicTo(181f, 188f, 212f, 199f, 193f, 218f)
    }
    canvas.drawPath(waterPath, strokePaint(secondary, 9f))
}

private fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    this.color = color
}

private fun strokePaint(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    strokeWidth = width
    this.color = color
}

private fun textPaint(color: Int, size: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    textSize = size
    this.color = color
}

private fun readableColor(background: Int): Int {
    val red = Color.red(background)
    val green = Color.green(background)
    val blue = Color.blue(background)
    val luminance = (red * 299 + green * 587 + blue * 114) / 1_000
    return if (luminance > 150) Color.BLACK else Color.WHITE
}

private fun withAlpha(color: Int, alpha: Int): Int {
    return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}

private const val FIDGET_FACE_IMAGE_SIZE = 320
private const val FIDGET_FAVORITE_PENDING_INTENT_BASE = 0xF1D600
private const val DEFAULT_FIDGET_FACE_BACKGROUND = 0xFF061112.toInt()
