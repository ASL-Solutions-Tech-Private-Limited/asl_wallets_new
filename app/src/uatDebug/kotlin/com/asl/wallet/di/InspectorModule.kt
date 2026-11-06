package com.asl.wallet.di

import android.content.Context
import com.asl.wallet.core.network.di.InspectorInterceptor
import com.asl.wallet.core.network.inspect.LogRedactor
import com.chuckerteam.chucker.api.BodyDecoder
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.chuckerteam.chucker.api.RetentionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.ByteString

/**
 * UAT debug only (this source set is not in release or live builds).
 * Chucker records API calls so testers can see and share them. Everything stored is redacted first:
 * secrets removed, personal numbers masked (see [LogRedactor]). Kept for one day.
 */
@Module
@InstallIn(SingletonComponent::class)
object InspectorModule {

    @Provides @IntoSet @InspectorInterceptor
    fun chucker(@ApplicationContext context: Context): Interceptor =
        ChuckerInterceptor.Builder(context)
            .collector(ChuckerCollector(context, showNotification = true, retentionPeriod = RetentionManager.Period.ONE_DAY))
            .redactHeaders("Authorization", "Cookie", "Set-Cookie", "X-Refresh-Token")
            // Runs before Chucker's own decoder and always returns text, so a raw body is never stored.
            .addBodyDecoder(RedactingDecoder)
            .maxContentLength(250_000L)
            .alwaysReadResponseBody(true)
            .createShortcut(true)
            .build()

    private object RedactingDecoder : BodyDecoder {
        override fun decodeRequest(request: Request, body: ByteString): String =
            LogRedactor.redact(body.toByteArray(), request.body?.contentType()?.toString() ?: request.header("Content-Type"))

        override fun decodeResponse(response: Response, body: ByteString): String =
            LogRedactor.redact(body.toByteArray(), response.header("Content-Type"))
    }
}
