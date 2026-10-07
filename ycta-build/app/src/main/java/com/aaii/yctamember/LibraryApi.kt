package com.aaii.yctamember

import android.content.Context
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class LibraryApi(private val context: Context) {
    companion object {
        const val WEB_BASE = "https://www.aaii.asia/edu/lib/"
        const val API_BASE = "https://www.aaii.asia/edu/lib/api/"
        private const val PREFS = "ycta_library_module"
        private const val TOKEN_KEY = "api_token"
    }

    class AuthException(message: String) : Exception(message)

    data class Book(
        val id: String,
        val title: String,
        val author: String = "",
        val category: String = "",
        val description: String = "",
        val fileType: String = "",
        val coverUrl: String = "",
        val downloadUrl: String = "",
        val rawUrl: String = ""
    )

    data class NamedItem(val id: String, val name: String, val count: String = "")
    data class MediaItem(val id: String, val title: String, val url: String, val type: String = "")

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun hasToken(): Boolean = token().isNotBlank()
    fun clearToken() = prefs.edit().remove(TOKEN_KEY).apply()
    private fun token(): String = prefs.getString(TOKEN_KEY, "").orEmpty()

    fun login(activation: String, username: String, password: String): String {
        val body = FormBody.Builder().apply {
            if (activation.isNotBlank()) add("activation_code", activation)
            if (username.isNotBlank()) add("username", username)
            if (password.isNotBlank()) add("password", password)
        }.build()
        val request = Request.Builder()
            .url(API_BASE + "login.php")
            .post(body)
            .header("Accept", "application/json")
            .build()
        val text = execute(request)
        val json = JSONObject(text)
        val found = findString(json, listOf("token", "api_token", "access_token"))
        if (found.isBlank()) {
            val msg = findString(json, listOf("message", "error"))
            error(if (msg.isBlank()) "Login succeeded but token was not returned." else msg)
        }
        prefs.edit().putString(TOKEN_KEY, found).apply()
        return found
    }

    fun fetchBooks(
        library: String,
        query: String = "",
        categoryId: String = "",
        authorId: String = ""
    ): List<Book> {
        val params = linkedMapOf("library" to library)
        if (query.isNotBlank()) params["q"] = query
        if (categoryId.isNotBlank()) params["category_id"] = categoryId
        if (authorId.isNotBlank()) params["author_id"] = authorId
        val json = getJson("library.php", params)
        val array = findArray(json, listOf("books", "items", "results", "data"))
        return parseBooks(array)
    }

    fun fetchCategories(library: String): List<NamedItem> {
        val json = getJson("categories.php", mapOf("library" to library))
        val array = findArray(json, listOf("categories", "items", "results", "data"))
        return parseNamed(array)
    }

    fun fetchAuthors(library: String): List<NamedItem> {
        val json = getJson("authors.php", mapOf("library" to library))
        val array = findArray(json, listOf("authors", "writers", "items", "results", "data"))
        return parseNamed(array)
    }

    fun fetchAudio(library: String): List<MediaItem> {
        val json = getJson("media_channels.php", mapOf("library" to library, "type" to "audio_books"))
        val array = findArray(json, listOf("audio_books", "channels", "media", "items", "results", "data"))
        val out = mutableListOf<MediaItem>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = first(o, "id", "channel_id", "media_id", "book_id")
            val title = first(o, "title", "name", "book_title")
            val url = first(o, "url", "link", "source_url", "audio_url", "youtube_url")
            val type = first(o, "type", "source_type", "media_type")
            if (title.isNotBlank() || url.isNotBlank()) out += MediaItem(id, title.ifBlank { "Audio Book" }, url, type)
        }
        return out
    }

    fun coverUrl(bookId: String): String = API_BASE + "book_cover.php?id=" + bookId

    fun downloadBook(book: Book): Pair<File, String> {
        val url = if (book.downloadUrl.startsWith("http")) book.downloadUrl else API_BASE + "book_download.php?id=" + book.id
        val request = requestBuilder(url).get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) throw AuthException("Library login required.")
            if (!response.isSuccessful) error("Book server returned HTTP ${response.code}")
            val body = response.body ?: error("Empty book file")
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            val disposition = response.header("Content-Disposition").orEmpty().lowercase()
            val hint = (book.fileType + " " + book.downloadUrl + " " + contentType + " " + disposition).lowercase()
            val ext = when {
                hint.contains("epub") -> "epub"
                hint.contains("pdf") -> "pdf"
                else -> "bin"
            }
            val dir = File(context.cacheDir, "library_books").apply { mkdirs() }
            val file = File(dir, "book_${book.id.ifBlank { System.currentTimeMillis().toString() }}.$ext")
            body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            return file to ext
        }
    }

    fun fetchImage(url: String): ByteArray? {
        if (url.isBlank()) return null
        return runCatching {
            val req = requestBuilder(url).get().header("Referer", WEB_BASE).build()
            client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
        }.getOrNull()
    }

    private fun getJson(endpoint: String, params: Map<String, String>): JSONObject {
        val b = (API_BASE + endpoint).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        val request = requestBuilder(b.build().toString()).get().build()
        val text = execute(request)
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("[") -> JSONObject().put("data", JSONArray(trimmed))
            trimmed.startsWith("{") -> JSONObject(trimmed)
            else -> error("Invalid server response")
        }
    }

    private fun requestBuilder(url: String): Request.Builder {
        val b = Request.Builder()
            .url(url)
            .header("Accept", "application/json,*/*")
            .header("User-Agent", "YCTA-Library-Native/5.0 Android")
        val t = token()
        if (t.isNotBlank()) b.header("X-API-Token", t)
        return b
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403 || text.contains("LOGIN_REQUIRED", true) || text.contains("Invalid token", true)) {
                throw AuthException("Library login required.")
            }
            if (!response.isSuccessful) error("Server HTTP ${response.code}")
            return text
        }
    }

    private fun parseBooks(array: JSONArray): List<Book> {
        val out = mutableListOf<Book>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = first(o, "id", "book_id", "ID")
            val title = first(o, "title", "book_title", "name")
            if (title.isBlank() && id.isBlank()) continue
            out += Book(
                id = id,
                title = title.ifBlank { "Untitled Book" },
                author = first(o, "author", "writer", "author_name", "writer_name"),
                category = first(o, "category", "category_name"),
                description = first(o, "description", "summary", "book_description"),
                fileType = first(o, "file_type", "format", "type", "extension"),
                coverUrl = first(o, "cover_url", "cover", "thumbnail", "image_url").ifBlank { if (id.isBlank()) "" else coverUrl(id) },
                downloadUrl = first(o, "download_url", "file_url", "book_url", "url"),
                rawUrl = first(o, "link", "drive_url", "source_url")
            )
        }
        return out
    }

    private fun parseNamed(array: JSONArray): List<NamedItem> {
        val out = mutableListOf<NamedItem>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = first(o, "id", "category_id", "author_id", "writer_id")
            val name = first(o, "name", "title", "category", "author", "writer")
            val count = first(o, "count", "book_count", "total")
            if (name.isNotBlank()) out += NamedItem(id, name, count)
        }
        return out
    }

    private fun findArray(json: JSONObject, keys: List<String>): JSONArray {
        for (k in keys) {
            val v = json.opt(k)
            if (v is JSONArray) return v
            if (v is JSONObject) {
                for (inner in keys) {
                    val a = v.optJSONArray(inner)
                    if (a != null) return a
                }
            }
        }
        // first array anywhere at top level
        val it = json.keys()
        while (it.hasNext()) {
            val v = json.opt(it.next())
            if (v is JSONArray) return v
        }
        return JSONArray()
    }

    private fun findString(json: JSONObject, keys: List<String>): String {
        for (k in keys) {
            val direct = json.optString(k, "")
            if (direct.isNotBlank() && direct != "null") return direct
        }
        val iter = json.keys()
        while (iter.hasNext()) {
            val v = json.opt(iter.next())
            if (v is JSONObject) {
                val nested = findString(v, keys)
                if (nested.isNotBlank()) return nested
            }
        }
        return ""
    }

    private fun first(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k, "")
            if (v.isNotBlank() && v != "null") return v
        }
        return ""
    }
}
