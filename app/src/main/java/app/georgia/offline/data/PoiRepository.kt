package app.georgia.offline.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

/** Installs and searches the bundled multilingual POI database; no network access is used. */
object PoiRepository {
    private const val ASSET_DB = "offline/georgia-poi.sqlite"
    private const val BUILD_MANIFEST = "offline/georgia-map-build.json"
    private const val MIN_DB_BYTES = 4096L
    private const val HASH_MARKER = ".georgia-poi.sha256"

    data class PoiRecord(
        val poiId: String,
        val category: String,
        val subcategory: String,
        val name: String,
        val nameEn: String,
        val nameRu: String,
        val nameKa: String,
        val latitude: Double,
        val longitude: Double,
        val tagsJson: String = "{}"
    ) {
        /** The one name to show for [language], falling back to the nearest readable alternative. */
        fun nameIn(language: String): String = pickName(language, name, nameEn, nameRu, nameKa)

        /** Settlement written in the OSM address tags, if the object has one. */
        fun taggedSettlement(): String? {
            val tags = runCatching { JSONObject(tagsJson) }.getOrNull() ?: return null
            return listOf("addr:city", "addr:town", "addr:village", "addr:place")
                .firstNotNullOfOrNull { key -> tags.optString(key, "").trim().takeIf { it.isNotEmpty() } }
        }

        fun namesFor(enabledLanguages: Set<String>): List<String> {
            val values = buildList {
                if ("en" in enabledLanguages && nameEn.isNotBlank()) add(nameEn)
                if ("ru" in enabledLanguages && nameRu.isNotBlank()) add(nameRu)
                if ("ka" in enabledLanguages && nameKa.isNotBlank()) add(nameKa)
            }.distinct()
            return values.ifEmpty { listOf(name) }
        }

        /** Public details are shown only when the source OSM object supplies them. */
        fun detailLines(): List<Pair<String, String>> {
            val tags = runCatching { JSONObject(tagsJson) }.getOrNull() ?: return emptyList()
            fun first(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
                tags.optString(key, "").trim().takeIf { it.isNotEmpty() }
            }

            val address = first("addr:full") ?: run {
                val house = first("addr:housenumber")
                val street = first("addr:street", "addr:place")
                val streetLine = listOfNotNull(house, street).joinToString(" ").takeIf { it.isNotBlank() }
                listOfNotNull(
                    streetLine,
                    first("addr:city", "addr:town", "addr:village"),
                    first("addr:postcode")
                ).joinToString(", ").takeIf { it.isNotBlank() }
            }
            return buildList {
                address?.let { add("Адрес" to it) }
                first("contact:phone", "phone", "telephone")?.let { add("Телефон" to it) }
                first("contact:website", "website", "url")?.let { add("Сайт" to it) }
                first("opening_hours")?.let { add("Часы работы (по данным OSM)" to it) }
                first("cuisine")?.let { add("Кухня" to it) }
                first("wheelchair")?.let { add("Доступность" to it) }
                first("internet_access")?.let { add("Интернет" to it) }
                first("operator", "brand")?.let { add("Оператор / бренд" to it) }
            }.take(8)
        }
    }

    /** A city, town, village or hamlet from the same database, used to say where an object is. */
    data class Settlement(
        val subcategory: String,
        val name: String,
        val nameEn: String,
        val nameRu: String,
        val nameKa: String,
        val latitude: Double,
        val longitude: Double
    ) {
        fun nameIn(language: String): String = pickName(language, name, nameEn, nameRu, nameKa)
    }

    /** Picks one name: the chosen language first, then the most readable alternatives. */
    fun pickName(language: String, name: String, en: String, ru: String, ka: String): String {
        val order = when (language) {
            "ru" -> listOf(ru, en, name, ka)
            "en" -> listOf(en, ru, name, ka)
            else -> listOf(ka, name, en, ru)
        }
        return order.firstOrNull { it.isNotBlank() } ?: ""
    }

