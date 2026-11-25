package com.asl.wallet.feature.matm.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.asl.wallet.core.designsystem.component.ShopInfo

object MatmRoutes { const val MATM = "matm" }

/** Module 7: one screen — enter mobile/amount, run the card reader, show the receipt. */
fun NavGraphBuilder.matmGraph(
    shop: @Composable () -> ShopInfo,
    onBack: () -> Unit,
    onHelp: () -> Unit,
) {
    composable(MatmRoutes.MATM) { MatmTransactionScreen(shop(), onBack = onBack, onHelp = onHelp) }
}
