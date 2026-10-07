package com.aaii.yctamember

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.concurrent.TimeUnit

class YctaRepository {
    companion object {
        private const val BASE = "https://ycta.yangoncity.net"
        private const val SEARCH = "$BASE/member-search/"
        private val PROFILE = Regex(
            "/visitor-inside-user-page/ycta([A-Za-z0-9_-]+)/?",
            RegexOption.IGNORE_CASE
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
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
        val doc = Jsoup.parse(get(url), url)
        if (doc.body()?.wholeText().orEmpty().isBlank()) error("Empty profile")

        fun value(vararg labels: String) = labeled(doc, labels.toList())

        val name = value("အမည်", "Name", "Member Name").ifBlank {
            doc.selectFirst(".um-name, .profile-name, .member-name, h1, h2")?.text().orEmpty()
        }

        val photo = findPhoto(doc, name)
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
            photoUrl = photo,
            maskedPhone = maskPhone(phone),
            maskedNrc = maskNrc(nrc),
            maskedAddress = maskAddress(address),
            profileUrl = canonicalProfileUrl(url)
        )
    }

    fun fetchImage(url: String): ByteArray? {
        if (url.isBlank()) return null
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "YCTA-Member-Native/3.0 Android")
                .header("Referer", BASE)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            }
        }.getOrNull()
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

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) YCTA-Member-Native/3.0")
            .header("Accept", "text/html,application/xhtml+xml")
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
                docs.add(Jsoup.parse(get(url), url))
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
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) YCTA-Member-Native/3.0")
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
            Jsoup.parse(get(url), url)
        }
    }

    private fun labeled(doc: Document, labels: List<String>): String {
        val wanted = labels.map(::norm)

        // Common WordPress / Ultimate Member field layouts.
        for (field in doc.select(".um-field, .field, .profile-field, .member-field, .form-group, .profile-row, .member-row")) {
            val label = field.selectFirst(".um-field-label, label, .label, dt, th, strong, b")?.text().orEmpty()
            if (matchesLabel(label, wanted)) {
                val candidate = field.selectFirst(".um-field-area, .value, dd, td, input, textarea, .field-value, .profile-value, p, span")
                val text = candidate?.let(::elementValue).orEmpty()
                if (isUseful(text, wanted)) return clean(text)
            }
        }

        // Standard tables.
        for (row in doc.select("tr")) {
            val cells = row.select("th,td")
            if (cells.size >= 2 && matchesLabel(cells[0].text(), wanted)) {
                val v = clean(elementValue(cells[1]))
                if (isUseful(v, wanted)) return v
            }
        }

        // Definition lists.
        for (dt in doc.select("dt")) {
            if (matchesLabel(dt.text(), wanted)) {
                dt.nextElementSibling()?.let {
                    val v = clean(elementValue(it))
                    if (isUseful(v, wanted)) return v
                }
            }
        }

        // Exact label element then nearby sibling/parent child.
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

        // Plain-text fallback, including "Label: value" on the same line.
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

    private fun findPhoto(doc: Document, name: String): String {
        val selectors = listOf(
            ".um-profile-photo img[src]",
            ".um-header img[src]",
            ".profile-photo img[src]",
            ".member-photo img[src]",
            "img.avatar[src]",
            "img.profile[src]",
            "img[class*=avatar][src]",
            "img[class*=profile][src]"
        )
        for (selector in selectors) {
            val src = doc.selectFirst(selector)?.absUrl("src").orEmpty()
            if (src.isNotBlank()) return src
        }
        if (name.isNotBlank()) {
            val n = name.lowercase()
            doc.select("img[src]").firstOrNull {
                it.attr("alt").lowercase().contains(n) || it.attr("title").lowercase().contains(n)
            }?.let { return it.absUrl("src") }
        }
        return ""
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

    private fun maskAddress(v: String): String = if (clean(v).isBlank()) "Protected" else "•••••••••••••• (protected)"

    private fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim().trim(':', '-', '–')
    private fun norm(s: String) = clean(s).lowercase().replace(" ", "").replace(".", "").replace(":", "")
}
