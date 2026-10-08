package com.aaii.kilotaxi.common

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class CommApi(private val baseUrl: String, private val token: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun get(action: String, params: Map<String,String> = emptyMap()): JSONObject {
        val tail = buildString {
            append("?action=").append(enc(action))
            params.forEach { (k,v) -> append("&").append(enc(k)).append("=").append(enc(v)) }
        }
        val b = Request.Builder().url(baseUrl.trimEnd('/') + tail).get().header("Accept","application/json")
        if (token.isNotBlank()) b.header("Authorization","Bearer $token")
        client.newCall(b.build()).execute().use { r ->
            val t = r.body?.string().orEmpty()
            return JSONObject(t.ifBlank { "{\"ok\":false,\"error\":\"EMPTY_RESPONSE\"}" })
        }
    }

    fun post(action: String, data: JSONObject = JSONObject()): JSONObject {
        val body = data.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val b = Request.Builder().url(baseUrl.trimEnd('/') + "?action=" + enc(action))
            .post(body).header("Accept","application/json")
        if (token.isNotBlank()) b.header("Authorization","Bearer $token")
        client.newCall(b.build()).execute().use { r ->
            val t = r.body?.string().orEmpty()
            return JSONObject(t.ifBlank { "{\"ok\":false,\"error\":\"EMPTY_RESPONSE\"}" })
        }
    }

    private fun enc(v:String)=URLEncoder.encode(v,"UTF-8")
}
