package app.georgia.offline

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.georgia.offline.data.PoiRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File

@RunWith(AndroidJUnit4::class)
class PoiSearchRankingTest {
    @Test
    fun exactAndPrefixNamesRankFirstWithoutChangingBlankSearchOrder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseFile = File(context.cacheDir, "poi-search-ranking-test.db")
        SQLiteDatabase.deleteDatabase(databaseFile)

        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            database.execSQL(
                """
                CREATE TABLE poi (
                    poi_id TEXT PRIMARY KEY,
                    category TEXT NOT NULL,
                    subcategory TEXT,
                    name TEXT NOT NULL,
                    name_en TEXT,
                    name_ru TEXT,
                    name_ka TEXT,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    tags_json TEXT,
                    search_text TEXT NOT NULL
                )
                """.trimIndent()
            )
            insert(database, "broad", "food", "Restaurant", "restaurant batumi")
            insert(database, "prefix", "historic", "Batumi Old Town", "batumi old town")
            insert(database, "exact", "settlement", "Batumi", "batumi settlement")
            insert(database, "tourism", "tourism", "Viewpoint", "viewpoint")

            val matching = PoiRepository.search(databaseFile, "Batumi")
            assertEquals("Batumi", matching[0].name)
            assertEquals("Batumi Old Town", matching[1].name)
            assertEquals("Restaurant", matching[2].name)

            val all = PoiRepository.search(databaseFile, "")
            assertEquals("tourism", all.first().category)
        } finally {
            database.close()
            databaseFile.delete()
        }
    }


    @Test
    fun routeCorridorSearchFiltersByDistanceAndOrdersInTravelDirection() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseFile = File(context.cacheDir, "poi-route-corridor-test.db")
        SQLiteDatabase.deleteDatabase(databaseFile)
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            database.execSQL(
                """
                CREATE TABLE poi (
                    poi_id TEXT PRIMARY KEY,
                    category TEXT NOT NULL,
                    subcategory TEXT,
                    name TEXT NOT NULL,
                    name_en TEXT,
                    name_ru TEXT,
                    name_ka TEXT,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    tags_json TEXT,
                    search_text TEXT NOT NULL
                )
                """.trimIndent()
            )
            insert(database, "near-end", "food", "End cafe", "cafe", 41.7002, 44.818)
            insert(database, "near-start", "food", "Start cafe", "cafe", 41.7001, 44.802)
            insert(database, "outside-corridor", "food", "Far cafe", "cafe", 41.725, 44.810)

            val results = PoiRepository.searchAlongRoute(
                databaseFile,
                query = "",
                routePoints = listOf(41.7 to 44.8, 41.7 to 44.82),
                corridorMeters = 2_500.0
            )
            assertEquals(listOf("near-start", "near-end"), results.map { it.poiId })
        } finally {
            database.close()
            databaseFile.delete()
        }
    }

    private fun insert(database: SQLiteDatabase, id: String, category: String, name: String, searchText: String, latitude: Double = 41.7, longitude: Double = 44.8) {
        val values = ContentValues().apply {
            put("poi_id", id)
            put("category", category)
            put("subcategory", "")
            put("name", name)
            put("name_en", name)
            put("name_ru", name)
            put("name_ka", name)
            put("latitude", latitude)
            put("longitude", longitude)
            put("tags_json", "{}")
            put("search_text", searchText)
        }
        database.insertOrThrow("poi", null, values)
    }
}
