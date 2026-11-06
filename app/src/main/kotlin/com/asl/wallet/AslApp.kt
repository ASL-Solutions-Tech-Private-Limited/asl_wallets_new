package com.asl.wallet

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.asl.wallet.core.security.lock.AppLockLifecycleObserver
import com.asl.wallet.core.security.lock.AppLockManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AslApp : Application() {

    @Inject lateinit var appLock: AppLockManager

    override fun onCreate() {
        super.onCreate()
        // Whole-app foreground/background tracking drives the app lock (30 s rule).
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLockLifecycleObserver(appLock))
    }
}
