package com.asl.wallet.feature.recharge.data

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
abstract class RechargeModule {
    @Binds @Singleton abstract fun repository(impl: RechargeRepositoryImpl): RechargeRepository

    companion object {
        /** Operators can take a while to answer: same client (pinning, auth, no auto-retry), longer timeouts. */
        @Provides @Singleton
        fun rechargeApi(retrofit: Retrofit, client: OkHttpClient): RechargeApi = retrofit.newBuilder()
            .client(client.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build())
            .build().create(RechargeApi::class.java)
    }
}
