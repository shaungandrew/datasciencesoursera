package com.aaii.yctamember

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.net.URLEncoder

/**
 * Shares first-party MOOC session cookies between native HTTP calls.
 * Uses Android's app-private cookie store; no WebView UI is required.
 */
class MoocSessionCookieJar : CookieJar {
    private val lock = Any()
    private val store: CookieManager by lazy {
        CookieManager.getInstance().apply { setAcceptCookie(true) }
    }

    fun setDeviceCookie(deviceId: String) {
        val encoded = URLEncoder.encode(deviceId, "UTF-8")
        synchronized(lock) {
            for (base in listOf(
                "https://aaii.asia/edu/mooc/",
                "https://www.aaii.asia/edu/mooc/"
            )) {
                store.setCookie(base, "device_id=$encoded; Path=/edu/mooc/; Secure; SameSite=Lax")
            }
            store.flush()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            cookies.forEach { cookie ->
                store.setCookie(url.toString(), cookie.toString())
            }
            store.flush()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val cookieHeader = store.getCookie(url.toString()).orEmpty()
            if (cookieHeader.isBlank()) return emptyList()
            return cookieHeader.split(";").mapNotNull {
                Cookie.parse(url, it.trim())
            }
        }
    }
}
