package com.aaii.yctamember

import android.content.Context
import android.os.Build
import android.provider.Settings
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MoocApi(private val context: Context) {
    companion object {
        const val WEB_BASE = "https://www.aaii.asia/edu/mooc/"
        const val API_BASE = "https://aaii.asia/edu/mooc/api/"
        private const val PREFS = "ycta_mooc_module"
        private const val TOKEN = "token"
    }

    class AuthException(message: String) : Exception(message)

    data class Course(
        val id: String = "",
        val title: String = "",
        val provider: String = "",
        val categoryId: String = "",
        val category: String = "",
        val sourceType: String = "",
        val courseType: String = "",
        val description: String = "",
        val url: String = "",
        val thumbnail: String = "",
        val certificate: String = ""
    )

    data class Category(
        val id: String = "",
        val name: String = "",
        val count: Int = 0
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun hasToken(): Boolean = token().isNotBlank()
    fun clearToken() = prefs.edit().remove(TOKEN).apply()
    fun lastSync(source: String): Long = prefs.getLong("sync_$source", 0L)

    private fun token(): String = prefs.getString(TOKEN, "").orEmpty().trim()

    private fun deviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "android-" + Build.MODEL.replace(" ", "-")

    private fun deviceLabel(): String =
        listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android Device" }

    fun login(activation: String, username: String, password: String): String {
        if (activation.isBlank() && (username.isBlank() || password.isBlank())) {
            error("Enter Activation Code OR Username + Password.")
        }

        val data = JSONObject()
        if (activation.isNotBlank()) data.put("activation_code", activation)
        if (username.isNotBlank()) data.put("username", username)
        if (password.isNotBlank()) data.put("password", password)
        data.put("device_id", deviceId())
        data.put("device_label", deviceLabel())
        val request = Request.Builder()
            .url(API_BASE + "login.php")
            .post(data.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .header("Referer", WEB_BASE)
            .header("User-Agent", "YCTA-MOOC-Native/6.1 Android")
            .build()
        val text = execute(request)
        val obj = parseObject(text)
        val found = findString(obj, listOf("token", "access_token", "api_token"))
        if (found.isBlank()) {
            val msg = findString(obj, listOf("message", "error", "detail"))
            error(if (msg.isBlank()) "Login succeeded but token was not returned." else msg)
        }
        prefs.edit().putString(TOKEN, found).apply()
        return found
    }

    fun fetchCourses(source: String, force: Boolean = false): List<Course> {
        if (!force) {
            val cached = cachedCourses(source)
            if (cached.isNotEmpty()) return cached
        }

        var lastError: Throwable? = null
        for (relative in candidates(source)) {
            val result = runCatching {
                val parsed = parseCourses(get(relative), source)
                if (parsed.isEmpty()) error("Empty response")
                parsed
            }
            result.onSuccess {
                cacheCourses(source, it)
                prefs.edit().putLong("sync_$source", System.currentTimeMillis()).apply()
                return it
            }.onFailure { e ->
                if (e is AuthException) throw e
                lastError = e
            }
        }

        val cached = cachedCourses(source)
        if (cached.isNotEmpty()) return cached
        throw lastError ?: IllegalStateException("MOOC server returned no records.")
    }

    fun fetchCategories(source: String, force: Boolean = false): List<Category> {
        val direct = runCatching { fetchDirectCategories(source) }.getOrDefault(emptyList())
        val courses = runCatching { fetchCourses(source, force) }.getOrDefault(emptyList())

        val derived = courses
            .filter { it.category.isNotBlank() || it.categoryId.isNotBlank() }
            .groupBy { it.categoryId.ifBlank { it.category } }
            .map { (key, values) ->
                Category(
                    id = values.first().categoryId.ifBlank { key },
                    name = values.first().category.ifBlank { "Category $key" },
                    count = values.size
                )
            }

        val merged = linkedMapOf<String, Category>()
        direct.forEach { c -> merged[c.id.ifBlank { c.name.lowercase() }] = c }
        derived.forEach { c ->
            val key = c.id.ifBlank { c.name.lowercase() }
            val old = merged[key]
            merged[key] = if (old == null) c else old.copy(count = maxOf(old.count, c.count))
        }
        return merged.values.sortedBy { it.name.lowercase() }
    }

    fun filterByCategory(courses: List<Course>, category: Category): List<Course> =
        courses.filter {
            (category.id.isNotBlank() && it.categoryId == category.id) ||
                it.category.equals(category.name, ignoreCase = true)
        }

    fun searchAll(query: String): List<Course> {
        val q = query.trim().lowercase()
        val all = listOf("mooc", "youtube", "drive", "freehub")
            .flatMap { runCatching { fetchCourses(it, false) }.getOrDefault(emptyList()) }
        if (q.isBlank()) return all
        return all.filter { c ->
            listOf(c.title, c.provider, c.category, c.description, c.sourceType)
                .any { it.lowercase().contains(q) }
        }
    }

    fun detail(course: Course): Course {
        if (course.id.isBlank()) return course
        val endpoints = when (course.sourceType.lowercase()) {
            "mooc" -> listOf(
                "mooc_course.php?id=${course.id}",
                "course.php?id=${course.id}"
            )
            "freehub" -> listOf(
                "directory_item.php?id=${course.id}",
                "course.php?id=${course.id}"
            )
            else -> listOf("course.php?id=${course.id}")
        }

        endpoints.forEach { relative ->
            val found = runCatching {
                parseSingleCourse(get(relative), course.sourceType.ifBlank { "mooc" })
            }.getOrNull()
            if (found != null && found.title.isNotBlank()) return merge(course, found)
        }
        return course
    }

    fun certificateUrl(courseId: String): String =
        API_BASE + "certificate.php?course_id=" + courseId

    fun fetchImage(url: String): ByteArray? {
        if (url.isBlank()) return null
        return runCatching {
            val req = authBuilder(url)
                .get()
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .build()
            client.newCall(req).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else null
            }
        }.getOrNull()
    }

    private fun fetchDirectCategories(source: String): List<Category> {
        val endpoints = when (source.lowercase()) {
            "freehub" -> listOf(
                "free_hub_categories.php",
                "categories.php?source=freehub"
            )
            else -> listOf(
                "categories.php?source=$source",
                "categories.php?type=$source"
            )
        }

        endpoints.forEach { relative ->
            val found = runCatching { parseCategories(get(relative)) }.getOrDefault(emptyList())
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    private fun candidates(source: String): List<String> =
        when (source.lowercase()) {
            "mooc" -> listOf(
                "mooc_courses.php?page=1&limit=100",
                "mooc_courses.php?limit=100",
                "courses.php?source=mooc&limit=120",
                "courses.php?type=mooc&limit=120"
            )
            "youtube" -> listOf(
                "courses.php?source=youtube&limit=150",
                "courses.php?type=youtube&limit=150",
                "courses.php?course_type=youtube&limit=150",
                "sources.php?type=youtube&limit=150"
            )
            "drive" -> listOf(
                "courses.php?source=drive&limit=150",
                "courses.php?type=drive&limit=150",
                "courses.php?course_type=drive&limit=150",
                "sources.php?type=drive&limit=150"
            )
            "freehub" -> listOf(
                "free_hub_sources.php?limit=150",
                "directory_items.php?category=freehub&limit=150",
                "courses.php?source=freehub&limit=150"
            )
            else -> listOf("courses.php?source=$source&limit=120")
        }

    private fun get(relative: String): String {
        val url = if (relative.startsWith("http")) relative else API_BASE + relative
        return execute(authBuilder(url).get().build())
    }

    private fun authBuilder(url: String): Request.Builder {
        val b = Request.Builder()
            .url(url)
            .header("Accept", "application/json,*/*")
            .header("Referer", WEB_BASE)
            .header("User-Agent", "YCTA-MOOC-Native/6.0 Android")
        val t = token()
        if (t.isNotBlank()) {
            b.header("Authorization", "Bearer $t")
            b.header("X-API-Token", t)
        }
        return b
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (
                response.code == 401 ||
                response.code == 403 ||
                text.contains("LOGIN_REQUIRED", true) ||
                text.contains("invalid token", true)
            ) {
                throw AuthException("MOOC login / activation is required.")
            }
            if (!response.isSuccessful) {
                val message = runCatching {
                    findString(parseObject(text), listOf("message", "error", "detail"))
                }.getOrDefault("")
                error(if (message.isBlank()) "MOOC server HTTP ${response.code}" else message)
            }
            return text
        }
    }

    private fun parseCourses(text: String, source: String): List<Course> {
        val root = parseAny(text)
        val array = findArray(root)
        val out = mutableListOf<Course>()

        if (array != null) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val c = objectToCourse(obj, source)
                if (c.title.isNotBlank() || c.id.isNotBlank()) out += c
            }
        } else if (root is JSONObject) {
            val c = objectToCourse(root, source)
            if (c.title.isNotBlank()) out += c
        }

        return out.distinctBy {
            (it.id.ifBlank { it.url.ifBlank { it.title } }) + "|" + source
        }
    }

    private fun parseSingleCourse(text: String, source: String): Course? {
        val root = parseAny(text)
        if (root is JSONObject) {
            listOf("course", "item", "data", "result").forEach { k ->
                root.optJSONObject(k)?.let { return objectToCourse(it, source) }
            }
            return objectToCourse(root, source)
        }
        if (root is JSONArray && root.length() > 0) {
            return root.optJSONObject(0)?.let { objectToCourse(it, source) }
        }
        return null
    }

    private fun parseCategories(text: String): List<Category> {
        val root = parseAny(text)
        val array = findArray(root) ?: return emptyList()
        val out = mutableListOf<Category>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = first(o, "id", "category_id", "source_id", "term_id")
            val name = first(o, "name", "title", "category", "category_name")
            val count = first(o, "count", "course_count", "total").toIntOrNull() ?: 0
            if (name.isNotBlank()) out += Category(id, name, count)
        }
        return out
    }

    private fun objectToCourse(o: JSONObject, sourceFallback: String): Course {
        val rawSource = first(
            o,
            "source_type",
            "source",
            "platform_type",
            "content_type",
            "type"
        )

        val id = first(o, "id", "course_id", "mooc_course_id", "post_id", "source_id")
        val title = first(o, "title", "course_title", "name", "post_title")
        val provider = first(
            o,
            "provider",
            "platform",
            "university",
            "organization",
            "channel_name",
            "source_name"
        )
        val categoryId = first(o, "category_id", "course_category_id", "cat_id")
        val category = first(o, "category", "category_name", "course_category")
        val description = first(
            o,
            "description",
            "summary",
            "overview",
            "content",
            "excerpt",
            "about"
        )
        val url = first(
            o,
            "url",
            "link",
            "course_url",
            "join_url",
            "enroll_url",
            "website_url",
            "youtube_url",
            "playlist_url",
            "drive_url",
            "source_url"
        )
        val thumbnail = first(
            o,
            "thumbnail",
            "thumbnail_url",
            "image",
            "image_url",
            "cover",
            "cover_url"
        )
        val certificate = first(
            o,
            "certificate",
            "certificate_url",
            "certificate_type"
        )
        val courseType = first(o, "course_type", "format", "mode", "learning_type")

        return Course(
            id = id,
            title = title,
            provider = provider,
            categoryId = categoryId,
            category = category,
            sourceType = normalizeSource(rawSource, url, sourceFallback),
            courseType = courseType,
            description = stripHtml(description),
            url = url,
            thumbnail = thumbnail,
            certificate = certificate
        )
    }

    private fun normalizeSource(raw: String, url: String, fallback: String): String {
        val text = "$raw $url".lowercase()
        return when {
            text.contains("youtube") || text.contains("youtu.be") -> "youtube"
            text.contains("drive.google") || text.contains("google drive") -> "drive"
            text.contains("freehub") || text.contains("free_hub") ||
                text.contains("scholarship") || text.contains("coupon") -> "freehub"
            raw.contains("mooc", true) -> "mooc"
            else -> fallback.lowercase()
        }
    }

    private fun merge(a: Course, b: Course): Course =
        Course(
            id = b.id.ifBlank { a.id },
            title = b.title.ifBlank { a.title },
            provider = b.provider.ifBlank { a.provider },
            categoryId = b.categoryId.ifBlank { a.categoryId },
            category = b.category.ifBlank { a.category },
            sourceType = b.sourceType.ifBlank { a.sourceType },
            courseType = b.courseType.ifBlank { a.courseType },
            description = b.description.ifBlank { a.description },
            url = b.url.ifBlank { a.url },
            thumbnail = b.thumbnail.ifBlank { a.thumbnail },
            certificate = b.certificate.ifBlank { a.certificate }
        )

    private fun cacheCourses(source: String, courses: List<Course>) {
        val arr = JSONArray()
        courses.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("title", c.title)
                put("provider", c.provider)
                put("category_id", c.categoryId)
                put("category", c.category)
                put("source_type", c.sourceType)
                put("course_type", c.courseType)
                put("description", c.description)
                put("url", c.url)
                put("thumbnail", c.thumbnail)
                put("certificate", c.certificate)
            })
        }
        prefs.edit().putString("cache_$source", arr.toString()).apply()
    }

    private fun cachedCourses(source: String): List<Course> {
        val raw = prefs.getString("cache_$source", "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { parseCourses(raw, source) }.getOrDefault(emptyList())
    }

    private fun parseAny(text: String): Any {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> JSONObject(trimmed)
            else -> error("Invalid MOOC API response")
        }
    }

    private fun parseObject(text: String): JSONObject {
        val any = parseAny(text)
        return when (any) {
            is JSONObject -> any
            is JSONArray -> JSONObject().put("data", any)
            else -> JSONObject()
        }
    }

    private fun findArray(root: Any, depth: Int = 0): JSONArray? {
        if (depth > 4) return null
        if (root is JSONArray) return root
        if (root !is JSONObject) return null

        val preferred = listOf(
            "courses",
            "items",
            "results",
            "data",
            "records",
            "posts",
            "sources",
            "categories"
        )

        preferred.forEach { k ->
            val value = root.opt(k)
            if (value is JSONArray) return value
        }

        val keys = root.keys()
        while (keys.hasNext()) {
            val value = root.opt(keys.next())
            when (value) {
                is JSONArray -> if (value.length() > 0) return value
                is JSONObject -> {
                    val nested = findArray(value, depth + 1)
                    if (nested != null) return nested
                }
            }
        }
        return null
    }

    private fun findString(root: JSONObject, keys: List<String>, depth: Int = 0): String {
        if (depth > 4) return ""
        keys.forEach { k ->
            val v = root.optString(k, "")
            if (v.isNotBlank() && v != "null") return v
        }
        val it = root.keys()
        while (it.hasNext()) {
            val value = root.opt(it.next())
            if (value is JSONObject) {
                val nested = findString(value, keys, depth + 1)
                if (nested.isNotBlank()) return nested
            }
        }
        return ""
    }

    private fun first(o: JSONObject, vararg keys: String): String {
        keys.forEach { k ->
            val v = o.optString(k, "")
            if (v.isNotBlank() && v != "null") return v.trim()
        }
        return ""
    }

    private fun stripHtml(text: String): String =
        text.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
