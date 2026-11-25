package com.asl.wallet.feature.matm.data

import dagger.Binds
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
abstract class MatmModule {
    @Binds @Singleton abstract fun repository(impl: MatmRepositoryImpl): MatmRepository
    @Binds @Singleton abstract fun location(impl: AndroidLocationSource): LocationSource

    companion object {
        /**
         * The card + bank round-trip can take a while. Same client (pinning, auth, no auto-retry) with
         * longer read/call timeouts, as AEPS does.
         */
        @Provides @Singleton
        fun matmApi(retrofit: Retrofit, client: OkHttpClient): MatmApi = retrofit.newBuilder()
            .client(client.newBuilder().readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build())
            .build().create(MatmApi::class.java)
    }
}
