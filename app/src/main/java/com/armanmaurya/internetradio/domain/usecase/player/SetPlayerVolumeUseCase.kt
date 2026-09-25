package com.armanmaurya.internetradio.domain.usecase.player

import com.armanmaurya.internetradio.domain.controller.PlayerController
import javax.inject.Inject

class SetPlayerVolumeUseCase @Inject constructor(
    private val playerController: PlayerController
) {
    operator fun invoke(volume: Float) {
        playerController.setVolume(volume)
    }
}
