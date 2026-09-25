package com.armanmaurya.internetradio.di

import com.armanmaurya.internetradio.data.repository.CoverArtRepositoryImpl
import com.armanmaurya.internetradio.data.repository.LibraryRepositoryImpl
import com.armanmaurya.internetradio.data.repository.LyricsRepositoryImpl
import com.armanmaurya.internetradio.data.repository.RecentRepositoryImpl
import com.armanmaurya.internetradio.data.repository.RecordingRepositoryImpl
import com.armanmaurya.internetradio.data.repository.ScheduleRepositoryImpl
import com.armanmaurya.internetradio.data.repository.SettingsRepositoryImpl
import com.armanmaurya.internetradio.data.repository.StationRepositoryImpl
import com.armanmaurya.internetradio.data.repository.TrackHistoryRepositoryImpl
import com.armanmaurya.internetradio.data.repository.UpdateRepositoryImpl
import com.armanmaurya.internetradio.domain.repository.CoverArtRepository
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.LyricsRepository
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.RecordingRepository
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.repository.StationRepository
import com.armanmaurya.internetradio.domain.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.domain.repository.UpdateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindCoverArtRepository(
        impl: CoverArtRepositoryImpl
    ): CoverArtRepository

    @Binds
    abstract fun bindLibraryRepository(
        impl: LibraryRepositoryImpl
    ): LibraryRepository

    @Binds
    abstract fun bindLyricsRepository(
        impl: LyricsRepositoryImpl
    ): LyricsRepository

    @Binds
    abstract fun bindRecentRepository(
        impl: RecentRepositoryImpl
    ): RecentRepository

    @Binds
    abstract fun bindRecordingRepository(
        impl: RecordingRepositoryImpl
    ): RecordingRepository

    @Binds
    abstract fun bindPlayerController(
        impl: com.armanmaurya.internetradio.data.player.PlayerControllerImpl
    ): com.armanmaurya.internetradio.domain.controller.PlayerController

    @Binds
    abstract fun bindRecordingController(
        impl: com.armanmaurya.internetradio.data.recording.RecordingControllerImpl
    ): com.armanmaurya.internetradio.domain.controller.RecordingController

    @Binds
    abstract fun bindCastController(
        impl: com.armanmaurya.internetradio.data.cast.CastControllerImpl
    ): com.armanmaurya.internetradio.domain.controller.CastController

    @Binds
    abstract fun bindWidgetController(
        impl: com.armanmaurya.internetradio.data.widget.WidgetControllerImpl
    ): com.armanmaurya.internetradio.domain.controller.WidgetController

    @Binds
    abstract fun bindScheduleController(
        impl: com.armanmaurya.internetradio.data.schedule.ScheduleControllerImpl
    ): com.armanmaurya.internetradio.domain.controller.ScheduleController

    @Binds
    abstract fun bindScheduleRepository(
        impl: ScheduleRepositoryImpl
    ): ScheduleRepository

    @Binds
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    abstract fun bindStationRepository(
        impl: StationRepositoryImpl
    ): StationRepository

    @Binds
    abstract fun bindTrackHistoryRepository(
        impl: TrackHistoryRepositoryImpl
    ): TrackHistoryRepository

    @Binds
    abstract fun bindUpdateRepository(
        impl: UpdateRepositoryImpl
    ): UpdateRepository
}
