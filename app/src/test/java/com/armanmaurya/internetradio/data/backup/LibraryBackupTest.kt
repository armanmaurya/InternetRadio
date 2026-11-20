package com.armanmaurya.internetradio.data.backup

import com.armanmaurya.internetradio.data.local.entity.StationEntity
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryBackupTest {

    @Test
    fun `toBackupStation detects content modifications`() {
        val entity1 = StationEntity(
            stationUuid = "test-uuid-1",
            name = "Test Radio",
            url = "https://stream.example.com"
        )

        val entity2 = entity1.copy(name = "Updated Radio Name")

        val backup1 = entity1.toBackupStation()
        val backup2 = entity2.toBackupStation()

        assertNotEquals(backup1, backup2)
    }

    @Test
    fun `backup with tracks serializes and deserializes correctly`() {
        val track = BackupTrack(
            trackTitle = "Track Title",
            timestamp = 123456789L,
            artist = "Artist Name",
            isStarred = true
        )
        val station = BackupStation(
            stationUuid = "s1",
            name = "Station 1",
            url = "https://s1.com",
            tracks = listOf(track)
        )
        val backup = LibraryBackup(stations = listOf(station))

        val json = Gson().toJson(backup)
        val restored = Gson().fromJson(json, LibraryBackup::class.java)

        assertEquals(1, restored.stations?.size)
        val restoredStation = restored.stations?.first()
        assertEquals(1, restoredStation?.tracks?.size)
        assertEquals("Track Title", restoredStation?.tracks?.first()?.trackTitle)
        assertEquals("Artist Name", restoredStation?.tracks?.first()?.artist)
        assertEquals(true, restoredStation?.tracks?.first()?.isStarred)
    }

    @Test
    fun `legacy backup without tracks deserializes with null tracks`() {
        val legacyJson = """
            {
                "schemaVersion": 2,
                "exportedAt": "2026-07-01T00:00:00Z",
                "appVersion": "0.6.0",
                "stations": [
                    {
                        "stationUuid": "s1",
                        "name": "Legacy Station",
                        "url": "https://legacy.com"
                    }
                ]
            }
        """.trimIndent()

        val restored = Gson().fromJson(legacyJson, LibraryBackup::class.java)
        assertEquals(1, restored.stations?.size)
        assertEquals("Legacy Station", restored.stations?.first()?.name)
        org.junit.Assert.assertNull(restored.stations?.first()?.tracks)
    }

    @Test
    fun `starred tracks round trip separately from history`() {
        val station = StationEntity(stationUuid = "s1", name = "Station 1", url = "https://s1.com").toBackupStation(
            tracks = listOf(
                com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity(
                    stationUuid = "s1", trackTitle = "Played", timestamp = 1000L, isStarred = true
                ).toBackupTrack()
            ),
            starredTracks = listOf(
                com.armanmaurya.internetradio.data.local.entity.StarredTrackEntity(
                    stationUuid = "s1", trackTitle = "Starred", rawTrackTitle = "Raw Starred", starredAt = 2000L
                ).toBackupStarredTrack()
            )
        )

        val json = Gson().toJson(LibraryBackup(stations = listOf(station)))
        val restored = Gson().fromJson(json, LibraryBackup::class.java).stations!!.single()

        // History rows never carry a star in new backups
        org.junit.Assert.assertNull(restored.tracks!!.single().isStarred)
        val star = restored.allStarredTracks().single().toStarredTrackEntity("s1")
        assertEquals("Starred", star.trackTitle)
        assertEquals("Raw Starred", star.rawTrackTitle)
        assertEquals(2000L, star.starredAt)
    }

    @Test
    fun `stars marked on tracks in older backups are still restored`() {
        val legacyJson = """
            {
                "schemaVersion": 2,
                "stations": [
                    {
                        "stationUuid": "s1",
                        "name": "Station",
                        "url": "https://s1.com",
                        "tracks": [
                            { "trackTitle": "Starred Song", "timestamp": 1000, "isStarred": true, "artist": "A" },
                            { "trackTitle": "Plain Song", "timestamp": 2000 }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val station = Gson().fromJson(legacyJson, LibraryBackup::class.java).stations!!.single()

        val stars = station.allStarredTracks()
        assertEquals(listOf("Starred Song"), stars.map { it.trackTitle })
        assertEquals("A", stars.single().artist)
        assertEquals(1000L, stars.single().starredAt)
        assertEquals(2, station.tracks!!.size)
    }
}
