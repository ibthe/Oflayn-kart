package app.georgia.offline.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.view.Surface
import android.view.WindowManager
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.georgia.offline.domain.GeoFormatting
import app.georgia.offline.domain.Qibla
import app.georgia.offline.ui.theme.accentBrush
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** What the phone's sensors say about direction. [heading] is null when the phone has no suitable sensor. */
@Stable
class CompassState {
    /** Smoothed heading, 0..360°, clockwise from north. */
    var heading by mutableStateOf<Float?>(null)
        internal set

    /** The same heading without the 360° wrap, so the dial never spins backwards when passing north. */
    var unwrapped by mutableFloatStateOf(0f)
        internal set

    /** [SensorManager.SENSOR_STATUS_ACCURACY_HIGH] .. UNRELIABLE. */
    var accuracy by mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        internal set

    /** True when the heading is corrected to geographic north using the GPS position. */
    var trueNorth by mutableStateOf(false)
        internal set

    val needsCalibration: Boolean
        get() = heading != null && accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
}

/**
 * Reads the rotation-vector sensor, smooths it and, when [location] is known, converts magnetic
 * north to true north with the geomagnetic declination for that spot.
 */
@Composable
fun rememberCompass(location: Location?): CompassState {
    val context = LocalContext.current
    val state = remember { CompassState() }
    val declination = remember(location?.latitude?.let { (it * 4).toInt() }, location?.longitude?.let { (it * 4).toInt() }) {
        location?.let {
            GeomagneticField(
                it.latitude.toFloat(),
                it.longitude.toFloat(),
                if (it.hasAltitude()) it.altitude.toFloat() else 0f,
                System.currentTimeMillis()
            ).declination
        }
    }

    DisposableEffect(context, declination) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotationSensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val useRotationVector = rotationSensor != null

        if (!useRotationVector && (accelerometer == null || magnetometer == null)) {
            state.heading = null
            state.accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                private val matrix = FloatArray(9)
                private val displayMatrix = FloatArray(9)
                private val uprightMatrix = FloatArray(9)
                private val orientation = FloatArray(3)
                private val gravity = FloatArray(3)
                private val geomagnetic = FloatArray(3)
                private var hasGravity = false
                private var hasGeomagnetic = false
                private var smoothed: Float? = null
                private var lastNormalized = 0f

                override fun onSensorChanged(event: SensorEvent) {
                    if (useRotationVector) {
                        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                        SensorManager.getRotationMatrixFromVector(matrix, event.values)
                        state.accuracy = event.accuracy
                    } else {
                        when (event.sensor.type) {
                            Sensor.TYPE_ACCELEROMETER -> {
                                System.arraycopy(event.values, 0, gravity, 0, 3)
                                hasGravity = true
                            }
                            Sensor.TYPE_MAGNETIC_FIELD -> {
                                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                                hasGeomagnetic = true
                                state.accuracy = event.accuracy
                            }
                            else -> return
                        }
                        if (!hasGravity || !hasGeomagnetic) return
                        if (!SensorManager.getRotationMatrix(matrix, null, gravity, geomagnetic)) return
                    }

                    // Sensor axes follow the device's natural orientation, not necessarily the
                    // current screen orientation. Re-map before reading azimuth so landscape,
                    // reverse portrait and naturally-landscape tablets use the visible top edge.
                    val axes = CompassMath.screenAxes(windowManager.defaultDisplay.rotation)
                    if (!SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, displayMatrix)) return
                    SensorManager.getOrientation(displayMatrix, orientation)
                    val pitch = Math.toDegrees(orientation[1].toDouble())

                    // When held upright, the screen's top edge points almost vertically. Use the
                    // screen normal (the direction the front of the phone faces) instead.
                    val azimuth = if (abs(pitch) > 50.0) {
                        if (!SensorManager.remapCoordinateSystem(
                                displayMatrix,
                                SensorManager.AXIS_X,
                                SensorManager.AXIS_Z,
                                uprightMatrix
                            )
                        ) return
                        SensorManager.getOrientation(uprightMatrix, orientation)
                        orientation[0]
                    } else {
                        orientation[0]
                    }

                    val raw = GeoFormatting.normalizeHeading(
                        Math.toDegrees(azimuth.toDouble()).toFloat() + (declination ?: 0f)
                    )
                    val previous = smoothed
                    val value = if (previous == null) {
                        raw
                    } else {
                        GeoFormatting.normalizeHeading(
                            previous + Qibla.angleDifference(previous.toDouble(), raw.toDouble()).toFloat() * SMOOTHING
                        )
                    }
                    smoothed = value
                    val step = Qibla.angleDifference(lastNormalized.toDouble(), value.toDouble()).toFloat()
                    state.unwrapped += step
                    lastNormalized = value
                    state.heading = value
                    state.trueNorth = declination != null
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR || sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        state.accuracy = accuracy
                    }
                }
            }

            val registered = if (useRotationVector) {
                manager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            } else {
                val accelerationRegistered = manager.registerListener(
                    listener, accelerometer, SensorManager.SENSOR_DELAY_GAME
                )
                val magneticRegistered = manager.registerListener(
                    listener, magnetometer, SensorManager.SENSOR_DELAY_GAME
                )
                accelerationRegistered && magneticRegistered
            }

            if (!registered) {
                manager.unregisterListener(listener)
                state.heading = null
                state.accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
            }
            onDispose { manager.unregisterListener(listener) }
        }
    }
    return state
}

