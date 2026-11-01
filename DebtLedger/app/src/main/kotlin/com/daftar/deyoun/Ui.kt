package com.daftar.deyoun

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Shows a snackbar message from any screen:  toast("t_saved_cust")  or  toast("t_debt_saved", "n" to name) */
class Toaster(private val show: (String, List<Pair<String, String>>) -> Unit) {
    operator fun invoke(key: String, vararg args: Pair<String, String>) {
        show(key, args.toList())
    }
}

val LocalToast = staticCompositionLocalOf { Toaster { _, _ -> } }

fun fmtTime(ms: Long, lang: String): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.forLanguageTag(if (lang == "ar") "ar" else "en-US"))
        .format(Date(ms))

/** Opens WhatsApp with a ready message. Returns a translation key if it could not, or null on success. */
fun openWhatsApp(ctx: Context, phone: String, msg: String): String? {
    val digits = phone.filter { it.isDigit() }
    if (digits.isEmpty()) return "t_no_wa_phone"
    return try {
        val uri = Uri.parse("https://wa.me/" + digits + "?text=" + Uri.encode(msg))
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (e: Exception) {
        "t_no_wa_app"
    }
}

// ---------------------------------------------------------------------------------------- building blocks

@Composable
fun Card2(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun ScreenList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun ScreenHead(title: String, sub: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, green: Boolean = false) {
    val e = LocalExtra.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                colors = if (green) ButtonDefaults.buttonColors(containerColor = e.green, contentColor = Color.White) else ButtonDefaults.buttonColors(),
            ) { Text(actionLabel) }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, green: Boolean, modifier: Modifier = Modifier) {
    val e = LocalExtra.current
    Card2(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(
            value,
            color = if (green) e.green else MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun Badge2(text: String, good: Boolean) {
    val e = LocalExtra.current
    val c = if (good) e.green else e.red
    Surface(color = c.copy(alpha = 0.16f), shape = RoundedCornerShape(20.dp)) {
        Text(text, color = c, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
fun EmptyText(text: String) {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TxRow(x: Tx, ledger: Ledger) {
    val tr = LocalTr.current
    val e = LocalExtra.current
    val name = ledger.person(x.person)?.name ?: tr("customer")
    Card2 {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text((if (x.kind == Kind.DEBT) tr("debt_on") else tr("pay_from")) + " " + name, fontWeight = FontWeight.Bold)
                Text(
                    x.date + (if (x.note.isNotBlank()) " · " + x.note else ""),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                (if (x.kind == Kind.DEBT) "+ " else "− ") + fmtMoney(x.cents, x.currency, tr.lang),
                color = if (x.kind == Kind.DEBT) e.red else e.green,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun AppHeader(tr: Tr, syncState: SyncState, onSync: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    val e = LocalExtra.current
    val cloud = when (syncState) {
        SyncState.OK -> Color(0xFF7EE2B0)
        SyncState.ERROR -> Color(0xFFFF9B9B)
        SyncState.AUTH -> Color(0xFFFF9B9B)
        SyncState.BUSY -> Color(0xFFFFD27A)
        SyncState.IDLE -> Color.White
    }
    Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(e.head1, e.head2)))) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("brand"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(tr("tagline"), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            }
            IconButton(onClick = onSync) { Text("☁", color = cloud, fontSize = 22.sp) }
            IconButton(onClick = onHistory) { Text("◷", color = Color.White, fontSize = 20.sp) }
            IconButton(onClick = onSettings) { Text("⚙", color = Color.White, fontSize = 20.sp) }
        }
    }
}

@Composable
fun <T> DropdownField(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(options.firstOrNull { it.first == selected }?.second ?: "")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (o in options) {
                DropdownMenuItem(text = { Text(o.second) }, onClick = {
                    onSelect(o.first)
                    open = false
                })
            }
        }
    }
}

@Composable
fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            if (o.first == selected) {
                Button(onClick = { onSelect(o.first) }, modifier = Modifier.weight(1f)) { Text(o.second, maxLines = 1) }
            } else {
                OutlinedButton(onClick = { onSelect(o.first) }, modifier = Modifier.weight(1f)) { Text(o.second, maxLines = 1) }
            }
        }
    }
}

@Composable
fun NumberField(label: String, value: Int, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            val d = s.filter { it.isDigit() }.take(4)
            text = d
            val n = d.toIntOrNull()
            if (n != null) onValue(n)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
