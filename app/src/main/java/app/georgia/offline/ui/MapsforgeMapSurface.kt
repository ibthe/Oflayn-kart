package app.georgia.offline.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
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
    modifier: Modifier = Modifier
) {
    key(mapFile.absolutePath, mapFile.lastModified(), preferredLanguage, mapTheme) {
        var mapViewRef by remember { mutableStateOf<MapView?>(null) }
        var mapDataStoreRef by remember { mutableStateOf<MapFile?>(null) }
        var rendererError by remember { mutableStateOf<String?>(null) }

        AndroidView(
            modifier = modifier,
            factory = { viewContext ->
                MapsforgeGraphics.initialize(viewContext)
                MapView(viewContext).apply {
                    setBuiltInZoomControls(true)
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
                    mapViewRef = this
                }
            },
            update = { /* map position is controlled by Mapsforge gestures and controls */ }
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
