package com.daftar.deyoun

/**
 * Merge engine. This is a line-for-line port of the web app's mergeDB / replaceWith / sanitize,
 * so the Android app and the website can read and write the same backup file.
 *
 *  - every record has a unique id and a last-edited time `u`
 *  - adds from either side are kept, the most recently edited version wins
 *  - deletions are remembered as tombstones (kept for 180 days) so they cannot come back
 */

private const val TOMB_KEEP_MS = 180L * 86_400_000L

fun sanitize(l: Ledger): Ledger {
    val people = l.people.filter { it.id.isNotBlank() && it.name.isNotBlank() }.distinctBy { it.id }
    val ids = people.map { it.id }.toHashSet()
    val tx = l.tx.filter { it.id.isNotBlank() && it.person in ids && it.cents > 0L }.distinctBy { it.id }
    val del = l.del.filter { it.id.isNotBlank() }
    return Ledger(people, tx, del)
}

private fun <T> pickNewest(
    list: List<T>,
    tomb: Map<String, Long>,
    id: (T) -> String,
    u: (T) -> Long,
): List<T> {
    val m = LinkedHashMap<String, T>()
    for (r in list) {
        val old = m[id(r)]
        if (old == null || u(r) > u(old)) m[id(r)] = r
    }
    return m.values.filter { r ->
        val t = tomb[id(r)]
        !(t != null && t >= u(r))
    }
}

fun mergeLedgers(a: Ledger, b: Ledger, now: Long): Ledger {
    val tomb = HashMap<String, Long>()
    for (d in a.del + b.del) {
        val old = tomb[d.id]
        if (old == null || old < d.u) tomb[d.id] = d.u
    }
    val people = pickNewest(a.people + b.people, tomb, { it.id }, { it.u })
    val ids = people.map { it.id }.toHashSet()
    val tx = pickNewest(a.tx + b.tx, tomb, { it.id }, { it.u }).filter { it.person in ids }
    val cut = now - TOMB_KEEP_MS
    val del = tomb.map { Tomb(it.key, it.value) }.filter { it.u > cut }
    return Ledger(people, tx, del)
}

/** Order-independent form used to ask "are these two ledgers the same?". */
fun canon(l: Ledger): Ledger =
    Ledger(l.people.sortedBy { it.id }, l.tx.sortedBy { it.id }, l.del.sortedBy { it.id })

fun sameLedger(a: Ledger, b: Ledger): Boolean = canon(a) == canon(b)

/** How many records differ between two ledgers (used for the "received N changes" message). */
fun diffCount(a: Ledger, b: Ledger): Int {
    fun key(l: Ledger): Map<String, Long> = (l.people.map { it.id to it.u } + l.tx.map { it.id to it.u }).toMap()
    val ka = key(a)
    val kb = key(b)
    var n = 0
    for ((id, u) in kb) if (ka[id] == null || ka[id] != u) n++
    for (id in ka.keys) if (id !in kb) n++
    return n
}

/** Restoring a backup: replace this device's data, and tombstone whatever the backup does not contain. */
fun replaceWith(cur: Ledger, snap: Ledger, now: Long): Ledger {
    val kp = snap.people.map { it.id }.toHashSet()
    val kt = snap.tx.map { it.id }.toHashSet()
    val del = ArrayList<Tomb>(snap.del)
    del.addAll(cur.del)
    for (p in cur.people) if (p.id !in kp) del.add(Tomb(p.id, now))
    for (x in cur.tx) if (x.id !in kt) del.add(Tomb(x.id, now))
    return Ledger(
        snap.people.map { it.copy(u = now) },
        snap.tx.map { it.copy(u = now) },
        del.filter { it.id !in kp && it.id !in kt },
    )
}
