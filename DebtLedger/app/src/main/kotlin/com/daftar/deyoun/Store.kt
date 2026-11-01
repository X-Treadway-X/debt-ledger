package com.daftar.deyoun

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.UUID

/** Holds the ledger, saves it to a file on every change, and tells the sync engine when the user edits something. */
class LedgerStore(private val file: File) {
    private val _state = MutableStateFlow(Ledger())
    val state: StateFlow<Ledger> = _state.asStateFlow()

    /** true when this device has changes the cloud has not seen yet */
    @Volatile
    var dirty: Boolean = false

    /** called after a user edit (not after a merge from the cloud) */
    var onEdit: (() -> Unit)? = null

    init {
        try {
            if (file.exists()) _state.value = Codec.parse(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            // never overwrite a file we could not read: keep a copy next to it
            try {
                file.copyTo(File(file.parentFile, "daftar.corrupt.json"), overwrite = true)
            } catch (_: Exception) {
            }
        }
    }

    private fun persist(l: Ledger) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(Codec.toJson(l, System.currentTimeMillis()), Charsets.UTF_8)
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // disk problems must not crash the app; the next change tries again
        }
    }

    @Synchronized
    private fun commit(l: Ledger, userEdit: Boolean) {
        _state.value = l
        persist(l)
        if (userEdit) dirty = true
    }

    private fun edit(f: (Ledger) -> Ledger) {
        synchronized(this) { commit(f(_state.value), true) }
        onEdit?.invoke()
    }

    private fun now() = System.currentTimeMillis()

    fun addPerson(name: String, phone: String): Person {
        val p = Person(UUID.randomUUID().toString(), name, phone, now())
        edit { it.copy(people = it.people + p) }
        return p
    }

    fun editPerson(id: String, name: String, phone: String) {
        edit { l -> l.copy(people = l.people.map { if (it.id == id) it.copy(name = name, phone = phone, u = now()) else it }) }
    }

    fun deletePerson(id: String) {
        edit { l ->
            val t = now()
            val gone = l.tx.filter { it.person == id }.map { Tomb(it.id, t) } + Tomb(id, t)
            l.copy(
                people = l.people.filter { it.id != id },
                tx = l.tx.filter { it.person != id },
                del = l.del + gone,
            )
        }
    }

    fun addTx(personId: String, kind: Kind, cents: Long, cur: String, due: String, note: String) {
        val x = Tx(
            id = UUID.randomUUID().toString(),
            person = personId,
            kind = kind,
            cents = cents,
            currency = cur,
            date = LocalDate.now().toString(),
            due = due,
            note = note,
            u = now(),
        )
        edit { it.copy(tx = it.tx + x) }
    }

    /** Restore a backup (replaces this device's data). Counts as a local change so it spreads to other devices. */
    fun replaceAll(snapshot: Ledger) {
        edit { cur -> replaceWith(cur, snapshot, now()) }
    }

    /** Merge what the cloud has into this device. Returns how many records changed. Does not mark the ledger dirty. */
    @Synchronized
    fun mergeFrom(remote: Ledger): Int {
        val cur = _state.value
        val merged = mergeLedgers(cur, remote, now())
        if (sameLedger(merged, cur)) return 0
        val n = diffCount(cur, merged)
        commit(merged, false)
        return n
    }
}
