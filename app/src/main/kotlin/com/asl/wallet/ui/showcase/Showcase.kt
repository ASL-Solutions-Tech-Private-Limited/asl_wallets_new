package com.asl.wallet.ui.showcase

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.asl.wallet.BuildConfig
import com.asl.wallet.R
import com.asl.wallet.ui.Routes
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.core.common.validation.Validators
import com.asl.wallet.core.designsystem.component.AlertBanner
import com.asl.wallet.core.designsystem.component.AlertKind
import com.asl.wallet.core.designsystem.component.AmountCard
import com.asl.wallet.core.designsystem.component.AslBottomNav
import com.asl.wallet.core.designsystem.component.AslButton
import com.asl.wallet.core.designsystem.component.AslButtonStyle
import com.asl.wallet.core.designsystem.component.AslCard
import com.asl.wallet.core.designsystem.component.AslPrimaryButton
import com.asl.wallet.core.designsystem.component.AslSwitch
import com.asl.wallet.core.designsystem.component.AslTabs
import com.asl.wallet.core.designsystem.component.AslTextField
import com.asl.wallet.core.designsystem.component.AslTopBar
import com.asl.wallet.core.designsystem.component.ChoiceChips
import com.asl.wallet.core.designsystem.component.Divider
import com.asl.wallet.core.designsystem.component.EarningsChip
import com.asl.wallet.core.designsystem.component.HelpCard
import com.asl.wallet.core.designsystem.component.KeyValue
import com.asl.wallet.core.designsystem.component.KeyValueList
import com.asl.wallet.core.designsystem.component.ListCard
import com.asl.wallet.core.designsystem.component.ListRow
import com.asl.wallet.core.designsystem.component.NavItem
import com.asl.wallet.core.designsystem.component.OtpInput
import com.asl.wallet.core.designsystem.component.PassbookTable
import com.asl.wallet.core.designsystem.component.ReceiptActionBar
import com.asl.wallet.core.designsystem.component.ReceiptCard
import com.asl.wallet.core.designsystem.component.SectionHeader
import com.asl.wallet.core.designsystem.component.SegmentedControl
import com.asl.wallet.core.designsystem.component.ServiceGrid
import com.asl.wallet.core.designsystem.component.ServiceItem
import com.asl.wallet.core.designsystem.component.ShopInfo
import com.asl.wallet.core.designsystem.component.SkeletonRow
import com.asl.wallet.core.designsystem.component.SlideToConfirm
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.component.StateView
import com.asl.wallet.core.designsystem.component.Status
import com.asl.wallet.core.designsystem.component.StatusPill
import com.asl.wallet.core.designsystem.component.TransactionRow
import com.asl.wallet.core.designsystem.component.WalletCard
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslGradients.Service
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.designsystem.theme.ThemeMode
import com.asl.wallet.core.security.ui.SecureScreen
import com.asl.wallet.ui.AppUiState
import com.asl.wallet.ui.AppViewModel
import com.asl.wallet.ui.ConnectionResult
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Shared screen frame: top bar + scrolling content with safe-area and keyboard padding. */
@Composable
private fun Screen(title: String, onBack: (() -> Unit)?, subtitle: String? = null, snackbar: SnackbarHostState? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            AslTopBar(title, subtitle = subtitle, onBack = onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
        if (snackbar != null) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
fun ShowcaseHome(onOpen: (String) -> Unit) {
    val c = AslTheme.colors
    var hidden by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.foundation_title), style = AslTheme.type.headline, color = c.textPrimary)
                    Text(stringResource(R.string.foundation_subtitle), style = AslTheme.type.caption, color = c.textSecondary)
                }
                StatusPill("${BuildConfig.FLAVOR.uppercase()} · ${BuildConfig.VERSION_NAME}", Status.Info)
            }
            WalletCard(
                balance = BigDecimal("48250.75"), hidden = hidden, onToggleHidden = { hidden = !hidden },
                subtitle = "Today · 23 transactions", earnedToday = "+₹ 312.40 earned",
                actions = listOf("+ Add money" to {}, "Fund request" to {}),
            )
            AlertBanner(AlertKind.Info, "Example data only", message = "This build tests the design system and security base. No real money moves.")
            SectionHeader("What to test")
            ListCard {
                ListRow(stringResource(R.string.section_components), subtitle = stringResource(R.string.section_components_sub), icon = AslIcons.More, iconColors = Service.Aeps.colors, onClick = { onOpen(Routes.COMPONENTS) })
                Divider()
                ListRow(stringResource(R.string.section_states), subtitle = stringResource(R.string.section_states_sub), icon = AslIcons.Info, iconColors = Service.Bills.colors, onClick = { onOpen(Routes.STATES) })
                Divider()
                ListRow(stringResource(R.string.section_security), subtitle = stringResource(R.string.section_security_sub), icon = AslIcons.Shield, iconColors = Service.Upi.colors, onClick = { onOpen(Routes.SECURITY) })
                Divider()
                ListRow(stringResource(R.string.section_settings), subtitle = stringResource(R.string.section_settings_sub), icon = AslIcons.Moon, iconColors = Service.Wallet.colors, onClick = { onOpen(Routes.SETTINGS) })
            }
        }
        var tab by remember { mutableIntStateOf(0) }
        AslBottomNav(
            listOf(NavItem("Home", AslIcons.Home), NavItem("Reports", AslIcons.Report), NavItem("Earnings", AslIcons.Earnings), NavItem("Profile", AslIcons.User)),
            selected = tab, onSelect = { tab = it },
        )
    }
}

