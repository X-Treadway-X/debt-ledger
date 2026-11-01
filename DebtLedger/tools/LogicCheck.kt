// Stand-alone check of the pure logic (Model.kt + Merge.kt). Not part of the Android build.
// Run:  see tools/README.md  (needs only a JDK and a Kotlin compiler)
import com.daftar.deyoun.*

var fails = 0
fun check(cond: Boolean, msg: String) { println((if (cond) "PASS " else "FAIL ") + msg); if (!cond) fails++ }

fun P(id: String, u: Long, n: String = "x") = Person(id, n, "", u)
fun T(id: String, p: String, u: Long, k: Kind = Kind.DEBT, c: Long = 500, cur: String = "SYP") =
    Tx(id, p, k, c, cur, "2026-01-01", "", "", u)

fun main() {
    // --- same scenario as the web app's merge test ---
    val a = Ledger(listOf(P("a", 1), P("b", 1)), listOf(T("t1", "a", 1), T("t2", "b", 1)))
    val b = Ledger(
        listOf(P("a", 5, "edited"), P("c", 2)), listOf(T("t1", "a", 1), T("t3", "c", 2)),
        listOf(Tomb("b", 9), Tomb("t2", 9)),
    )
    val m = mergeLedgers(a, b, 10)
    check(m.people.map { it.id + ":" + it.name }.sorted() == listOf("a:edited", "c:x"), "merge: newest edit wins, add kept, delete wins")
    check(m.tx.map { it.id }.sorted() == listOf("t1", "t3"), "merge: tx of deleted customer removed, new tx kept")
    check(sameLedger(mergeLedgers(a, b, 10), mergeLedgers(b, a, 10)), "merge is order independent")
    check(sameLedger(mergeLedgers(m, m, 10), m), "merge is idempotent")

    // tombstone older than a re-edit does not kill the record
    val re = mergeLedgers(Ledger(listOf(P("z", 20))), Ledger(emptyList(), emptyList(), listOf(Tomb("z", 10))), 30)
    check(re.people.size == 1, "newer edit survives an older tombstone")

    // old tombstones are pruned after 180 days
    val old = mergeLedgers(Ledger(), Ledger(del = listOf(Tomb("q", 1))), 1L + 181L * 86_400_000L)
    check(old.del.isEmpty(), "tombstones older than 180 days are dropped")

    // diff count
    check(diffCount(a, m) >= 2, "diffCount counts changed/added/removed records: " + diffCount(a, m))
    check(diffCount(m, m) == 0, "diffCount is 0 for identical ledgers")

    // restore
    val snap = Ledger(listOf(P("a", 1)), listOf(T("t1", "a", 1)))
    val cur = Ledger(listOf(P("a", 1), P("n", 1)), listOf(T("t1", "a", 1), T("t9", "n", 1)))
    val r = replaceWith(cur, snap, 100)
    check(r.people.map { it.id } == listOf("a") && r.del.any { it.id == "n" } && r.del.any { it.id == "t9" }, "restore tombstones what the backup lacks")
    // an edit made elsewhere AFTER the restore must not be erased by those tombstones
    val later = mergeLedgers(r, Ledger(listOf(P("n", 200)), listOf(T("t9", "n", 200))), 300)
    check(later.people.any { it.id == "n" }, "records edited after a restore come back")

    // sanitize
    val dirty = Ledger(listOf(P("a", 1), P("", 1), Person("b", "  ", "", 1)), listOf(T("t1", "a", 1), T("t2", "ghost", 1), T("t3", "a", 1, c = 0)))
    val s = sanitize(dirty)
    check(s.people.size == 1 && s.tx.size == 1, "sanitize drops blank names, orphans and zero amounts")

    // balances
    val l = Ledger(listOf(P("a", 1)), listOf(T("d", "a", 1, c = 1000), T("p", "a", 2, Kind.PAYMENT, 300), T("e", "a", 3, c = 700, cur = "USD")))
    check(l.balanceCents("a", "SYP") == 700L && l.balanceCents("a", "USD") == 700L, "per-currency balance")
    check(l.remainingCents("SYP") == 700L && l.hasBalance("a"), "remaining and hasBalance")
    check(l.currenciesInUse() == listOf("USD", "SYP"), "currencies in use ordered USD,TRY,SYP")

    // amounts
    check(parseCents("12.5") == 1250L, "parse 12.5")
    check(parseCents("١٢٫٥٠") == 1250L, "parse Arabic digits")
    check(parseCents("12,5") == 1250L, "parse comma decimal")
    check(parseCents("0") == null && parseCents("-5") == null && parseCents("abc") == null && parseCents("") == null, "reject zero, negative, junk, empty")
    check(parseCents("0.005") == 1L, "rounds half up to a cent")
    println(fmtMoney(123456, "USD", "en") + " | " + fmtMoney(123456, "SYP", "ar") + " | " + balanceSummary(l, "a", "en"))
    println(if (fails == 0) "ALL PASSED" else "FAILED $fails")
    if (fails != 0) System.exit(1)
}
