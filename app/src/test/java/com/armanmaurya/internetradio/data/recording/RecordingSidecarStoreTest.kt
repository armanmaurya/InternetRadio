package com.armanmaurya.internetradio.data.recording

import com.armanmaurya.internetradio.domain.model.RecordingTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSidecarStoreTest {

    private val appTracks = listOf(
        RecordingTrack(title = "Nightcall", offsetMs = 0L, timestampMs = 1L),
        RecordingTrack(title = "Tech Noir", offsetMs = 215000L, timestampMs = 2L)
    )
    private val folderTracks = listOf(
        RecordingTrack(title = "Nightcall", offsetMs = 0L, timestampMs = 1L),
        RecordingTrack(title = "Tech Noir", offsetMs = 215000L, timestampMs = 2L),
        RecordingTrack(title = "Turbo Killer", offsetMs = 420000L, timestampMs = 3L)
    )

    @Test
    fun mergeSidecars_withoutFolderSidecar_keepsAppSidecar() {
        val app = RecordingSidecarDto("Synthwave FM", "rec.mp3", appTracks, "app note")

        assertEquals(app, RecordingSidecarStore.mergeSidecars(null, 0L, app, 100L))
    }

    @Test
    fun mergeSidecars_newerFolderSidecarWins() {
        val folder = RecordingSidecarDto("Synthwave FM", "rec.mp3", folderTracks, "folder note")
        val app = RecordingSidecarDto("Synthwave FM", "rec.mp3", appTracks, "app note")

        val merged = RecordingSidecarStore.mergeSidecars(folder, 200L, app, 100L)

        assertEquals(folderTracks, merged.tracks)
        assertEquals("folder note", merged.note)
    }

    @Test
    fun mergeSidecars_newerAppSidecarWins() {
        // Access was revoked, the note was edited into app storage, then access was granted again
        val folder = RecordingSidecarDto("Synthwave FM", "rec.mp3", folderTracks, "old note")
        val app = RecordingSidecarDto("Synthwave FM", "rec.mp3", appTracks, "edited note")

        val merged = RecordingSidecarStore.mergeSidecars(folder, 100L, app, 200L)

        assertEquals(appTracks, merged.tracks)
        assertEquals("edited note", merged.note)
    }

    @Test
    fun mergeSidecars_fillsMissingValuesFromOlderCopy() {
        // A note-only sidecar written without access has no tracks; keep the folder's tracks
        val folder = RecordingSidecarDto("Synthwave FM", "rec.mp3", folderTracks, null)
        val app = RecordingSidecarDto("", "", emptyList(), "app note")

        val merged = RecordingSidecarStore.mergeSidecars(folder, 100L, app, 200L)

        assertEquals("Synthwave FM", merged.stationName)
        assertEquals("rec.mp3", merged.fileName)
        assertEquals(folderTracks, merged.tracks)
        assertEquals("app note", merged.note)
    }

    @Test
    fun mergeSidecars_sameTimestamp_prefersFolderSidecar() {
        val folder = RecordingSidecarDto("Synthwave FM", "rec.mp3", folderTracks, "folder note")
        val app = RecordingSidecarDto("Synthwave FM", "rec.mp3", appTracks, "app note")

        val merged = RecordingSidecarStore.mergeSidecars(folder, 100L, app, 100L)

        assertEquals("folder note", merged.note)
    }

    @Test
    fun isRecordingsTreeDocumentId_acceptsOnlyMusicInternetRadio() {
        assertTrue(RecordingFolderAccess.isRecordingsTreeDocumentId("primary:Music/InternetRadio"))
        assertTrue(RecordingFolderAccess.isRecordingsTreeDocumentId("primary:music/internetradio"))

        assertFalse(RecordingFolderAccess.isRecordingsTreeDocumentId("primary:Music"))
        assertFalse(RecordingFolderAccess.isRecordingsTreeDocumentId("primary:Music/InternetRadio/Jazz FM"))
        assertFalse(RecordingFolderAccess.isRecordingsTreeDocumentId("primary:Download/InternetRadio"))
        assertFalse(RecordingFolderAccess.isRecordingsTreeDocumentId("1234-ABCD:Music/InternetRadio"))
    }
}