@Composable
fun ComponentsScreen(onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun toast(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("SBIN0001234") }
    var amountText by remember { mutableStateOf("5000") }
    var otp by remember { mutableStateOf("") }
    var chip by remember { mutableIntStateOf(2) }
    var seg by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var sw by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var slideReset by remember { mutableIntStateOf(0) }
    val amount = Money.parse(amountText)

    Screen(stringResource(R.string.section_components), onBack, snackbar = snackbar) {
        SectionHeader("Buttons")
        AslPrimaryButton("Continue", onClick = { loading = true; toast("Tapped once. Loading blocks double taps.") }, loading = loading)
        AslButton("Stop loading", onClick = { loading = false }, style = AslButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
        AslPrimaryButton("Disabled", onClick = {}, enabled = false)
        AslButton("Log out", onClick = {}, style = AslButtonStyle.Danger, modifier = Modifier.fillMaxWidth())

        SectionHeader("Text fields")
        AslTextField(mobile, { mobile = it.filter(Char::isDigit) }, "Mobile number", placeholder = "10-digit number", keyboardType = KeyboardType.Phone, maxLength = 10,
            error = if (mobile.length == 10 && !Validators.isMobile(mobile)) "Enter a valid Indian mobile number" else null,
            success = if (Validators.isMobile(mobile)) "Looks good" else null)
        AslTextField(password, { password = it }, "Password", password = true)
        AslTextField(ifsc, { ifsc = it.uppercase() }, "IFSC", maxLength = 11,
            success = if (Validators.isIfsc(ifsc)) "Valid IFSC format" else null,
            error = if (ifsc.length == 11 && !Validators.isIfsc(ifsc)) "Check the IFSC" else null)
        AslTextField("Rahim Sk", {}, "Full name (KYC verified)", locked = true)

        SectionHeader("OTP")
        OtpInput(otp, { otp = it }, length = 4, onComplete = { toast("OTP entered: ${it.length} digits") })

        SectionHeader("Selection")
        ChoiceChips(listOf("500", "1,000", "2,000", "5,000"), chip, { chip = it })
        SegmentedControl(listOf("IMPS · instant", "NEFT"), seg, { seg = it })
        AslTabs(listOf("Popular", "Data", "Unlimited", "Offers"), tab, { tab = it })
        AslCard { Row(verticalAlignment = Alignment.CenterVertically) { Text("Speak AEPS results", style = AslTheme.type.body, color = AslTheme.colors.textPrimary, modifier = Modifier.weight(1f)); AslSwitch(sw, { sw = it }) } }

        SectionHeader("Status")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill("Success", Status.Success); StatusPill("Pending", Status.Pending); StatusPill("Failed", Status.Failed); StatusPill("Verified", Status.Info)
        }
        AlertBanner(AlertKind.Success, "Payout is open", message = "7:00 am – 11:45 pm")
        AlertBanner(AlertKind.Pending, "₹ 5,000 transfer pending", message = "Checking with the bank")
        AlertBanner(AlertKind.Error, "Transfer failed", message = "₹ 5,025 refunded to wallet")

        SectionHeader("Money")
        AslTextField(amountText, { amountText = it.filter { ch -> ch.isDigit() } }, "Amount", keyboardType = KeyboardType.Number, maxLength = 7)
        AmountCard(amount, quickAmounts = listOf(1000, 2000, 5000, 10000), selectedQuick = amount?.toLong(), onQuickSelect = { amountText = it.toString() })
        AslCard {
            KeyValueList(listOf(
                KeyValue("Charge", "₹ 25.00"),
                KeyValue("Taken from wallet", Money.format((amount ?: BigDecimal.ZERO) + BigDecimal(25)), total = true),
                KeyValue("Balance after", "₹ 43,225.75"),
            ))
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("You earn ", style = AslTheme.type.body, color = AslTheme.colors.textSecondary); EarningsChip("+₹ 12.50") }
        SlideToConfirm("Slide to send ${Money.format(amount ?: BigDecimal.ZERO, showPaise = false)}", onConfirmed = { toast("Confirmed exactly once") }, resetKey = slideReset)
        AslButton("Reset slider", onClick = { slideReset++ }, style = AslButtonStyle.Outline, compact = true, modifier = Modifier.fillMaxWidth())

        SectionHeader("Lists")
        ListCard {
            TransactionRow("CW", Service.Aeps.colors, "Cash withdrawal · SBI", "XXXX 4821 · 9:32 am", "₹ 2,000", "Success", Status.Success)
            Divider()
            TransactionRow("DT", Service.SendMoney.colors, "Transfer · Sk. Ayan Ali", "IMPS · 9:41 am", "₹ 5,000", "Pending", Status.Pending)
        }
        ServiceGrid(
            listOf(
                ServiceItem("AEPS", AslIcons.Fingerprint, Service.Aeps.colors), ServiceItem("Micro ATM", AslIcons.Atm, Service.MicroAtm.colors),
                ServiceItem("Send money", AslIcons.Send, Service.SendMoney.colors), ServiceItem("Scan & Pay", AslIcons.Upi, Service.Upi.colors),
                ServiceItem("Recharge", AslIcons.Phone, Service.Recharge.colors), ServiceItem("Bill pay", AslIcons.Bill, Service.Bills.colors),
                ServiceItem("QR collection", AslIcons.Qr, Service.Qr.colors, tag = "NEW"), ServiceItem("More", AslIcons.More, Service.Neutral.colors),
            ),
            onClick = { toast(it.label) },
        )

        SectionHeader("Receipt")
        ReceiptCard(
            ShopInfo("Rahim Store", "R-20431", "22, Bazar Road, Habra 743263 · 98765 43210"),
            "Money transfer", "₹ 5,000", Status.Success,
            listOf(
                KeyValue("Beneficiary", "Sk. Ayan Ali · SBI ••••4417"), KeyValue("IFSC · Mode", "SBIN0001234 · IMPS"),
                KeyValue("Bank ref (UTR)", "4192 7781 0023"), KeyValue("Txn ID", "ASL-DMT-8F3K2Q"), KeyValue("Date", "3 Nov 2026, 9:41 am", total = true),
            ),
        )
        ReceiptActionBar(onWhatsApp = { toast("WhatsApp: built in Module 5") }, onSavePdf = { toast("Save PDF: built in Module 5") }, onPrint = { toast("Print: built in Module 5") }, onHelp = { toast("Help: built in Module 5") })
        HelpCard(onComplaint = {}, onWhatsApp = {}, onCall = {})

        SectionHeader("Passbook")
        PassbookTable(
            listOf("Date", "Narration", "", "Amount"),
            listOf(listOf("02/11", "UPI/CR/ANIL DAS", "Cr", "+1,500.00"), listOf("01/11", "ATM WDL KOLKATA", "Dr", "−2,000.00"), listOf("30/10", "AEPS CW HABRA", "Dr", "−1,000.00")),
            weights = listOf(1f, 2.4f, 0.6f, 1.4f),
        )
        PassbookTable(
            listOf("Time", "Description", "Debit", "Credit", "Balance"),
            listOf(listOf("!", "", "Opening balance", "", "", "51,120.00"), listOf("09:32", "AEPS CW SBI", "", "+2,000.00", "53,120.00"), listOf("09:41", "DMT Sk Ayan Ali", "−5,025.00", "", "48,095.00")),
            weights = listOf(0.9f, 2f, 1.3f, 1.3f, 1.4f),
        )
    }
}

