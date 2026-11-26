package app.georgia.offline.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

/** A place the person saved; the car mark is exposed as one too, with id [LocalSettings.CAR_PLACE_ID]. */
data class SavedPlace(val id: String, val name: String, val latitude: Double, val longitude: Double)

/** Local-only preferences. This class does not perform network or account operations. */
class LocalSettings(context: Context) {
    private val preferences = context.getSharedPreferences("georgia_offline_settings", Context.MODE_PRIVATE)

    /** The single interface/map language: "ru", "en" or "ka". */
    var language by mutableStateOf(readLanguage())
        private set

    /** Kept for code that still asks "which languages are shown": always exactly the chosen one. */
    val enabledLanguages: Set<String> get() = setOf(language)

    /** Chosen colour family id. */
    var palette by mutableStateOf(
        preferences.getString("color_palette", null)?.takeIf { it in PALETTES } ?: "mist"
    )
        private set

    /** "system", "light", "dim" or "dark". Older versions tied light/dark to the palette, so migrate that choice. */
    var themeMode by mutableStateOf(readThemeMode())
        private set

    /** Opacity of floating layers (cards, bars, chips): 0.25 (see-through) .. 1.0 (solid). */
    var layerOpacity by mutableFloatStateOf(
        preferences.getFloat("layer_opacity", DEFAULT_LAYER_OPACITY).coerceIn(MIN_LAYER_OPACITY, 1f)
    )
        private set

    /** Places the person saved from the map or from the places list. */
    var savedPlaces by mutableStateOf(readSavedPlaces())
        private set

    var mapTheme by mutableStateOf(
        preferences.getString("map_theme", "classic")?.takeIf { it in MAP_THEMES } ?: "classic"
    )
        private set

    var storageTreeUri by mutableStateOf(preferences.getString("storage_tree_uri", null))
        private set

    var mapFilePath by mutableStateOf(preferences.getString("map_file_path", null))
        private set

    /** True only when the currently selected map was installed from this APK. */
    var mapFileIsBundled by mutableStateOf(preferences.getBoolean("map_file_is_bundled", false))
        private set

    /** User may explicitly hide/disable the map without it being reinstalled on next launch. */
    var mapDisabled by mutableStateOf(preferences.getBoolean("map_disabled", false))
        private set

    var carLatitude by mutableStateOf(preferences.getString("car_latitude", null)?.toDoubleOrNull())
        private set

    var carLongitude by mutableStateOf(preferences.getString("car_longitude", null)?.toDoubleOrNull())
        private set

    fun updateLanguage(code: String) {
        if (code !in SUPPORTED_LANGUAGES) return
        language = code
        preferences.edit().putString("language", code).apply()
    }

    fun updatePalette(value: String) {
        if (value !in PALETTES) return
        palette = value
        preferences.edit().putString("color_palette", value).apply()
    }

    fun updateThemeMode(value: String) {
        if (value !in THEME_MODES) return
        themeMode = value
        preferences.edit().putString("theme_mode", value).apply()
    }

    fun updateLayerOpacity(value: Float) {
        val clamped = value.coerceIn(MIN_LAYER_OPACITY, 1f)
        layerOpacity = clamped
        preferences.edit().putFloat("layer_opacity", clamped).apply()
    }

    fun updateMapTheme(value: String) {
        if (value !in MAP_THEMES) return
        mapTheme = value
        preferences.edit().putString("map_theme", value).apply()
    }

    fun setStorageTree(uri: Uri?) {
        storageTreeUri = uri?.toString()
        preferences.edit().putString("storage_tree_uri", storageTreeUri).apply()
    }

    fun storageUri(): Uri? = storageTreeUri?.let(Uri::parse)

    /** Stores the active Mapsforge map path and whether it came from the APK bundle. */
    fun setMapFilePath(path: String?, bundled: Boolean = false) {
        mapFilePath = path
        mapFileIsBundled = path != null && bundled
        mapDisabled = path == null
        preferences.edit()
            .putString("map_file_path", path)
            .putBoolean("map_file_is_bundled", mapFileIsBundled)
            .putBoolean("map_disabled", mapDisabled)
            .apply()
    }

    /** Re-enables the map packaged in the APK after a user has hidden it. */
    fun enableBundledMap() {
        mapFilePath = null
        mapFileIsBundled = false
        mapDisabled = false
        preferences.edit()
            .remove("map_file_path")
            .putBoolean("map_file_is_bundled", false)
            .putBoolean("map_disabled", false)
            .apply()
    }

