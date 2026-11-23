package app.georgia.offline.ui

import android.app.Application
import android.content.Context
import android.location.Location
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.MapPosition
import org.mapsforge.core.util.LatLongUtils
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Circle
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import java.io.File

private object MapsforgeGraphics {
    @Volatile private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (!initialized) {
            AndroidGraphicFactory.createInstance(context.applicationContext as Application)
            initialized = true
        }
    }
}

/** Displays a local Mapsforge map file. This view never creates an online tile source. */
@Composable
fun MapsforgeMapSurface(
    mapFile: File,
    preferredLanguage: String?,
    mapTheme: String,
    modifier: Modifier = Modifier,
    userLocation: Location? = null,
    destination: LatLong? = null,
    accentColor: Int = 0xFF2F6FED.toInt(),
    destinationColor: Int = 0xFF14171C.toInt(),
    recenterToken: Int = 0,
    zoomInToken: Int = 0,
    zoomOutToken: Int = 0,
    routePoints: DoubleArray? = null,
    routeColor: Int = 0xFF2F6FED.toInt(),
    followUser: Boolean = false,
    fitToken: Int = 0,
    onLongPress: ((LatLong) -> Unit)? = null
) {
    val currentLongPress by rememberUpdatedState(onLongPress)
    key(mapFile.absolutePath, mapFile.lastModified(), preferredLanguage, mapTheme) {
        val overlays = remember { MapOverlays(accentColor, destinationColor, routeColor) }
        var mapViewRef by remember { mutableStateOf<MapView?>(null) }
        var mapDataStoreRef by remember { mutableStateOf<MapFile?>(null) }
        var rendererError by remember { mutableStateOf<String?>(null) }

        AndroidView(
            modifier = modifier,
            factory = { viewContext ->
                MapsforgeGraphics.initialize(viewContext)
                MapView(viewContext).apply {
                    setBuiltInZoomControls(false) // replaced by +/- chips so they cannot collide with the floating controls
                    getMapScaleBar().setVisible(true)

                    try {
                        val dataStore = if (preferredLanguage == null) {
                            MapFile(mapFile)
                        } else {
                            MapFile(mapFile, preferredLanguage)
                        }
                        val tileCache = AndroidUtil.createTileCache(
                            viewContext,
                            "georgia-offline-map-cache",
                            model.displayModel.tileSize,
                            1f,
                            model.frameBufferModel.overdrawFactor
                        )
                        val renderLayer = AndroidUtil.createTileRendererLayer(
                            tileCache,
                            model.mapViewPosition,
                            dataStore,
                            resolveMapTheme(mapTheme)
                        )
                        layerManager.layers.add(renderLayer)
                        val initialPosition = dataStore.startPosition()
                        if (initialPosition != null) {
                            setCenter(initialPosition)
                        } else {
                            setCenter(LatLong(42.0, 43.5))
                        }
                        val initialZoom = dataStore.startZoomLevel()
                        setZoomLevel(initialZoom?.takeIf { it.toInt() in 1..18 } ?: 7.toByte())
                        mapDataStoreRef = dataStore
                    } catch (error: Exception) {
                        rendererError = error.message ?: "Не удалось открыть карту."
                    }
                    // Long press sets a navigation target. The listener returns false, so Mapsforge
                    // still handles every gesture itself.
                    val detector = GestureDetector(viewContext, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onLongPress(event: MotionEvent) {
                            val point = mapViewProjection.fromPixels(event.x.toDouble(), event.y.toDouble())
                            if (point != null) currentLongPress?.invoke(point)
                        }
                    })
                    setOnTouchListener { _, event ->
                        detector.onTouchEvent(event)
                        false
                    }
                    mapViewRef = this
                }
            },
            update = { view ->
                overlays.sync(
                    view,
                    OverlayState(userLocation, destination, recenterToken, zoomInToken, zoomOutToken, routePoints, followUser, fitToken)
                )
            }
        )

        if (rendererError != null) {
            androidx.compose.material3.Text(
                text = "Ошибка отображения карты: $rendererError",
                color = androidx.compose.material3.MaterialTheme.colorScheme.error
            )
        }

        DisposableEffect(Unit) {
            onDispose {
                runCatching { mapViewRef?.destroyAll() }
                runCatching { mapDataStoreRef?.close() }
                AndroidGraphicFactory.clearResourceMemoryCache()
            }
        }
    }
}


