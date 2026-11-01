package com.daftar.deyoun

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------------------------------ Home

@Composable
fun HomeScreen(ledger: Ledger, onAddDebt: () -> Unit, onNewCustomer: () -> Unit, onPayment: () -> Unit) {
    val tr = LocalTr.current
    val e = LocalExtra.current
    ScreenList {
        item { ScreenHead(tr("nav_home"), tr("home_sub"), tr("add_debt"), onAddDebt) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(tr("total_balance"), formatTotals(ledger, tr.lang) { ledger.remainingCents(it) }, false, Modifier.weight(1f))
                StatCard(tr("customers_count"), ledger.people.size.toString(), true, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onNewCustomer, modifier = Modifier.weight(1f)) { Text(tr("new_customer"), maxLines = 1) }
                Button(
                    onClick = onPayment,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = e.green, contentColor = Color.White),
                ) { Text(tr("record_payment"), maxLines = 1) }
            }
        }
        item { Text(tr("recent"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (ledger.tx.isEmpty()) {
            item { EmptyText(tr("no_tx")) }
        } else {
            items(ledger.tx.asReversed(), key = { it.id }) { TxRow(it, ledger) }
        }
    }
}

// ------------------------------------------------------------------------------------------ Customers

@Composable
fun CustomersScreen(
    ledger: Ledger,
    onAdd: () -> Unit,
    onDetails: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val tr = LocalTr.current
    val e = LocalExtra.current
    var q by remember { mutableStateOf("") }
    val key = q.trim().lowercase()
    val list = ledger.people.filter { (it.name + " " + it.phone).lowercase().contains(key) }
    ScreenList {
        item { ScreenHead(tr("nav_customers"), tr("customers_sub"), tr("add_customer"), onAdd) }
        item {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                placeholder = { Text(tr("search_cust_ph")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (list.isEmpty()) {
            item { EmptyText(tr("no_customers_add")) }
        } else {
            items(list, key = { it.id }) { p ->
                val good = !ledger.hasBalance(p.id)
                Card2 {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text(
                                if (p.phone.isBlank()) tr("no_phone") else p.phone,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Badge2(tr(if (good) "settled" else "has_balance"), good)
                        }
                        Text(
                            balanceSummary(ledger, p.id, tr.lang),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onDetails(p.id) }) { Text(tr("details")) }
                        TextButton(onClick = { onEdit(p.id) }) { Text(tr("edit_name")) }
                        TextButton(onClick = { onDelete(p.id) }, colors = ButtonDefaults.textButtonColors(contentColor = e.red)) { Text(tr("del")) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ Debts / Payments / History

@Composable
fun DebtsScreen(ledger: Ledger, onAdd: () -> Unit) {
    val tr = LocalTr.current
    var q by remember { mutableStateOf("") }
    val key = q.trim().lowercase()
    val list = ledger.tx.filter { it.kind == Kind.DEBT && (ledger.person(it.person)?.name ?: "").lowercase().contains(key) }.asReversed()
    ScreenList {
        item { ScreenHead(tr("nav_debts"), tr("debts_sub"), tr("add_debt"), onAdd) }
        item {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                placeholder = { Text(tr("search_debt_ph")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (list.isEmpty()) item { EmptyText(tr("no_debts")) } else items(list, key = { it.id }) { TxRow(it, ledger) }
    }
}

@Composable
fun PaymentsScreen(ledger: Ledger, onAdd: () -> Unit) {
    val tr = LocalTr.current
    val list = ledger.tx.filter { it.kind == Kind.PAYMENT }.asReversed()
    ScreenList {
        item { ScreenHead(tr("nav_payments"), tr("payments_sub"), tr("add_payment"), onAdd, green = true) }
        if (list.isEmpty()) item { EmptyText(tr("no_payments")) } else items(list, key = { it.id }) { TxRow(it, ledger) }
    }
}

@Composable
fun HistoryScreen(ledger: Ledger) {
    val tr = LocalTr.current
    ScreenList {
        item { ScreenHead(tr("history_title"), tr("history_sub")) }
        if (ledger.tx.isEmpty()) item { EmptyText(tr("history_empty")) } else items(ledger.tx.asReversed(), key = { it.id }) { TxRow(it, ledger) }
    }
}

// ------------------------------------------------------------------------------------------ Reports

@Composable
fun ReportsScreen(ledger: Ledger, backup: BackupActions) {
    val tr = LocalTr.current
    ScreenList {
        item { ScreenHead(tr("nav_reports"), tr("reports_sub")) }
        item { StatCard(tr("total_debts"), formatTotals(ledger, tr.lang) { ledger.totalCents(Kind.DEBT, it) }, false, Modifier.fillMaxWidth()) }
        item { StatCard(tr("total_payments"), formatTotals(ledger, tr.lang) { ledger.totalCents(Kind.PAYMENT, it) }, true, Modifier.fillMaxWidth()) }
        item { StatCard(tr("remaining"), formatTotals(ledger, tr.lang) { ledger.remainingCents(it) }, false, Modifier.fillMaxWidth()) }
        item { Text(tr("cust_balances"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (ledger.people.isEmpty()) {
            item { EmptyText(tr("no_customers")) }
        } else {
            items(ledger.people, key = { it.id }) { p ->
                Card2 {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text(
                                if (p.phone.isBlank()) tr("no_phone") else p.phone,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(balanceSummary(ledger, p.id, tr.lang), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item { OutlinedButton(onClick = backup.export, modifier = Modifier.fillMaxWidth()) { Text(tr("export_backup")) } }
    }
}

// ------------------------------------------------------------------------------------------ Backup files (export / import)

class BackupActions(val export: () -> Unit, val import: () -> Unit, val pending: MutableState<Ledger?>)

@Composable
fun rememberBackupActions(app: DaftarApp): BackupActions {
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val pending = remember { mutableStateOf<Ledger?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val text = Codec.toJson(app.store.state.value, System.currentTimeMillis())
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                toast("t_exported")
            } catch (e: Exception) {
                toast("s_err", "e" to (e.message ?: e.toString()))
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                if (text == null) {
                    toast("t_bad_backup")
                } else {
                    pending.value = Codec.parse(text)
                }
            } catch (e: Exception) {
                toast("t_bad_backup")
            }
        }
    }

    return remember {
        BackupActions(
            export = { exportLauncher.launch("daftar-backup.json") },
            import = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            pending = pending,
        )
    }
}

/** Shows "replace this device's data with the imported file?" when a file has been picked. */
@Composable
fun BackupDialogHost(app: DaftarApp, backup: BackupActions) {
    val tr = LocalTr.current
    val toast = LocalToast.current
    val p = backup.pending.value
    if (p != null) {
        ConfirmDialog(
            message = tr("confirm_restore"),
            confirmLabel = tr("restore_btn"),
            destructive = false,
            onConfirm = {
                app.store.replaceAll(p)
                backup.pending.value = null
                toast("t_restored")
            },
            onDismiss = { backup.pending.value = null },
        )
    }
}

// ------------------------------------------------------------------------------------------ Settings

@Composable
fun SettingsScreen(app: DaftarApp, backup: BackupActions, onOpenSync: () -> Unit) {
    val tr = LocalTr.current
    val lang by app.prefs.lang.collectAsState()
    val theme by app.prefs.theme.collectAsState()
    ScreenList {
        item { ScreenHead(tr("nav_settings"), tr("settings_sub")) }
        item {
            Card2 {
                Text(tr("appearance"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(tr("language"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                ChoiceRow(listOf("ar" to "العربية", "en" to "English"), lang) { app.prefs.setLang(it) }
                Spacer(Modifier.height(12.dp))
                Text(tr("theme"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                ChoiceRow(listOf("system" to tr("system"), "light" to tr("light"), "dark" to tr("dark")), theme) { app.prefs.setTheme(it) }
            }
        }
        item {
            Card2 {
                Text(tr("data_saving"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(tr("data_saving_txt"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                Button(onClick = onOpenSync, modifier = Modifier.fillMaxWidth()) { Text(tr("open_sync")) }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = backup.export, modifier = Modifier.weight(1f)) { Text(tr("download_backup"), maxLines = 1) }
                    OutlinedButton(onClick = backup.import, modifier = Modifier.weight(1f)) { Text(tr("restore"), maxLines = 1) }
                }
            }
        }
        item {
            Card2 {
                Text(tr("wa_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(tr("wa_txt"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Card2 {
                Text(tr("about"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(tr("about_txt"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ Sync

@Composable
private fun ProviderCard(logo: String, name: String, desc: String, button: String, onClick: () -> Unit) {
    Card2 {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Text(logo, color = Color.White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Text(name, fontWeight = FontWeight.Bold)
        }
        Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(button) }
    }
}

@Composable
fun SyncScreen(app: DaftarApp, backup: BackupActions) {
    val tr = LocalTr.current
    val e = LocalExtra.current
    val cfg by app.prefs.sync.collectAsState()
    val ui by app.sync.ui.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var restoreFile by remember { mutableStateOf<DriveApi.FileInfo?>(null) }
    var deleteFile by remember { mutableStateOf<DriveApi.FileInfo?>(null) }
    var confirmDisconnect by remember { mutableStateOf(false) }

    LaunchedEffect(cfg.provider) {
        if (cfg.provider == "gdrive") app.sync.loadHistory()
    }

    val rf = restoreFile
    if (rf != null) {
        ConfirmDialog(
            message = tr("confirm_restore"),
            confirmLabel = tr("restore_btn"),
            destructive = false,
            onConfirm = {
                app.sync.restoreBackup(rf.id)
                restoreFile = null
            },
            onDismiss = { restoreFile = null },
        )
    }
    val df = deleteFile
    if (df != null) {
        ConfirmDialog(
            message = tr("confirm_del_backup"),
            confirmLabel = tr("del"),
            onConfirm = {
                app.sync.deleteBackup(df.id)
                deleteFile = null
            },
            onDismiss = { deleteFile = null },
        )
    }
    if (confirmDisconnect) {
        ConfirmDialog(
            message = tr("disconnect") + "?",
            confirmLabel = tr("disconnect"),
            onConfirm = {
                app.sync.disconnect()
                confirmDisconnect = false
            },
            onDismiss = { confirmDisconnect = false },
        )
    }

    val showPicker = cfg.provider == "none" || picking
    val cloud = cfg.provider == "gdrive"

    ScreenList {
        item { ScreenHead(tr("sync_title"), tr("sync_sub")) }

        if (showPicker) {
            item { Text(tr("choose_provider"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                ProviderCard("💾", tr("prov_local"), tr("prov_local_d"), tr("use_local")) {
                    app.sync.useLocal()
                    picking = false
                }
            }
            item {
                ProviderCard("G", "Google Drive", tr("prov_gdrive_d"), tr("connect")) {
                    app.sync.connectGoogle()
                    picking = false
                }
            }
            item { Text(tr("more_soon"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (cfg.provider != "none") {
                item { TextButton(onClick = { picking = false }) { Text(tr("cancel")) } }
            }
        } else {
            // ---- status card
            item {
                val dot = when (ui.state) {
                    SyncState.BUSY -> Color(0xFFE0A030)
                    SyncState.ERROR, SyncState.AUTH -> e.red
                    else -> e.green
                }
                val status = when (ui.state) {
                    SyncState.BUSY -> tr("syncing")
                    SyncState.AUTH -> tr("need_auth_short")
                    SyncState.ERROR -> tr("s_err", "e" to ui.message)
                    else -> if (cfg.last > 0L) tr("last_sync", "t" to fmtTime(cfg.last, tr.lang)) else tr("never")
                }
                Card2 {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(11.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(tr("connected_to", "p" to (if (cloud) "Google Drive" else tr("prov_local"))), fontWeight = FontWeight.Bold)
                            if (cloud) Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (cloud) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { app.sync.syncNow() }, enabled = ui.state != SyncState.BUSY, modifier = Modifier.weight(1f)) {
                                Text(tr("sync_now"), maxLines = 1)
                            }
                            OutlinedButton(onClick = { app.sync.backupNow() }, enabled = ui.state != SyncState.BUSY, modifier = Modifier.weight(1f)) {
                                Text(tr("backup_now"), maxLines = 1)
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = backup.export, modifier = Modifier.weight(1f)) { Text(tr("download_backup"), maxLines = 1) }
                            OutlinedButton(onClick = backup.import, modifier = Modifier.weight(1f)) { Text(tr("restore"), maxLines = 1) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { picking = true }) { Text(tr("change_provider")) }
                        TextButton(onClick = { confirmDisconnect = true }, colors = ButtonDefaults.textButtonColors(contentColor = e.red)) {
                            Text(tr("disconnect"))
                        }
                    }
                    if (cloud) {
                        Text(tr("sync_hint"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (cloud && ui.needAuth) {
                item {
                    Surface(color = e.warnBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(tr("need_auth"), color = e.warnInk, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { app.sync.syncNow() }) { Text(tr("sign_in_again")) }
                        }
                    }
                }
            }

            if (cloud) {
                // ---- schedule
                item {
                    Card2 {
                        Text(tr("schedule"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Text(tr("auto_mode"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        DropdownField(
                            options = listOf("live" to tr("am_live"), "open" to tr("am_open"), "interval" to tr("am_interval"), "manual" to tr("am_manual")),
                            selected = cfg.auto,
                            onSelect = { v -> app.prefs.updateSync { it.copy(auto = v) } },
                        )
                        if (cfg.auto == "interval") {
                            Spacer(Modifier.height(10.dp))
                            NumberField(tr("every_min"), cfg.every) { v -> app.prefs.updateSync { it.copy(every = v.coerceIn(1, 1440)) } }
                        }
                        Spacer(Modifier.height(10.dp))
                        NumberField(tr("keep_n"), cfg.keep) { v -> app.prefs.updateSync { it.copy(keep = v.coerceIn(1, 100)) } }
                        if (cfg.auto == "live") {
                            Text(tr("live_hint"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                        }
                        Text(tr("keep_hint"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }

                // ---- saved backups
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("history"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { app.sync.loadHistory() }) { Text(tr("refresh")) }
                    }
                }
                val h = ui.history
                if (h == null) {
                    item { EmptyText(tr("loading")) }
                } else if (h.isEmpty()) {
                    item { EmptyText(tr("no_backups")) }
                } else {
                    items(h, key = { it.id }) { f ->
                        Card2 {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(fmtTime(SyncManager.nameToMillis(f.name), tr.lang), fontWeight = FontWeight.Bold)
                                    Text(
                                        maxOf(1L, f.size / 1024L).toString() + " " + tr("kb"),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { restoreFile = f }) { Text(tr("restore_btn")) }
                                TextButton(onClick = { deleteFile = f }, colors = ButtonDefaults.textButtonColors(contentColor = e.red)) { Text(tr("del")) }
                            }
                        }
                    }
                }
            }
        }
    }
}
