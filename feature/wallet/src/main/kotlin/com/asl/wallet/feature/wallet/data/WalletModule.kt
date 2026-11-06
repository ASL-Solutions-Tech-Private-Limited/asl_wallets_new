package com.asl.wallet.feature.wallet.data

import android.content.Context
import com.asl.wallet.core.common.session.SignOutCleanup
import com.asl.wallet.feature.wallet.payu.PayUCheckout
import com.asl.wallet.feature.wallet.payu.PayUCheckoutProLauncher
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WalletModule {
    @Binds @Singleton abstract fun repository(impl: WalletRepositoryImpl): WalletRepository
    @Binds abstract fun payu(impl: PayUCheckoutProLauncher): PayUCheckout

    companion object {
        /** Bank transfers can take a while: same client (pinning, auth, no auto-retry), longer timeouts. */
        @Provides @Singleton
        fun walletApi(retrofit: Retrofit, client: OkHttpClient): WalletApi = retrofit.newBuilder()
            .client(client.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build())
            .build().create(WalletApi::class.java)

        /** Bank document photos never outlive the session. */
        @Provides @IntoSet
        fun walletCleanup(@ApplicationContext context: Context, session: WalletSession): SignOutCleanup = SignOutCleanup { session.clear(); DocPhotos.wipeAll(context) }
    }
}