/**
 * Maps the sensor's natural coordinate system to the current display orientation before azimuth
 * is read. The sensor coordinate system is independent of screen rotation.
 */
internal object CompassMath {
    fun screenAxes(rotation: Int): Pair<Int, Int> = when (rotation) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }
}

private const val SMOOTHING = 0.18f

/**
 * A compass card: the dial turns under a fixed pointer, so the heading is whatever is at the top.
 * When [qiblaBearing] is given, a Kaaba marker sits on the dial in that direction and lights up when
 * the phone points at it.
 */
@Composable
fun CompassDial(
    unwrappedHeading: Float,
    heading: Float,
    qiblaBearing: Float?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val aligned = qiblaBearing != null && abs(Qibla.angleDifference(heading.toDouble(), qiblaBearing.toDouble())) < ALIGNED_DEGREES
    val accent = scheme.primary
    val glow = scheme.tertiary
    val ink = scheme.onSurface
    val faint = scheme.onSurfaceVariant
    val cardColor = scheme.surfaceContainerHigh
    val north = scheme.error
    val gradient = scheme.accentBrush()
    val surface = scheme.surfaceContainerHighest

    Canvas(modifier.fillMaxWidth().widthIn(max = 340.dp).aspectRatio(1f)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outer = size.minDimension / 2f
        val card = outer * 0.84f
        val dp = 1.dp.toPx()

        // Card and bezel.
        drawCircle(cardColor, radius = card, center = center)
        drawCircle(
            brush = if (aligned) Brush.linearGradient(listOf(glow, glow)) else gradient,
            radius = card,
            center = center,
            style = Stroke(width = if (aligned) 4 * dp else 2.5f * dp)
        )

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        rotate(degrees = -unwrappedHeading, pivot = center) {
            // Ticks every 5°, longer every 15°, longest at the cardinal points.
            for (degree in 0 until 360 step 5) {
                val cardinal = degree % 90 == 0
                val major = degree % 30 == 0
                val length = when {
                    cardinal -> card * 0.11f
                    major -> card * 0.08f
                    degree % 15 == 0 -> card * 0.055f
                    else -> card * 0.035f
                }
                val color = when {
                    degree == 0 -> north
                    cardinal || major -> ink
                    else -> faint
                }
                val angle = Math.toRadians(degree.toDouble())
                val dx = sin(angle).toFloat()
                val dy = -cos(angle).toFloat()
                drawLine(
                    color = color,
                    start = Offset(center.x + dx * (card - 3 * dp), center.y + dy * (card - 3 * dp)),
                    end = Offset(center.x + dx * (card - 3 * dp - length), center.y + dy * (card - 3 * dp - length)),
                    strokeWidth = if (cardinal) 3 * dp else if (major) 2 * dp else 1.2f * dp
                )
            }
            // Cardinal letters and degree numbers turn with the card.
            val letters = listOf(0 to "С", 90 to "В", 180 to "Ю", 270 to "З")
            letters.forEach { (degree, letter) ->
                drawLabel(labelPaint, letter, center, card * 0.70f, degree.toFloat(), card * 0.20f, if (degree == 0) north else ink)
            }
            for (degree in 30 until 360 step 30) {
                if (degree % 90 == 0) continue
                drawLabel(labelPaint, degree.toString(), center, card * 0.70f, degree.toFloat(), card * 0.085f, faint)
            }

        }

        // Draw the Qibla direction in screen coordinates, independently from the animated
        // compass-card rotation. This keeps the indicator locked to the same heading used by
        // the numeric turn instruction, even when the dial's unwrapped animation crosses 360°.
        if (qiblaBearing != null) {
            val relativeBearing = Qibla.angleDifference(heading.toDouble(), qiblaBearing.toDouble())
            val angle = Math.toRadians(relativeBearing)
            val dx = sin(angle).toFloat()
            val dy = -cos(angle).toFloat()
            val lineColor = if (aligned) glow else accent
            drawLine(
                color = lineColor,
                start = Offset(center.x + dx * card * 0.12f, center.y + dy * card * 0.12f),
                end = Offset(center.x + dx * card * 0.50f, center.y + dy * card * 0.50f),
                strokeWidth = 3 * dp,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10 * dp, 8 * dp))
            )
            val marker = Offset(center.x + dx * outer * 0.935f, center.y + dy * outer * 0.935f)
            drawKaaba(
                marker,
                outer * if (aligned) 0.105f else 0.085f,
                surface,
                glow.takeIf { aligned } ?: accent
            )
        }

        // Fixed pointer at the top: the heading is read here.
        val tip = Offset(center.x, center.y - card + 14 * dp)
        val pointer = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - 9 * dp, tip.y - 20 * dp)
            lineTo(tip.x + 9 * dp, tip.y - 20 * dp)
            close()
        }
        drawPath(pointer, brush = gradient)
        // Hub.
        drawCircle(surface, radius = card * 0.07f, center = center)
        drawCircle(gradient, radius = card * 0.07f, center = center, style = Stroke(width = 2.5f * dp))
        drawCircle(accent, radius = card * 0.025f, center = center)
    }
}

