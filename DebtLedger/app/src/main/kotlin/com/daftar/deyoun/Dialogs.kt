package com.daftar.deyoun

import android.app.Activity
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent

// ------------------------------------------------------------------------------------------ add / edit customer

@Composable
fun PersonDialog(existing: Person?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val tr = LocalTr.current
    val toast = LocalToast.current
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }

    // Picking one phone number from the contacts app needs no permission: Android grants read access to just that row.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        if (r.resultCode == Activity.RESULT_OK && uri != null) {
            try {
                val cols = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
                ctx.contentResolver.query(uri, cols, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getString(0)
                        val p = c.getString(1)
                        if (!n.isNullOrBlank()) name = n
                        if (!p.isNullOrBlank()) phone = p
                    }
                }
            } catch (e: Exception) {
                toast("t_contact_fail")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (existing == null) "add_cust_title" else "edit_title")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("cust_name")) },
                    placeholder = { Text(tr("full_name_ph")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(tr("phone_cc")) },
                    placeholder = { Text(tr("phone_ph")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (existing == null) {
                    OutlinedButton(
                        onClick = {
                            try {
                                picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                            } catch (e: Exception) {
                                toast("t_contact_fail")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("pick_contact")) }
                    Text(tr("contact_hint"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim(), phone.trim()) }) { Text(tr(if (existing == null) "save_customer" else "save_edit")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("cancel")) } },
    )
}

// ------------------------------------------------------------------------------------------ add debt / record payment

@Composable
fun TxDialog(
    kind: Kind,
    ledger: Ledger,
    initialPersonId: String?,
    onDismiss: () -> Unit,
    onSave: (personId: String?, amount: String, cur: String, due: String, note: String, wa: Boolean) -> Unit,
) {
    val tr = LocalTr.current
    val e = LocalExtra.current
    var query by remember { mutableStateOf(initialPersonId?.let { ledger.person(it)?.name } ?: "") }
    var chosen by remember { mutableStateOf(initialPersonId) }
    var amount by remember { mutableStateOf("") }
    var cur by remember { mutableStateOf("SYP") }
    var due by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var wa by remember { mutableStateOf(true) }
    val debt = kind == Kind.DEBT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (debt) "new_debt_title" else "pay_title")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        chosen = null
                    },
                    label = { Text(tr("search_name")) },
                    placeholder = { Text(tr("type_name_ph")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val cid = chosen
                val q = query.trim().lowercase()
                if (cid != null) {
                    Text(tr("selected") + (ledger.person(cid)?.name ?: ""), color = e.green, fontSize = 12.sp)
                } else if (q.isEmpty()) {
                    Text(tr("type_to_search"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val matches = ledger.people.filter { it.name.lowercase().contains(q) }.take(5)
                    if (matches.isEmpty()) {
                        Text(tr("no_match"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column {
                            for (p in matches) {
                                Text(
                                    p.name + (if (p.phone.isNotBlank()) "  ·  " + p.phone else ""),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            chosen = p.id
                                            query = p.name
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(tr("amount")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(tr("currency"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceRow(
                    options = listOf("USD" to "USD \$", "TRY" to "TRY ₺", "SYP" to "SYP"),
                    selected = cur,
                    onSelect = { cur = it },
                )
                if (debt) {
                    OutlinedTextField(
                        value = due,
                        onValueChange = { due = it },
                        label = { Text(tr("due")) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(tr("notes")) },
                    placeholder = { Text(tr("optional")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wa, onCheckedChange = { wa = it })
                    Text(tr(if (debt) "wa_debt" else "wa_pay"), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(chosen, amount, cur, due, note, wa) },
                colors = if (debt) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = e.green, contentColor = Color.White),
            ) { Text(tr("save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("cancel")) } },
    )
}

// ------------------------------------------------------------------------------------------ customer profile

@Composable
fun DetailsDialog(
    ledger: Ledger,
    personId: String,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDebt: () -> Unit,
    onPay: () -> Unit,
) {
    val tr = LocalTr.current
    val p = ledger.person(personId) ?: return
    val good = !ledger.hasBalance(p.id)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ScreenList {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (p.phone.isBlank()) tr("no_phone_reg") else p.phone,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Badge2(tr(if (good) "settled" else "has_balance"), good)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(tr("remaining"), balanceSummary(ledger, p.id, tr.lang), false, Modifier.weight(1f))
                        StatCard(tr("total_payments"), paymentsSummary(ledger, p.id, tr.lang), true, Modifier.weight(1f))
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text(tr("edit_cust_data")) }
                        Button(onClick = onDebt, modifier = Modifier.fillMaxWidth()) { Text(tr("add_debt_for")) }
                        FilledTonalButton(onClick = onPay, modifier = Modifier.fillMaxWidth()) { Text(tr("pay_for")) }
                    }
                }
                item { Text(tr("acct_move", "n" to p.name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                val list = ledger.txFor(p.id).asReversed()
                if (list.isEmpty()) {
                    item { EmptyText(tr("no_tx_cust")) }
                } else {
                    items(list, key = { it.id }) { TxRow(it, ledger) }
                }
                item { OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(tr("close")) } }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ confirm

@Composable
fun ConfirmDialog(message: String, confirmLabel: String, destructive: Boolean = true, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val tr = LocalTr.current
    val e = LocalExtra.current
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = e.red, contentColor = Color.White) else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("cancel")) } },
    )
}