    @Volatile private var settlementCache: Pair<String, List<Settlement>>? = null

    /** All settlements, read once per database file (a few thousand rows) and kept in memory. */
    suspend fun settlements(database: File): List<Settlement> = withContext(Dispatchers.IO) {
        val key = "${database.absolutePath}:${database.length()}"
        settlementCache?.takeIf { it.first == key }?.let { return@withContext it.second }
        val db = SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val loaded = try {
            db.rawQuery(
                "SELECT subcategory, name, name_en, name_ru, name_ka, latitude, longitude FROM poi WHERE category = 'settlement'",
                null
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(Settlement(
                            subcategory = cursor.getString(0).orEmpty(),
                            name = cursor.getString(1).orEmpty(),
                            nameEn = cursor.getString(2).orEmpty(),
                            nameRu = cursor.getString(3).orEmpty(),
                            nameKa = cursor.getString(4).orEmpty(),
                            latitude = cursor.getDouble(5),
                            longitude = cursor.getDouble(6)
                        ))
                    }
                }
            }
        } finally {
            db.close()
        }
        settlementCache = key to loaded
        loaded
    }

    /** The closest settlement within [maxKm], by flat-earth distance (fine at Georgia's latitude). */
    fun nearestSettlement(settlements: List<Settlement>, latitude: Double, longitude: Double, maxKm: Double = 30.0): Settlement? {
        val kmPerLon = 111.32 * Math.cos(Math.toRadians(latitude))
        var best: Settlement? = null
        var bestSquared = maxKm * maxKm
        for (candidate in settlements) {
            val dy = (candidate.latitude - latitude) * 110.57
            if (dy * dy >= bestSquared) continue
            val dx = (candidate.longitude - longitude) * kmPerLon
            val squared = dx * dx + dy * dy
            if (squared < bestSquared) {
                bestSquared = squared
                best = candidate
            }
        }
        return best
    }

    data class SearchResult(val database: File?, val poiCount: Int, val message: String?)

    /**
     * Installation is serialized because Map and Places screens can briefly coexist during a tab
     * transition and both request the same bundled database.
     */
    private val installationLock = Any()

    suspend fun ensureInstalled(context: Context): SearchResult = withContext(Dispatchers.IO) {
        synchronized(installationLock) { installBlocking(context) }
    }

    private fun installBlocking(context: Context): SearchResult {
        val manifest = readBuildManifest(context)
        val expectedHash = manifest?.optString("poiFileSha256")
            ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
        val expectedSize = manifest?.optLong("poiFileSizeBytes", -1L)?.takeIf { it > 0L }
        if (expectedHash == null || expectedSize == null) {
            return SearchResult(null, 0, "Офлайн-база POI ещё не включена в эту сборку приложения.")
        }

        val directory = File(context.filesDir, "poi")
        if (!directory.exists() && !directory.mkdirs()) {
            return SearchResult(null, 0, "Не удалось создать локальную папку базы объектов.")
        }
        val database = File(directory, "georgia-poi.sqlite")
        val marker = File(directory, HASH_MARKER)
        if (database.isFile && database.length() == expectedSize && marker.isFile &&
            marker.readText(Charsets.UTF_8).trim().equals(expectedHash, ignoreCase = true) &&
            runCatching { validateDatabase(database) }.getOrDefault(false)
        ) {
            return SearchResult(database, readCount(database), null)
        }

        val requiredSpace = expectedSize + 8L * 1024L * 1024L
        if (directory.usableSpace < requiredSpace) {
            return SearchResult(null, 0, "Недостаточно свободного места для локального справочника объектов.")
        }

        val input = try {
            context.assets.open(ASSET_DB)
        } catch (_: FileNotFoundException) {
            return SearchResult(null, 0, "База объектов не найдена во встроенных ресурсах APK.")
        }
        val temporary = File(directory, "georgia-poi.sqlite.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            input.use { source ->
                temporary.outputStream().buffered().use { target ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        target.write(buffer, 0, count)
                    }
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualHash.equals(expectedHash, ignoreCase = true) || temporary.length() != expectedSize) {
                error("Контрольная сумма локальной базы объектов не совпадает с манифестом APK.")
            }
            if (!validateDatabase(temporary)) error("Встроенная база POI повреждена или имеет неверный формат.")
            try {
                Files.move(
                    temporary.toPath(), database.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), database.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            marker.writeText(actualHash, Charsets.UTF_8)
            SearchResult(database, readCount(database), null)
        } catch (error: Exception) {
            temporary.delete()
            SearchResult(null, 0, error.message ?: "Не удалось установить базу объектов.")
        }
    
    }

    suspend fun search(
        database: File,
        query: String,
        category: String = "all",
        limit: Int = 80
    ): List<PoiRecord> = withContext(Dispatchers.IO) {
        val conditions = mutableListOf<String>()
        val arguments = mutableListOf<String>()
        if (category != "all") {
            conditions += "category = ?"
            arguments += category
        }
        val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }.take(5)
        terms.forEach { term ->
            conditions += "instr(search_text, ?) > 0"
            arguments += term
        }
        val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val sql = """
            SELECT poi_id, category, subcategory, name, name_en, name_ru, name_ka, latitude, longitude, tags_json
            FROM poi $where
            ORDER BY CASE category
                WHEN 'tourism' THEN 1 WHEN 'historic' THEN 2 WHEN 'nature' THEN 3
                WHEN 'food' THEN 4 WHEN 'lodging' THEN 5 WHEN 'medical' THEN 6
                WHEN 'transport' THEN 7 WHEN 'services' THEN 8 WHEN 'shops' THEN 9
                ELSE 10 END, name COLLATE NOCASE
            LIMIT ?
        """.trimIndent()
        arguments += limit.coerceIn(1, 200).toString()
        val db = SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery(sql, arguments.toTypedArray()).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(PoiRecord(
                            poiId = cursor.getString(0),
                            category = cursor.getString(1),
                            subcategory = cursor.getString(2),
                            name = cursor.getString(3),
                            nameEn = cursor.getString(4),
                            nameRu = cursor.getString(5),
                            nameKa = cursor.getString(6),
                            latitude = cursor.getDouble(7),
                            longitude = cursor.getDouble(8),
                            tagsJson = cursor.getString(9)
                        ))
                    }
                }
            }
        } finally {
            db.close()
        }
    }



    /** Search only inside the visible map rectangle; the coordinate index bounds the offline query. */
    suspend fun searchInBounds(
        database: File,
        query: String,
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        category: String = "all",
        limit: Int = 60
    ): List<PoiRecord> = withContext(Dispatchers.IO) {
        val conditions = mutableListOf(
            "latitude BETWEEN ? AND ?",
            "longitude BETWEEN ? AND ?"
        )
        val arguments = mutableListOf(
            south.toString(), north.toString(), west.toString(), east.toString()
        )
        if (category != "all") {
            conditions += "category = ?"
            arguments += category
        }
        val terms = query.trim().lowercase(Locale.ROOT)
            .split(Regex("\\s+")).filter { it.isNotBlank() }.take(5)
        terms.forEach { term ->
            conditions += "instr(search_text, ?) > 0"
            arguments += term
        }
        val sql = """
            SELECT poi_id, category, subcategory, name, name_en, name_ru, name_ka,
                   latitude, longitude, tags_json
            FROM poi
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY CASE category
                WHEN 'tourism' THEN 1 WHEN 'historic' THEN 2 WHEN 'nature' THEN 3
                WHEN 'food' THEN 4 WHEN 'lodging' THEN 5 WHEN 'medical' THEN 6
                WHEN 'transport' THEN 7 WHEN 'services' THEN 8 WHEN 'shops' THEN 9
                ELSE 10 END, name COLLATE NOCASE
            LIMIT ?
        """.trimIndent()
        arguments += limit.coerceIn(1, 120).toString()
        val db = SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery(sql, arguments.toTypedArray()).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(PoiRecord(
                        poiId = cursor.getString(0), category = cursor.getString(1),
                        subcategory = cursor.getString(2), name = cursor.getString(3),
                        nameEn = cursor.getString(4), nameRu = cursor.getString(5),
                        nameKa = cursor.getString(6), latitude = cursor.getDouble(7),
                        longitude = cursor.getDouble(8), tagsJson = cursor.getString(9)
                    ))
                }
            }
        } finally {
            db.close()
        }
    }

    /** Bounded offline lookup for a POI nearest to a tapped map coordinate. */
    suspend fun nearestPoi(
        database: File,
        latitude: Double,
        longitude: Double,
        maxDistanceMeters: Double = 120.0
    ): PoiRecord? = withContext(Dispatchers.IO) {
        val latDelta = maxDistanceMeters / 110_540.0
        val lonScale = (111_320.0 * kotlin.math.cos(Math.toRadians(latitude))).coerceAtLeast(1.0)
        val lonDelta = maxDistanceMeters / lonScale
        val sql = """
            SELECT poi_id, category, subcategory, name, name_en, name_ru, name_ka,
                   latitude, longitude, tags_json
            FROM poi
            WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?
            ORDER BY ABS(latitude - ?) + ABS(longitude - ?)
            LIMIT 64
        """.trimIndent()
        val args = arrayOf(
            (latitude - latDelta).toString(), (latitude + latDelta).toString(),
            (longitude - lonDelta).toString(), (longitude + lonDelta).toString(),
            latitude.toString(), longitude.toString()
        )
        val db = SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery(sql, args).use { cursor ->
                var nearest: PoiRecord? = null
                var nearestMeters = maxDistanceMeters
                while (cursor.moveToNext()) {
                    val candidateLat = cursor.getDouble(7)
                    val candidateLon = cursor.getDouble(8)
                    val dLat = Math.toRadians(candidateLat - latitude)
                    val dLon = Math.toRadians(candidateLon - longitude)
                    val sinLat = kotlin.math.sin(dLat / 2)
                    val sinLon = kotlin.math.sin(dLon / 2)
                    val a = (sinLat * sinLat +
                        kotlin.math.cos(Math.toRadians(latitude)) *
                        kotlin.math.cos(Math.toRadians(candidateLat)) * sinLon * sinLon).coerceIn(0.0, 1.0)
                    val meters = 6_371_000.0 * 2.0 * kotlin.math.atan2(
                        kotlin.math.sqrt(a), kotlin.math.sqrt((1.0 - a).coerceAtLeast(0.0))
                    )
                    if (meters < nearestMeters) {
                        nearestMeters = meters
                        nearest = PoiRecord(
                            poiId = cursor.getString(0), category = cursor.getString(1),
                            subcategory = cursor.getString(2), name = cursor.getString(3),
                            nameEn = cursor.getString(4), nameRu = cursor.getString(5),
                            nameKa = cursor.getString(6), latitude = candidateLat,
                            longitude = candidateLon, tagsJson = cursor.getString(9)
                        )
                    }
                }
                nearest
            }
        } finally {
            db.close()
        }
    }

    private fun readBuildManifest(context: Context): JSONObject? = try {
        context.assets.open(BUILD_MANIFEST).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    } catch (_: Exception) {
        null
    }

    private fun validateDatabase(file: File): Boolean {
        if (!file.isFile || file.length() < MIN_DB_BYTES) return false
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery("SELECT COUNT(*) FROM poi", null).use { it.moveToFirst() && it.getInt(0) > 0 }
        } finally {
            db.close()
        }
    }

    private fun readCount(file: File): Int {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery("SELECT COUNT(*) FROM poi", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        } finally {
            db.close()
        }
    }
}
