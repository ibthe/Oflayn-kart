package app.georgia.offline.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Local-only preferences. This class does not perform network or account operations. */
class LocalSettings(context: Context) {
    private val preferences = context.getSharedPreferences("georgia_offline_settings", Context.MODE_PRIVATE)

    var enabledLanguages by mutableStateOf(
        setOfNotNull(
            "en".takeIf { preferences.getBoolean("language_en", true) },
            "ru".takeIf { preferences.getBoolean("language_ru", true) },
            "ka".takeIf { preferences.getBoolean("language_ka", true) }
        )
    )
        private set

    var appTheme by mutableStateOf(preferences.getString("app_theme", "system") ?: "system")
        private set

    var mapTheme by mutableStateOf(preferences.getString("map_theme", "classic") ?: "classic")
        private set

    var storageTreeUri by mutableStateOf(preferences.getString("storage_tree_uri", null))
        private set

    var carLatitude by mutableStateOf(preferences.getString("car_latitude", null)?.toDoubleOrNull())
        private set

    var carLongitude by mutableStateOf(preferences.getString("car_longitude", null)?.toDoubleOrNull())
        private set

    fun setLanguage(code: String, enabled: Boolean) {
        if (code !in SUPPORTED_LANGUAGES) return
        val updated = enabledLanguages.toMutableSet().apply {
            if (enabled) add(code) else remove(code)
        }
        enabledLanguages = updated
        preferences.edit().putBoolean("language_$code", enabled).apply()
    }

    fun updateAppTheme(value: String) {
        if (value !in APP_THEMES) return
        appTheme = value
        preferences.edit().putString("app_theme", value).apply()
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

    companion object {
        val SUPPORTED_LANGUAGES = setOf("en", "ru", "ka")
        val APP_THEMES = setOf("system", "light", "dark")
        val MAP_THEMES = setOf("classic", "outdoor", "night", "high_contrast")
    }
}
