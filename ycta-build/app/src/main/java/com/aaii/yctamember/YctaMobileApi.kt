package com.aaii.yctamember

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** SQL-backed member directory from the YCTA-owned server. */
class YctaMobileApi {
    companion object { const val BASE = "https://ycta.aaii.asia/mobile-api/v1/index.php" }

    data class DistrictCount(val slug: String, val count: Int, val unassigned: Int)
    data class TownCount(val slug: String, val count: Int)
    data class Row(val id: Long, val code: String, val name: String,
                   val district: String, val township: String?, val photoUrl: String?) {
        fun asMember() = Member(
            name = name, memberId = code, district = district,
            photoUrls = listOfNotNull(photoUrl?.takeIf { it.startsWith("https://") }),
            maskedPhone = "Protected", maskedNrc = "Protected",
            maskedAddress = "Protected",
            profileUrl = "$BASE?action=member&id=$id"
        )
    }
    data class Page(val total: Int, val page: Int, val more: Boolean,
                    val note: String, val members: List<Row>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(18, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS).build()

    private fun get(vararg p: Pair<String,String>): JSONObject {
        val q = p.joinToString("&") {
            URLEncoder.encode(it.first, "UTF-8")+"="+URLEncoder.encode(it.second, "UTF-8")
        }
        val req = Request.Builder().url("$BASE?$q").get()
            .header("Accept", "application/json").build()
        client.newCall(req).execute().use { r ->
            val j = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrNull()
            if (!r.isSuccessful || j == null || !j.optBoolean("ok")) {
                error("Mobile API: "+(j?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "HTTP ${r.code} / invalid JSON"))
            }
            return j
        }
    }
    fun districts(): List<DistrictCount> {
        val a = get("action" to "districts").getJSONArray("districts")
        return (0 until a.length()).map { i ->
            val r=a.getJSONObject(i)
            DistrictCount(r.getString("slug"),r.optInt("member_count"),r.optInt("unassigned"))
        }
    }
    fun towns(slug: String): Pair<List<TownCount>, Int> {
        val j=get("action" to "townships","district" to slug)
        val a=j.getJSONArray("townships")
        val items=(0 until a.length()).map { i ->
            val r=a.getJSONObject(i)
            TownCount(r.getString("slug"),r.optInt("member_count"))
        }
        return items to j.optInt("unassigned")
    }
    fun members(d: String, t: String?, page: Int): Page {
        val p=mutableListOf("action" to "members","district" to d,
             "page" to page.toString(),"per_page" to "25")
        if(!t.isNullOrBlank())p.add("township" to t)
        val j=get(*p.toTypedArray())
        val a=j.getJSONArray("members")
        val items=(0 until a.length()).map { i ->
            val r=a.getJSONObject(i)
            Row(r.optLong("id"),r.optString("member_code"),r.optString("name"),
                r.optString("district"),r.optString("township_slug").takeIf { it.isNotBlank() && it!="null" },
                r.optString("photo_url").takeIf { it.startsWith("https://") })
        }
        return Page(j.optInt("total"),j.optInt("page",page),j.optBoolean("has_more"),j.optString("note"),items)
    }
    /** Searches the YCTA SQL mirror by member ID, full/partial name or normalized ID. */
    fun search(query: String, page: Int = 1): Page {
        require(query.trim().length >= 2) { "Enter at least 2 characters" }
        val json = get(
            "action" to "search",
            "q" to query.trim(),
            "page" to page.toString()
        )
        val arr = json.getJSONArray("members")
        val found = (0 until arr.length()).map { i ->
            val r = arr.getJSONObject(i)
            Row(
                r.optLong("id"),
                r.optString("member_code"),
                r.optString("name"),
                r.optString("district"),
                r.optString("township_slug").takeIf { it.isNotBlank() && it != "null" },
                r.optString("photo_url").takeIf { it.startsWith("https://") }
            )
        }
        return Page(json.optInt("total"), json.optInt("page", page),
            json.optBoolean("has_more"), json.optString("note"), found)
    }

    fun member(id: Long): Row {
        val r=get("action" to "member","id" to id.toString()).getJSONObject("member")
        return Row(r.optLong("id"),r.optString("member_code"),r.optString("name"),
          r.optString("district"),r.optString("township_slug").takeIf { it.isNotBlank() && it!="null" },
                r.optString("photo_url").takeIf { it.startsWith("https://") })
    }
}
