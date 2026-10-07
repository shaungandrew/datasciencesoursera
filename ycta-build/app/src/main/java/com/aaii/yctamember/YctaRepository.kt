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

        val name = value("အမည်", "Name").ifBlank {
            doc.selectFirst("h1, h2, .um-name, .profile-name")?.text().orEmpty()
        }

        return Member(
            name = name,
            memberId = value("ID.No", "ID No", "Member ID", "Member No"),
            joinedDate = value("အသင်းဝင် သည့်နေ့", "အသင်းဝင်သည့်နေ့", "Joined Date", "Member Since"),
            vehicleNo = value("ယာဉ် အမှတ်", "ယာဉ်အမှတ်", "Vehicle No", "Vehicle Number"),
            cityNo = value("City No", "City Number"),
            district = value("ခရိုင်/မြို့နယ်", "ခရိုင် / မြို့နယ်", "District/Township", "Township"),
            profileUrl = url
        )
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

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "YCTA-Member-Native/1.0 Android")
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

        for (key in listOf("search", "s", "q")) {
            runCatching {
                val url = SEARCH.toHttpUrl().newBuilder()
                    .addQueryParameter(key, query)
                    .build()
                    .toString()
                docs.add(Jsoup.parse(get(url), url))
            }
        }

        val unique = linkedMapOf<String, MemberSummary>()
        docs.forEach { doc ->
            doc.select("a[href*=/visitor-inside-user-page/]").forEach { a ->
                val absolute = absolute(a.absUrl("href").ifBlank { a.attr("href") })
                if (PROFILE.containsMatchIn(absolute)) {
                    val card = a.closest("tr, li, article, .member, .user, .card, .um-member")
                        ?.text().orEmpty()
                    val title = a.text().trim().ifBlank {
                        val code = PROFILE.find(absolute)?.groupValues?.getOrNull(1)
                        if (code.isNullOrBlank()) "Member" else "YCTA " + code
                    }
                    unique[absolute] = MemberSummary(
                        title,
                        card.replace(title, "").trim().take(120),
                        absolute
                    )
                }
            }
        }
        return unique.values.take(30)
    }

    private fun submitForm(doc: Document, query: String): Document? {
        val form = doc.select("form").firstOrNull { f ->
            f.select("input[type=text], input[type=search]")
                .any { it.attr("name").isNotBlank() }
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
                .header("User-Agent", "YCTA-Member-Native/1.0 Android")
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

        for (field in doc.select(".um-field, .field, .profile-field, .member-field")) {
            val label = field.selectFirst(".um-field-label, label, .label, dt, th")
                ?.text().orEmpty()
            if (wanted.any { norm(label).contains(it) }) {
                val candidate = field.selectFirst(".um-field-area, .value, dd, td, input, textarea")
                val text = candidate?.let(::elementValue).orEmpty()
                if (text.isNotBlank()) return clean(text)
            }
        }

        for (row in doc.select("tr")) {
            val cells = row.select("th,td")
            if (cells.size >= 2 && wanted.any { norm(cells[0].text()).contains(it) }) {
                return clean(elementValue(cells[1]))
            }
        }

        val lines = doc.body()?.wholeText()?.lines()?.map(::clean)
            ?.filter { it.isNotBlank() }.orEmpty()
        for (i in lines.indices) {
            if (wanted.any { norm(lines[i]) == it }) {
                val end = minOf(i + 3, lines.lastIndex)
                for (j in (i + 1)..end) {
                    if (j in lines.indices && wanted.none { norm(lines[j]) == it }) {
                        return lines[j]
                    }
                }
            }
        }
        return ""
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

    private fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim().trim(':', '-', '–')
    private fun norm(s: String) = clean(s).lowercase().replace(" ", "")
}
