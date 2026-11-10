package com.asl.wallet.di

import com.asl.wallet.core.common.demo.DemoToggle
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/** Declares the (usually empty) set of UAT demos; only the uatDebug build adds any. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DemoTogglesModule {
    @Multibinds abstract fun demoToggles(): Set<DemoToggle>
}
