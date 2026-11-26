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
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderTheme
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

/** Lowest zoom the person can reach: the whole of Georgia fits on screen, nothing smaller. */
private const val MIN_ZOOM: Byte = 6
private const val MAX_ZOOM: Byte = 18

/** What kind of saved point a marker stands for. */
enum class PlaceKind { SAVED, CAR }

/** The route element captured by a drag gesture on the map. */
enum class RouteDragTarget { START, DESTINATION, WAYPOINT, ROUTE_LINE }

/** A saved point drawn on the map; the selected one is enlarged and haloed. */
class MapPlace(val id: String, val latitude: Double, val longitude: Double, val kind: PlaceKind)

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
    viaPoints: List<LatLong> = emptyList(),
    routeColor: Int = 0xFF2F6FED.toInt(),
    followUser: Boolean = false,
    fitToken: Int = 0,
    places: List<MapPlace> = emptyList(),
    selectedPlaceId: String? = null,
    origin: LatLong? = null,
    savedColor: Int = 0xFF7B5CFF.toInt(),
    carColor: Int = 0xFF1FB89A.toInt(),
    originColor: Int = 0xFF1FB89A.toInt(),
    focusToken: Int = 0,
    onPlaceTap: ((String?) -> Unit)? = null,
    onLongPress: ((LatLong) -> Unit)? = null,
    onRouteDrag: ((RouteDragTarget, Int, LatLong) -> Unit)? = null
) {
    val currentLongPress by rememberUpdatedState(onLongPress)
    val currentPlaces by rememberUpdatedState(places)
    val currentPlaceTap by rememberUpdatedState(onPlaceTap)
    val currentRoutePoints by rememberUpdatedState(routePoints)
    val currentViaPoints by rememberUpdatedState(viaPoints)
    val currentRouteDrag by rememberUpdatedState(onRouteDrag)
    key(mapFile.absolutePath, mapFile.lastModified(), preferredLanguage, mapTheme, accentColor, routeColor) {
        val overlays = remember { MapOverlays(accentColor, destinationColor, routeColor, savedColor, carColor, originColor) }
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
                            resolveMapTheme(mapTheme, viewContext)
                        )
                        layerManager.layers.add(renderLayer)
                        // The map can neither be shrunk to a speck nor dragged away from Georgia.
                        model.mapViewPosition.setZoomLevelMin(MIN_ZOOM)
                        model.mapViewPosition.setZoomLevelMax(MAX_ZOOM)
                        dataStore.boundingBox()?.let { model.mapViewPosition.setMapLimit(it) }
                        val initialPosition = dataStore.startPosition()
                        if (initialPosition != null) {
                            setCenter(initialPosition)
                        } else {
                            setCenter(LatLong(42.0, 43.5))
                        }
                        val initialZoom = dataStore.startZoomLevel()
                        setZoomLevel(initialZoom?.takeIf { it.toInt() in MIN_ZOOM..MAX_ZOOM } ?: 7.toByte())
                        mapDataStoreRef = dataStore
                    } catch (error: Exception) {
                        rendererError = error.message ?: "Не удалось открыть карту."
                    }
                    // Long press sets a navigation target. The listener returns false, so Mapsforge
                    // still handles every gesture itself.
                    var activeDrag: Pair<RouteDragTarget, Int>? = null
                    var dragPreview: Marker? = null
                    fun screenDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
                        val dx = x1 - x2
                        val dy = y1 - y2
                        return kotlin.math.sqrt(dx * dx + dy * dy)
                    }
                    fun distanceToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
                        val dx = bx - ax
                        val dy = by - ay
                        val denom = dx * dx + dy * dy
                        val t = if (denom == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / denom).coerceIn(0.0, 1.0)
                        return screenDistance(px, py, ax + dx * t, ay + dy * t)
                    }
                    fun hitRouteElement(x: Float, y: Float): Pair<RouteDragTarget, Int>? {
                        val route = currentRoutePoints ?: return null
                        if (route.size < 4) return null
                        val density = viewContext.resources.displayMetrics.density
                        val markerReach = 34.0 * density
                        val startPixel = mapViewProjection.toPixels(LatLong(route[0], route[1]))
                        val endPixel = mapViewProjection.toPixels(LatLong(route[route.size - 2], route[route.size - 1]))
                        if (startPixel != null && screenDistance(x.toDouble(), y.toDouble(), startPixel.x, startPixel.y) <= markerReach) {
                            return RouteDragTarget.START to -1
                        }
                        if (endPixel != null && screenDistance(x.toDouble(), y.toDouble(), endPixel.x, endPixel.y) <= markerReach) {
                            return RouteDragTarget.DESTINATION to -1
                        }
                        currentViaPoints.forEachIndexed { index, point ->
                            val pixel = mapViewProjection.toPixels(point) ?: return@forEachIndexed
                            if (screenDistance(x.toDouble(), y.toDouble(), pixel.x, pixel.y) <= markerReach) {
                                return RouteDragTarget.WAYPOINT to index
                            }
                        }
                        var nearest = Double.MAX_VALUE
                        for (i in 0 until route.size / 2 - 1) {
                            val a = mapViewProjection.toPixels(LatLong(route[2 * i], route[2 * i + 1])) ?: continue
                            val b = mapViewProjection.toPixels(LatLong(route[2 * i + 2], route[2 * i + 3])) ?: continue
                            nearest = minOf(nearest, distanceToSegment(x.toDouble(), y.toDouble(), a.x, a.y, b.x, b.y))
                        }
                        return if (nearest <= 19.0 * density) RouteDragTarget.ROUTE_LINE to -1 else null
                    }
                    val detector = GestureDetector(viewContext, object : GestureDetector.SimpleOnGestureListener() {
                        // A tap near a saved point selects it; a tap on empty map clears the selection.
                        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                            val callback = currentPlaceTap ?: return false
                            val density = viewContext.resources.displayMetrics.density
                            val reach = 30f * density
                            var best: String? = null
                            var bestSquared = (reach * reach).toDouble()
                            for (place in currentPlaces) {
                                val pixel = mapViewProjection.toPixels(LatLong(place.latitude, place.longitude)) ?: continue
                                // The pin's body sits above its tip, which is anchored on the point.
                                val dx = pixel.x - event.x
                                val dy = pixel.y - 18f * density - event.y
                                val squared = dx * dx + dy * dy
                                if (squared < bestSquared) {
                                    bestSquared = squared
                                    best = place.id
                                }
                            }
                            callback(best)
                            return false
                        }

                        override fun onLongPress(event: MotionEvent) {
                            val point = mapViewProjection.fromPixels(event.x.toDouble(), event.y.toDouble())
                            if (point != null) currentLongPress?.invoke(point)
                        }
                    })
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                activeDrag = hitRouteElement(event.x, event.y)
                                if (activeDrag != null) {
                                    val point = mapViewProjection.fromPixels(event.x.toDouble(), event.y.toDouble())
                                    if (point != null) {
                                        val (bitmap, offset) = pinBitmap(viewContext, routeColor, "•", true, viewContext.resources.displayMetrics.density)
                                        dragPreview = Marker(point, bitmap, 0, offset).also { layerManager.layers.add(it) }
                                    }
                                    true
                                } else {
                                    detector.onTouchEvent(event)
                                    false
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (activeDrag != null) {
                                    val point = mapViewProjection.fromPixels(event.x.toDouble(), event.y.toDouble())
                                    if (point != null) {
                                        dragPreview?.setLatLong(point)
                                        layerManager.redrawLayers()
                                    }
                                    true
                                } else {
                                    detector.onTouchEvent(event)
                                    false
                                }
                            }
                            MotionEvent.ACTION_UP -> {
                                val drag = activeDrag
                                if (drag != null) {
                                    val point = mapViewProjection.fromPixels(event.x.toDouble(), event.y.toDouble())
                                    if (point != null) currentRouteDrag?.invoke(drag.first, drag.second, point)
                                    dragPreview?.let { layerManager.layers.remove(it) }
                                    dragPreview = null
                                    activeDrag = null
                                    layerManager.redrawLayers()
                                    true
                                } else {
                                    detector.onTouchEvent(event)
                                    false
                                }
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                if (activeDrag != null) {
                                    dragPreview?.let { layerManager.layers.remove(it) }
                                    dragPreview = null
                                    activeDrag = null
                                    layerManager.redrawLayers()
                                    true
                                } else {
                                    detector.onTouchEvent(event)
                                    false
                                }
                            }
                            else -> {
                                detector.onTouchEvent(event)
                                activeDrag != null
                            }
                        }
                    }
                    mapViewRef = this
                }
            },
            update = { view ->
                overlays.sync(
                    view,
                    OverlayState(userLocation, destination, recenterToken, zoomInToken, zoomOutToken, routePoints, viaPoints, followUser, fitToken, places, selectedPlaceId, origin, focusToken)
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


private fun resolveMapTheme(value: String, context: Context): XmlRenderTheme {
    if (value == "oflayn_natural") {
        val themeFile = File(context.filesDir, "oflayn-natural.xml")
        if (!themeFile.isFile || themeFile.length() == 0L) {
            context.assets.open("themes/oflayn-natural.xml").use { input ->
                themeFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return ExternalRenderTheme(themeFile)
    }
    return when (value) {
        // The built-in HILLSHADING style needs elevation (DEM) data, which is not bundled with this map.
        "outdoor" -> MapsforgeThemes.OSMARENDER
        "motorider" -> MapsforgeThemes.MOTORIDER
        "biker" -> MapsforgeThemes.BIKER
        "osmarender" -> MapsforgeThemes.OSMARENDER
        else -> MapsforgeThemes.DEFAULT
    }
}


/** Everything the overlays depend on; one value per recomposition. */
private class OverlayState(
    val location: Location?,
    val destination: LatLong?,
    val recenterToken: Int,
    val zoomInToken: Int,
    val zoomOutToken: Int,
    val routePoints: DoubleArray?,
    val viaPoints: List<LatLong>,
    val followUser: Boolean,
    val fitToken: Int,
    val places: List<MapPlace>,
    val selectedPlaceId: String?,
    val origin: LatLong?,
    val focusToken: Int
)

/** Position marker, accuracy circle, destination marker, route line and the straight guide line. */
private class MapOverlays(
    private val accentColor: Int,
    private val destinationColor: Int,
    private val routeColor: Int,
    private val savedColor: Int,
    private val carColor: Int,
    private val originColor: Int
) {
    private val placeMarkers = ArrayList<Marker>()
    private var placeKey = ""
    private var originMarker: Marker? = null
    private var originAt: LatLong? = null
    private var handledFocus = 0
    private var focusedPlace: String? = null
    private var me: Marker? = null
    private var accuracy: Circle? = null
    private var destinationMarker: Marker? = null
    private var routeStartMarker: Marker? = null
    private var routeEndMarker: Marker? = null
    private val waypointMarkers = ArrayList<Marker>()
    private var waypointKey = ""
    private var line: Polyline? = null
    private var routeCasing: Polyline? = null
    private var routeLine: Polyline? = null
    private var lastRoute: DoubleArray? = null
    private var handledRecenter = 0
    private var handledZoomIn = 0
    private var handledZoomOut = 0
    private var handledFit = 0
    private var followZoomLevel: Int? = null

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

        if (destination != null && state.routePoints == null) {
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

        // Route start chosen from a saved point ("route from here").
        val origin = state.origin
        if (origin != null && state.routePoints == null) {
            if (originMarker == null || originAt != origin) {
                originMarker?.let { layers.remove(it) }
                val (bitmap, offset) = pinBitmap(view.context, originColor, "A", false, density)
                val created = Marker(origin, bitmap, 0, offset)
                originMarker = created
                originAt = origin
                layers.add(created)
            }
        } else if (originMarker != null) {
            originMarker?.let { layers.remove(it) }
            originMarker = null
            originAt = null
        }

        // Saved points: rebuilt only when the list or the selection changes; the selected pin is drawn last, on top.
        val key = state.places.joinToString("|") { "${it.id}:${it.latitude}:${it.longitude}" } + "#" + state.selectedPlaceId
        if (key != placeKey) {
            placeKey = key
            placeMarkers.forEach { layers.remove(it) }
            placeMarkers.clear()
            val ordered = state.places.sortedBy { if (it.id == state.selectedPlaceId) 1 else 0 }
            for (place in ordered) {
                val selected = place.id == state.selectedPlaceId
                val (bitmap, offset) = if (place.kind == PlaceKind.CAR) {
                    pinBitmap(view.context, carColor, "P", selected, density)
                } else {
                    pinBitmap(view.context, savedColor, null, selected, density)
                }
                val marker = Marker(LatLong(place.latitude, place.longitude), bitmap, 0, offset)
                placeMarkers.add(marker)
                layers.add(marker)
            }
        }
        // Bring a newly selected point into view.
        val selectedPlace = state.places.firstOrNull { it.id == state.selectedPlaceId }
        if (selectedPlace != null && (focusedPlace != selectedPlace.id || state.focusToken != handledFocus)) {
            focusedPlace = selectedPlace.id
            handledFocus = state.focusToken
            view.setCenter(LatLong(selectedPlace.latitude, selectedPlace.longitude))
            if (view.model.mapViewPosition.zoomLevel.toInt() < 13) view.setZoomLevel(14.toByte())
        } else if (selectedPlace == null) {
            focusedPlace = null
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

        // Draggable A/B handles and numbered intermediate stops follow the snapped route geometry.
        if (route != null && route.size >= 4) {
            val start = LatLong(route[0], route[1])
            val end = LatLong(route[route.size - 2], route[route.size - 1])
            if (routeStartMarker == null) {
                val (bitmap, offset) = pinBitmap(view.context, originColor, "A", true, density)
                routeStartMarker = Marker(start, bitmap, 0, offset).also { layers.add(it) }
            } else routeStartMarker?.setLatLong(start)
            if (routeEndMarker == null) {
                val (bitmap, offset) = pinBitmap(view.context, destinationColor, "B", true, density)
                routeEndMarker = Marker(end, bitmap, 0, offset).also { layers.add(it) }
            } else routeEndMarker?.setLatLong(end)
            val key = state.viaPoints.joinToString("|") { "${it.latitude},${it.longitude}" }
            if (key != waypointKey) {
                waypointKey = key
                waypointMarkers.forEach { layers.remove(it) }
                waypointMarkers.clear()
                state.viaPoints.forEachIndexed { index, point ->
                    val (bitmap, offset) = pinBitmap(view.context, routeColor, (index + 1).toString(), true, density)
                    waypointMarkers.add(Marker(point, bitmap, 0, offset).also { layers.add(it) })
                }
            }
        } else {
            routeStartMarker?.let { layers.remove(it) }
            routeEndMarker?.let { layers.remove(it) }
            routeStartMarker = null
            routeEndMarker = null
            if (waypointMarkers.isNotEmpty()) {
                waypointMarkers.forEach { layers.remove(it) }
                waypointMarkers.clear()
                waypointKey = ""
            }
        }

        // Dashed straight guide line only while there is no road route.
        val guideStart = origin ?: here
        if (guideStart != null && destination != null && lastRoute == null) {
            val polyline = line ?: Polyline(paint(accentColor, Style.STROKE, 3f * density, dashed = true), AndroidGraphicFactory.INSTANCE).also {
                line = it
                layers.add(it)
            }
            polyline.latLongs.clear()
            polyline.latLongs.add(guideStart)
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
            view.model.mapViewPosition.mapPosition = MapPosition(box.centerPoint, zoom.coerceIn(MIN_ZOOM.toInt(), 17).toByte())
        }
        if (state.followUser && here != null) {
            view.setCenter(here)
            val speedKmh = if (location?.hasSpeed() == true) location.speed * 3.6f else 0f
            val desiredZoom = when {
                speedKmh >= 90f -> 11
                speedKmh >= 60f -> 12
                speedKmh >= 35f -> 13
                speedKmh >= 15f -> 14
                else -> 16
            }
            if (followZoomLevel != desiredZoom) {
                followZoomLevel = desiredZoom
                view.setZoomLevel(desiredZoom.toByte())
            }
        } else if (!state.followUser) {
            followZoomLevel = null
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

/**
 * A map pin (teardrop) with the tip anchored on the point. [label] is drawn inside; with no label a star is drawn.
 * A selected pin is larger and has a coloured halo. Returns the bitmap and the vertical offset that puts its tip on the point.
 */
private fun pinBitmap(context: Context, color: Int, label: String?, selected: Boolean, density: Float): Pair<org.mapsforge.core.graphics.Bitmap, Int> {
    val scale = if (selected) 1.3f else 1f
    val radius = 13f * density * scale
    val pad = 7f * density
    val width = ((radius + pad) * 2).toInt()
    val cx = width / 2f
    val cy = pad + radius
    val tipY = cy + radius * 2.05f
    val height = (tipY + pad * 0.6f).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val brush = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    if (selected) {
        brush.color = withAlpha(color, 70)
        canvas.drawCircle(cx, cy, radius + pad * 0.95f, brush)
    }
    fun drawPin(outset: Float, paintColor: Int) {
        brush.color = paintColor
        val r = radius + outset
        canvas.drawCircle(cx, cy, r, brush)
        val angle = Math.toRadians(38.0)
        val path = android.graphics.Path()
        path.moveTo(cx - (r * Math.cos(angle)).toFloat(), cy + (r * Math.sin(angle)).toFloat())
        path.lineTo(cx, tipY + outset * 0.8f)
        path.lineTo(cx + (r * Math.cos(angle)).toFloat(), cy + (r * Math.sin(angle)).toFloat())
        path.close()
        canvas.drawPath(path, brush)
    }
    drawPin(2f * density, android.graphics.Color.WHITE)
    drawPin(0f, color)

    brush.color = android.graphics.Color.WHITE
    if (label != null) {
        brush.textAlign = android.graphics.Paint.Align.CENTER
        brush.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        brush.textSize = radius * 1.15f
        canvas.drawText(label, cx, cy - (brush.ascent() + brush.descent()) / 2f, brush)
    } else {
        val star = android.graphics.Path()
        val outer = radius * 0.62f
        val inner = outer * 0.45f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + (r * Math.cos(a)).toFloat()
            val y = cy + (r * Math.sin(a)).toFloat()
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        canvas.drawPath(star, brush)
    }
    val converted = AndroidGraphicFactory.convertToBitmap(android.graphics.drawable.BitmapDrawable(context.resources, bitmap))
    // Marker centres its bitmap on the point; shift it up so the tip lands there instead.
    return converted to -((tipY - height / 2f).toInt())
}
