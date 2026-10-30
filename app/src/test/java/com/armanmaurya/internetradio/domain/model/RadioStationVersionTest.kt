package com.armanmaurya.internetradio.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationVersionTest {

    private val browseVersion = RadioStation(
        changeUuid = "", stationUuid = "station-a", name = "Rock FM",
        url = "http://browse.example.com/stream", urlResolved = "http://browse.example.com/stream",
        homepage = "", favicon = "", tags = emptyList(), country = "", countryCode = "", state = "",
        iso3166_2 = null, language = "", languageCodes = emptyList(), votes = 10, lastChangeTime = "",
        codec = "MP3", bitrate = 128, lastCheckOk = true, lastCheckTime = "", lastCheckOkTime = "",
        lastLocalCheckTime = "", clickTimestamp = "", clickCount = 5, clickTrend = 0, sslError = false,
        geoLat = null, geoLong = null, geoDistance = null, hasExtendedInfo = false
    )

    @Test
    fun editedLibraryVersion_isDifferentVersion() {
        val libraryVersion = browseVersion.copy(name = "My Rock FM")
        assertFalse(libraryVersion.isSameVersionAs(browseVersion))
    }

    @Test
    fun refreshedBrowseData_isSameVersion() {
        // Click counts and votes change between list refreshes; tapping still pauses instead of restarting
        val refreshed = browseVersion.copy(clickCount = 6, votes = 11, codec = "AAC", bitrate = 64)
        assertTrue(refreshed.isSameVersionAs(browseVersion))
    }

    @Test
    fun differentStationOrNothingPlaying_isDifferentVersion() {
        assertFalse(browseVersion.copy(stationUuid = "station-b").isSameVersionAs(browseVersion))
        assertFalse(browseVersion.isSameVersionAs(null))
    }
}