    /** Switches away from a user-imported map and back to the APK-bundled country map. */
    fun restoreBundledMap() = enableBundledMap()

    /** Save a car marker only after the user explicitly taps the save action. */
    fun saveCarLocation(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        carLatitude = latitude
        carLongitude = longitude
        preferences.edit()
            .putString("car_latitude", latitude.toString())
            .putString("car_longitude", longitude.toString())
            .apply()
    }

    fun clearCarLocation() {
        carLatitude = null
        carLongitude = null
        preferences.edit()
            .remove("car_latitude")
            .remove("car_longitude")
            .apply()
    }

    /** Adds a place and returns it. Names are trimmed; an empty name becomes "Точка N". */
    fun addPlace(name: String, latitude: Double, longitude: Double): SavedPlace? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        // The same spot saved twice is one place.
        savedPlaces.firstOrNull { abs(it.latitude - latitude) < 1e-5 && abs(it.longitude - longitude) < 1e-5 }?.let { return it }
        val label = name.trim().ifEmpty { "Точка ${savedPlaces.size + 1}" }.take(80)
        val place = SavedPlace(UUID.randomUUID().toString(), label, latitude, longitude)
        savedPlaces = savedPlaces + place
        persistPlaces()
        return place
    }

    fun removePlace(id: String) {
        if (id == CAR_PLACE_ID) {
            clearCarLocation()
            return
        }
        savedPlaces = savedPlaces.filterNot { it.id == id }
        persistPlaces()
    }

    fun renamePlace(id: String, name: String) {
        val label = name.trim().take(80)
        if (label.isEmpty()) return
        savedPlaces = savedPlaces.map { if (it.id == id) it.copy(name = label) else it }
        persistPlaces()
    }

    /** Everything that can be shown as a saved point on the map: saved places plus the car mark. */
    fun allPlaces(): List<SavedPlace> {
        val lat = carLatitude
        val lon = carLongitude
        val car = if (lat != null && lon != null) listOf(SavedPlace(CAR_PLACE_ID, "Автомобиль", lat, lon)) else emptyList()
        return car + savedPlaces
    }

    private fun persistPlaces() {
        val array = JSONArray()
        savedPlaces.forEach { place ->
            array.put(
                JSONObject()
                    .put("id", place.id)
                    .put("name", place.name)
                    .put("lat", place.latitude)
                    .put("lon", place.longitude)
            )
        }
        preferences.edit().putString("saved_places", array.toString()).apply()
    }

    private fun readSavedPlaces(): List<SavedPlace> {
        val raw = preferences.getString("saved_places", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val lat = item.optDouble("lat", Double.NaN)
                val lon = item.optDouble("lon", Double.NaN)
                if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
                SavedPlace(
                    id = item.optString("id").ifEmpty { UUID.randomUUID().toString() },
                    name = item.optString("name").ifEmpty { "Точка" },
                    latitude = lat,
                    longitude = lon
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun readLanguage(): String {
        preferences.getString("language", null)?.takeIf { it in SUPPORTED_LANGUAGES }?.let { return it }
        // Versions before 0.8 let several languages be switched on at once; keep the first of them.
        return listOf("ru", "en", "ka").firstOrNull { preferences.getBoolean("language_$it", true) } ?: "ru"
    }

    private fun readThemeMode(): String {
        preferences.getString("theme_mode", null)?.takeIf { it in THEME_MODES }?.let { return it }
        // Old palettes carried their own brightness: Dusk and Graphite were the dark ones.
        return when (preferences.getString("color_palette", null)) {
            "dusk", "graphite" -> "dark"
            "mist", "sage", "sand" -> "light"
            else -> "system"
        }
    }

    companion object {
        const val CAR_PLACE_ID = "car"
        const val DEFAULT_LAYER_OPACITY = 0.92f
        const val MIN_LAYER_OPACITY = 0.25f
        val SUPPORTED_LANGUAGES = setOf("en", "ru", "ka")
        val PALETTES = setOf("mist", "sage", "sand", "dusk", "graphite", "hc_mono", "hc_amber")
        val THEME_MODES = setOf("system", "light", "dim", "dark")
        val MAP_THEMES = setOf("classic", "oflayn_natural", "oflayn_natural_dark", "outdoor", "motorider", "biker")
    }
}
