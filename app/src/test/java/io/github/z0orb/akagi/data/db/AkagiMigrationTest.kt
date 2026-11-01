package io.github.z0orb.akagi.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * An update must keep the user's library (PLAN D-071): a v1 database, filled the way v1.1.0 fills it, has to open
 * as v2 with every row intact. The exported schemas in `app/schemas` are added to the unit tests' assets in
 * build.gradle.kts, which is where the helper looks for them.
 */
@RunWith(RobolectricTestRunner::class)
class AkagiMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-test.db"),
        AndroidSQLiteDriver(),
        AkagiDatabase::class,
        { AkagiDatabase_Impl() },
        emptyList(),
    )

    private fun SQLiteConnection.longs(sql: String): List<Long> = prepare(sql).use { statement ->
        buildList { while (statement.step()) add(statement.getLong(0)) }
    }

    private fun SQLiteConnection.texts(sql: String): List<String?> = prepare(sql).use { statement ->
        buildList { while (statement.step()) add(if (statement.isNull(0)) null else statement.getText(0)) }
    }

    private fun fillVersion1(): Unit = helper.createDatabase(1).use { db ->
        listOf("a" to "OPUS", "b" to "M4A", "c" to "MP3").forEachIndexed { index, (videoId, codec) ->
            db.execSQL(
                "INSERT INTO tracks (id, videoId, title, durationMs, sizeBytes, codec, bitrateKbps, contentUri, " +
                    "fileName, thumbnailPath, downloadedAt, missing) " +
                    "VALUES (${index + 1}, '$videoId', 'Title $videoId', 200000, 3000000, '$codec', 128, " +
                    "'content://media/external_primary/audio/media/${index + 1}', " +
                    "'$videoId.${codec.lowercase()}', NULL, ${index + 10}, ${if (videoId == "b") 1 else 0})",
            )
        }
        db.execSQL("INSERT INTO track_waveforms (trackId, peaks) VALUES (1, X'0A0B0C')")
        db.execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, 'Custom order', 1, 2)")
        db.execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (2, 'Other', 3, 4)")
        // Dragged into a custom order: c, a, b.
        db.execSQL("INSERT INTO playlist_tracks (playlistId, trackId, position) VALUES (1, 3, 0), (1, 1, 1), (1, 2, 2)")
        db.execSQL("INSERT INTO playlist_tracks (playlistId, trackId, position) VALUES (2, 2, 0)")
        db.execSQL(
            "INSERT INTO download_tasks (id, videoId, title, formatId, codec, bitrateKbps, state, progress, " +
                "errorType, errorMessage, replaceTrackId, createdAt, updatedAt) VALUES (1, 'd', 'Title d', '251', " +
                "'OPUS', 128, 'FAILED', 0.4, 'Network', 'timeout', NULL, 5, 6)",
        )
    }

    @Test
    fun `a v1 library opens as v2 with every row intact`() {
        fillVersion1()

        helper.runMigrationsAndValidate(2, emptyList()).use { db ->
            assertEquals(listOf(1L, 2L, 3L), db.longs("SELECT id FROM tracks ORDER BY id"))
            assertEquals(listOf("OPUS", "M4A", "MP3"), db.texts("SELECT codec FROM tracks ORDER BY id"))
            assertEquals(listOf(2L), db.longs("SELECT id FROM tracks WHERE missing = 1"))
            assertEquals(listOf(3L), db.longs("SELECT length(peaks) FROM track_waveforms WHERE trackId = 1"))

            assertEquals(listOf("Custom order", "Other"), db.texts("SELECT name FROM playlists ORDER BY id"))
            assertEquals(
                listOf(3L, 1L, 2L),
                db.longs("SELECT trackId FROM playlist_tracks WHERE playlistId = 1 ORDER BY position"),
            )
            assertEquals(listOf(2L), db.longs("SELECT trackId FROM playlist_tracks WHERE playlistId = 2"))

            assertEquals(listOf("FAILED"), db.texts("SELECT state FROM download_tasks"))
            assertEquals(listOf("Network"), db.texts("SELECT errorType FROM download_tasks"))
        }
    }

    @Test
    fun `the new columns start empty`() {
        fillVersion1()

        helper.runMigrationsAndValidate(2, emptyList()).use { db ->
            assertTrue(db.texts("SELECT sourceListId FROM playlists").all { it == null })
            assertEquals(2, db.texts("SELECT sourceListId FROM playlists").size)
            assertTrue(db.texts("SELECT targetPlaylistId FROM download_tasks").all { it == null })
            assertTrue(db.texts("SELECT targetPosition FROM download_tasks").all { it == null })
        }
    }

    @Test
    fun `the new columns take values after the migration`() {
        fillVersion1()

        helper.runMigrationsAndValidate(2, emptyList()).use { db ->
            db.execSQL("UPDATE playlists SET sourceListId = 'PLabc' WHERE id = 2")
            db.execSQL("UPDATE download_tasks SET targetPlaylistId = 2, targetPosition = 7 WHERE id = 1")
            assertEquals(listOf("PLabc"), db.texts("SELECT sourceListId FROM playlists WHERE id = 2"))
            assertEquals(
                listOf(2L, 7L),
                db.longs("SELECT targetPlaylistId FROM download_tasks") +
                    db.longs("SELECT targetPosition FROM download_tasks"),
            )
        }
    }
}
