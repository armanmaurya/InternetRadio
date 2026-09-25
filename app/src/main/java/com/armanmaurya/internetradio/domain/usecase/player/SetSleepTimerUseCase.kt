package com.armanmaurya.internetradio.domain.usecase.player

import com.armanmaurya.internetradio.domain.controller.PlayerController
import javax.inject.Inject

class SetSleepTimerUseCase @Inject constructor(
    private val playerController: PlayerController
) {
    operator fun invoke(durationMillis: Long) {
        playerController.setSleepTimer(durationMillis)
    }

    fun cancel() {
        playerController.cancelSleepTimer()
    }
}
