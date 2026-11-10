package com.asl.wallet.di

import android.content.Context
import com.asl.wallet.core.common.demo.DemoToggle
import com.asl.wallet.core.network.di.DemoInterceptor
import com.asl.wallet.demo.AepsDemo
import com.asl.wallet.demo.AepsDemoInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import javax.inject.Singleton

/** UAT debug only: the AEPS demo (switch in Profile; off by default). */
@Module
@InstallIn(SingletonComponent::class)
object AepsDemoModule {
    @Provides @Singleton fun aepsDemo(@ApplicationContext context: Context) = AepsDemo(context)
    @Provides @IntoSet fun aepsDemoToggle(demo: AepsDemo): DemoToggle = demo
    @Provides @IntoSet @DemoInterceptor fun aepsDemoInterceptor(demo: AepsDemo): Interceptor = AepsDemoInterceptor(demo)
}
