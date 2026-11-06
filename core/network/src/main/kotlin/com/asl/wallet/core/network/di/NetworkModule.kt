package com.asl.wallet.core.network.di

import com.asl.wallet.core.common.session.TokenStore
import com.asl.wallet.core.network.NetworkConfig
import com.asl.wallet.core.network.api.HealthApi
import com.asl.wallet.core.network.interceptor.ApiHeaders
import com.asl.wallet.core.network.interceptor.AuthInterceptor
import com.asl.wallet.core.network.interceptor.CommonHeadersInterceptor
import com.asl.wallet.core.network.interceptor.StaleConnectionRetryInterceptor
import com.asl.wallet.core.network.interceptor.SessionEvents
import com.asl.wallet.core.network.interceptor.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import dagger.multibindings.Multibinds
import okhttp3.Interceptor
import javax.inject.Singleton

/** Debug-only HTTP inspectors (Chucker in UAT debug). Empty in every other build. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class InspectorInterceptor

@Module
@InstallIn(SingletonComponent::class)
abstract class InspectorDeclarations {
    @Multibinds @InspectorInterceptor abstract fun inspectors(): Set<Interceptor>
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true   // backend sends many extra fields
        explicitNulls = false
        coerceInputValues = true
        isLenient = false
    }

    @Provides @Singleton
    fun okHttp(
        config: NetworkConfig,
        tokenStore: TokenStore,
        sessionEvents: SessionEvents,
        json: Json,
        @InspectorInterceptor inspectors: Set<@JvmSuppressWildcards Interceptor>,
    ): OkHttpClient {
        val base = buildBaseClient(config)
        val refreshUrl = config.baseUrl.toHttpUrl().resolve("merchant/auth/refresh-token")!!
        return base.newBuilder()
            .addInterceptor(StaleConnectionRetryInterceptor())
            .addInterceptor(CommonHeadersInterceptor(config))
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore, sessionEvents, json, base, refreshUrl))
            .apply { if (config.isDebug) addInterceptor(debugLogger()) }
            // Last, so inspectors see the final request (headers are redacted by the inspector itself).
            .apply { inspectors.forEach { addInterceptor(it) } }
            .build()
    }

    @Provides @Singleton
    fun retrofit(config: NetworkConfig, client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(config.baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun healthApi(retrofit: Retrofit): HealthApi = retrofit.create(HealthApi::class.java)

    /**
     * TLS 1.2+ only, certificate pinning, sane timeouts, and NO automatic retries.
     * A silent OkHttp retry of a payment POST is how double debits happen.
     */
    internal fun buildBaseClient(config: NetworkConfig): OkHttpClient {
        val pinner = CertificatePinner.Builder().apply {
            config.certificatePins.forEach { (host, pins) -> pins.forEach { add(host, it) } }
        }.build()
        val tls = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
            .build()
        return OkHttpClient.Builder()
            .connectionSpecs(listOf(tls))
            .certificatePinner(pinner)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .build()
    }

    /** Debug builds only: method, URL, status and headers. Never bodies (OTPs, PID data, tokens). */
    private fun debugLogger() = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.HEADERS
        redactHeader(ApiHeaders.AUTHORIZATION)
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
    }
}
