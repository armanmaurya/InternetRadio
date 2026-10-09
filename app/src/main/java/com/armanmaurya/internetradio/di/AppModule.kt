package com.armanmaurya.internetradio.di

import android.content.Context
import androidx.room.Room
import com.armanmaurya.internetradio.data.local.RadioDatabase
import com.armanmaurya.internetradio.data.remote.RadioBrowserApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * radio-browser.info runs multiple community servers.
     * de1.api.radio-browser.info is the primary stable endpoint.
     * For production, consider resolving a random server via DNS SRV lookup:
     * https://docs.radio-browser.info/#server-selection
     */
    private const val BASE_URL = "https://de1.api.radio-browser.info/"
    private const val APP_USER_AGENT = "InternetRadio/1.0 ( https://github.com/armanmaurya/InternetRadio )"
    private const val CACHE_SIZE = 50 * 1024 * 1024L // 50 MB

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val cache = Cache(context.cacheDir, CACHE_SIZE)

        val cacheInterceptor = Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val path = request.url.encodedPath

            val cacheHeader = when {
                path.contains("stations/search") ->
                    "public, max-age=${24 * 60 * 60}, stale-if-error=${7 * 24 * 60 * 60}"
                path.contains("countries") ||
                        path.contains("languages") ||
                        path.contains("tags") ->
                    "public, max-age=${7 * 24 * 60 * 60}, stale-if-error=${30 * 24 * 60 * 60}"
                else -> null
            }

            if (cacheHeader != null) {
                response.newBuilder()
                    .header("Cache-Control", cacheHeader)
                    .removeHeader("Pragma")
                    .build()
            } else {
                response
            }
        }

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", APP_USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .addNetworkInterceptor(cacheInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("RadioBrowserRetrofit")
    fun provideRadioBrowserRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    @Named("LrcLibRetrofit")
    fun provideLrcLibRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideRadioBrowserApi(@Named("RadioBrowserRetrofit") retrofit: Retrofit): RadioBrowserApi =
        retrofit.create(RadioBrowserApi::class.java)

    @Provides
    @Singleton
    fun provideLrcLibApi(@Named("LrcLibRetrofit") retrofit: Retrofit): com.armanmaurya.internetradio.data.remote.LrcLibApi =
        retrofit.create(com.armanmaurya.internetradio.data.remote.LrcLibApi::class.java)

    @Provides
    @Singleton
    @Named("ITunesRetrofit")
    fun provideITunesRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideITunesApiService(@Named("ITunesRetrofit") retrofit: Retrofit): com.armanmaurya.internetradio.data.remote.ITunesApiService =
        retrofit.create(com.armanmaurya.internetradio.data.remote.ITunesApiService::class.java)

    @Provides
    @Singleton
    fun provideRadioDatabase(@ApplicationContext context: Context): RadioDatabase =
        Room.databaseBuilder(
            context,
            RadioDatabase::class.java,
            "radio_database"
        )
        .addMigrations(*com.armanmaurya.internetradio.data.local.migration.DatabaseMigrations.ALL_MIGRATIONS)
        .build()

    @Provides
    @Singleton
    fun provideStationDao(database: RadioDatabase): com.armanmaurya.internetradio.data.local.dao.StationDao =
        database.stationDao

    @Provides
    @Singleton
    fun provideTrackHistoryDao(database: RadioDatabase): com.armanmaurya.internetradio.data.local.dao.TrackHistoryDao =
        database.trackHistoryDao

    @Provides
    @Singleton
    fun provideScheduleDao(database: RadioDatabase): com.armanmaurya.internetradio.data.local.dao.ScheduleDao =
        database.scheduleDao
}