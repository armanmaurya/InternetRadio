package com.armanmaurya.internetradio

import android.app.Application
import com.armanmaurya.internetradio.core.crash.GlobalExceptionHandler
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class RadioApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var autoBackupManager: com.armanmaurya.internetradio.domain.controller.AutoBackupManager

    @Inject
    lateinit var shortcutController: com.armanmaurya.internetradio.domain.controller.ShortcutController

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            GlobalExceptionHandler(this, defaultHandler)
        )
        autoBackupManager.initialize()
        shortcutController.initialize()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { okHttpClient }
                    )
                )
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
