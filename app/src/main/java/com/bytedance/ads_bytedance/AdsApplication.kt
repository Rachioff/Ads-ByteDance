package com.bytedance.ads_bytedance

import android.app.Application
import com.bytedance.ads_bytedance.di.appModule
import com.bytedance.ads_bytedance.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AdsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AdsApplication)
            modules(appModule, viewModelModule)
        }
    }
}
