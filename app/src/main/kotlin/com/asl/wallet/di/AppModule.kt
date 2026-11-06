package com.asl.wallet.di

import com.asl.wallet.BuildConfig
import com.asl.wallet.core.network.NetworkConfig
import com.asl.wallet.feature.profile.data.SupportContacts
import com.asl.wallet.feature.wallet.payu.WalletConfig
import com.asl.wallet.feature.recharge.ui.RechargeConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Per-flavor network settings (uat / live) from BuildConfig. */
    @Provides @Singleton
    fun networkConfig(): NetworkConfig {
        val pins = BuildConfig.PINS.split(',').map(String::trim).filter(String::isNotEmpty)
        return NetworkConfig(
            baseUrl = BuildConfig.BASE_URL,
            certificatePins = if (pins.isEmpty()) emptyMap() else mapOf(BuildConfig.PIN_HOST to pins),
            isDebug = BuildConfig.DEBUG,
            appVersion = BuildConfig.VERSION_NAME,
            flavor = BuildConfig.FLAVOR,
        )
    }

    /** ASL's official support line (from the old app). Same number for call and WhatsApp until ASL says otherwise. */
    @Provides
    fun supportContacts(): SupportContacts = SupportContacts(phone = BuildConfig.SUPPORT_PHONE, whatsapp = BuildConfig.SUPPORT_PHONE)

    /** PayU switch and environment per flavor. The environment never comes from the server. */
    @Provides
    fun walletConfig(): WalletConfig = WalletConfig(payuEnabled = BuildConfig.PAYU_ENABLED, payuProduction = BuildConfig.PAYU_PRODUCTION)

    /** DTH customer details: on (API shared 5 Nov 2026). Recharge works without them. */
    @Provides
    fun rechargeConfig(): RechargeConfig = RechargeConfig(dthInfoEnabled = true)
}