private fun resolveMapTheme(value: String) = when (value) {
    "outdoor" -> MapsforgeThemes.HILLSHADING
    "motorider" -> MapsforgeThemes.MOTORIDER
    "biker" -> MapsforgeThemes.BIKER
    "osmarender" -> MapsforgeThemes.OSMARENDER
    else -> MapsforgeThemes.DEFAULT
}


/** Everything the overlays depend on; one value per recomposition. */
private class OverlayState(
    val location: Location?,
    val destination: LatLong?,
    val recenterToken: Int,
    val zoomInToken: Int,
    val zoomOutToken: Int,
    val routePoints: DoubleArray?,
    val followUser: Boolean,
    val fitToken: Int
)

/** Position marker, accuracy circle, destination marker, route line and the straight guide line. */
private class MapOverlays(private val accentColor: Int, private val destinationColor: Int, private val routeColor: Int) {
    private var me: Marker? = null
    private var accuracy: Circle? = null
    private var destinationMarker: Marker? = null
    private var line: Polyline? = null
    private var routeCasing: Polyline? = null
    private var routeLine: Polyline? = null
    private var lastRoute: DoubleArray? = null
    private var handledRecenter = 0
    private var handledZoomIn = 0
    private var handledZoomOut = 0
    private var handledFit = 0
    private var followZoomed = false