@Composable
fun StatesScreen(onBack: () -> Unit) {
    var which by remember { mutableIntStateOf(0) }
    val names = listOf("Loading", "Empty", "Offline", "Session", "Update", "Location", "Hours")
    Screen(stringResource(R.string.section_states), onBack) {
        ChoiceChips(names, which, { which = it })
        when (which) {
            0 -> AslCard { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { repeat(5) { SkeletonRow() } } }
            1 -> StateView(AslIcons.Report, StatePresets.emptyColors, "No transfers yet today", "When you send money, it shows up here with its live status.") { AslPrimaryButton("Send money now", {}, icon = AslIcons.Send) }
            2 -> StateView(AslIcons.WifiOff, StatePresets.offlineColors, "You're offline", "Check mobile data or Wi-Fi. We'll reconnect automatically. No money moves while offline.") {
                AslPrimaryButton("Try again", {}, icon = AslIcons.Refresh); AslButton("View saved reports", {}, style = AslButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
            }
            3 -> StateView(AslIcons.Lock, StatePresets.warnColors, "Please sign in again", "For your security you've been signed out after a long time away, or from another phone.") { AslPrimaryButton("Sign in", {}) }
            4 -> StateView(AslIcons.Update, AslGradients.action, "A new version is ready", "This update includes important security fixes. It takes about a minute.") { AslPrimaryButton("Update now", {}) }
            5 -> StateView(AslIcons.Location, StatePresets.okColors, "Turn on location", "Banks need the shop's real location for every AEPS transaction. We never track you outside transactions.") { AslPrimaryButton("Turn on location", {}, icon = AslIcons.Location) }
            else -> StateView(AslIcons.Clock, StatePresets.warnColors, "Payout opens at 7:00 am", "Bank payouts are available 7:00 am – 11:45 pm. You can still use AEPS, recharge and bill pay now.") { AslPrimaryButton("Remind me at 7:00 am", {}, icon = AslIcons.Bell) }
        }
    }
}

@Composable
fun SecurityScreen(ui: AppUiState, vm: AppViewModel, signedIn: Boolean, onBack: () -> Unit) {
    SecureScreen() // screenshots and screen recording are blocked while this screen is open
    val c = AslTheme.colors
    Screen(stringResource(R.string.section_security), onBack) {
        AlertBanner(AlertKind.Info, "Screenshots are blocked on this screen", message = "Try a screenshot or check the recent-apps preview: it should be blank.", icon = AslIcons.Lock)

        SectionHeader("Phone safety")
        AslCard {
            if (ui.integrityIssues.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("No root, tampering or debugger found", style = AslTheme.type.body, color = c.textPrimary, modifier = Modifier.weight(1f)); StatusPill("Safe", Status.Success) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Found: " + ui.integrityIssues.joinToString { it.name }, style = AslTheme.type.body, color = c.textPrimary)
                    Text(if (BuildConfig.ALLOW_EMULATOR) "UAT build: emulators are allowed for testing." else "Live build: payments are blocked on this phone.", style = AslTheme.type.caption, color = c.textSecondary)
                }
            }
        }

        SectionHeader("App lock")
        AslCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (signedIn) "You're signed in, so the app lock is active." else "Sign in first: the app lock only runs during a session.", style = AslTheme.type.body, color = c.textPrimary)
                Text("Locks after 30 s in the background, or 5 min without a touch. Unlock uses your phone's fingerprint, PIN or pattern.", style = AslTheme.type.caption, color = c.textSecondary)
                AslButton("Lock now", onClick = vm::lockNow, enabled = signedIn, style = AslButtonStyle.Outline, modifier = Modifier.fillMaxWidth(), icon = AslIcons.Lock)
            }
        }

        SectionHeader("Secure connection (UAT server)")
        AslCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(BuildConfig.BASE_URL, style = AslTheme.type.caption, color = c.textSecondary)
                when (val r = ui.connection) {
                    null -> Text("Checks TLS and the certificate pin against the real server.", style = AslTheme.type.caption, color = c.textSecondary)
                    ConnectionResult.Checking -> StatusPill("Checking…", Status.Info)
                    is ConnectionResult.Verified -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusPill("Secure · certificate pin matched", Status.Success)
                        Text("Server reachable in ${r.millis} ms", style = AslTheme.type.caption, color = c.textSecondary)
                    }
                    ConnectionResult.InsecureBlocked -> StatusPill("Blocked: certificate didn't match (possible interception)", Status.Failed)
                    ConnectionResult.NoInternet -> StatusPill("No internet", Status.Pending)
                    is ConnectionResult.Unexpected -> StatusPill("Unexpected: ${r.detail}", Status.Failed)
                }
                AslPrimaryButton(stringResource(R.string.connection_check), onClick = vm::checkConnection, loading = ui.connection == ConnectionResult.Checking, icon = AslIcons.Shield)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SettingsScreen(themeMode: ThemeMode, onTheme: (ThemeMode) -> Unit, onBack: () -> Unit) {
    val c = AslTheme.colors
    val langs = listOf("en", "hi", "bn")
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().take(2).ifEmpty { "en" }
    Screen(stringResource(R.string.section_settings), onBack) {
        SectionHeader(stringResource(R.string.theme))
        SegmentedControl(
            listOf(stringResource(R.string.theme_light), stringResource(R.string.theme_dark), stringResource(R.string.theme_system)),
            ThemeMode.entries.indexOf(themeMode), { onTheme(ThemeMode.entries[it]) },
        )
        SectionHeader(stringResource(R.string.language))
        SegmentedControl(listOf("English", "हिन्दी", "বাংলা"), langs.indexOf(current).coerceAtLeast(0), { i ->
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langs[i]))
        })
        SectionHeader("Font size")
        AslCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("The app follows your phone's font size (Settings → Display → Font size). Try Large and Largest: every screen should still fit.", style = AslTheme.type.body, color = c.textPrimary)
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(AslGradients.action), AslShapes.card).padding(14.dp)) {
                    Text("₹ 5,000 · পাঁচ হাজার · पाँच हज़ार", style = AslTheme.type.bodyStrong, color = Color.White)
                }
            }
        }
    }
}
