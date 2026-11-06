package com.asl.wallet.feature.home.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.asl.wallet.core.common.session.SignOutCleanup
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class HomeDataStore

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds @Singleton abstract fun repository(impl: HomeRepositoryImpl): HomeRepository
    @Binds @Singleton abstract fun localStore(impl: DataStoreHomeLocalStore): HomeLocalStore

    companion object {
        @Provides @Singleton
        fun homeApi(retrofit: Retrofit): HomeApi = retrofit.create(HomeApi::class.java)

        @Provides @Singleton @HomeDataStore
        fun homeDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("home_local") }

        @Provides
        fun wallClock(): WallClock = WallClock { System.currentTimeMillis() }

        /** Balance and recent transactions never outlive the session. */
        @Provides @IntoSet
        fun homeCleanup(local: HomeLocalStore): SignOutCleanup = SignOutCleanup { local.clearCache() }
    }
}