    fun sync(view: MapView, state: OverlayState) {
        if (view.layerManager.layers.size() == 0) return // renderer failed to open the map
        val layers = view.layerManager.layers
        val density = view.context.resources.displayMetrics.density
        val location = state.location
        val destination = state.destination
        val here = location?.let { LatLong(it.latitude, it.longitude) }

        if (here != null) {
            val radius = location.accuracy
            val showAccuracy = location.hasAccuracy() && radius in 8f..2000f
            val circle = accuracy
            if (showAccuracy) {
                if (circle == null) {
                    val created = Circle(here, radius, paint(withAlpha(accentColor, 40), Style.FILL), paint(withAlpha(accentColor, 120), Style.STROKE, 1.5f * density))
                    accuracy = created
                    layers.add(created)
                } else {
                    circle.setLatLong(here)
                    circle.setRadius(radius)
                }
            } else if (circle != null) {
                layers.remove(circle)
                accuracy = null
            }
            val marker = me
            if (marker == null) {
                val created = Marker(here, markerBitmap(view.context, accentColor, false), 0, 0)
                me = created
                layers.add(created)
            } else {
                marker.setLatLong(here)
            }
        } else {
            me?.let { layers.remove(it) }
            accuracy?.let { layers.remove(it) }
            me = null
            accuracy = null
        }

        if (destination != null) {
            val marker = destinationMarker
            if (marker == null) {
                val created = Marker(destination, markerBitmap(view.context, destinationColor, true), 0, 0)
                destinationMarker = created
                layers.add(created)
            } else {
                marker.setLatLong(destination)
            }
        } else {
            destinationMarker?.let { layers.remove(it) }
            destinationMarker = null
        }

        // Road route: a white casing under a coloured line, inserted below the markers.
        val route = state.routePoints
        if (route != null && route.size >= 4) {
            if (route !== lastRoute) {
                val casing = routeCasing ?: Polyline(paint(0xFFFFFFFF.toInt(), Style.STROKE, 9f * density), AndroidGraphicFactory.INSTANCE).also {
                    routeCasing = it
                    layers.add(1, it)
                }
                val main = routeLine ?: Polyline(paint(routeColor, Style.STROKE, 5.5f * density), AndroidGraphicFactory.INSTANCE).also {
                    routeLine = it
                    layers.add(2, it)
                }
                for (target in listOf(casing, main)) {
                    val list = target.latLongs
                    list.clear()
                    for (i in 0 until route.size / 2) list.add(LatLong(route[2 * i], route[2 * i + 1]))
                }
                lastRoute = route
            }
        } else if (lastRoute != null) {
            routeCasing?.let { layers.remove(it) }
            routeLine?.let { layers.remove(it) }
            routeCasing = null
            routeLine = null
            lastRoute = null
        }

        // Dashed straight guide line only while there is no road route.
        if (here != null && destination != null && lastRoute == null) {
            val polyline = line ?: Polyline(paint(accentColor, Style.STROKE, 3f * density, dashed = true), AndroidGraphicFactory.INSTANCE).also {
                line = it
                layers.add(it)
            }
            polyline.latLongs.clear()
            polyline.latLongs.add(here)
            polyline.latLongs.add(destination)
        } else {
            line?.let { layers.remove(it) }
            line = null
        }

        if (state.recenterToken != handledRecenter && here != null) {
            handledRecenter = state.recenterToken
            view.setCenter(here)
            if (view.model.mapViewPosition.zoomLevel.toInt() < 14) view.setZoomLevel(15.toByte())
        }
        if (state.fitToken != handledFit && route != null && route.size >= 4) {
            handledFit = state.fitToken
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE
            for (i in 0 until route.size / 2) {
                minLat = minOf(minLat, route[2 * i])
                maxLat = maxOf(maxLat, route[2 * i])
                minLon = minOf(minLon, route[2 * i + 1])
                maxLon = maxOf(maxLon, route[2 * i + 1])
            }
            val box = BoundingBox(minLat, minLon, maxLat, maxLon)
            val zoom = LatLongUtils.zoomForBounds(view.dimension, box, view.model.displayModel.tileSize).toInt() - 1
            view.model.mapViewPosition.mapPosition = MapPosition(box.centerPoint, zoom.coerceIn(5, 17).toByte())
        }
        if (state.followUser && here != null) {
            view.setCenter(here)
            if (!followZoomed) {
                followZoomed = true
                if (view.model.mapViewPosition.zoomLevel.toInt() < 16) view.setZoomLevel(16.toByte())
            }
        } else if (!state.followUser) {
            followZoomed = false
        }
        if (state.zoomInToken != handledZoomIn) {
            handledZoomIn = state.zoomInToken
            view.model.mapViewPosition.zoomIn()
        }
        if (state.zoomOutToken != handledZoomOut) {
            handledZoomOut = state.zoomOutToken
            view.model.mapViewPosition.zoomOut()
        }
        view.layerManager.redrawLayers()
    }
}

private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

private fun paint(color: Int, style: Style, strokeWidth: Float = 0f, dashed: Boolean = false): org.mapsforge.core.graphics.Paint {
    val paint = AndroidGraphicFactory.INSTANCE.createPaint()
    paint.setColor(color)
    paint.setStyle(style)
    if (strokeWidth > 0f) paint.setStrokeWidth(strokeWidth)
    if (dashed) paint.setDashPathEffect(floatArrayOf(24f, 16f))
    return paint
}

/** A round marker with a white ring; the destination gets a white centre so the two read apart. */
private fun markerBitmap(context: Context, color: Int, destination: Boolean): org.mapsforge.core.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (28 * density).toInt().coerceAtLeast(24)
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val brush = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val center = size / 2f
    brush.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, center, brush)
    brush.color = color
    canvas.drawCircle(center, center, center * 0.74f, brush)
    if (destination) {
        brush.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, center * 0.28f, brush)
    }
    return AndroidGraphicFactory.convertToBitmap(android.graphics.drawable.BitmapDrawable(context.resources, bitmap))
}
