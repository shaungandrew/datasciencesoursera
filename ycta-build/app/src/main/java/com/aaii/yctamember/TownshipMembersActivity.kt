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
    private lateinit var repo: YctaRepository
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var heading: TextView
    private lateinit var summary: TextView
    private lateinit var loading: ProgressBar
    private var district: YctaGeography.District? = null
    private var township: YctaGeography.Township? = null
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        repo = YctaRepository(cacheDir)
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
            val selected = township
            if (selected == null) showDistricts() else loadMembers(selected)
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

    private fun showDistricts() {
        generation++
        loading.visibility = View.GONE
        district = null
        township = null
        heading.text = "ခရိုင် ၁၄ ခု"
        summary.text = "14 Districts • 44 Townships"
        content.removeAllViews()
        content.addView(message("ခရိုင်ရွေးပါ • Select a district"))
        geography.districts.forEachIndexed { i, d ->
            content.addView(card(
                (i+1).toString()+". "+d.myanmar,
                d.english+" District • "+d.townships.size+" townships"
            ) { showTownships(d) })
        }
        scroll.scrollTo(0,0)
    }

    private fun showTownships(d: YctaGeography.District) {
        generation++
        loading.visibility = View.GONE
        district = d
        township = null
        heading.text = d.myanmar+" ခရိုင်"
        summary.text = d.english+" • "+d.townships.size+" Townships"
        content.removeAllViews()
        content.addView(message("မြို့နယ်တစ်ခုရွေးပြီး Member တွေကြည့်ပါ။"))
        d.townships.forEachIndexed { i,t ->
            content.addView(card(
                (i+1).toString()+". "+t.myanmar,
                t.english+" Township"
            ) { loadMembers(t) })
        }
        scroll.scrollTo(0,0)
    }

    private fun loadMembers(t: YctaGeography.Township) {
        val d = district ?: return
        val seq = ++generation
        township = t
        loading.visibility = View.VISIBLE
        heading.text = t.myanmar+" မြို့နယ်"
        summary.text = d.english+" District • YCTA Members"
        content.removeAllViews()
        content.addView(message("YCTA member-search website မှရှာနေပါသည်…"))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val records = linkedMapOf<String, MemberSummary>()
                    val failures = mutableListOf<String>()
                    for (query in listOf(t.myanmar,t.english)) {
                        when (val found = repo.search(query)) {
                            is SearchOutcome.Results ->
                                found.members.forEach { records[it.profileUrl] = it }
                            is SearchOutcome.Direct -> {
                                val m = found.member
                                if (geography.matchesTownship(m.district,t)) {
                                    records[m.profileUrl] = MemberSummary(
                                        m.name.ifBlank { "YCTA Member" },
                                        "Member ID: "+m.memberId+" • "+m.district,
                                        m.profileUrl
                                    )
                                }
                            }
                            is SearchOutcome.Failure -> failures.add(found.message)
                        }
                    }
                    Pair(records.values.toList(),failures)
                }
            }
            if (seq != generation) return@launch
            loading.visibility = View.GONE
            content.removeAllViews()
            result.onSuccess { pair ->
                val members = pair.first
                if (members.isEmpty()) {
                    content.addView(message(
                        "ဤမြို့နယ်အတွက် public search results မတွေ့ပါ။" +
                        " This does not mean no members exist."
                    ))
                } else {
                    content.addView(message(
                        members.size.toString()+" website search results." +
                        " Tap a member to verify township and open Smart Card." +
                        " Full member totals require a dedicated township API."
                    ))
                    members.forEach { m ->
                        content.addView(card(
                            m.title,
                            m.subtitle.ifBlank { "YCTA Member • View profile" }
                        ) { openMember(m,t) })
                    }
                }
                if (pair.second.isNotEmpty()) {
                    content.addView(message("Some search queries returned no results."))
                }
            }.onFailure {
                content.addView(message("Website search error: "+(it.message ?: "Network error")))
            }
            scroll.scrollTo(0,0)
        }
    }

    private fun openMember(item: MemberSummary, t: YctaGeography.Township) {
        val seq = generation
        loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repo.fetchByUrl(item.profileUrl) }
            }
            if (seq != generation) return@launch
            loading.visibility = View.GONE
            result.onSuccess { member ->
                if (member.district.isNotBlank() &&
                    !geography.matchesTownship(member.district,t)) {
                    android.app.AlertDialog.Builder(this@TownshipMembersActivity)
                        .setTitle("Township does not match")
                        .setMessage("Selected: "+t.myanmar+
                            "\nProfile: "+member.district+
                            "\nThis member belongs to a different township.")
                        .setPositiveButton("OK",null)
                        .show()
                } else {
                    startActivity(Intent(this@TownshipMembersActivity,MainActivity::class.java)
                        .putExtra("member_profile_url",member.profileUrl))
                }
            }.onFailure {
                Toast.makeText(this@TownshipMembersActivity,
                    "Profile unavailable: "+(it.message ?: "Network error"),
                    Toast.LENGTH_LONG).show()
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
            township != null -> district?.let { showTownships(it) } ?: showDistricts()
            district != null -> showDistricts()
            else -> finish()
        }
    }

    @Deprecated("Android compatibility")
    override fun onBackPressed() = goBack()

    private fun dp(v: Int) = (v*resources.displayMetrics.density).toInt()
}
