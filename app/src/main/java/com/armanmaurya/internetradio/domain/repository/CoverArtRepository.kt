package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.domain.model.TrackMetadata

interface CoverArtRepository {
    suspend fun getTrackMetadata(trackName: String): TrackMetadata?
}
