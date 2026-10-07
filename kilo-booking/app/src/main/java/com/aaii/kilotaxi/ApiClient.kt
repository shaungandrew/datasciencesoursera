package com.aaii.kilotaxi

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiClient(private val baseUrl: () -> String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun post(action: String, data: JSONObject = JSONObject(), token: String = ""): JSONObject {
        val url = baseUrl().trimEnd('/') + "?action=" + enc(action)
        val body = data.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val b = Request.Builder().url(url).post(body).header("Accept","application/json")
        if (token.isNotBlank()) b.header("Authorization","Bearer $token")
        client.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (text.isBlank()) return JSONObject().put("ok", false).put("error", "EMPTY_RESPONSE")
            return JSONObject(text)
        }
    }

    fun get(action: String, params: Map<String,String> = emptyMap(), token: String = ""): JSONObject {
        val tail = buildString {
            append("?action=").append(enc(action))
            params.forEach { (k,v) -> append("&").append(enc(k)).append("=").append(enc(v)) }
        }
        val b = Request.Builder().url(baseUrl().trimEnd('/') + tail).get().header("Accept","application/json")
        if (token.isNotBlank()) b.header("Authorization","Bearer $token")
        client.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (text.isBlank()) return JSONObject().put("ok", false).put("error", "EMPTY_RESPONSE")
            return JSONObject(text)
        }
    }

    private fun enc(v:String)=URLEncoder.encode(v,"UTF-8")
}
