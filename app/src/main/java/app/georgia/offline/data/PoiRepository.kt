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

    data class SearchResult(val database: File?, val poiCount: Int, val message: String?)

    suspend fun ensureInstalled(context: Context): SearchResult = withContext(Dispatchers.IO) {
        val manifest = readBuildManifest(context)
        val expectedHash = manifest?.optString("poiFileSha256")
            ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
        val expectedSize = manifest?.optLong("poiFileSizeBytes", -1L)?.takeIf { it > 0L }
        if (expectedHash == null || expectedSize == null) {
            return@withContext SearchResult(null, 0, "Офлайн-база POI ещё не включена в эту сборку приложения.")
        }

        val directory = File(context.filesDir, "poi")
        if (!directory.exists() && !directory.mkdirs()) {
            return@withContext SearchResult(null, 0, "Не удалось создать локальную папку базы объектов.")
        }
        val database = File(directory, "georgia-poi.sqlite")
        val marker = File(directory, HASH_MARKER)
        if (database.isFile && database.length() == expectedSize && marker.isFile &&
            marker.readText(Charsets.UTF_8).trim().equals(expectedHash, ignoreCase = true) &&
            runCatching { validateDatabase(database) }.getOrDefault(false)
        ) {
            return@withContext SearchResult(database, readCount(database), null)
        }

        val requiredSpace = expectedSize + 8L * 1024L * 1024L
        if (directory.usableSpace < requiredSpace) {
            return@withContext SearchResult(null, 0, "Недостаточно свободного места для локального справочника объектов.")
        }

        val input = try {
            context.assets.open(ASSET_DB)
        } catch (_: FileNotFoundException) {
            return@withContext SearchResult(null, 0, "База объектов не найдена во встроенных ресурсах APK.")
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
