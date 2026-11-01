package com.daftar.deyoun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DaftarApp

        // Google's one-time consent screen is launched from here when the sync engine asks for it.
        val consentLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            app.sync.onConsentResult(res.resultCode == RESULT_OK)
        }
        lifecycleScope.launch {
            app.sync.consent.collect { pi ->
                if (pi != null) {
                    app.sync.consent.value = null
                    try {
                        consentLauncher.launch(IntentSenderRequest.Builder(pi).build())
                    } catch (e: Exception) {
                        app.sync.onConsentResult(false)
                    }
                }
            }
        }

        // While the app is on screen: sync when it opens, then check the cloud every 30 seconds.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.sync.onForeground()
                while (true) {
                    delay(30_000L)
                    app.sync.tick()
                }
            }
        }

        setContent { AppRoot(app) }
    }
}

/** What dialog is open on top of the current screen. */
sealed interface Dlg {
    data class PersonForm(val editId: String?) : Dlg
    data class TxForm(val kind: Kind, val personId: String?) : Dlg
    data class Details(val personId: String) : Dlg
    data class ConfirmDelete(val personId: String) : Dlg
}

private val NAV = listOf("home" to "⌂", "customers" to "♙", "debts" to "▤", "payments" to "＋", "reports" to "▥")

@Composable
fun AppRoot(app: DaftarApp) {
    val lang by app.prefs.lang.collectAsState()
    val themePref by app.prefs.theme.collectAsState()
    val ledger by app.store.state.collectAsState()
    val syncUi by app.sync.ui.collectAsState()
    val tr = remember(lang) { Tr(lang) }
    val dark = when (themePref) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current

    DaftarTheme(dark = dark, rtl = lang == "ar") {
        val snack = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val trNow by rememberUpdatedState(tr)
        val toaster = remember {
            Toaster { key, args ->
                scope.launch {
                    snack.currentSnackbarData?.dismiss()
                    snack.showSnackbar(trNow(key, *args.toTypedArray()))
                }
            }
        }
        LaunchedEffect(Unit) {
            app.sync.events.collect { m -> toaster(m.key, *m.args.toTypedArray()) }
        }

        var screen by remember { mutableStateOf("home") }
        var dlg by remember { mutableStateOf<Dlg?>(null) }

        CompositionLocalProvider(LocalTr provides tr, LocalToast provides toaster) {
            val backup = rememberBackupActions(app)
            BackupDialogHost(app, backup)
            BackHandler(enabled = dlg == null && screen != "home") { screen = "home" }

            fun openTx(kind: Kind, personId: String?) {
                if (ledger.people.isEmpty()) {
                    toaster("t_add_first")
                    dlg = Dlg.PersonForm(null)
                } else {
                    dlg = Dlg.TxForm(kind, personId)
                }
            }

            fun savePerson(editId: String?, name: String, phone: String) {
                if (name.isBlank()) {
                    toaster("t_enter_name")
                    return
                }
                if (editId == null) {
                    app.store.addPerson(name, phone)
                    toaster("t_saved_cust")
                } else {
                    app.store.editPerson(editId, name, phone)
                    toaster("t_edited")
                }
                dlg = null
            }

            fun saveTx(kind: Kind, personId: String?, amount: String, cur: String, due: String, note: String, wa: Boolean) {
                val p = if (personId == null) null else ledger.person(personId)
                if (p == null) {
                    toaster(if (personId == null) "t_choose" else "t_not_found")
                    return
                }
                val cents = parseCents(amount)
                if (cents == null) {
                    toaster("t_bad_amount")
                    return
                }
                if (kind == Kind.PAYMENT && cents > ledger.balanceCents(p.id, cur)) {
                    toaster("t_over")
                    return
                }
                app.store.addTx(p.id, kind, cents, cur, due.trim(), note.trim())
                dlg = null
                toaster(if (kind == Kind.DEBT) "t_debt_saved" else "t_pay_saved", "n" to p.name)
                if (wa) {
                    val msg = tr(if (kind == Kind.DEBT) "wam_debt" else "wam_pay", "n" to p.name, "a" to fmtMoney(cents, cur, lang))
                    val err = openWhatsApp(ctx, p.phone, msg)
                    if (err != null) toaster(err)
                }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snack) },
                topBar = {
                    AppHeader(
                        tr = tr,
                        syncState = syncUi.state,
                        onSync = { screen = "sync" },
                        onHistory = { screen = "history" },
                        onSettings = { screen = "settings" },
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        for (item in NAV) {
                            NavigationBarItem(
                                selected = screen == item.first,
                                onClick = { screen = item.first },
                                icon = { Text(item.second, fontSize = 20.sp) },
                                label = { Text(tr("nav_" + item.first), fontSize = 10.sp, maxLines = 1) },
                            )
                        }
                    }
                },
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (screen) {
                        "home" -> HomeScreen(
                            ledger,
                            onAddDebt = { openTx(Kind.DEBT, null) },
                            onNewCustomer = { dlg = Dlg.PersonForm(null) },
                            onPayment = { openTx(Kind.PAYMENT, null) },
                        )
                        "customers" -> CustomersScreen(
                            ledger,
                            onAdd = { dlg = Dlg.PersonForm(null) },
                            onDetails = { dlg = Dlg.Details(it) },
                            onEdit = { dlg = Dlg.PersonForm(it) },
                            onDelete = { dlg = Dlg.ConfirmDelete(it) },
                        )
                        "debts" -> DebtsScreen(ledger, onAdd = { openTx(Kind.DEBT, null) })
                        "payments" -> PaymentsScreen(ledger, onAdd = { openTx(Kind.PAYMENT, null) })
                        "reports" -> ReportsScreen(ledger, backup)
                        "history" -> HistoryScreen(ledger)
                        "settings" -> SettingsScreen(app, backup, onOpenSync = { screen = "sync" })
                        "sync" -> SyncScreen(app, backup)
                    }
                }
            }

            when (val d = dlg) {
                is Dlg.PersonForm -> PersonDialog(
                    existing = d.editId?.let { ledger.person(it) },
                    onDismiss = { dlg = null },
                    onSave = { n, p -> savePerson(d.editId, n, p) },
                )
                is Dlg.TxForm -> TxDialog(
                    kind = d.kind,
                    ledger = ledger,
                    initialPersonId = d.personId,
                    onDismiss = { dlg = null },
                    onSave = { pid, amt, cur, due, note, wa -> saveTx(d.kind, pid, amt, cur, due, note, wa) },
                )
                is Dlg.Details -> DetailsDialog(
                    ledger = ledger,
                    personId = d.personId,
                    onClose = { dlg = null },
                    onEdit = { dlg = Dlg.PersonForm(d.personId) },
                    onDebt = { dlg = Dlg.TxForm(Kind.DEBT, d.personId) },
                    onPay = { dlg = Dlg.TxForm(Kind.PAYMENT, d.personId) },
                )
                is Dlg.ConfirmDelete -> ConfirmDialog(
                    message = tr("confirm_del"),
                    confirmLabel = tr("del"),
                    onConfirm = {
                        app.store.deletePerson(d.personId)
                        dlg = null
                        toaster("t_deleted")
                    },
                    onDismiss = { dlg = null },
                )
                null -> {}
            }
        }
    }
}