private const val ALIGNED_DEGREES = 3.0

/** Draws [text] on the dial at [radius] from the hub in direction [degrees], upright relative to the dial's own axis. */
private fun DrawScope.drawLabel(
    paint: Paint,
    text: String,
    center: Offset,
    radius: Float,
    degrees: Float,
    textSize: Float,
    color: Color
) {
    val angle = Math.toRadians(degrees.toDouble())
    val x = center.x + sin(angle).toFloat() * radius
    val y = center.y - cos(angle).toFloat() * radius
    paint.textSize = textSize
    paint.color = color.toArgb()
    val offset = -(paint.ascent() + paint.descent()) / 2f
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.rotate(degrees, x, y)
        canvas.nativeCanvas.drawText(text, x, y + offset, paint)
        canvas.nativeCanvas.restore()
    }
}

/** A little Kaaba: dark cube with the gold band, on a round plate. */
private fun DrawScope.drawKaaba(at: Offset, radius: Float, plate: Color, ring: Color) {
    drawCircle(plate, radius = radius * 1.35f, center = at)
    drawCircle(ring, radius = radius * 1.35f, center = at, style = Stroke(width = radius * 0.16f))
    val half = radius * 0.82f
    drawRoundRect(
        color = Color(0xFF141414),
        topLeft = Offset(at.x - half, at.y - half),
        size = Size(half * 2, half * 2),
        cornerRadius = CornerRadius(radius * 0.18f)
    )
    drawRect(
        color = Color(0xFFD9B44A),
        topLeft = Offset(at.x - half, at.y - half * 0.42f),
        size = Size(half * 2, half * 0.30f)
    )
}
