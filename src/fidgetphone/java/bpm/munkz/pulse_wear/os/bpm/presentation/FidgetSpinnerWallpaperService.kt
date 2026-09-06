package bpm.munkz.pulse_wear.os.bpm.presentation

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.graphics.withRotation
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class FidgetSpinnerWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SpinnerEngine()

    private inner class SpinnerEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val preferences = getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE)
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF,
                android.graphics.Typeface.BOLD,
            )
        }
        private val frameRunnable = Runnable { advanceAndDraw() }
        private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in FIDGET_WALLPAPER_THEME_KEYS) {
                snapshot = applicationContext.loadFidgetPhoneSurfaceSnapshot()
                drawFrame()
            }
        }

        private var snapshot = applicationContext.loadFidgetPhoneSurfaceSnapshot()
        private var visible = false
        private var surfaceReady = false
        private var dragging = false
        private var spinnerAngle = 0f
        private var angularVelocity = 0f
        private var lastTouchAngle = 0f
        private var lastTouchEventMs = 0L
        private var lastMotionEventMs = 0L
        private var lastFrameNanos = 0L
        private var xOffset = 0.5f

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(true)
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frameRunnable)
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            super.onDestroy()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                snapshot = applicationContext.loadFidgetPhoneSurfaceSnapshot()
                lastFrameNanos = System.nanoTime()
                scheduleFrame(immediate = true)
            } else {
                handler.removeCallbacks(frameRunnable)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = true
            scheduleFrame(immediate = true)
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceReady = true
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            handler.removeCallbacks(frameRunnable)
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            this.xOffset = xOffset
            drawFrame()
        }

        override fun onTouchEvent(event: MotionEvent) {
            val bounds = surfaceHolder.surfaceFrame
            val centerX = bounds.width() * (0.5f + (xOffset - 0.5f) * 0.08f)
            val centerY = bounds.height() * 0.5f
            val touchAngle = angleFor(event.x, event.y, centerX, centerY)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    angularVelocity = 0f
                    lastTouchAngle = touchAngle
                    lastTouchEventMs = event.eventTime
                    lastMotionEventMs = event.eventTime
                    scheduleFrame(immediate = true)
                }

                MotionEvent.ACTION_MOVE -> {
                    val delta = shortestAngleDelta(lastTouchAngle, touchAngle)
                    val elapsedMs = (event.eventTime - lastTouchEventMs).coerceAtLeast(1L)
                    spinnerAngle = (spinnerAngle + delta) % 360f
                    val measuredVelocity = delta * 1_000f / elapsedMs.toFloat()
                    angularVelocity = angularVelocity * 0.28f + measuredVelocity * 0.72f
                    if (abs(delta) >= 0.12f) {
                        lastMotionEventMs = event.eventTime
                    }
                    lastTouchAngle = touchAngle
                    lastTouchEventMs = event.eventTime
                    drawFrame()
                }

                MotionEvent.ACTION_UP -> {
                    dragging = false
                    if (event.eventTime - lastMotionEventMs >= HOLD_TO_STOP_MILLIS) {
                        angularVelocity = 0f
                    } else {
                        angularVelocity = angularVelocity.coerceIn(
                            -MAX_SPINNER_VELOCITY,
                            MAX_SPINNER_VELOCITY,
                        )
                    }
                    lastFrameNanos = System.nanoTime()
                    scheduleFrame(immediate = true)
                }

                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    angularVelocity = 0f
                    drawFrame()
                }
            }
            super.onTouchEvent(event)
        }

        private fun scheduleFrame(immediate: Boolean) {
            if (!visible || !surfaceReady) return
            handler.removeCallbacks(frameRunnable)
            handler.postDelayed(frameRunnable, if (immediate) 0L else FRAME_DELAY_MILLIS)
        }

        private fun advanceAndDraw() {
            if (!visible || !surfaceReady) return
            val frameNanos = System.nanoTime()
            val deltaSeconds = if (lastFrameNanos == 0L) {
                0f
            } else {
                ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            }
            lastFrameNanos = frameNanos

            if (!dragging && abs(angularVelocity) > STOP_VELOCITY) {
                spinnerAngle = (spinnerAngle + angularVelocity * deltaSeconds) % 360f
                angularVelocity *= FRAME_FRICTION.pow(deltaSeconds * 60f)
            } else if (!dragging) {
                angularVelocity = 0f
            }

            drawFrame()
            if (
                dragging ||
                abs(angularVelocity) > STOP_VELOCITY ||
                isRainbowColor(snapshot.ringColorArgb)
            ) {
                scheduleFrame(immediate = false)
            }
        }

        private fun drawFrame() {
            if (!surfaceReady) return
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas() ?: return
                drawWallpaper(canvas)
            } finally {
                canvas?.let(surfaceHolder::unlockCanvasAndPost)
            }
        }

        private fun drawWallpaper(canvas: Canvas) {
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()
            val centerX = width * (0.5f + (xOffset - 0.5f) * 0.08f)
            val centerY = height * 0.5f
            val stageRadius = minOf(width, height) * if (snapshot.spinnerMultiEnabled) 0.235f else 0.315f
            val ringAccentColor = if (isRainbowColor(snapshot.ringColorArgb)) {
                NEON_GREEN_COLOR
            } else {
                snapshot.ringColorArgb
            }

            canvas.drawColor(snapshot.backgroundColorArgb)
            backgroundPaint.shader = RadialGradient(
                centerX,
                centerY,
                stageRadius * 2.5f,
                intArrayOf(
                    withAlpha(snapshot.mainColorArgb, 0x36),
                    withAlpha(ringAccentColor, 0x14),
                    snapshot.backgroundColorArgb,
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, width, height, backgroundPaint)
            backgroundPaint.shader = null

            drawFieldDots(canvas, width, height)

            drawThemeRing(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                radius = stageRadius * 1.34f,
                strokeWidth = maxOf(2f, stageRadius * 0.018f),
                alpha = 0x72,
                rotationMultiplier = 1f,
            )
            drawThemeRing(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                radius = stageRadius * 1.08f,
                strokeWidth = maxOf(3f, stageRadius * 0.026f),
                alpha = 0x92,
                rotationMultiplier = -1f,
            )

            val style = fidgetSpinnerStyle(snapshot.spinnerStyleIndex)
            if (snapshot.spinnerMultiEnabled) {
                val satelliteHorizontalOffset = stageRadius * 1.48f
                val satelliteVerticalOffset = stageRadius * 0.62f
                val satelliteRadius = stageRadius * 0.38f
                val satelliteCenters = arrayOf(
                    centerX - satelliteHorizontalOffset to centerY - satelliteVerticalOffset,
                    centerX - satelliteHorizontalOffset to centerY + satelliteVerticalOffset,
                    centerX + satelliteHorizontalOffset to centerY - satelliteVerticalOffset,
                    centerX + satelliteHorizontalOffset to centerY + satelliteVerticalOffset,
                )
                val rotationMultipliers = floatArrayOf(-1.38f, 1.72f, -1.96f, 1.44f)
                val rotationOffsets = floatArrayOf(18f, 72f, 126f, 216f)

                satelliteCenters.forEachIndexed { index, satelliteCenter ->
                    drawSpinner(
                        canvas = canvas,
                        centerX = satelliteCenter.first,
                        centerY = satelliteCenter.second,
                        radius = satelliteRadius,
                        angleDegrees = spinnerAngle * rotationMultipliers[index] + rotationOffsets[index],
                        style = style,
                        alpha = 0.92f,
                    )
                }
            }
            drawSpinner(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                radius = stageRadius,
                angleDegrees = spinnerAngle,
                style = style,
            )

            labelPaint.color = withAlpha(snapshot.mainColorArgb, 0xD9)
            labelPaint.textSize = minOf(width, height) * 0.026f
            canvas.drawText("MUNKZ FIDGET", width * 0.5f, height * 0.92f, labelPaint)
        }

        private fun drawFieldDots(canvas: Canvas, width: Float, height: Float) {
            val ringAccentColor = if (isRainbowColor(snapshot.ringColorArgb)) {
                NEON_GREEN_COLOR
            } else {
                snapshot.ringColorArgb
            }
            fillPaint.color = withAlpha(ringAccentColor, 0x1F)
            val spacing = minOf(width, height) / 9f
            val radius = maxOf(1.5f, spacing * 0.025f)
            var y = spacing * 0.8f
            while (y < height) {
                var x = spacing * 0.8f
                while (x < width) {
                    canvas.drawCircle(x, y, radius, fillPaint)
                    x += spacing
                }
                y += spacing
            }
        }

        private fun drawThemeRing(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            radius: Float,
            strokeWidth: Float,
            alpha: Int,
            rotationMultiplier: Float,
        ) {
            linePaint.strokeWidth = strokeWidth
            if (isRainbowColor(snapshot.ringColorArgb)) {
                val rotationDegrees = (
                    (SystemClock.uptimeMillis() % RAINBOW_ROTATION_MILLIS).toFloat() /
                        RAINBOW_ROTATION_MILLIS * 360f
                    ) * rotationMultiplier
                canvas.withRotation(rotationDegrees, centerX, centerY) {
                    linePaint.shader = SweepGradient(
                        centerX,
                        centerY,
                        RAINBOW_RING_COLORS,
                        null,
                    )
                    linePaint.alpha = alpha.coerceIn(0, 255)
                    canvas.drawCircle(centerX, centerY, radius, linePaint)
                    linePaint.shader = null
                    linePaint.alpha = 255
                }
            } else {
                linePaint.shader = null
                linePaint.color = withAlpha(snapshot.ringColorArgb, alpha)
                canvas.drawCircle(centerX, centerY, radius, linePaint)
            }
        }

        private fun drawSpinner(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            radius: Float,
            angleDegrees: Float,
            style: FidgetSpinnerStyle,
            alpha: Float = 1f,
        ) {
            val bearingRadius = radius * 0.22f * style.ballScale
            val armRadius = radius * 0.62f * style.stemLengthScale
            val armWidth = maxOf(1f, radius * 0.16f * style.stemWidthScale)
            val armColor = withAlpha(
                style.armColorArgb ?: snapshot.mainColorArgb,
                (0xF5 * alpha).toInt(),
            )
            val centerColor = style.centerColorArgb ?: snapshot.ringColorArgb
            val bearingCenters = Array(3) { index ->
                val radians = Math.toRadians((index * 120.0) - 90.0)
                centerX + cos(radians).toFloat() * armRadius to
                    centerY + sin(radians).toFloat() * armRadius
            }

            canvas.withRotation(angleDegrees, centerX, centerY) {

            linePaint.color = armColor
            linePaint.strokeWidth = armWidth
            bearingCenters.forEach { bearingCenter ->
                canvas.drawLine(
                    centerX,
                    centerY,
                    bearingCenter.first,
                    bearingCenter.second,
                    linePaint,
                )
            }

            val hubPath = Path()
            bearingCenters.forEachIndexed { index, bearingCenter ->
                val pointX = centerX + (bearingCenter.first - centerX) * 0.72f
                val pointY = centerY + (bearingCenter.second - centerY) * 0.72f
                if (index == 0) hubPath.moveTo(pointX, pointY) else hubPath.lineTo(pointX, pointY)
            }
            hubPath.close()
            fillPaint.color = withAlpha(armColor, (0xE6 * alpha).toInt())
            canvas.drawPath(hubPath, fillPaint)

            bearingCenters.forEachIndexed { index, bearingCenter ->
                fillPaint.shader = null
                fillPaint.color = withAlpha(Color.BLACK, (0xB3 * alpha).toInt())
                canvas.drawCircle(
                    bearingCenter.first,
                    bearingCenter.second,
                    bearingRadius * 1.13f,
                    fillPaint,
                )
                fillPaint.shader = RadialGradient(
                    bearingCenter.first - bearingRadius * 0.24f,
                    bearingCenter.second - bearingRadius * 0.24f,
                    bearingRadius * 1.35f,
                    intArrayOf(
                        withAlpha(Color.WHITE, (0xEB * alpha).toInt()),
                        withAlpha(style.ballColor(index), (0xFF * alpha).toInt()),
                        withAlpha(Color.BLACK, (0xE6 * alpha).toInt()),
                    ),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(
                    bearingCenter.first,
                    bearingCenter.second,
                    bearingRadius,
                    fillPaint,
                )
                fillPaint.shader = null
            }

            drawCenterPiece(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                radius = radius * 0.3f * style.centerScale,
                color = centerColor,
                centerPiece = style.centerPiece,
                alpha = alpha,
            )

            }
        }

        private fun drawCenterPiece(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            radius: Float,
            color: Int,
            centerPiece: FidgetSpinnerCenterPiece,
            alpha: Float,
        ) {
            fillPaint.shader = null
            fillPaint.color = withAlpha(Color.BLACK, (0xC2 * alpha).toInt())
            canvas.drawCircle(centerX, centerY, radius * 1.12f, fillPaint)
            val pieceColor = withAlpha(color, (0xFF * alpha).toInt())

            when (centerPiece) {
                FidgetSpinnerCenterPiece.Disc -> {
                    fillPaint.color = pieceColor
                    canvas.drawCircle(centerX, centerY, radius, fillPaint)
                    fillPaint.color = withAlpha(Color.WHITE, (0x80 * alpha).toInt())
                    canvas.drawCircle(
                        centerX - radius * 0.26f,
                        centerY - radius * 0.26f,
                        radius * 0.18f,
                        fillPaint,
                    )
                }
                FidgetSpinnerCenterPiece.Ring -> {
                    fillPaint.color = pieceColor
                    canvas.drawCircle(centerX, centerY, radius, fillPaint)
                    fillPaint.color = withAlpha(Color.BLACK, (0xE6 * alpha).toInt())
                    canvas.drawCircle(centerX, centerY, radius * 0.55f, fillPaint)
                    fillPaint.color = withAlpha(color, (0xC7 * alpha).toInt())
                    canvas.drawCircle(centerX, centerY, radius * 0.18f, fillPaint)
                }
                FidgetSpinnerCenterPiece.Hex -> {
                    drawPolygon(canvas, centerX, centerY, radius, 6, -90f, pieceColor)
                    drawPolygon(
                        canvas,
                        centerX,
                        centerY,
                        radius * 0.62f,
                        6,
                        -90f,
                        withAlpha(Color.BLACK, (0x47 * alpha).toInt()),
                    )
                }
                FidgetSpinnerCenterPiece.Square -> {
                    fillPaint.color = pieceColor
                    canvas.drawRoundRect(
                        centerX - radius * 0.78f,
                        centerY - radius * 0.78f,
                        centerX + radius * 0.78f,
                        centerY + radius * 0.78f,
                        radius * 0.24f,
                        radius * 0.24f,
                        fillPaint,
                    )
                    fillPaint.color = withAlpha(Color.WHITE, (0x52 * alpha).toInt())
                    canvas.drawCircle(centerX, centerY, radius * 0.16f, fillPaint)
                }
                FidgetSpinnerCenterPiece.Diamond -> {
                    val diamond = Path().apply {
                        moveTo(centerX, centerY - radius)
                        lineTo(centerX + radius, centerY)
                        lineTo(centerX, centerY + radius)
                        lineTo(centerX - radius, centerY)
                        close()
                    }
                    fillPaint.color = pieceColor
                    canvas.drawPath(diamond, fillPaint)
                    linePaint.color = withAlpha(Color.WHITE, (0x75 * alpha).toInt())
                    linePaint.strokeWidth = maxOf(1f, radius * 0.13f)
                    canvas.drawPath(diamond, linePaint)
                }
                FidgetSpinnerCenterPiece.Target -> {
                    fillPaint.color = pieceColor
                    canvas.drawCircle(centerX, centerY, radius, fillPaint)
                    fillPaint.color = withAlpha(Color.BLACK, (0xDB * alpha).toInt())
                    canvas.drawCircle(centerX, centerY, radius * 0.66f, fillPaint)
                    fillPaint.color = pieceColor
                    canvas.drawCircle(centerX, centerY, radius * 0.36f, fillPaint)
                    fillPaint.color = withAlpha(Color.WHITE, (0xB8 * alpha).toInt())
                    canvas.drawCircle(centerX, centerY, radius * 0.11f, fillPaint)
                }
                FidgetSpinnerCenterPiece.Bolt -> {
                    drawPolygon(canvas, centerX, centerY, radius, 6, -90f, pieceColor)
                    fillPaint.color = withAlpha(Color.BLACK, (0xD1 * alpha).toInt())
                    canvas.drawCircle(centerX, centerY, radius * 0.48f, fillPaint)
                    drawPolygon(
                        canvas,
                        centerX,
                        centerY,
                        radius * 0.28f,
                        6,
                        -90f,
                        withAlpha(Color.WHITE, (0xA6 * alpha).toInt()),
                    )
                }
            }
        }

        private fun drawPolygon(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            radius: Float,
            sides: Int,
            rotationDegrees: Float,
            color: Int,
        ) {
            val path = Path()
            repeat(sides) { index ->
                val radians = Math.toRadians(rotationDegrees.toDouble() + (360.0 * index / sides))
                val pointX = centerX + cos(radians).toFloat() * radius
                val pointY = centerY + sin(radians).toFloat() * radius
                if (index == 0) path.moveTo(pointX, pointY) else path.lineTo(pointX, pointY)
            }
            path.close()
            fillPaint.shader = null
            fillPaint.color = color
            canvas.drawPath(path, fillPaint)
        }
    }

    private companion object {
        const val FRAME_DELAY_MILLIS = 16L
        const val HOLD_TO_STOP_MILLIS = 180L
        const val MAX_SPINNER_VELOCITY = 1_800f
        const val STOP_VELOCITY = 2.5f
        const val FRAME_FRICTION = 0.982f
        const val RAINBOW_ROTATION_MILLIS = 8_000L

        val RAINBOW_RING_COLORS = intArrayOf(
            0xFFFF3B30.toInt(),
            0xFFFFD60A.toInt(),
            0xFF32D74B.toInt(),
            0xFF64D2FF.toInt(),
            0xFFBF5AF2.toInt(),
            0xFFFF2D55.toInt(),
            0xFFFF3B30.toInt(),
        )

        val FIDGET_WALLPAPER_THEME_KEYS = setOf(
            FIDGET_MAIN_COLOR_KEY,
            FIDGET_BACKGROUND_COLOR_KEY,
            FIDGET_RING_COLOR_KEY,
            FIDGET_SPINNER_STYLE_KEY,
            FIDGET_SPINNER_MULTI_KEY,
            FIDGET_PINNED_TOYS_KEY,
        )

        fun angleFor(x: Float, y: Float, centerX: Float, centerY: Float): Float {
            return Math.toDegrees(atan2(y - centerY, x - centerX).toDouble()).toFloat()
        }

        fun shortestAngleDelta(from: Float, to: Float): Float {
            var delta = (to - from) % 360f
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            return delta
        }

        fun withAlpha(color: Int, alpha: Int): Int {
            return (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
        }
    }
}
