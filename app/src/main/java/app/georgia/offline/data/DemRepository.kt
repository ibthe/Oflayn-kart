package app.georgia.offline.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import app.georgia.offline.domain.GeoMath
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong

data class ElevationSample(
    val distanceMeters: Double,
    val elevationMeters: Double
)

data class ElevationProfile(
    val samples: List<ElevationSample>,
    val ascentMeters: Int,
    val descentMeters: Int,
    val minimumMeters: Int,
    val maximumMeters: Int
)

/** Lazy, memory-bounded reader for the bundled 90 m Copernicus DEM tiles. */
object DemRepository {
    private const val TILE_PIXELS = 1200
    private const val TILE_BYTES = TILE_PIXELS * TILE_PIXELS * 2
    private const val NO_DATA = -32768
    private const val MAX_CACHED_TILES = 4
    private const val DEFAULT_SAMPLE_SPACING_METERS = 100.0
    private const val ELEVATION_NOISE_THRESHOLD_METERS = 2.0

    private val tileCache = object : LinkedHashMap<String, ShortArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ShortArray>?): Boolean =
            size > MAX_CACHED_TILES
    }

    suspend fun elevationAt(context: Context, latitude: Double, longitude: Double): Double? =
        withContext(Dispatchers.IO) { sample(context, latitude, longitude) }

    suspend fun profile(
        context: Context,
        geometry: List<LatLong>,
        sampleSpacingMeters: Double = DEFAULT_SAMPLE_SPACING_METERS
    ): ElevationProfile? = withContext(Dispatchers.IO) {
        if (geometry.size < 2) return@withContext null
        val spacing = sampleSpacingMeters.coerceIn(50.0, 500.0)
        val samples = mutableListOf<ElevationSample>()
        var cumulativeDistance = 0.0
        geometry.zipWithNext().forEachIndexed { segmentIndex, (start, end) ->
            val segmentDistance = GeoMath.distanceMeters(
                start.latitude, start.longitude, end.latitude, end.longitude
            )
            if (segmentDistance <= 0.1) return@forEachIndexed
            val steps = max(1, ceil(segmentDistance / spacing).toInt())
            for (step in 0..steps) {
                if (segmentIndex > 0 && step == 0) continue
                val fraction = step.toDouble() / steps
                val latitude = start.latitude + (end.latitude - start.latitude) * fraction
                val longitude = start.longitude + (end.longitude - start.longitude) * fraction
                val elevation = sample(context, latitude, longitude) ?: continue
                samples += ElevationSample(cumulativeDistance + segmentDistance * fraction, elevation)
            }
            cumulativeDistance += segmentDistance
        }
        if (samples.size < 2) return@withContext null
        var ascent = 0.0
        var descent = 0.0
        samples.zipWithNext().forEach { (previous, next) ->
            val delta = next.elevationMeters - previous.elevationMeters
            if (delta > ELEVATION_NOISE_THRESHOLD_METERS) ascent += delta
            if (delta < -ELEVATION_NOISE_THRESHOLD_METERS) descent -= delta
        }
        ElevationProfile(
            samples = samples,
            ascentMeters = ascent.roundToInt(),
            descentMeters = descent.roundToInt(),
            minimumMeters = samples.minOf { it.elevationMeters }.roundToInt(),
            maximumMeters = samples.maxOf { it.elevationMeters }.roundToInt()
        )
    }



    fun bundledHillshadeBytes(context: Context): Long = runCatching {
        val json = context.assets.open("dem/manifest.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val tiles = JSONObject(json).optJSONArray("tiles") ?: return 0L
        (0 until tiles.length()).sumOf { index ->
            tiles.optJSONObject(index)?.optLong("hgtCompressedBytes", 0L)?.coerceAtLeast(0L) ?: 0L
        }
    }.getOrDefault(0L)

    fun installedHillshadeBytes(context: Context): Long =
        File(context.filesDir, "dem/hgt").walkTopDown()
            .filter { it.isFile && it.name.endsWith(".hgt.zip") }
            .sumOf { it.length() }

    /**
     * Installs the bundled HGT archives into app-private storage for Mapsforge's hillshading
     * reader. Files are copied atomically and checked against the manifest; no network is used.
     */
    suspend fun ensureHillshadeInstalled(context: Context): File? = withContext(Dispatchers.IO) {
        val manifestBytes = runCatching {
            context.assets.open("dem/manifest.json").use { it.readBytes() }
        }.getOrNull() ?: return@withContext null
        val manifest = runCatching { JSONObject(String(manifestBytes, Charsets.UTF_8)) }.getOrNull()
            ?: return@withContext null
        val tiles = manifest.optJSONArray("tiles") ?: return@withContext null
        val expected = mutableListOf<Triple<String, String, Long>>()
        for (index in 0 until tiles.length()) {
            val tile = tiles.optJSONObject(index) ?: continue
            val relativePath = tile.optString("hgtFile").takeIf { it.startsWith("hgt/") } ?: continue
            val sha256 = tile.optString("hgtSha256").takeIf { it.length == 64 } ?: continue
            val size = tile.optLong("hgtCompressedBytes", -1L).takeIf { it > 0L } ?: continue
            expected += Triple(relativePath.removePrefix("hgt/"), sha256, size)
        }
        if (expected.isEmpty()) return@withContext null

        val manifestHash = sha256(manifestBytes)
        val hillshadeRoot = File(context.filesDir, "dem/hgt")
        val directory = File(hillshadeRoot, manifestHash.take(12))
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Не удалось подготовить локальные данные рельефа.")
        }
        val marker = File(directory, ".manifest-sha256")
        val alreadyInstalled = marker.isFile && runCatching { marker.readText() }.getOrNull() == manifestHash &&
            expected.all { (name, _, size) -> File(directory, name).let { it.isFile && it.length() == size } }
        if (!alreadyInstalled) {
            expected.forEach { (name, expectedSha, expectedSize) ->
                val assetPath = "dem/hgt/$name"
                val bytes = context.assets.open(assetPath).use { it.readBytes() }
                if (bytes.size.toLong() != expectedSize || sha256(bytes) != expectedSha) {
                    throw IllegalStateException("Повреждён архив рельефа: $name")
                }
                val target = File(directory, name)
                val temporary = File(directory, "$name.tmp")
                target.parentFile?.let { if (!it.exists() && !it.mkdirs()) error("Не удалось создать каталог рельефа.") }
                temporary.writeBytes(bytes)
                if (target.exists() && !target.delete()) {
                    temporary.delete()
                    throw IllegalStateException("Не удалось заменить архив рельефа: $name")
                }
                if (!temporary.renameTo(target)) {
                    temporary.delete()
                    throw IllegalStateException("Не удалось сохранить архив рельефа: $name")
                }
            }
            val expectedNames = expected.map { it.first }.toSet()
            directory.listFiles()?.filter { it.name.endsWith(".hgt.zip") && it.name !in expectedNames }
                ?.forEach { it.delete() }
            marker.writeText(manifestHash)
        }
        hillshadeRoot.listFiles()?.filter { it.isDirectory && it != directory }?.forEach { it.deleteRecursively() }
        directory
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @Synchronized
    fun clearCache() = tileCache.clear()

    private fun sample(context: Context, latitude: Double, longitude: Double): Double? {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return null
        val tileLatitude = floor(latitude).toInt()
        val tileLongitude = floor(longitude).toInt()
        val key = tileKey(tileLatitude, tileLongitude)
        val tile = loadTile(context, key) ?: return null
        val row = ((tileLatitude + 1.0 - latitude) * TILE_PIXELS)
            .roundToInt().coerceIn(0, TILE_PIXELS - 1)
        val column = ((longitude - tileLongitude) * TILE_PIXELS)
            .roundToInt().coerceIn(0, TILE_PIXELS - 1)
        val value = tile[row * TILE_PIXELS + column].toInt()
        return if (value == NO_DATA) null else value.toDouble()
    }

    @Synchronized
    private fun loadTile(context: Context, key: String): ShortArray? {
        tileCache[key]?.let { return it }
        val bytes = try {
            GZIPInputStream(context.assets.open("dem/$key.dem.gzip")).use { it.readBytes() }
        } catch (_: Exception) {
            return null
        }
        if (bytes.size != TILE_BYTES) return null
        val values = ShortArray(TILE_PIXELS * TILE_PIXELS)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(values)
        tileCache[key] = values
        return values
    }

    private fun tileKey(latitude: Int, longitude: Int): String {
        val ns = if (latitude >= 0) "N" else "S"
        val ew = if (longitude >= 0) "E" else "W"
        return "$ns${latitude.absoluteValue.toString().padStart(2, '0')}$ew${longitude.absoluteValue.toString().padStart(3, '0')}"
    }
}
