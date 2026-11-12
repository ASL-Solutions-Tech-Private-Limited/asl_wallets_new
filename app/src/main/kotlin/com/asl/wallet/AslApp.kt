package com.asl.wallet

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.asl.wallet.core.security.lock.AppLockLifecycleObserver
import com.asl.wallet.core.security.lock.AppLockManager
import com.asl.wallet.push.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AslApp : Application() {

    @Inject lateinit var appLock: AppLockManager

    override fun onCreate() {
        super.onCreate()
        // Whole-app foreground/background tracking drives the app lock (30 s rule).
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLockLifecycleObserver(appLock))
        NotificationChannels.ensure(this)
        initFirebase()
    }

    /**
     * Crashlytics collection is off in debug and on in release. Guarded so the app still runs before
     * google-services.json is added (FirebaseApp has no default instance until then).
     */
    private fun initFirebase() = runCatching {
        if (com.google.firebase.FirebaseApp.getApps(this).isNotEmpty()) {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                .isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        }
    }
}
