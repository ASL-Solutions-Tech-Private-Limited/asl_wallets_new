package com.asl.wallet.feature.wallet.data

import android.app.Activity
import android.content.Context
import com.asl.wallet.core.common.session.SignOutCleanup
import com.asl.wallet.feature.wallet.payu.PayUCheckout
import com.asl.wallet.feature.wallet.payu.PayUCheckoutProLauncher
import com.asl.wallet.feature.wallet.payu.PayUEvents
import com.asl.wallet.feature.wallet.payu.PayUReturn
import com.asl.wallet.feature.wallet.payu.PayUStandIn
import com.asl.wallet.feature.wallet.payu.HashSource
import com.asl.wallet.feature.wallet.domain.PayUOrder
import dagger.BindsOptionalOf
import java.util.Optional
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
    @Binds @Singleton abstract fun payoutLocation(impl: AndroidPayoutLocation): PayoutLocation
    /** Present only in UAT debug builds (the Add Money demo). */
    @BindsOptionalOf abstract fun payuStandIn(): PayUStandIn
    @BindsOptionalOf abstract fun payuEvents(): PayUEvents

    companion object {
        /** Real PayU screens, unless a UAT-debug stand-in says it handles this order (demo without a PayU test account). */
        @Provides
        fun payu(real: PayUCheckoutProLauncher, standIn: Optional<PayUStandIn>): PayUCheckout = object : PayUCheckout {
            override fun open(activity: Activity, order: PayUOrder, production: Boolean, hashes: HashSource, onReturn: (PayUReturn, String?) -> Unit) {
                val s = standIn.orElse(null)
                if (s != null && s.handles(order)) s.open(activity, order, production, hashes, onReturn)
                else real.open(activity, order, production, hashes, onReturn)
            }
        }

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
