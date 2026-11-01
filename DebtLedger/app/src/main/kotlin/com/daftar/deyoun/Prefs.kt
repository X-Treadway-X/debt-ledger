package com.daftar.deyoun

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** provider: "none" | "local" | "gdrive"      auto: "live" | "open" | "interval" | "manual" */
data class SyncCfg(
    val provider: String = "none",
    val auto: String = "live",
    val every: Int = 15,
    val keep: Int = 10,
    val last: Long = 0L,
    val top: String = "",
)

/** Small settings store (language, theme, sync options) backed by SharedPreferences. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("daftar", Context.MODE_PRIVATE)

    val lang = MutableStateFlow(sp.getString("lang", null) ?: "ar")
    val theme = MutableStateFlow(sp.getString("theme", null) ?: "system")
    val sync = MutableStateFlow(
        SyncCfg(
            provider = sp.getString("s_provider", null) ?: "none",
            auto = sp.getString("s_auto", null) ?: "live",
            every = sp.getInt("s_every", 15),
            keep = sp.getInt("s_keep", 10),
            last = sp.getLong("s_last", 0L),
            top = sp.getString("s_top", null) ?: "",
        ),
    )

    fun setLang(v: String) {
        lang.value = v
        sp.edit().putString("lang", v).apply()
    }

    fun setTheme(v: String) {
        theme.value = v
        sp.edit().putString("theme", v).apply()
    }

    fun updateSync(f: (SyncCfg) -> SyncCfg) {
        val n = f(sync.value)
        sync.value = n
        sp.edit()
            .putString("s_provider", n.provider)
            .putString("s_auto", n.auto)
            .putInt("s_every", n.every)
            .putInt("s_keep", n.keep)
            .putLong("s_last", n.last)
            .putString("s_top", n.top)
            .apply()
    }
}
