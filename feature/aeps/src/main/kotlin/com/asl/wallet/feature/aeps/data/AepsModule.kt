package com.asl.wallet.feature.aeps.data

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
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AepsDataStore

@Module
@InstallIn(SingletonComponent::class)
abstract class AepsModule {
    @Binds @Singleton abstract fun repository(impl: AepsRepositoryImpl): AepsRepository
    @Binds @Singleton abstract fun location(impl: AndroidLocationSource): LocationSource

    companion object {
        /**
         * Bank calls can take up to a minute. Same client (pinning, auth, no auto-retry), longer read/call timeouts.
         * The GET-only stale-connection retry never applies to these POSTs.
         */
        @Provides @Singleton
        fun aepsApi(retrofit: Retrofit, client: OkHttpClient): AepsApi = retrofit.newBuilder()
            .client(client.newBuilder().readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build())
            .build().create(AepsApi::class.java)

        @Provides @Singleton
        fun identityApi(retrofit: Retrofit): AgentIdentityApi = retrofit.create(AgentIdentityApi::class.java)

        @Provides @Singleton @AepsDataStore
        fun aepsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("aeps_prefs") }

        /** Identity, status and customer details never outlive the session; document photos are wiped too. */
        @Provides @IntoSet
        fun aepsCleanup(session: AepsSession, @ApplicationContext context: Context): SignOutCleanup =
            SignOutCleanup { session.clear(); PhotoFiles.wipeAll(context) }
    }
}
