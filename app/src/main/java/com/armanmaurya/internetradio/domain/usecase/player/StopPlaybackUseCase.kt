package com.armanmaurya.internetradio.domain.usecase.player

import com.armanmaurya.internetradio.domain.controller.PlayerController
import javax.inject.Inject

class StopPlaybackUseCase @Inject constructor(
    private val playerController: PlayerController
) {
    operator fun invoke() {
        playerController.stop()
    }
}
