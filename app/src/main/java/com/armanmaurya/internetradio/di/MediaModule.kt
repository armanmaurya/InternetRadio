package com.armanmaurya.internetradio.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.armanmaurya.internetradio.core.media.recorder.DefaultStreamRecorder
import com.armanmaurya.internetradio.core.media.recorder.StreamRecorder
import com.armanmaurya.internetradio.core.system.FileSystemFacade
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    @Provides
    @Singleton
    fun provideStreamRecorder(
        okHttpClient: OkHttpClient,
        fileSystemFacade: FileSystemFacade
    ): StreamRecorder {
        return DefaultStreamRecorder(okHttpClient, fileSystemFacade)
    }
}

