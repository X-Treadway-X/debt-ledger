package com.daftar.deyoun

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Holds the app-wide singletons so the data and the sync engine outlive any single screen. */
class DaftarApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var store: LedgerStore
        private set
    lateinit var sync: SyncManager
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        store = LedgerStore(File(filesDir, "daftar.json"))
        sync = SyncManager(this, store, prefs, scope)
        store.onEdit = { sync.scheduleAuto() }
    }
}
