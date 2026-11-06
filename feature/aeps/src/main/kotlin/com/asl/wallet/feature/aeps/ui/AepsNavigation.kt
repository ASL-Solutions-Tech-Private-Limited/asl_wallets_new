package com.asl.wallet.feature.aeps.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.asl.wallet.core.designsystem.component.ShopInfo
import com.asl.wallet.feature.aeps.domain.AepsProvider
import com.asl.wallet.feature.aeps.domain.NextStep

object AepsRoutes {
    const val GRAPH = "aeps"
    const val GATE = "aeps/gate/{provider}"
    const val ONBOARDING = "aeps/onboarding"
    const val CHECK = "aeps/check/{kind}"
    const val TXN = "aeps/txn"
    fun gate(p: AepsProvider) = "aeps/gate/${p.id}"
    fun check(k: CheckKind) = "aeps/check/${k.name}"
}

/**
 * AEPS screens. Each step replaces the previous one in the back stack, so Back from any AEPS screen
 * returns to Home rather than to a finished step. Money data never goes into routes.
 */
fun NavGraphBuilder.aepsGraph(
    nav: NavHostController,
    shop: @Composable () -> ShopInfo,
    onHome: () -> Unit,
    onCallSupport: () -> Unit,
    onWhatsAppSupport: () -> Unit,
) {
    fun go(step: NextStep) {
        val route = when (step) {
            NextStep.Onboarding -> AepsRoutes.ONBOARDING
            NextStep.BankVerification -> AepsRoutes.check(CheckKind.BankVerification)
            NextStep.BiometricKyc -> AepsRoutes.check(CheckKind.BiometricKyc)
            NextStep.DailyLogin -> AepsRoutes.check(CheckKind.DailyLogin)
            NextStep.Ready -> AepsRoutes.TXN
        }
        nav.navigate(route) { popUpTo(AepsRoutes.GRAPH) { inclusive = false }; launchSingleTop = true }
    }
    navigation(startDestination = AepsRoutes.GATE, route = AepsRoutes.GRAPH) {
        composable(AepsRoutes.GATE, arguments = listOf(navArgument("provider") { type = NavType.IntType; defaultValue = AepsProvider.Kotak.id })) { e ->
            val p = AepsProvider.of(e.arguments?.getInt("provider")) ?: AepsProvider.Kotak
            AepsGateScreen(p, onStep = ::go, onBack = onHome)
        }
        composable(AepsRoutes.ONBOARDING) { AepsOnboardingScreen(onNext = ::go, onBack = onHome) }
        composable(AepsRoutes.CHECK, arguments = listOf(navArgument("kind") { type = NavType.StringType })) { e ->
            val kind = runCatching { CheckKind.valueOf(e.arguments?.getString("kind").orEmpty()) }.getOrDefault(CheckKind.DailyLogin)
            AepsCheckScreen(kind, onNext = ::go, onBack = onHome)
        }
        composable(AepsRoutes.TXN) {
            AepsTransactionScreen(
                shop = shop(), onHome = onHome, onBack = onHome,
                onUseKotak = { nav.navigate(AepsRoutes.gate(AepsProvider.Kotak)) { popUpTo(AepsRoutes.GRAPH) { inclusive = true } } },
                onCallSupport = onCallSupport, onWhatsAppSupport = onWhatsAppSupport,
            )
        }
    }
}
