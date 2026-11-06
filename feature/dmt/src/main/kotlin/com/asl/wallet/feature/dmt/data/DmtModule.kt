package com.asl.wallet.feature.dmt.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.asl.wallet.core.common.session.SignOutCleanup
import dagger.Binds
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.multibindings.IntoSet
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DmtModule {
    @Binds @Singleton abstract fun repository(impl: DmtRepositoryImpl): DmtRepository
    @Binds abstract fun recentCustomers(impl: EncryptedRecentCustomerStore): RecentCustomerStore

    companion object {
        /** Bank transfers can take a while: same client (pinning, auth, no auto-retry), longer timeouts. */
        @Provides @Singleton
        fun dmtApi(retrofit: Retrofit, client: OkHttpClient): DmtApi = retrofit.newBuilder()
            .client(client.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build())
            .build().create(DmtApi::class.java)

        @Provides @Singleton @DmtDataStore
        fun dmtDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("dmt_local") }

        /** Recent customers never outlive the session. */
        @Provides @IntoSet
        fun dmtCleanup(store: RecentCustomerStore): SignOutCleanup = SignOutCleanup { store.clearAll() }
    }
}
