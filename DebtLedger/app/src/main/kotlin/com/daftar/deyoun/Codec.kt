package com.daftar.deyoun

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Reads and writes the backup file. The format is identical to the web app's:
 *   { "app":"daftar", "v":2, "savedAt":..., "data": { "people":[], "tx":[], "del":[] } }
 * and the reader also accepts the older shape without the "data" wrapper.
 */
object Codec {
    private val CUR = setOf("USD", "TRY", "SYP")

    fun toJson(l: Ledger, savedAt: Long): String {
        val people = JSONArray()
        for (p in l.people) {
            people.put(JSONObject().put("id", p.id).put("name", p.name).put("phone", p.phone).put("u", p.u))
        }
        val tx = JSONArray()
        for (x in l.tx) {
            tx.put(
                JSONObject()
                    .put("id", x.id)
                    .put("person", x.person)
                    .put("kind", x.kind.json)
                    .put("amount", x.cents / 100.0)
                    .put("currency", x.currency)
                    .put("date", x.date)
                    .put("due", x.due)
                    .put("note", x.note)
                    .put("u", x.u),
            )
        }
        val del = JSONArray()
        for (d in l.del) del.put(JSONObject().put("id", d.id).put("u", d.u))
        val data = JSONObject().put("people", people).put("tx", tx).put("del", del)
        return JSONObject().put("app", "daftar").put("v", 2).put("savedAt", savedAt).put("data", data).toString()
    }

    fun parse(text: String): Ledger {
        val root = JSONObject(text)
        val d = root.optJSONObject("data") ?: root
        val pa = d.optJSONArray("people") ?: throw IllegalArgumentException("bad file")
        val ta = d.optJSONArray("tx") ?: throw IllegalArgumentException("bad file")
        val da = d.optJSONArray("del") ?: JSONArray()

        val people = ArrayList<Person>()
        for (i in 0 until pa.length()) {
            val o = pa.optJSONObject(i) ?: continue
            people.add(Person(o.optString("id", ""), o.optString("name", "").trim(), o.optString("phone", ""), o.optLong("u", 0L)))
        }
        val tx = ArrayList<Tx>()
        for (i in 0 until ta.length()) {
            val o = ta.optJSONObject(i) ?: continue
            val kind = when (o.optString("kind", "")) {
                "debt" -> Kind.DEBT
                "payment" -> Kind.PAYMENT
                else -> continue
            }
            val cur = o.optString("currency", "SYP").let { if (it in CUR) it else "SYP" }
            val cents = Math.round(Math.abs(o.optDouble("amount", 0.0)) * 100.0)
            val date = o.optString("date", "").ifBlank { LocalDate.now().toString() }
            tx.add(
                Tx(
                    id = o.optString("id", ""),
                    person = o.optString("person", ""),
                    kind = kind,
                    cents = cents,
                    currency = cur,
                    date = date,
                    due = o.optString("due", ""),
                    note = o.optString("note", ""),
                    u = o.optLong("u", 0L),
                ),
            )
        }
        val del = ArrayList<Tomb>()
        for (i in 0 until da.length()) {
            val o = da.optJSONObject(i) ?: continue
            del.add(Tomb(o.optString("id", ""), o.optLong("u", 0L)))
        }
        return sanitize(Ledger(people, tx, del))
    }
}
