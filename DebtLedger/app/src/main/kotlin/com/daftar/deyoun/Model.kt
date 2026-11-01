package com.daftar.deyoun

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Pure data + money helpers. No Android imports so this file can be unit-checked on a plain JVM. */

enum class Kind(val json: String) { DEBT("debt"), PAYMENT("payment") }

data class Person(val id: String, val name: String, val phone: String, val u: Long)

data class Tx(
    val id: String,
    val person: String,
    val kind: Kind,
    val cents: Long,
    val currency: String,
    val date: String,
    val due: String,
    val note: String,
    val u: Long,
)

/** A deletion marker ("tombstone") so a deleted record does not come back from another device. */
data class Tomb(val id: String, val u: Long)

data class Ledger(
    val people: List<Person> = emptyList(),
    val tx: List<Tx> = emptyList(),
    val del: List<Tomb> = emptyList(),
)

val CURRENCIES: List<String> = listOf("USD", "TRY", "SYP")

fun Ledger.person(id: String): Person? = people.firstOrNull { it.id == id }

fun Ledger.txFor(personId: String): List<Tx> = tx.filter { it.person == personId }

fun Ledger.balanceCents(personId: String, cur: String): Long =
    tx.filter { it.person == personId && it.currency == cur }
        .fold(0L) { acc, x -> if (x.kind == Kind.DEBT) acc + x.cents else acc - x.cents }

fun Ledger.hasBalance(personId: String): Boolean = CURRENCIES.any { balanceCents(personId, it) > 0L }

fun Ledger.totalCents(kind: Kind, cur: String): Long =
    tx.filter { it.kind == kind && it.currency == cur }.fold(0L) { acc, x -> acc + x.cents }

fun Ledger.remainingCents(cur: String): Long =
    people.fold(0L) { acc, p -> acc + maxOf(0L, balanceCents(p.id, cur)) }

fun Ledger.currenciesInUse(): List<String> = CURRENCIES.filter { c -> tx.any { it.currency == c } }

fun symbol(cur: String, lang: String): String = when (cur) {
    "USD" -> "\$"
    "TRY" -> "₺"
    else -> if (lang == "ar") "ل.س" else "SYP"
}

fun fmtMoney(cents: Long, cur: String, lang: String): String {
    val nf = NumberFormat.getNumberInstance(Locale.forLanguageTag(if (lang == "ar") "ar" else "en-US"))
    nf.minimumFractionDigits = 2
    nf.maximumFractionDigits = 2
    return nf.format(cents / 100.0) + " " + symbol(cur, lang)
}

/** Sum of a per-currency value for every currency that has activity, e.g. "12.00 $ · 5.00 ل.س". */
fun formatTotals(l: Ledger, lang: String, getter: (String) -> Long): String {
    val parts = l.currenciesInUse().map { fmtMoney(getter(it), it, lang) }
    return if (parts.isEmpty()) fmtMoney(0L, "SYP", lang) else parts.joinToString(" · ")
}

fun balanceSummary(l: Ledger, personId: String, lang: String): String {
    val used = CURRENCIES.filter { c -> l.tx.any { it.person == personId && it.currency == c } }
    if (used.isEmpty()) return fmtMoney(0L, "SYP", lang)
    return used.joinToString(" · ") { fmtMoney(maxOf(0L, l.balanceCents(personId, it)), it, lang) }
}

fun paymentsSummary(l: Ledger, personId: String, lang: String): String {
    val used = CURRENCIES.filter { c -> l.tx.any { it.person == personId && it.kind == Kind.PAYMENT && it.currency == c } }
    if (used.isEmpty()) return fmtMoney(0L, "SYP", lang)
    return used.joinToString(" · ") { c ->
        fmtMoney(l.tx.filter { it.person == personId && it.kind == Kind.PAYMENT && it.currency == c }.fold(0L) { a, x -> a + x.cents }, c, lang)
    }
}

/** Parses what a person typed into a positive amount in cents. Accepts Arabic-Indic digits and ٫ or , as the decimal mark. */
fun parseCents(input: String): Long? {
    val s = input.trim().map { ch ->
        when (ch) {
            in '٠'..'٩' -> '0' + (ch - '٠')
            '٫', ',' -> '.'
            else -> ch
        }
    }.joinToString("")
    if (s.isEmpty()) return null
    return try {
        val c = BigDecimal(s).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        if (c > 0L) c else null
    } catch (e: Exception) {
        null
    }
}
