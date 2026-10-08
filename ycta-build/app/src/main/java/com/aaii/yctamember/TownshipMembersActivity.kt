package com.aaii.yctamember

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TownshipMembersActivity : ComponentActivity() {
    private val geography = YctaGeography
    private val api = YctaMobileApi()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var heading: TextView
    private lateinit var summary: TextView
    private lateinit var loading: ProgressBar
    private var district: YctaGeography.District? = null
    private var township: YctaGeography.Township? = null
    private var generation = 0
    private var showingMemberList = false
    private var currentPage = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EEF3F8"))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(10))
            setBackgroundColor(Color.parseColor("#103D60"))
        }
        top.addView(TextView(this).apply {
            text = "YCTA • NATIVE MEMBER DIRECTORY"
            textSize = 12f
            setTextColor(Color.parseColor("#A0D3EE"))
        })
        heading = TextView(this).apply {
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        summary = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#C8DFED"))
        }
        top.addView(heading)
        top.addView(summary)
        root.addView(top)
        loading = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(loading, LinearLayout.LayoutParams(dp(32),dp(32)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14),dp(14),dp(14),dp(24))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8),dp(6),dp(8),dp(6))
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }
        bottom.addView(nav("← Back") { goBack() })
        bottom.addView(nav("Districts") { showDistricts() })
        bottom.addView(nav("Refresh") {
            val d = district
            when {
                showingMemberList -> loadPage(township, currentPage)
                d != null -> showTownships(d)
                else -> showDistricts()
            }
        })
        root.addView(bottom)
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(top) { v, ins ->
            val bars = ins.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(dp(14)+bars.left,dp(8)+bars.top,dp(14)+bars.right,dp(10))
            ins
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottom) { v, ins ->
            val bars = ins.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(dp(8)+bars.left,dp(6),dp(8)+bars.right,dp(6)+bars.bottom)
            ins
        }
        ViewCompat.requestApplyInsets(root)
        showDistricts()
    }

    private fun nav(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0,-2,1f)
    }

    private fun slug(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').let {
            if (it == "twante") "twantay" else it
        }

    private fun showDistricts() {
        val seq = ++generation
        loading.visibility = View.VISIBLE
        district = null; township = null
        showingMemberList = false
        heading.text = "ခရိုင် ၁၄ ခု"
        summary.text = "14 Districts • 44 Townships • SQL Mobile API"
        content.removeAllViews()
        content.addView(message("Loading district totals from ycta.aaii.asia…"))
        lifecycleScope.launch {
            val result=withContext(Dispatchers.IO) { runCatching { api.districts() } }
            if (seq != generation) return@launch
            loading.visibility = View.GONE
            content.removeAllViews()
            val totals=result.getOrNull()?.associateBy { it.slug } ?: emptyMap()
            result.onFailure {
                content.addView(message("Mobile API: ${it.message}. Install server ZIP and run sync first."))
            }
            geography.districts.forEachIndexed { i, d ->
                val r=totals[slug(d.english)]
                val description=if(r==null) "API not connected yet"
                  else "${r.count} members • ${r.unassigned} township unassigned"
                content.addView(card("${i+1}. ${d.myanmar}",description) { showTownships(d) })
            }
            scroll.scrollTo(0,0)
        }
    }

    private fun showTownships(d: YctaGeography.District) {
        val seq = ++generation
        district = d; township = null
        showingMemberList = false
        heading.text = d.myanmar+" ခရိုင်"
        summary.text = d.english+" • ${d.townships.size} Townships"
        loading.visibility = View.VISIBLE
        content.removeAllViews()
        content.addView(message("Loading township member counts from SQL API…"))
        lifecycleScope.launch {
            val result=withContext(Dispatchers.IO) { runCatching { api.towns(slug(d.english)) } }
            if(seq!=generation)return@launch
            loading.visibility = View.GONE
            content.removeAllViews()
            result.onSuccess { pair ->
                val counts=pair.first.associateBy { it.slug }
                content.addView(message(
                    "District members with unassigned townships: ${pair.second}. " +
                    "The original SQL stores District only; township assignment is done by Admin."
                ))
                content.addView(card("ALL ${d.myanmar} ခရိုင် Members",
                    "View all members in this district (assigned and unassigned)") {
                    loadPage(null,1)
                })
                d.townships.forEachIndexed { i,t ->
                    val count=counts[slug(t.english)]?.count ?: 0
                    content.addView(card("${i+1}. ${t.myanmar}",
                        "${t.english} Township • $count assigned members") {
                        loadPage(t,1)
                    })
                }
            }.onFailure {
                content.addView(message("API unavailable: ${it.message}. Upload server ZIP and run setup."))
                d.townships.forEach { t ->
                    content.addView(card(t.myanmar,"Load SQL-linked members") {loadPage(t,1)})
                }
            }
            scroll.scrollTo(0,0)
        }
    }

    private fun loadMembers(t: YctaGeography.Township) = loadPage(t,1)

    private fun loadPage(t: YctaGeography.Township?, page: Int) {
        val d=district ?: return
        val seq=++generation
        township=t
        showingMemberList = true
        currentPage = page
        heading.text=(t?.myanmar ?: d.myanmar)+" • Members"
        summary.text="SQL-linked YCTA Directory • Page $page"
        loading.visibility=View.VISIBLE
        content.removeAllViews()
        content.addView(message("Fetching members from ycta.aaii.asia…"))
        lifecycleScope.launch {
            val result=withContext(Dispatchers.IO) {
                runCatching { api.members(slug(d.english),t?.let { slug(it.english) },page) }
            }
            if(seq!=generation)return@launch
            loading.visibility=View.GONE
            content.removeAllViews()
            result.onSuccess { p ->
                content.addView(message("${p.total} matched records • page ${p.page}" +
                     if(p.note.isBlank()) "" else "\n"+p.note))
                if(p.members.isEmpty()) content.addView(message(
                     "No assigned members in this township yet. Admin must map members from the SQL district."
                ))
                p.members.forEach { m ->
                    content.addView(card(m.name.ifBlank { "YCTA Member" },
                        "${m.code} • ${m.district}") { openMember(m.id) })
                }
                if(page>1) content.addView(card("← Previous Page","Page ${page-1}") {
                    loadPage(t,page-1)
                })
                if(p.more) content.addView(card("Next Page →","Load 25 more members") {
                    loadPage(t,page+1)
                })
            }.onFailure {
                content.addView(message("Cannot fetch SQL member list: ${it.message}"))
            }
            scroll.scrollTo(0,0)
        }
    }

    private fun openMember(id: Long) {
        val seq=generation
        loading.visibility=View.VISIBLE
        lifecycleScope.launch {
            val result=withContext(Dispatchers.IO) { runCatching { api.member(id) } }
            if(seq!=generation)return@launch
            loading.visibility=View.GONE
            result.onSuccess {
                startActivity(Intent(this@TownshipMembersActivity,MainActivity::class.java)
                    .putExtra("mobile_api_member_id",id))
            }.onFailure {
                Toast.makeText(this@TownshipMembersActivity,
                    "Member profile unavailable: ${it.message}",Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun card(name: String, extra: String, action: () -> Unit): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15),dp(13),dp(15),dp(13))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1),Color.parseColor("#D9E5EF"))
            }
            elevation = dp(2).toFloat()
            setOnClickListener { action() }
            isClickable = true
            isFocusable = true
        }
        c.addView(TextView(this).apply {
            text = name
            textSize = 18f
            setTypeface(typeface,Typeface.BOLD)
            setTextColor(Color.parseColor("#16436B"))
        })
        c.addView(TextView(this).apply {
            text = extra+"  ›"
            textSize = 13f
            setTextColor(Color.parseColor("#698092"))
            setPadding(0,dp(4),0,0)
        })
        c.layoutParams = LinearLayout.LayoutParams(-1,-2).apply {
            bottomMargin = dp(10)
        }
        return c
    }

    private fun message(msg: String): View = TextView(this).apply {
        text = msg
        textSize = 13f
        setTextColor(Color.parseColor("#566B7D"))
        setPadding(dp(5),dp(8),dp(5),dp(13))
    }

    private fun goBack() {
        when {
            showingMemberList ->
                district?.let { showTownships(it) } ?: showDistricts()
            district != null -> showDistricts()
            else -> finish()
        }
    }

    @Deprecated("Android compatibility")
    override fun onBackPressed() = goBack()

    private fun dp(v: Int) = (v*resources.displayMetrics.density).toInt()
}
