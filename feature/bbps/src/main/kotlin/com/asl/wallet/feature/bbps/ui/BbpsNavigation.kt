package com.asl.wallet.feature.bbps.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.asl.wallet.core.designsystem.component.ShopInfo

object BbpsRoutes {
    const val BBPS = "bbps?category={category}"
    /** All categories, or straight into one ([category] is a category name like "Electricity", never customer data). */
    fun bbps(category: String? = null) = if (category == null) "bbps" else "bbps?category=${Uri.encode(category)}"

    /** A Home service (its key, e.g. "electricitybill") → the BBPS category it opens, or null for all categories. */
    fun categoryForService(key: String): String? {
        val k = key.lowercase()
        return when {
            "electric" in k || "power" in k -> "Electricity"
            "fastag" in k -> "Fastag"
            "lpg" in k || "gas" in k -> "LPG Gas"
            "loan" in k -> "Loan Repayment"
            "postpaid" in k -> "Mobile Postpaid"
            "water" in k -> "Water"
            "broadband" in k -> "Broadband Postpaid"
            else -> null
        }
    }
}

/** Module 10: BBPS bill payment. One screen with its own steps; consumer numbers and amounts never go into routes. */
fun NavGraphBuilder.bbpsGraph(
    shop: @Composable () -> ShopInfo,
    onBack: () -> Unit,
    onAddMoney: () -> Unit,
    onFundRequest: () -> Unit,
    onCallSupport: () -> Unit,
    onWhatsAppSupport: () -> Unit,
) {
    composable(BbpsRoutes.BBPS, arguments = listOf(navArgument("category") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
        BbpsScreen(e.arguments?.getString("category"), shop(), onBack, onAddMoney, onFundRequest, onCallSupport, onWhatsAppSupport)
    }
}
