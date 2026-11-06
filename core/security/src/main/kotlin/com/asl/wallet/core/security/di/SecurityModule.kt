package com.asl.wallet.core.security.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.asl.wallet.core.common.session.TokenStore
import com.asl.wallet.core.security.integrity.AndroidDeviceEnvironment
import com.asl.wallet.core.security.integrity.DeviceIntegrityChecker
import com.asl.wallet.core.security.lock.AppLockManager
import com.asl.wallet.core.security.storage.SecureTokenStore
import com.asl.wallet.core.security.storage.StringCipher
import com.asl.wallet.core.security.storage.TinkCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SessionDataStore

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides @Singleton @SessionDataStore
    fun sessionDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("secure_session") }

    /** One Keystore-backed cipher for every encrypted value in the app (tokens, cached profile). */
    @Provides @Singleton
    fun stringCipher(@ApplicationContext context: Context): StringCipher = TinkCipher(context)

    @Provides @Singleton
    fun tokenStore(@SessionDataStore ds: DataStore<Preferences>, cipher: StringCipher): TokenStore =
        SecureTokenStore(ds, cipher)

    @Provides @Singleton
    fun appLockManager(): AppLockManager = AppLockManager()

    @Provides @Singleton
    fun integrityChecker(@ApplicationContext context: Context): DeviceIntegrityChecker =
        DeviceIntegrityChecker(AndroidDeviceEnvironment(context))
}
