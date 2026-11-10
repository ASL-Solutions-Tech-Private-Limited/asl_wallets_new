package com.asl.wallet.di

import android.content.Context
import com.asl.wallet.BuildConfig
import com.asl.wallet.core.network.di.DemoInterceptor
import com.asl.wallet.core.network.di.InspectorInterceptor
import com.asl.wallet.demo.AddMoneyDemoInterceptor
import com.asl.wallet.demo.AddMoneyDemoServer
import com.asl.wallet.demo.DemoLog
import com.asl.wallet.demo.PayUSimulator
import com.asl.wallet.feature.wallet.payu.PayUEvents
import com.asl.wallet.feature.wallet.payu.PayUStandIn
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * UAT debug only (this source set is not in release or live builds): the Add Money / PayU demo.
 * The PayU **test** key and salt come from local.properties (`payu.test.key`, `payu.test.salt`), which is never
 * committed; without them a simulated PayU screen is used. ASL's real PayU key and salt are never in the app.
 */
@Module
@InstallIn(SingletonComponent::class)
object AddMoneyDemoModule {

    @Provides @Singleton
    fun demoLog(@ApplicationContext context: Context) = DemoLog(context)

    @Provides @Singleton
    fun demoServer(log: DemoLog, @InspectorInterceptor inspectors: Set<@JvmSuppressWildcards Interceptor>): AddMoneyDemoServer {
        val creds = BuildConfig.PAYU_TEST_KEY.takeIf { it.isNotBlank() }?.let { k ->
            BuildConfig.PAYU_TEST_SALT.takeIf { it.isNotBlank() }?.let { s -> AddMoneyDemoServer.Credentials(k, s) }
        }
        // Server → PayU test verify_payment. Its own client (PayU's host isn't pinned to ASL's certificates), shown in Chucker too.
        val payu = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .apply { inspectors.forEach { addInterceptor(it) } }.build()
        val verify: (Map<String, String>) -> String? = { form ->
            val url = "https://test.payu.in/merchant/postservice.php?form=2"
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            runCatching {
                payu.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
                    val text = r.body.string()
                    log.http(
                        "POST", "$url  (server → PayU, verify_payment)", listOf("Content-Type" to "application/x-www-form-urlencoded"),
                        form.entries.joinToString("&") { (k, v) -> "$k=$v" }, r.code, emptyList(), text,
                    )
                    text
                }
            }.getOrNull()
        }
        return AddMoneyDemoServer(creds, verifyPayment = verify, note = log::add)
    }

    @Provides @IntoSet @DemoInterceptor
    fun addMoneyDemo(server: AddMoneyDemoServer, log: DemoLog): Interceptor = AddMoneyDemoInterceptor(server, log)

    @Provides
    fun payuStandIn(server: AddMoneyDemoServer, log: DemoLog): PayUStandIn = PayUSimulator(server, log)

    @Provides
    fun payuEvents(log: DemoLog): PayUEvents = PayUEvents { event, detail -> log.add(event, detail) }
}
