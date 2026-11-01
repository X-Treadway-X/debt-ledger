package com.daftar.deyoun

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal Google Drive REST client. It only touches the hidden "appDataFolder": the app cannot see
 * any of the user's other Drive files (scope drive.appdata).
 */
class DriveApi {
    data class FileInfo(val id: String, val name: String, val size: Long)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    private fun fail(code: Int, body: String): Nothing = throw IOException("HTTP $code ${body.take(160)}")

    suspend fun list(token: String): List<FileInfo> = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("pageSize", "1000")
            .addQueryParameter("fields", "files(id,name,size)")
            .build()
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Cache-Control", "no-cache").build()
        client.newCall(req).execute().use { r ->
            val body = r.body?.string() ?: ""
            if (!r.isSuccessful) fail(r.code, body)
            val arr = JSONObject(body).optJSONArray("files")
            val out = ArrayList<FileInfo>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(FileInfo(o.getString("id"), o.optString("name", ""), o.optString("size", "0").toLongOrNull() ?: 0L))
                }
            }
            out
        }
    }

    suspend fun read(token: String, id: String): String = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$id".toHttpUrl().newBuilder().addQueryParameter("alt", "media").build()
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Cache-Control", "no-cache").build()
        client.newCall(req).execute().use { r ->
            val body = r.body?.string() ?: ""
            if (!r.isSuccessful) fail(r.code, body)
            body
        }
    }

    suspend fun write(token: String, name: String, text: String): Unit = withContext(Dispatchers.IO) {
        val boundary = "daftar" + System.nanoTime()
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder")).put("mimeType", "application/json").toString()
        val payload = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$boundary\r\nContent-Type: application/json\r\n\r\n$text\r\n--$boundary--"
        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()
        client.newCall(req).execute().use { r ->
            val body = r.body?.string() ?: ""
            if (!r.isSuccessful) fail(r.code, body)
        }
    }

    suspend fun delete(token: String, id: String): Unit = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$id")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful && r.code != 404) fail(r.code, r.body?.string() ?: "")
        }
    }
}
