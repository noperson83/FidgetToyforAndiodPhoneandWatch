package bpm.munkz.pulse_wear.os.bpm.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.sqrt

internal enum class FidgetControlMode(val persistedValue: Int) {
    Touch(0),
    Motion(1),
    Both(2),
    ;

    companion object {
        fun fromPersistedValue(value: Int): FidgetControlMode =
            entries.firstOrNull { it.persistedValue == value } ?: Touch
    }
}

internal enum class FidgetMotionSensitivity(
    val persistedValue: Int,
    val tiltScale: Float,
    val shakeThreshold: Float,
) {
    Low(0, 0.62f, 4.4f),
    Medium(1, 0.86f, 3.5f),
    High(2, 1.12f, 2.8f),
    ;

    companion object {
        fun fromPersistedValue(value: Int): FidgetMotionSensitivity =
            entries.firstOrNull { it.persistedValue == value } ?: Medium
    }
}

internal data class FidgetMotionSnapshot(
    val rawTilt: Offset = Offset.Zero,
    val tilt: Offset = Offset.Zero,
    val shakeToken: Int = 0,
    val sensorsAvailable: Boolean = false,
)

@Composable
internal fun rememberFidgetMotionSnapshot(
    enabled: Boolean,
    sensitivity: FidgetMotionSensitivity,
    neutralTilt: Offset,
): FidgetMotionSnapshot {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(FidgetMotionSnapshot()) }

    DisposableEffect(context, enabled, sensitivity, neutralTilt.x, neutralTilt.y) {
        if (!enabled) {
            snapshot = FidgetMotionSnapshot()
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val tiltSensor = gravitySensor ?: accelerometer
            var filteredGravity = FloatArray(3)
            var lastTiltPublishMs = 0L
            var lastShakeMs = 0L

            fun rotateTilt(x: Float, y: Float): Offset {
                val rotation = context.display.rotation
                return when (rotation) {
                    Surface.ROTATION_90 -> Offset(y, x)
                    Surface.ROTATION_180 -> Offset(x, -y)
                    Surface.ROTATION_270 -> Offset(-y, -x)
                    else -> Offset(-x, y)
                }
            }

            val listener = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

                override fun onSensorChanged(event: SensorEvent) {
                    val now = SystemClock.elapsedRealtime()
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        val magnitude = sqrt(
                            event.values[0] * event.values[0] +
                                event.values[1] * event.values[1] +
                                event.values[2] * event.values[2],
                        )
                        if (
                            abs(magnitude - SensorManager.GRAVITY_EARTH) >= sensitivity.shakeThreshold &&
                            now - lastShakeMs >= FIDGET_SHAKE_COOLDOWN_MS
                        ) {
                            lastShakeMs = now
                            snapshot = snapshot.copy(shakeToken = snapshot.shakeToken + 1)
                        }
                    }

                    if (event.sensor != tiltSensor || now - lastTiltPublishMs < FIDGET_TILT_SAMPLE_MILLIS) {
                        return
                    }
                    lastTiltPublishMs = now
                    if (gravitySensor == null) {
                        repeat(3) { index ->
                            filteredGravity[index] = filteredGravity[index] * 0.82f + event.values[index] * 0.18f
                        }
                    } else {
                        filteredGravity = event.values.copyOf()
                    }
                    val rawTilt = rotateTilt(
                        x = (filteredGravity[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f),
                        y = (filteredGravity[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f),
                    )
                    val calibrated = Offset(
                        x = (rawTilt.x - neutralTilt.x).coerceIn(-1f, 1f),
                        y = (rawTilt.y - neutralTilt.y).coerceIn(-1f, 1f),
                    )
                    snapshot = snapshot.copy(
                        rawTilt = rawTilt,
                        tilt = calibrated,
                        sensorsAvailable = true,
                    )
                }
            }

            snapshot = snapshot.copy(sensorsAvailable = tiltSensor != null)
            tiltSensor?.let { sensor ->
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
            if (accelerometer != null && accelerometer != tiltSensor) {
                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            }

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    return snapshot
}

private const val FIDGET_TILT_SAMPLE_MILLIS = 34L
private const val FIDGET_SHAKE_COOLDOWN_MS = 1_100L
