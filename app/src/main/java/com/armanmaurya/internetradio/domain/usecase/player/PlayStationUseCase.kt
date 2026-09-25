package com.armanmaurya.internetradio.domain.usecase.player

import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.StationRepository
import javax.inject.Inject

class PlayStationUseCase @Inject constructor(
    private val playerController: PlayerController,
    private val recentRepository: RecentRepository,
    private val stationRepository: StationRepository
) {
    suspend operator fun invoke(
        stations: List<RadioStation>,
        startIndex: Int,
        source: PlaybackSource = PlaybackSource.None,
        playWhenReady: Boolean = true
    ) {
        if (stations.isEmpty() || startIndex !in stations.indices) return
        val station = stations[startIndex]
        playerController.play(stations, startIndex, source, playWhenReady)
        recentRepository.addRecentStation(station)
        stationRepository.registerClick(station.stationUuid)
    }
}
