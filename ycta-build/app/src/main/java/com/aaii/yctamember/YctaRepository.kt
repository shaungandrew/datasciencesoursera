package com.aaii.yctamember

import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class YctaRepository(cacheDir: File) {
    companion object {
        private const val BASE = "https://ycta.yangoncity.net"
        private const val SEARCH = "$BASE/member-search/"
        private val PROFILE = Regex(
            "/visitor-inside-user-page/ycta([A-Za-z0-9_-]+)/?",
            RegexOption.IGNORE_CASE
        )
    }

    private class SessionCookieJar : CookieJar {
        private val store = mutableMapOf<String, MutableList<Cookie>>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = store.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { incoming ->
                hostCookies.removeAll { it.name == incoming.name && it.path == incoming.path }
                if (incoming.expiresAt > System.currentTimeMillis()) hostCookies.add(incoming)
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            val hostCookies = store[url.host] ?: return emptyList()
            hostCookies.removeAll { it.expiresAt <= now }
            return hostCookies.filter { it.matches(url) }
        }
    }

    private val imageCacheDir = File(cacheDir, "ycta_member_photos").apply { mkdirs() }
    private val httpCacheDir = File(cacheDir, "ycta_http").apply { mkdirs() }

    private val client = OkHttpClient.Builder()
        .cookieJar(SessionCookieJar())
        .cache(Cache(httpCacheDir, 12L * 1024L * 1024L))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun search(input: String): SearchOutcome {
        return try {
            val q = input.trim()
            directUrl(q)?.let { return SearchOutcome.Direct(fetchByUrl(it)) }
            val found = searchWebsite(q)
            when {
                found.isEmpty() -> SearchOutcome.Failure("No member profile found.")
                found.size == 1 -> SearchOutcome.Direct(fetchByUrl(found.first().profileUrl))
                else -> SearchOutcome.Results(found)
            }
        } catch (e: Exception) {
            SearchOutcome.Failure("Network/search error: " + (e.message ?: "Unknown error"))
        }
    }

    fun fetchByUrl(url: String): Member {
        val canonical = canonicalProfileUrl(url)
        val doc = Jsoup.parse(get(canonical, canonical), canonical)
        if (doc.body()?.wholeText().orEmpty().isBlank()) error("Empty profile")

        fun value(vararg labels: String) = labeled(doc, labels.toList())

        val name = value("အမည်", "Name", "Member Name").ifBlank {
            doc.selectFirst(".um-name, .profile-name, .member-name, .user-name, h1, h2")?.text().orEmpty()
        }

        val phone = value("ဆက်သွယ်ရန် ဖုန်း", "ဆက်သွယ်ရန်ဖုန်း", "Phone", "Contact Phone", "Mobile")
        val nrc = value("နိုင်ငံသားစိစစ်ရေး ကတ်", "နိုင်ငံသားစိစစ်ရေးကတ်", "NRC", "National ID")
        val address = value("ဆက်သွယ်ရန်နေရပ်လိပ်စာ", "ဆက်သွယ်ရန် နေရပ်လိပ်စာ", "Address")

        return Member(
            name = name,
            memberId = value("ID.No", "ID No", "Member ID", "Member No", "Member Number"),
            driverLicense = value("Driver License", "Driving License", "License No", "Driver Licence"),
            joinedDate = value("အသင်းဝင် သည့်နေ့", "အသင်းဝင်သည့်နေ့", "Joined Date", "Member Since", "Join Date"),
            vehicleNo = value("ယာဉ် အမှတ်", "ယာဉ်အမှတ်", "Vehicle No", "Vehicle Number", "Car No"),
            cityNo = value("City No", "City Number"),
            district = value("ခရိုင်/မြို့နယ်", "ခရိုင် / မြို့နယ်", "District/Township", "District / Township", "Township"),
            photoUrls = findPhotoCandidates(doc, name),
            maskedPhone = maskPhone(phone),
            maskedNrc = maskNrc(nrc),
            maskedAddress = maskAddress(address),
            profileUrl = canonical
        )
    }

    fun fetchBestImage(urls: List<String>, referer: String): ByteArray? {
        for (url in urls.distinct()) {
            val bytes = fetchImage(url, referer) ?: continue
            // A server may respond HTTP 200 with a login HTML page, or
            // an image CDN may return a corrupt/unsupported image.
            // Validate bytes before accepting; then try the next URL.
            if (android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
                return bytes
            }
            runCatching { imageCacheFile(url).delete() }
        }
        return null
    }

    private fun fetchImage(url: String, referer: String): ByteArray? {
        if (url.isBlank() || !url.startsWith("http")) return null
        val cached = imageCacheFile(url)
        if (cached.exists() && cached.length() > 128) {
            return runCatching { cached.readBytes() }.getOrNull()
        }

        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/154 Mobile Safari/537.36")
                .header("Referer", referer.ifBlank { BASE })
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Accept-Language", "my,en-US;q=0.9,en;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                if (bytes.size < 128) return null
                runCatching {
                    cached.parentFile?.mkdirs()
                    cached.writeBytes(bytes)
                }
                bytes
            }
        }.getOrNull()
    }

    private fun imageCacheFile(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(imageCacheDir, "$hash.img")
    }

    private fun directUrl(input: String): String? {
        PROFILE.find(input)?.let {
            return "$BASE/visitor-inside-user-page/ycta" + it.groupValues[1] + "/"
        }
        Regex("(?i)^ycta([A-Za-z0-9_-]+)$").matchEntire(input)?.let {
            return "$BASE/visitor-inside-user-page/ycta" + it.groupValues[1] + "/"
        }
        Regex("^(\\d{2})\\s*/\\s*(\\d{4})$").matchEntire(input)?.let {
            return "$BASE/visitor-inside-user-page/ycta" + it.groupValues[1] + it.groupValues[2] + "/"
        }
        val digits = input.filter(Char::isDigit)
        if (digits.length == 6 && input.all { it.isDigit() || it.isWhitespace() || it == '/' || it == '-' }) {
            return "$BASE/visitor-inside-user-page/ycta$digits/"
        }
        return null
    }

    private fun canonicalProfileUrl(url: String): String {
        PROFILE.find(url)?.let {
            return "$BASE/visitor-inside-user-page/ycta" + it.groupValues[1] + "/"
        }
        return url
    }

    private fun get(url: String, referer: String = BASE): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/154 Mobile Safari/537.36")
            .header("Referer", referer)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "my,en-US;q=0.9,en;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP " + response.code)
            return response.body?.string().orEmpty()
        }
    }

    private fun searchWebsite(query: String): List<MemberSummary> {
        val landing = Jsoup.parse(get(SEARCH), SEARCH)
        val docs = mutableListOf(landing)
        submitForm(landing, query)?.let(docs::add)

        for (key in listOf("search", "s", "q", "member", "member_id")) {
            runCatching {
                val url = SEARCH.toHttpUrl().newBuilder()
                    .addQueryParameter(key, query)
                    .build().toString()
                docs.add(Jsoup.parse(get(url, SEARCH), url))
            }
        }

        val unique = linkedMapOf<String, MemberSummary>()
        docs.forEach { doc ->
            doc.select("a[href*=/visitor-inside-user-page/]").forEach { a ->
                val absolute = absolute(a.absUrl("href").ifBlank { a.attr("href") })
                if (PROFILE.containsMatchIn(absolute)) {
                    val card = a.closest("tr, li, article, .member, .user, .card, .um-member, .row")
                        ?.text().orEmpty()
                    val title = a.text().trim().ifBlank {
                        val code = PROFILE.find(absolute)?.groupValues?.getOrNull(1)
                        if (code.isNullOrBlank()) "YCTA Member" else "YCTA $code"
                    }
                    unique[canonicalProfileUrl(absolute)] = MemberSummary(
                        title = title,
                        subtitle = card.replace(title, "").trim().take(140),
                        profileUrl = canonicalProfileUrl(absolute)
                    )
                }
            }
        }
        return unique.values.take(40)
    }

    private fun submitForm(doc: Document, query: String): Document? {
        val form = doc.select("form").firstOrNull { f ->
            f.select("input[type=text], input[type=search]").any { it.attr("name").isNotBlank() }
        } ?: return null

        val input = form.select("input[type=text], input[type=search]")
            .firstOrNull { it.attr("name").isNotBlank() } ?: return null

        val action = form.absUrl("action").ifBlank { SEARCH }
        val method = form.attr("method").ifBlank { "get" }.lowercase()
        val hidden = form.select("input[type=hidden][name]")
            .associate { it.attr("name") to it.attr("value") }

        return if (method == "post") {
            val body = FormBody.Builder()
            hidden.forEach { (k, v) -> body.add(k, v) }
            body.add(input.attr("name"), query)
            val request = Request.Builder()
                .url(action)
                .post(body.build())
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/154 Mobile Safari/537.36")
                .header("Referer", SEARCH)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                Jsoup.parse(response.body?.string().orEmpty(), action)
            }
        } else {
            val builder = action.toHttpUrl().newBuilder()
            hidden.forEach { (k, v) -> builder.addQueryParameter(k, v) }
            builder.addQueryParameter(input.attr("name"), query)
            val url = builder.build().toString()
            Jsoup.parse(get(url, SEARCH), url)
        }
    }

    private fun labeled(doc: Document, labels: List<String>): String {
        val wanted = labels.map(::norm)

        for (field in doc.select(".um-field, .field, .profile-field, .member-field, .form-group, .profile-row, .member-row")) {
            val label = field.selectFirst(".um-field-label, label, .label, dt, th, strong, b")?.text().orEmpty()
            if (matchesLabel(label, wanted)) {
                val candidate = field.selectFirst(".um-field-area, .value, dd, td, input, textarea, .field-value, .profile-value, p, span")
                val text = candidate?.let(::elementValue).orEmpty()
                if (isUseful(text, wanted)) return clean(text)
            }
        }

        for (row in doc.select("tr")) {
            val cells = row.select("th,td")
            if (cells.size >= 2 && matchesLabel(cells[0].text(), wanted)) {
                val v = clean(elementValue(cells[1]))
                if (isUseful(v, wanted)) return v
            }
        }

        for (dt in doc.select("dt")) {
            if (matchesLabel(dt.text(), wanted)) {
                dt.nextElementSibling()?.let {
                    val v = clean(elementValue(it))
                    if (isUseful(v, wanted)) return v
                }
            }
        }

        for (el in doc.getAllElements()) {
            val own = clean(el.ownText())
            if (own.isNotBlank() && matchesLabel(own, wanted)) {
                el.nextElementSibling()?.let {
                    val v = clean(elementValue(it))
                    if (isUseful(v, wanted)) return v
                }
                val parent = el.parent()
                if (parent != null) {
                    val children = parent.children()
                    val index = children.indexOf(el)
                    if (index >= 0 && index + 1 < children.size) {
                        val v = clean(elementValue(children[index + 1]))
                        if (isUseful(v, wanted)) return v
                    }
                }
            }
        }

        val lines = doc.body()?.wholeText()?.lines()?.map(::clean)?.filter { it.isNotBlank() }.orEmpty()
        for (i in lines.indices) {
            val lineNorm = norm(lines[i])
            for (w in wanted) {
                if (lineNorm.startsWith(w) && lineNorm.length > w.length) {
                    val original = lines[i]
                    val colon = original.indexOf(':')
                    if (colon >= 0 && colon + 1 < original.length) {
                        val sameLine = clean(original.substring(colon + 1))
                        if (isUseful(sameLine, wanted)) return sameLine
                    }
                }
            }
            if (matchesLabel(lines[i], wanted)) {
                val end = minOf(i + 3, lines.lastIndex)
                for (j in (i + 1)..end) {
                    val v = clean(lines[j])
                    if (isUseful(v, wanted)) return v
                }
            }
        }
        return ""
    }

    private fun findPhotoCandidates(doc: Document, name: String): List<String> {
        val urls = linkedSetOf<String>()

        fun addFrom(el: Element) {
            listOf("src", "data-src", "data-lazy-src", "data-original", "data-url").forEach { attr ->
                val value = el.attr(attr).trim()
                if (value.isNotBlank()) addPhotoUrl(urls, el.absUrl(attr).ifBlank { absolute(value) })
            }

            val srcset = el.attr("srcset")
            if (srcset.isNotBlank()) {
                srcset.split(",").forEach { item ->
                    val raw = item.trim().substringBefore(" ").trim()
                    if (raw.isNotBlank()) addPhotoUrl(urls, absolute(raw))
                }
            }

            val style = el.attr("style")
            Regex("""url\(['"]?([^'")]+)""", RegexOption.IGNORE_CASE)
                .findAll(style)
                .forEach { match -> addPhotoUrl(urls, absolute(match.groupValues[1])) }

            if (el.tagName().equals("a", true)) {
                val href = el.absUrl("href").ifBlank { absolute(el.attr("href")) }
                if (looksLikeImage(href)) addPhotoUrl(urls, href)
            }
        }

        val selectors = listOf(
            ".um-profile-photo img",
            ".um-profile-photo a",
            ".um-header img",
            ".um-profile img",
            ".profile-photo img",
            ".profile-image img",
            ".member-photo img",
            ".member-image img",
            ".user-photo img",
            ".user-avatar img",
            "img.avatar",
            "img.profile",
            "img[class*=avatar]",
            "img[class*=profile]",
            "img[class*=member]",
            "[class*=profile-photo]",
            "[class*=member-photo]"
        )

        selectors.forEach { selector ->
            doc.select(selector).forEach(::addFrom)
        }

        val normalizedName = name.lowercase()
        doc.select("img").forEach { img ->
            val hint = listOf(
                img.attr("alt"),
                img.attr("title"),
                img.className(),
                img.id()
            ).joinToString(" ").lowercase()

            val width = img.attr("width").toIntOrNull() ?: 0
            val height = img.attr("height").toIntOrNull() ?: 0
            if (
                hint.contains("profile") ||
                hint.contains("avatar") ||
                hint.contains("member") ||
                (normalizedName.isNotBlank() && hint.contains(normalizedName)) ||
                (width >= 100 && height >= 100)
            ) addFrom(img)
        }

        return urls.take(12)
    }

    private fun addPhotoUrl(out: MutableSet<String>, raw: String) {
        val url = raw.trim()
        if (!url.startsWith("http")) return
        val lower = url.lowercase()
        if (
            lower.contains("logo") ||
            lower.contains("icon") ||
            lower.contains("spinner") ||
            lower.contains("loading") ||
            lower.contains("emoji")
        ) return
        out.add(url)
    }

    private fun looksLikeImage(url: String): Boolean {
        val lower = url.substringBefore("?").lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".gif")
    }

    private fun matchesLabel(text: String, wanted: List<String>): Boolean {
        val n = norm(text)
        return wanted.any { n == it || n.startsWith(it) || n.contains(it) }
    }

    private fun isUseful(text: String, wanted: List<String>): Boolean {
        val v = clean(text)
        if (v.isBlank()) return false
        return wanted.none { norm(v) == it }
    }

    private fun elementValue(el: Element): String = when (el.tagName()) {
        "input" -> el.attr("value")
        "textarea" -> el.text()
        else -> el.text()
    }

    private fun absolute(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        return try { URI(BASE).resolve(url).toString() }
        catch (_: Exception) { "$BASE/" + url.trimStart('/') }
    }

    private fun maskPhone(v: String): String {
        val s = clean(v)
        if (s.isBlank()) return "Protected"
        val digits = s.filter { it.isDigit() }
        if (digits.length < 6) return "••••••"
        return digits.take(3) + " •••• " + digits.takeLast(3)
    }

    private fun maskNrc(v: String): String {
        val s = clean(v)
        if (s.isBlank()) return "Protected"
        return if (s.length <= 4) "••••••" else s.take(2) + "••••••••" + s.takeLast(2)
    }

    private fun maskAddress(v: String): String =
        if (clean(v).isBlank()) "Protected" else "•••••••••••••• (protected)"

    private fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim().trim(':', '-', '–')
    private fun norm(s: String) = clean(s).lowercase().replace(" ", "").replace(".", "").replace(":", "")
}
