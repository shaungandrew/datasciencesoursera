package com.aaii.kilotaxi.driver

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiClient(private val base:()->String){
    private val c=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).build()
    fun get(action:String,params:Map<String,String> = emptyMap(),token:String=""):JSONObject{
        val q=buildString{append("?action=").append(e(action));params.forEach{(k,v)->append("&").append(e(k)).append("=").append(e(v))}}
        val b=Request.Builder().url(base().trimEnd('/')+q).get().header("Accept","application/json");if(token.isNotBlank())b.header("Authorization","Bearer $token")
        c.newCall(b.build()).execute().use{return JSONObject(it.body?.string().orEmpty().ifBlank{"{\"ok\":false,\"error\":\"EMPTY_RESPONSE\"}"})}
    }
    fun post(action:String,d:JSONObject=JSONObject(),token:String=""):JSONObject{
        val b=Request.Builder().url(base().trimEnd('/')+"?action="+e(action)).post(d.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).header("Accept","application/json");if(token.isNotBlank())b.header("Authorization","Bearer $token")
        c.newCall(b.build()).execute().use{return JSONObject(it.body?.string().orEmpty().ifBlank{"{\"ok\":false,\"error\":\"EMPTY_RESPONSE\"}"})}
    }
    private fun e(v:String)=URLEncoder.encode(v,"UTF-8")
}
