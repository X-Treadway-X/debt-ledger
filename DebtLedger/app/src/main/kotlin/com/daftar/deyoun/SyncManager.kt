package com.daftar.deyoun

import android.app.PendingIntent
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

enum class SyncState { IDLE, BUSY, OK, ERROR, AUTH }

data class SyncUi(
    val state: SyncState = SyncState.IDLE,
    val message: String = "",
    val needAuth: Boolean = false,
    val history: List<DriveApi.FileInfo>? = null,
)

/** A message for the snackbar: a translation key plus {placeholders}. */
data class Msg(val key: String, val args: List<Pair<String, String>> = emptyList())

/**
 * The sync engine. Same behaviour as the website:
 *  - "Sync" = download the newest cloud copy, merge it with this device, upload the result if it differs
 *  - "Back up" = upload this device's data as a new backup file
 *  - old backups beyond the "keep" setting are deleted
 *  - fully automatic mode checks the cloud every 30 s while the app is open and uploads a few seconds after each edit
 */
class SyncManager(
    ctx: Context,
    private val store: LedgerStore,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    val ui = MutableStateFlow(SyncUi())
    val events = MutableSharedFlow<Msg>(extraBufferCapacity = 16)

    /** the activity watches this and shows Google's consent screen when it is non-null */
    val consent = MutableStateFlow<PendingIntent?>(null)

    private val api = DriveApi()
    private val auth = GoogleAuth(ctx.applicationContext)
    private val busy = AtomicBoolean(false)
    private var debounceJob: Job? = null
    private var pendingAction: (() -> Unit)? = null

    companion object {
        private val NAME_RE = Regex("^daftar-\\d{8}T\\d{6}Z\\.json$")

        private fun stampFormat(): SimpleDateFormat =
            SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun newName(): String = "daftar-" + stampFormat().format(Date()) + ".json"

        fun nameToMillis(name: String): Long = try {
            stampFormat().parse(name.removePrefix("daftar-").removeSuffix(".json"))?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun backups(all: List<DriveApi.FileInfo>): List<DriveApi.FileInfo> =
        all.filter { NAME_RE.matches(it.name) }.sortedByDescending { it.name }

    private fun setUi(state: SyncState, message: String = "", needAuth: Boolean = false) {
        ui.value = ui.value.copy(state = state, message = message, needAuth = needAuth)
    }

    private fun say(key: String, vararg args: Pair<String, String>) {
        events.tryEmit(Msg(key, args.toList()))
    }

    private val cloud: Boolean get() = prefs.sync.value.provider == "gdrive"

    // ------------------------------------------------------------------ connect / disconnect

    fun connectGoogle() {
        scope.launch {
            try {
                when (val r = auth.authorize()) {
                    is GoogleAuth.Result.Token -> {
                        prefs.updateSync { it.copy(provider = "gdrive") }
                        runSync("sync", false)
                    }
                    is GoogleAuth.Result.NeedsConsent -> {
                        pendingAction = { connectGoogle() }
                        consent.value = r.intent
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("connect_fail", "e" to (e.message ?: e.toString()))
            }
        }
    }

    fun useLocal() {
        prefs.updateSync { it.copy(provider = "local") }
        ui.value = SyncUi()
    }

    fun disconnect() {
        debounceJob?.cancel()
        prefs.updateSync { it.copy(provider = "none", top = "", last = 0L) }
        ui.value = SyncUi()
    }

    /** called by the activity after Google's consent screen closes */
    fun onConsentResult(ok: Boolean) {
        val a = pendingAction
        pendingAction = null
        if (ok) a?.invoke()
    }

    // ------------------------------------------------------------------ the engine

    fun syncNow() {
        scope.launch { runSync("sync", false) }
    }

    fun backupNow() {
        scope.launch { runSync("backup", false) }
    }

    suspend fun runSync(mode: String, silent: Boolean) {
        if (!cloud) return
        if (!busy.compareAndSet(false, true)) return
        try {
            setUi(SyncState.BUSY)
            val r = try {
                auth.authorize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.dirty = true
                setUi(SyncState.ERROR, e.message ?: e.toString())
                if (!silent) say("s_err", "e" to (e.message ?: e.toString()))
                return
            }
            if (r is GoogleAuth.Result.NeedsConsent) {
                if (silent) {
                    setUi(SyncState.AUTH, needAuth = true)
                } else {
                    pendingAction = { scope.launch { runSync(mode, false) } }
                    consent.value = r.intent
                    setUi(SyncState.IDLE)
                }
                return
            }
            val token = (r as GoogleAuth.Result.Token).value

            store.dirty = false
            try {
                var files = backups(api.list(token))
                var remote: Ledger? = null
                var pulled = 0
                if (mode == "sync" && files.isNotEmpty()) {
                    remote = Codec.parse(api.read(token, files[0].id))
                    pulled = store.mergeFrom(remote)
                }
                val local = store.state.value
                var uploaded = false
                if (mode == "backup" || remote == null || !sameLedger(local, remote)) {
                    api.write(token, newName(), Codec.toJson(local, System.currentTimeMillis()))
                    uploaded = true
                    val keep = maxOf(1, prefs.sync.value.keep)
                    files = backups(api.list(token))
                    for (f in files.drop(keep)) api.delete(token, f.id)
                    files = files.take(keep)
                }
                val top = files.firstOrNull()?.name ?: ""
                prefs.updateSync { it.copy(last = System.currentTimeMillis(), top = top) }
                ui.value = SyncUi(SyncState.OK, "", false, files)
                if (pulled > 0) {
                    say("pulled", "n" to pulled.toString())
                } else if (!silent) {
                    say(if (mode == "backup") "backup_done" else if (uploaded) "sync_done" else "up_to_date")
                }
            } catch (e: CancellationException) {
                store.dirty = true
                throw e
            } catch (e: Exception) {
                store.dirty = true
                setUi(SyncState.ERROR, e.message ?: e.toString())
                if (!silent) say("s_err", "e" to (e.message ?: e.toString()))
            }
        } finally {
            busy.set(false)
        }
    }

    // ------------------------------------------------------------------ automatic modes

    /** called after every local edit */
    fun scheduleAuto() {
        val cfg = prefs.sync.value
        if (cfg.provider == "gdrive" && cfg.auto == "live") {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(3000)
                runSync("sync", true)
            }
        }
    }

    /** called when the app comes to the foreground */
    fun onForeground() {
        val cfg = prefs.sync.value
        if (cfg.provider == "gdrive" && cfg.auto != "manual") {
            scope.launch { runSync("sync", true) }
        }
    }

    /** called every 30 seconds while the app is on screen */
    fun tick() {
        scope.launch { tickNow() }
    }

    private suspend fun tickNow() {
        val cfg = prefs.sync.value
        if (cfg.provider != "gdrive" || busy.get()) return
        when (cfg.auto) {
            "live" -> {
                if (store.dirty) {
                    runSync("sync", true)
                    return
                }
                val r = try {
                    auth.authorize()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return
                }
                if (r is GoogleAuth.Result.NeedsConsent) {
                    setUi(SyncState.AUTH, needAuth = true)
                    return
                }
                if (ui.value.needAuth) setUi(SyncState.OK)
                val token = (r as GoogleAuth.Result.Token).value
                try {
                    val top = backups(api.list(token)).firstOrNull()?.name ?: ""
                    if (top != cfg.top) runSync("sync", true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // offline: try again at the next tick
                }
            }
            "interval" -> {
                if (System.currentTimeMillis() - cfg.last >= cfg.every.coerceAtLeast(1) * 60_000L) runSync("sync", true)
            }
            else -> {}
        }
    }

    // ------------------------------------------------------------------ backup list

    private suspend fun silentToken(): String? = try {
        (auth.authorize() as? GoogleAuth.Result.Token)?.value
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    fun loadHistory() {
        scope.launch {
            if (!cloud) return@launch
            ui.value = ui.value.copy(history = null)
            val token = silentToken()
            if (token == null) {
                ui.value = ui.value.copy(history = emptyList())
                return@launch
            }
            try {
                ui.value = ui.value.copy(history = backups(api.list(token)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.value = ui.value.copy(history = emptyList())
                say("s_err", "e" to (e.message ?: e.toString()))
            }
        }
    }

    fun restoreBackup(id: String) {
        scope.launch {
            val token = silentToken()
            if (token == null) {
                say("need_auth_short")
                return@launch
            }
            try {
                store.replaceAll(Codec.parse(api.read(token, id)))
                say("restored_ok")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("s_err", "e" to (e.message ?: e.toString()))
            }
        }
    }

    fun deleteBackup(id: String) {
        scope.launch {
            val token = silentToken()
            if (token == null) {
                say("need_auth_short")
                return@launch
            }
            try {
                api.delete(token, id)
                say("backup_deleted")
                loadHistory()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("s_err", "e" to (e.message ?: e.toString()))
            }
        }
    }
}
