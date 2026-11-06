package com.asl.wallet.feature.dmt.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.asl.wallet.core.designsystem.component.ShopInfo

object DmtRoutes { const val DMT = "dmt" }

/** Module 5B: one screen with its own steps. Customer and recipient data never go into routes. */
fun NavGraphBuilder.dmtGraph(
    shop: @Composable () -> ShopInfo,
    onBack: () -> Unit,
    onAddMoney: () -> Unit,
    onFundRequest: () -> Unit,
    onCallSupport: () -> Unit,
    onWhatsAppSupport: () -> Unit,
) {
    composable(DmtRoutes.DMT) { DmtScreen(shop(), onBack, onAddMoney, onFundRequest, onCallSupport, onWhatsAppSupport) }
}
