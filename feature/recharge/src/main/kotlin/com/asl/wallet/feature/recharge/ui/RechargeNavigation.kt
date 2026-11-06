package com.asl.wallet.feature.recharge.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.asl.wallet.core.designsystem.component.ShopInfo
import com.asl.wallet.feature.recharge.domain.RechargeType

object RechargeRoutes {
    const val RECHARGE = "recharge?type={type}"
    fun recharge(type: RechargeType) = "recharge?type=${type.name}"
}

/** Module 5A: one screen with its own steps (form → plans → review → result). Numbers and amounts never go into routes. */
fun NavGraphBuilder.rechargeGraph(
    shop: @Composable () -> ShopInfo,
    onBack: () -> Unit,
    onAddMoney: () -> Unit,
    onFundRequest: () -> Unit,
    onCallSupport: () -> Unit,
    onWhatsAppSupport: () -> Unit,
) {
    composable(RechargeRoutes.RECHARGE, arguments = listOf(navArgument("type") { type = NavType.StringType; defaultValue = RechargeType.Mobile.name })) { e ->
        val type = runCatching { RechargeType.valueOf(e.arguments?.getString("type").orEmpty()) }.getOrDefault(RechargeType.Mobile)
        RechargeScreen(type, shop(), onBack, onAddMoney, onFundRequest, onCallSupport, onWhatsAppSupport)
    }
}
