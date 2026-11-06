package com.asl.wallet.ui

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.asl.wallet.BuildConfig
import com.asl.wallet.core.common.session.SignOutReason
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.security.lock.LockState
import com.asl.wallet.feature.auth.session.SessionState
import com.asl.wallet.feature.auth.ui.AppLockSetupScreen
import com.asl.wallet.feature.auth.ui.ForgotPasswordScreen
import com.asl.wallet.feature.auth.ui.LanguageScreen
import com.asl.wallet.feature.auth.ui.LoginScreen
import com.asl.wallet.feature.auth.ui.OtpScreen
import com.asl.wallet.feature.auth.ui.PasswordChangedScreen
import com.asl.wallet.feature.auth.ui.SessionViewModel
import com.asl.wallet.feature.auth.ui.SplashScreen
import com.asl.wallet.feature.auth.ui.StartDestination
import com.asl.wallet.ui.gate.LockScreen
import com.asl.wallet.ui.gate.UnsafeDeviceScreen
import com.asl.wallet.core.designsystem.component.ListCard
import com.asl.wallet.core.designsystem.component.ShopInfo
import com.asl.wallet.feature.aeps.ui.AepsBankChooserSheet
import com.asl.wallet.feature.aeps.ui.AepsRoutes
import com.asl.wallet.feature.aeps.ui.AepsSetupRow
import com.asl.wallet.feature.aeps.ui.aepsGraph
import com.asl.wallet.feature.home.domain.ServiceCatalogue
import com.asl.wallet.feature.wallet.ui.WalletRoutes
import com.asl.wallet.feature.wallet.ui.walletGraph
import com.asl.wallet.feature.recharge.domain.RechargeType
import com.asl.wallet.feature.recharge.ui.RechargeRoutes
import com.asl.wallet.feature.recharge.ui.rechargeGraph
import com.asl.wallet.feature.dmt.ui.DmtRoutes
import com.asl.wallet.feature.dmt.ui.dmtGraph
import com.asl.wallet.feature.reports.ui.ReportsRoutes
import com.asl.wallet.feature.reports.ui.ReportsTab
import com.asl.wallet.feature.reports.ui.reportsGraph
import com.asl.wallet.feature.profile.ui.ActiveServicesScreen
import com.asl.wallet.feature.profile.ui.ChangePasswordScreen
import com.asl.wallet.feature.profile.ui.CommissionScreen
import com.asl.wallet.feature.profile.ui.FeedbackScreen
import com.asl.wallet.feature.profile.ui.HelpScreen
import com.asl.wallet.feature.profile.ui.MyComplaintsScreen
import com.asl.wallet.feature.profile.ui.ProfileDetailsScreen
import com.asl.wallet.feature.profile.ui.ProfileTab
import com.asl.wallet.feature.profile.ui.RaiseComplaintScreen
import com.asl.wallet.feature.profile.ui.openDialer
import com.asl.wallet.feature.profile.ui.openWhatsApp
import com.asl.wallet.feature.profile.ui.SettingsScreen as ProfileSettingsScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.asl.wallet.core.designsystem.component.ListRow
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.feature.home.ui.AllServicesScreen
import com.asl.wallet.feature.home.ui.EditServicesScreen
import com.asl.wallet.feature.home.ui.HomeShell
import com.asl.wallet.feature.home.ui.HomeViewModel
import com.asl.wallet.feature.home.ui.NotificationsScreen
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.navigation
import com.asl.wallet.ui.showcase.ComponentsScreen
import com.asl.wallet.ui.showcase.SecurityScreen
import com.asl.wallet.ui.showcase.SettingsScreen
import com.asl.wallet.ui.showcase.ShowcaseHome
import com.asl.wallet.ui.showcase.StatesScreen
import kotlinx.coroutines.delay

object Routes {
    const val SPLASH = "splash"
    const val LANGUAGE = "language"
    const val LOGIN = "login"
    const val OTP = "otp"
    const val FORGOT = "forgot?prefill={prefill}"
    const val PASSWORD_CHANGED = "password_changed"
    const val LOCK_SETUP = "lock_setup"
    /** Signed-in graph; its screens share one HomeViewModel. */
    const val MAIN = "main"
    const val HOME = "home"
    const val ALL_SERVICES = "all_services"
    const val EDIT_SERVICES = "edit_services"
    const val NOTIFICATIONS = "notifications"
    // Module 3 · Profile & settings
    const val PROFILE_DETAILS = "profile/details"
    const val COMMISSION = "profile/commission"
    const val ACTIVE_SERVICES = "profile/services"
    const val CHANGE_PASSWORD = "profile/password"
    const val APP_SETTINGS = "profile/settings"
    const val HELP = "profile/help"
    const val RAISE_COMPLAINT = "profile/complaint"
    const val MY_COMPLAINTS = "profile/complaints"
    const val FEEDBACK = "profile/feedback"
    // Foundation showcase (UAT only)
    const val SHOWCASE = "showcase"
    const val COMPONENTS = "components"
    const val STATES = "states"
    const val SECURITY = "security"
    const val SETTINGS = "settings"

