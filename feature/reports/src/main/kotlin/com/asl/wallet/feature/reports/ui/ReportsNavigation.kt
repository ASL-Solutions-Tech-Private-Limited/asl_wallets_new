package com.asl.wallet.feature.reports.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.asl.wallet.core.designsystem.component.ShopInfo
import com.asl.wallet.feature.reports.domain.ReportKind

object ReportsRoutes {
    const val LEDGER = "reports/ledger"
    const val REPORT = "reports/report/{kind}"
    const val STATEMENT = "reports/statement"
    fun report(kind: ReportKind) = "reports/report/${kind.name}"
}

/** Module 6. Rows and filters never go into routes; only the report kind (an enum name) does. */
fun NavGraphBuilder.reportsGraph(shop: @Composable () -> ShopInfo, onBack: () -> Unit) {
    composable(ReportsRoutes.LEDGER) { LedgerScreen(shop(), onBack) }
    composable(ReportsRoutes.REPORT, arguments = listOf(navArgument("kind") { type = NavType.StringType })) { ReportScreen(shop(), onBack) }
    composable(ReportsRoutes.STATEMENT) { StatementScreen(shop(), onBack) }
}
