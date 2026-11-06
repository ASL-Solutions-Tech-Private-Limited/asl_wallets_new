package com.asl.wallet.feature.wallet.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.asl.wallet.core.designsystem.component.ShopInfo

object WalletRoutes {
    const val GRAPH = "wallet"
    const val HOME = "wallet/home"
    const val ADD_MONEY = "wallet/add"
    const val FUND = "wallet/fund"
    const val REQUESTS = "wallet/requests"
    const val SEND = "wallet/send"
    const val BANKS = "wallet/banks"
    const val ADD_BANK = "wallet/banks/add"
}

/**
 * Module 4 screens. Money data never goes into routes. Finished flows return to the Wallet screen, so Back never
 * lands on a completed payment or transfer.
 */
fun NavGraphBuilder.walletGraph(
    nav: NavHostController,
    shop: @Composable () -> ShopInfo,
    onExit: () -> Unit,
    onCallSupport: () -> Unit,
    onWhatsAppSupport: () -> Unit,
    onReports: (() -> Unit)? = null,
) {
    val back: () -> Unit = { if (!nav.popBackStack()) onExit() }
    val toWallet: () -> Unit = { if (!nav.popBackStack(WalletRoutes.HOME, inclusive = false)) nav.navigate(WalletRoutes.HOME) }
    navigation(startDestination = WalletRoutes.HOME, route = WalletRoutes.GRAPH) {
        composable(WalletRoutes.HOME) {
            WalletScreen(
                onBack = onExit,
                onAddMoney = { nav.navigate(WalletRoutes.ADD_MONEY) },
                onFundRequest = { nav.navigate(WalletRoutes.FUND) },
                onSendToBank = { nav.navigate(WalletRoutes.SEND) },
                onBankAccounts = { nav.navigate(WalletRoutes.BANKS) },
                onViewAll = onReports,
            )
        }
        composable(WalletRoutes.ADD_MONEY) { AddMoneyScreen(onBack = back, onDone = toWallet, onCallSupport = onCallSupport) }
        composable(WalletRoutes.FUND) {
            FundRequestScreen(
                onBack = back,
                onMyRequests = { nav.navigate(WalletRoutes.REQUESTS) { popUpTo(WalletRoutes.HOME); launchSingleTop = true } },
                onDone = toWallet, onCallSupport = onCallSupport,
            )
        }
        composable(WalletRoutes.REQUESTS) {
            MyRequestsScreen(
                onBack = back,
                onEdit = { nav.navigate(WalletRoutes.FUND) },
                onNewRequest = { nav.navigate(WalletRoutes.FUND) { popUpTo(WalletRoutes.HOME) } },
            )
        }
        composable(WalletRoutes.SEND) {
            SendMoneyScreen(
                shop = shop(), onBack = back,
                onAddAccount = { nav.navigate(WalletRoutes.ADD_BANK) { popUpTo(WalletRoutes.HOME) } },
                onDone = toWallet, onCallSupport = onCallSupport, onWhatsAppSupport = onWhatsAppSupport,
            )
        }
        composable(WalletRoutes.BANKS) { BankAccountsScreen(onBack = back, onAdd = { nav.navigate(WalletRoutes.ADD_BANK) }) }
        composable(WalletRoutes.ADD_BANK) { AddBankScreen(onBack = back, onDone = back, onWallet = toWallet) }
    }
}