    fun forgot(prefill: String?) = "forgot?prefill=" + Uri.encode(prefill.orEmpty())
    val signedOutRoutes = setOf(SPLASH, LANGUAGE, LOGIN, OTP, FORGOT, PASSWORD_CHANGED)
}

@Composable
fun AslWalletApp(vm: AppViewModel) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val lock by vm.lockState.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sessionVm: SessionViewModel = hiltViewModel()
    val session by sessionVm.state.collectAsStateWithLifecycle()

    AslTheme(themeMode) {
        val dark = AslTheme.colors.isDark
        val view = LocalView.current
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        LaunchedEffect(Unit) { while (true) { delay(5_000); vm.checkIdle() } }

        Surface(Modifier.fillMaxSize(), color = AslTheme.colors.background) {
            val nav = rememberNavController()
            when {
                ui.integrityBlocked -> UnsafeDeviceScreen(ui.integrityIssues, BuildConfig.ALLOW_INTEGRITY_BYPASS, vm::bypassIntegrity)
                lock == LockState.Locked && session is SessionState.SignedIn -> LockScreen(onUnlocked = vm::onUnlocked)
                else -> AppNavHost(nav, vm, ui, session, sessionVm)
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, vm: AppViewModel, ui: AppUiState, session: SessionState, sessionVm: SessionViewModel) {
    // Any sign-out (user, expired refresh token, revoked elsewhere) lands on Login with a clean back stack.
    LaunchedEffect(session) {
        if (session is SessionState.SignedOut) {
            val current = nav.currentDestination?.route
            if (current != null && current !in Routes.signedOutRoutes) {
                nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            }
        }
    }
    val goHome: () -> Unit = { nav.navigate(Routes.MAIN) { popUpTo(0) { inclusive = true } } }
    val afterSignIn: (Boolean) -> Unit = { needsLock ->
        if (needsLock) nav.navigate(Routes.LOCK_SETUP) { popUpTo(0) { inclusive = true } } else goHome()
    }

    NavHost(nav, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onDestination = { d ->
                val route = when (d) {
                    StartDestination.Language -> Routes.LANGUAGE
                    StartDestination.Login -> Routes.LOGIN
                    StartDestination.Home -> Routes.MAIN
                }
                nav.navigate(route) { popUpTo(Routes.SPLASH) { inclusive = true } }
            })
        }
        composable(Routes.LANGUAGE) {
            LanguageScreen(onDone = { nav.navigate(Routes.LOGIN) { popUpTo(Routes.LANGUAGE) { inclusive = true } } })
        }
        composable(Routes.LOGIN) {
            val expired = (session as? SessionState.SignedOut)?.reason == SignOutReason.SessionExpired
            LoginScreen(
                onOtp = { nav.navigate(Routes.OTP) },
                onSignedIn = afterSignIn,
                onForgot = { nav.navigate(Routes.forgot(it)) },
                showSessionExpired = expired,
                onSessionExpiredShown = sessionVm::consumeReason,
            )
        }
        composable(Routes.OTP) { OtpScreen(onSignedIn = afterSignIn, onBack = { nav.popBackStack() }) }
        composable(Routes.FORGOT, arguments = listOf(navArgument("prefill") { type = NavType.StringType; defaultValue = "" })) { entry ->
            ForgotPasswordScreen(
                prefill = entry.arguments?.getString("prefill")?.ifBlank { null },
                onDone = { nav.navigate(Routes.PASSWORD_CHANGED) { popUpTo(Routes.LOGIN) } },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.PASSWORD_CHANGED) { PasswordChangedScreen(onGoToLogin = { nav.popBackStack(Routes.LOGIN, inclusive = false) }) }
        composable(Routes.LOCK_SETUP) { AppLockSetupScreen(allowSkip = BuildConfig.ALLOW_INTEGRITY_BYPASS, onDone = goHome) }
        navigation(startDestination = Routes.HOME, route = Routes.MAIN) {
            composable(Routes.HOME) { entry ->
                val homeVm = mainViewModel(nav, entry)
                var aepsChooser by rememberSaveable { mutableStateOf(false) }
                val user by vm.currentUser.user.collectAsStateWithLifecycle()
                HomeShell(
                    vm = homeVm,
                    onAllServices = { nav.navigate(Routes.ALL_SERVICES) },
                    onEditServices = { nav.navigate(Routes.EDIT_SERVICES) },
                    onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                    onOpenModule = { module, key ->
                        when (module) {
                            ServiceCatalogue.AEPS_MODULE -> aepsChooser = true
                            ServiceCatalogue.RECHARGE_MODULE -> nav.navigate(RechargeRoutes.recharge(if ("dth" in key) RechargeType.Dth else RechargeType.Mobile))
                            ServiceCatalogue.DMT_MODULE -> nav.navigate(DmtRoutes.DMT)
                            ServiceCatalogue.PAYOUT_MODULE -> { nav.navigate(WalletRoutes.GRAPH); nav.navigate(WalletRoutes.SEND) }
                        }
                    },
                    onWallet = { nav.navigate(WalletRoutes.GRAPH) },
                    onAddMoney = { nav.navigate(WalletRoutes.GRAPH); nav.navigate(WalletRoutes.ADD_MONEY) },
                    reportsTab = {
                        ReportsTab(
                            onLedger = { nav.navigate(ReportsRoutes.LEDGER) },
                            onReport = { nav.navigate(ReportsRoutes.report(it)) },
                            onFundRequests = { nav.navigate(WalletRoutes.GRAPH); nav.navigate(WalletRoutes.REQUESTS) },
                            onStatement = { nav.navigate(ReportsRoutes.STATEMENT) },
                        )
                    },
                    onSeeAll = { nav.navigate(ReportsRoutes.LEDGER) },
                    profileTab = {
                        ProfileTab(
                            onDetails = { nav.navigate(Routes.PROFILE_DETAILS) },
                            onCommission = { nav.navigate(Routes.COMMISSION) },
                            onServices = { nav.navigate(Routes.ACTIVE_SERVICES) },
                            onChangePassword = { nav.navigate(Routes.CHANGE_PASSWORD) },
                            onSettings = { nav.navigate(Routes.APP_SETTINGS) },
                            onHelp = { nav.navigate(Routes.HELP) },
                            versionName = BuildConfig.VERSION_NAME,
                            isDistributor = user?.isDistributor == true,
                            aepsRow = { AepsSetupRow(onClick = { aepsChooser = true }) },
                            extra = if (BuildConfig.ALLOW_INTEGRITY_BYPASS) {
                                {
                                    ListCard {
                                        ListRow("Foundation showcase", subtitle = "Components, states, security checks (UAT only)", icon = AslIcons.More,
                                            iconColors = AslGradients.Service.Aeps.colors, onClick = { nav.navigate(Routes.SHOWCASE) })
                                    }
                                }
                            } else null,
                        )
                    },
                )
                if (aepsChooser) AepsBankChooserSheet(
                    onChosen = { p -> aepsChooser = false; nav.navigate(AepsRoutes.gate(p)) },
                    onDismiss = { aepsChooser = false },
                )
            }
            composable(Routes.ALL_SERVICES) { entry -> AllServicesScreen(mainViewModel(nav, entry), onBack = { nav.popBackStack() }) }
            composable(Routes.EDIT_SERVICES) { entry -> EditServicesScreen(mainViewModel(nav, entry), onDone = { nav.popBackStack() }) }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(onBack = { nav.popBackStack() }) }
        }
        // Module 3 · Profile & settings
        val back: () -> Unit = { nav.popBackStack() }
        composable(Routes.PROFILE_DETAILS) { ProfileDetailsScreen(onBack = back) }
        composable(Routes.COMMISSION) { CommissionScreen(onBack = back) }
        composable(Routes.ACTIVE_SERVICES) { ActiveServicesScreen(onBack = back) }
        composable(Routes.CHANGE_PASSWORD) { ChangePasswordScreen(onDone = back, onBack = back) }
        composable(Routes.APP_SETTINGS) {
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            ProfileSettingsScreen(themeMode = mode, onTheme = vm::setTheme, versionName = BuildConfig.VERSION_NAME, onBack = back)
        }
        composable(Routes.HELP) {
            HelpScreen(vm.support, onRaise = { nav.navigate(Routes.RAISE_COMPLAINT) }, onMyComplaints = { nav.navigate(Routes.MY_COMPLAINTS) }, onFeedback = { nav.navigate(Routes.FEEDBACK) }, onBack = back)
        }
        composable(Routes.RAISE_COMPLAINT) { RaiseComplaintScreen(vm.support, onDone = back, onBack = back) }
        composable(Routes.MY_COMPLAINTS) { MyComplaintsScreen(onBack = back) }
        composable(Routes.FEEDBACK) { FeedbackScreen(vm.support, onDone = back, onBack = back) }
        // Module 3 · AEPS
        val context = nav.context
        aepsGraph(
            nav,
            shop = {
                val u by vm.currentUser.user.collectAsStateWithLifecycle()
                ShopInfo(u?.shopName?.ifBlank { null } ?: u?.name.orEmpty(), u?.regno?.let { "R-$it" }.orEmpty(), "")
            },
            onHome = { nav.navigate(Routes.MAIN) { popUpTo(Routes.MAIN) { inclusive = false }; launchSingleTop = true } },
            onCallSupport = { openDialer(context, vm.support.phone) },
            onWhatsAppSupport = { openWhatsApp(context, vm.support.whatsapp) },
        )
        // Module 4 · Wallet
        walletGraph(
            nav,
            shop = {
                val u by vm.currentUser.user.collectAsStateWithLifecycle()
                ShopInfo(u?.shopName?.ifBlank { null } ?: u?.name.orEmpty(), u?.regno?.let { "R-$it" }.orEmpty(), "")
            },
            onExit = { nav.navigate(Routes.MAIN) { popUpTo(Routes.MAIN) { inclusive = false }; launchSingleTop = true } },
            onCallSupport = { openDialer(context, vm.support.phone) },
            onWhatsAppSupport = { openWhatsApp(context, vm.support.whatsapp) },
        )
        // Module 5A · Recharge
        rechargeGraph(
            shop = {
                val u by vm.currentUser.user.collectAsStateWithLifecycle()
                ShopInfo(u?.shopName?.ifBlank { null } ?: u?.name.orEmpty(), u?.regno?.let { "R-$it" }.orEmpty(), "")
            },
            onBack = { nav.popBackStack() },
            onAddMoney = { nav.navigate(WalletRoutes.GRAPH); nav.navigate(WalletRoutes.ADD_MONEY) },
            onFundRequest = { nav.navigate(WalletRoutes.GRAPH); nav.navigate(WalletRoutes.FUND) },
            onCallSupport = { openDialer(context, vm.support.phone) },
            onWhatsAppSupport = { openWhatsApp(context, vm.support.whatsapp) },
        )
        // Module 5B · Money transfer (DMT)
        dmtGraph(
            shop = {
                val u by vm.currentUser.user.collectAsStateWithLifecycle()
                ShopInfo(u?.shopName?.ifBlank { null } ?: u?.name.orEmpty(), u?.regno?.let { "R-$it" }.orEmpty(), "")
            },
            onBack = { nav.popBackStack() },
            onAddMoney = { nav.navigate(WalletRoutes.GRAPH); nav.navigate(WalletRoutes.ADD_MONEY) },
            onFundRequest = { nav.navigate(WalletRoutes.GRAPH); nav.navigate(WalletRoutes.FUND) },
            onCallSupport = { openDialer(context, vm.support.phone) },
            onWhatsAppSupport = { openWhatsApp(context, vm.support.whatsapp) },
        )
        // Module 6 · Reports
        reportsGraph(
            shop = {
                val u by vm.currentUser.user.collectAsStateWithLifecycle()
                ShopInfo(u?.shopName?.ifBlank { null } ?: u?.name.orEmpty(), u?.regno?.let { "R-$it" }.orEmpty(), "")
            },
            onBack = { nav.popBackStack() },
        )
        // Foundation showcase (UAT only)
        composable(Routes.SHOWCASE) { ShowcaseHome(onOpen = { nav.navigate(it) }) }
        composable(Routes.COMPONENTS) { ComponentsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.STATES) { StatesScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SECURITY) { SecurityScreen(ui = ui, vm = vm, signedIn = session is SessionState.SignedIn, onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) {
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            SettingsScreen(themeMode = mode, onTheme = vm::setTheme, onBack = { nav.popBackStack() })
        }
    }
}

/** One HomeViewModel for the whole signed-in graph, so Home, All services and Edit stay in sync. */
@Composable
private fun mainViewModel(nav: NavHostController, entry: NavBackStackEntry): HomeViewModel {
    val parent = remember(entry) { nav.getBackStackEntry(Routes.MAIN) }
    return hiltViewModel(parent)
}
