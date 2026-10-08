package com.aaii.yctamember

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MoocApi(private val context: Context) {
    companion object {
        const val SOURCE = "https://www.aaii.asia/edu/mooc"
        const val API_BASE = "https://www.aaii.asia/edu/mooc/api/"
        private const val PREFS = "ycta_mooc_native_cache"
    }

    data class Item(
        val id: String = "",
        val title: String = "",
        val subtitle: String = "",
        val category: String = "",
        val provider: String = "",
        val description: String = "",
        val url: String = "",
        val sourceType: String = "",
        val certificate: String = "",
        val imageUrl: String = ""
    )

    data class Snapshot(
        val items: List<Item>,
        val fromCache: Boolean
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun fetchMooc(force: Boolean = false): Snapshot =
        fetchWithCache(
            key = "mooc",
            force = force,
            urls = listOf(
                API_BASE + "mooc_courses.php?page=1&limit=100",
                API_BASE + "mooc_courses.php"
            )
        ) { parseItems(it, "mooc") }

    fun fetchDirectory(force: Boolean = false): Snapshot =
        fetchWithCache(
            key = "directory",
            force = force,
            urls = listOf(
                API_BASE + "directory_items.php?q=",
                API_BASE + "courses.php?q="
            )
        ) { parseItems(it, "directory") }

    fun fetchFreeHub(force: Boolean = false): Snapshot =
        fetchWithCache(
            key = "freehub",
            force = force,
            urls = listOf(
                API_BASE + "free_hub_sources.php",
                API_BASE + "free_hub_categories.php"
            )
        ) { parseItems(it, "free_hub") }

    fun fetchProducts(force: Boolean = false): Snapshot =
        fetchWithCache(
            key = "products",
            force = force,
            urls = listOf(API_BASE + "products_services.php")
        ) { parseItems(it, "product") }

    fun fetchMoocDetail(id: String): Item? {
        if (id.isBlank()) return null
        val urls = listOf(
            API_BASE + "mooc_course.php?id=" + id,
            API_BASE + "course.php?id=" + id,
            API_BASE + "directory_item.php?id=" + id
        )
        for (url in urls) {
            val text = runCatching { get(url) }.getOrNull() ?: continue
            val parsed = parseItems(text, "detail")
            if (parsed.isNotEmpty()) return parsed.first()
            val obj = runCatching { JSONObject(text) }.getOrNull()
            if (obj != null) {
                val item = parseObject(obj, "detail")
                if (item.title.isNotBlank() || item.description.isNotBlank()) return item
            }
        }
        return null
    }

    fun youtubeItems(directory: List<Item>): List<Item> =
        directory.filter { item ->
            val hay = (item.sourceType + " " + item.url + " " + item.subtitle).lowercase()
            hay.contains("youtube") || hay.contains("youtu.be")
        }

    fun driveItems(directory: List<Item>): List<Item> =
        directory.filter { item ->
            val hay = (item.sourceType + " " + item.url + " " + item.subtitle).lowercase()
            hay.contains("drive") || hay.contains("google_drive") || hay.contains("google drive")
        }

    fun categories(items: List<Item>): List<String> =
        items.map { it.category.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    fun filter(items: List<Item>, query: String): List<Item> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return items
        return items.filter { item ->
            listOf(
                item.title,
                item.subtitle,
                item.category,
                item.provider,
                item.description,
                item.sourceType
            ).any { it.lowercase().contains(q) }
        }
    }

    fun syncAll(): Map<String, Int> {
        val mooc = fetchMooc(true).items
        val directory = fetchDirectory(true).items
        val freeHub = fetchFreeHub(true).items
        val products = runCatching { fetchProducts(true).items }.getOrDefault(emptyList())
        return linkedMapOf(
            "MOOC" to mooc.size,
            "YouTube" to youtubeItems(directory).size,
            "Drive" to driveItems(directory).size,
            "Free Hub" to freeHub.size,
            "Products" to products.size
        )
    }

    private fun fetchWithCache(
        key: String,
        force: Boolean,
        urls: List<String>,
        parser: (String) -> List<Item>
    ): Snapshot {
        if (!force) {
            val cached = prefs.getString(key, "").orEmpty()
            if (cached.isNotBlank()) {
                val parsed = runCatching { parser(cached) }.getOrDefault(emptyList())
                if (parsed.isNotEmpty()) {
                    return Snapshot(parsed, true)
                }
            }
        }

        var lastError: Throwable? = null
        for (url in urls) {
            try {
                val text = get(url)
                val parsed = parser(text)
                if (parsed.isNotEmpty()) {
                    prefs.edit()
                        .putString(key, text)
                        .putLong(key + "_ts", System.currentTimeMillis())
                        .apply()
                    return Snapshot(parsed, false)
                }
                lastError = IllegalStateException("Empty response from " + url.substringAfterLast('/'))
            } catch (t: Throwable) {
                lastError = t
            }
        }

        val cached = prefs.getString(key, "").orEmpty()
        if (cached.isNotBlank()) {
            val parsed = runCatching { parser(cached) }.getOrDefault(emptyList())
            if (parsed.isNotEmpty()) return Snapshot(parsed, true)
        }

        throw lastError ?: IllegalStateException("MOOC source unavailable.")
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json,*/*")
            .header("Referer", SOURCE + "/")
            .header("User-Agent", "YCTA-MOOC-Native/5.3 Android")
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("MOOC server HTTP " + response.code)
            }
            if (text.isBlank()) error("Empty MOOC server response.")
            return text
        }
    }

    private fun parseItems(text: String, fallbackType: String): List<Item> {
        val trimmed = text.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> findArray(JSONObject(trimmed))
            else -> JSONArray()
        }

        val out = mutableListOf<Item>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val item = parseObject(obj, fallbackType)
            if (item.title.isNotBlank() || item.url.isNotBlank()) out += item
        }

        if (out.isNotEmpty()) return out

        if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            val one = parseObject(obj, fallbackType)
            if (one.title.isNotBlank() || one.url.isNotBlank()) return listOf(one)
        }

        return emptyList()
    }

    private fun parseObject(o: JSONObject, fallbackType: String): Item {
        val nested = listOf("course", "item", "data", "result", "post")
            .mapNotNull { key -> o.optJSONObject(key) }
            .firstOrNull()

        val src = nested ?: o

        val id = first(src, "id", "course_id", "mooc_course_id", "directory_id", "post_id", "source_id")
        val title = first(src, "title", "name", "course_title", "post_title")
        val category = first(src, "category", "category_title", "category_name", "subject")
        val provider = first(
            src,
            "provider",
            "platform",
            "university",
            "university_name",
            "institution",
            "source_name"
        )
        val description = first(
            src,
            "description",
            "summary",
            "overview",
            "content",
            "about",
            "excerpt"
        )
        val url = first(
            src,
            "url",
            "link",
            "join_url",
            "course_url",
            "enrollment_url",
            "source_url",
            "website_url",
            "youtube_url",
            "playlist_url",
            "drive_url"
        )
        val type = first(
            src,
            "source_type",
            "type",
            "course_type",
            "provider_type"
        ).ifBlank { fallbackType }
        val subtitle = listOf(provider, type.replace('_', ' '))
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        val certificate = first(src, "certificate", "certificate_info", "certificate_type", "has_certificate")
        val image = first(src, "image", "image_url", "thumbnail", "thumbnail_url", "logo", "cover")

        return Item(
            id = id,
            title = title,
            subtitle = subtitle,
            category = category,
            provider = provider,
            description = description,
            url = url,
            sourceType = type,
            certificate = certificate,
            imageUrl = image
        )
    }

    private fun findArray(obj: JSONObject): JSONArray {
        val preferred = listOf(
            "courses",
            "items",
            "results",
            "data",
            "posts",
            "sources",
            "categories",
            "entities",
            "records"
        )

        for (key in preferred) {
            val value = obj.opt(key)
            if (value is JSONArray) return value
            if (value is JSONObject) {
                for (inner in preferred) {
                    val nested = value.optJSONArray(inner)
                    if (nested != null) return nested
                }
            }
        }

        val keys = obj.keys()
        while (keys.hasNext()) {
            val value = obj.opt(keys.next())
            if (value is JSONArray) return value
        }
        return JSONArray()
    }

    private fun first(o: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = o.optString(key, "")
            if (value.isNotBlank() && value != "null") return value
        }
        return ""
    }
}
