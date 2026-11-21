package com.armanmaurya.internetradio.di

import com.armanmaurya.internetradio.data.remote.lyrics.betterlyrics.BetterLyricsApi
import com.armanmaurya.internetradio.data.remote.lyrics.betterlyrics.BetterLyricsProvider
import com.armanmaurya.internetradio.data.remote.lyrics.kugou.KuGouApi
import com.armanmaurya.internetradio.data.remote.lyrics.kugou.KuGouLyricsProvider
import com.armanmaurya.internetradio.data.remote.lyrics.lrclib.LrcLibLyricsProvider
import com.armanmaurya.internetradio.data.remote.lyrics.lyricsplus.LyricsPlusApi
import com.armanmaurya.internetradio.data.remote.lyrics.lyricsplus.LyricsPlusProvider
import com.armanmaurya.internetradio.data.remote.lyrics.unison.UnisonApi
import com.armanmaurya.internetradio.data.remote.lyrics.unison.UnisonLyricsProvider
import com.armanmaurya.internetradio.domain.lyrics.LyricsProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LyricsNetworkModule {

    @Provides
    @Singleton
    @Named("BetterLyricsRetrofit")
    fun provideBetterLyricsRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.betterlyrics.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideBetterLyricsApi(@Named("BetterLyricsRetrofit") retrofit: Retrofit): BetterLyricsApi =
        retrofit.create(BetterLyricsApi::class.java)

    @Provides
    @Singleton
    @Named("KuGouRetrofit")
    fun provideKuGouRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://krcs.kugou.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideKuGouApi(@Named("KuGouRetrofit") retrofit: Retrofit): KuGouApi =
        retrofit.create(KuGouApi::class.java)

    @Provides
    @Singleton
    @Named("UnisonRetrofit")
    fun provideUnisonRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://unison.betterlyrics.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideUnisonApi(@Named("UnisonRetrofit") retrofit: Retrofit): UnisonApi =
        retrofit.create(UnisonApi::class.java)

    @Provides
    @Singleton
    @Named("LyricsPlusRetrofit")
    fun provideLyricsPlusRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://lyricsplus.prjktla.my.id/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideLyricsPlusApi(@Named("LyricsPlusRetrofit") retrofit: Retrofit): LyricsPlusApi =
        retrofit.create(LyricsPlusApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LyricsBindingModule {

    @Binds
    @IntoSet
    abstract fun bindBetterLyricsProvider(impl: BetterLyricsProvider): LyricsProvider

    @Binds
    @IntoSet
    abstract fun bindUnisonProvider(impl: UnisonLyricsProvider): LyricsProvider

    @Binds
    @IntoSet
    abstract fun bindLyricsPlusProvider(impl: LyricsPlusProvider): LyricsProvider

    @Binds
    @IntoSet
    abstract fun bindKuGouProvider(impl: KuGouLyricsProvider): LyricsProvider

    @Binds
    @IntoSet
    abstract fun bindLrcLibProvider(impl: LrcLibLyricsProvider): LyricsProvider
}
