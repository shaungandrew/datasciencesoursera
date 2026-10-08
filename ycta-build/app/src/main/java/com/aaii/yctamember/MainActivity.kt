package com.aaii.yctamember

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var searchArea: LinearLayout
    private lateinit var profileArea: LinearLayout
    private lateinit var input: EditText
    private lateinit var searchBtn: Button
    private lateinit var scanBtn: Button
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var results: LinearLayout
    private lateinit var liveCount: TextView
    private val mobileApi = YctaMobileApi()
    private lateinit var repository: YctaRepository

    private var currentMember: Member? = null
    private var currentQr: Bitmap? = null
    private var currentPhoto: Bitmap? = null

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        val value = result.contents ?: return@registerForActivityResult
        input.setText(value)
        doSearch(value)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = YctaRepository(cacheDir)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.parseColor("#082F55")
        window.navigationBarColor = Color.WHITE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(28))
            setBackgroundColor(Color.parseColor("#EEF3F8"))
        }

        searchArea = buildSearchArea()
        profileArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        root.addView(searchArea)
        root.addView(profileArea)

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(dp(14) + top.left, dp(14) + top.top,
                dp(14) + top.right, dp(28) + bottom.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        refreshHomeCounts()

        val apiMemberId = intent?.getLongExtra("mobile_api_member_id", -1L) ?: -1L
        if (apiMemberId > 0L) {
            openApiMember(apiMemberId)
        } else {
            intent?.getStringExtra("member_profile_url")
                ?.takeIf { it.isNotBlank() }
                ?.let { memberUrl -> doSearch(memberUrl) }
        }
    }

    private fun buildSearchArea(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            val header = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(18), dp(14), dp(18))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor("#0B3658"), Color.parseColor("#116D95"))
                ).apply { cornerRadius = dp(22).toFloat() }
                elevation = dp(4).toFloat()
            }
            header.addView(yctaLogo(76), LinearLayout.LayoutParams(dp(76), dp(76)).apply {
                marginEnd = dp(10)
            })
            val headerTitles = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }
            headerTitles.addView(TextView(this@MainActivity).apply {
                text = "YANGON CITY TAXI ASSOCIATION"
                textSize = 12f
                letterSpacing = 0.04f
                setTextColor(Color.parseColor("#B7E8FF"))
                setTypeface(typeface, Typeface.BOLD)
            })
            headerTitles.addView(TextView(this@MainActivity).apply {
                text = "YCTA • Member Hub"
                textSize = 26f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            })
            header.addView(headerTitles, LinearLayout.LayoutParams(0, -2, 1f))
            addView(header)

            liveCount = TextView(this@MainActivity).apply {
                text = "Connecting to YCTA SQL member directory…"
                textSize = 12.5f
                setTextColor(Color.parseColor("#4C6980"))
                setPadding(dp(3), dp(13), dp(3), dp(9))
            }
            addView(liveCount)

            addView(TextView(this@MainActivity).apply {
                text = "Search member or scan QR to open the native smart card."
                textSize = 14f
                setTextColor(Color.parseColor("#60758A"))
                setPadding(0, dp(4), 0, dp(14))
            })

            val searchCard = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                elevation = dp(3).toFloat()
                background = rounded(Color.WHITE, 18, "#D5E0EB", 1)
            }

            input = EditText(this@MainActivity).apply {
                hint = "Search YCTA SQL by Member ID / Name"
                textSize = 16f
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(Color.parseColor("#F7F9FC"), 14, "#CBD8E4", 1)
                setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_SEARCH) {
                        doSearch(text.toString())
                        true
                    } else false
                }
            }
            searchCard.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

            val actions = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }

            searchBtn = Button(this@MainActivity).apply {
                text = "Search"
                isAllCaps = false
                setTextColor(Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1467B8"))
                setOnClickListener { doSearch(input.text.toString()) }
            }

            scanBtn = Button(this@MainActivity).apply {
                text = "Scan QR"
                isAllCaps = false
                setTextColor(Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0B8A70"))
                setOnClickListener {
                    qrLauncher.launch(
                        ScanOptions()
                            .setPrompt("Scan YCTA Member QR")
                            .setBeepEnabled(true)
                            .setOrientationLocked(false)
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    )
                }
            }

            actions.addView(searchBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
            actions.addView(scanBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
            searchCard.addView(actions)
            addView(searchCard)

            addView(TextView(this@MainActivity).apply {
                text = "QUICK ACCESS"
                textSize = 12f
                letterSpacing = 0.1f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#70879C"))
                setPadding(dp(3), dp(19), 0, dp(10))
            })

            val first = dashboardRow(
                dashboardCard("📍", "Districts & Townships",
                    "14 ခရိုင် • 44 မြို့နယ်", "#155C9C") {
                    startActivity(Intent(this@MainActivity, TownshipMembersActivity::class.java))
                },
                dashboardCard("🔎", "Search Member",
                    "SQL Member ID / Name", "#007B7F") {
                    input.requestFocus()
                    status.text = "Enter a member ID or name above to search YCTA database."
                }
            )
            addView(first)
            addView(dashboardRow(
                dashboardCard("▣", "QR Scanner",
                    "Scan YCTA Member Card", "#138167") { scanBtn.performClick() },
                dashboardCard("📚", "eLibrary",
                    "PDF • EPUB • Reader", "#6755A7") {
                    startActivity(Intent(this@MainActivity, LibraryActivity::class.java))
                }
            ))
            addView(dashboardRow(
                dashboardCard("🎓", "MOOC Courses",
                    "Learning • Video Player", "#C58616") {
                    startActivity(Intent(this@MainActivity, MoocActivity::class.java))
                },
                dashboardCard("🌐", "YCTA Website",
                    "ycta.aaii.asia", "#485A88") {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://ycta.aaii.asia/")))
                }
            ))
            addView(TextView(this@MainActivity).apply {
                text = "All member search and district counts come from the YCTA SQL Mobile API."
                textSize = 12f
                setTextColor(Color.parseColor("#72859B"))
                setPadding(dp(3), dp(10), dp(3), dp(8))
            })

            progress = ProgressBar(this@MainActivity).apply { visibility = View.GONE }
            addView(progress, LinearLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(10)
            })

            status = TextView(this@MainActivity).apply {
                text = "SQL Search: Member ID, Member Name or Scan QR."
                textSize = 13f
                setTextColor(Color.parseColor("#60758A"))
                setPadding(0, dp(12), 0, dp(10))
            }
            addView(status)

            results = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(results)
        }
    }

    private fun dashboardRow(left: View, right: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, dp(126), 1f).apply {
                marginEnd = dp(6)
            })
            addView(right, LinearLayout.LayoutParams(0, dp(126), 1f).apply {
                marginStart = dp(6)
            })
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            }
        }

    private fun dashboardCard(iconText: String, name: String, caption: String,
                              accent: String, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(11), dp(8), dp(11))
            background = rounded(Color.WHITE, 18, "#DAE5EF", 1)
            elevation = dp(3).toFloat()
            setOnClickListener { action() }
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            }
            addView(TextView(this@MainActivity).apply {
                text = iconText
                textSize = 22f
                setTextColor(Color.parseColor(accent))
            })
            addView(TextView(this@MainActivity).apply {
                text = name
                textSize = 15f
                maxLines = 2
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#193A59"))
                setPadding(0, dp(4), 0, dp(2))
            })
            addView(TextView(this@MainActivity).apply {
                text = caption
                textSize = 11f
                maxLines = 2
                setTextColor(Color.parseColor("#647C92"))
            })
        }

    private fun refreshHomeCounts() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { mobileApi.districts() } }
            result.onSuccess { list ->
                val mapped = list.sumOf { it.count }
                val assigned = list.sumOf { (it.count - it.unassigned).coerceAtLeast(0) }
                liveCount.text = "$mapped district-linked members   •   $assigned township-assigned   •   14 districts"
            }.onFailure {
                liveCount.text = "Mobile API unavailable • Install YCTA API V1.5 and run SQL Sync"
            }
        }
    }

    private fun doSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) {
            status.text = "Enter a member ID or name."
            return
        }
        val directId = if (q.contains("mobile-api/v1/") && q.contains("action=member")) {
            Regex("[?&]id=(\\d+)").find(q)?.groupValues?.getOrNull(1)?.toLongOrNull()
        } else null
        if (directId != null) {
            openApiMember(directId)
            return
        }
        // QR codes issued by the old YCTA WordPress directory can still
        // open their original member profile. All ordinary searches use SQL.
        if (q.startsWith("https://") || q.startsWith("http://")) {
            searchLegacyQr(q)
            return
        }
        searchSqlPage(q, 1)
    }

    private fun searchSqlPage(q: String, page: Int) {
        loading(true)
        results.removeAllViews()
        status.text = "Searching YCTA member database • page $page…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { mobileApi.search(q, page) }
            }
            result.onSuccess { p ->
                status.text = "${p.total} SQL matching members • page ${p.page}"
                if (p.members.isEmpty()) {
                    status.text = "No matching members in SQL. Try another name or member ID."
                }
                p.members.forEach { row ->
                    val sub = "${row.code}   •   ${row.district}" +
                        (row.township?.let { "   •   $it" } ?: "   •   Township unassigned")
                    val item = MemberSummary(
                        row.name.ifBlank { "YCTA Member" }, sub,
                        row.id.toString()
                    )
                    val c = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        elevation = dp(2).toFloat()
                        background = rounded(Color.WHITE, 16, "#D9E3EC", 1)
                        setOnClickListener { openApiMember(row.id) }
                        isClickable = true
                        isFocusable = true
                    }
                    c.addView(TextView(this@MainActivity).apply {
                        text = item.title
                        textSize = 17f
                        setTextColor(Color.parseColor("#143C5C"))
                        setTypeface(typeface, Typeface.BOLD)
                    })
                    c.addView(TextView(this@MainActivity).apply {
                        text = item.subtitle
                        textSize = 12f
                        setTextColor(Color.parseColor("#677E91"))
                        setPadding(0, dp(5), 0, 0)
                    })
                    results.addView(c, LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(10)
                    })
                }
                if (page > 1) {
                    results.addView(Button(this@MainActivity).apply {
                        text = "← Previous results"
                        isAllCaps = false
                        setOnClickListener { searchSqlPage(q, page - 1) }
                    })
                }
                if (p.more) {
                    results.addView(Button(this@MainActivity).apply {
                        text = "Load next 25 results →"
                        isAllCaps = false
                        setOnClickListener { searchSqlPage(q, page + 1) }
                    })
                }
            }.onFailure {
                status.text = "YCTA SQL Mobile API search failed: ${it.message}. " +
                    "Deploy API V1.5 on ycta.aaii.asia and finish Sync SQL Members."
            }
            loading(false)
        }
    }

    private fun searchLegacyQr(q: String) {
        loading(true)
        status.text = "Verifying older YCTA member QR…"
        results.removeAllViews()
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { repository.search(q) }
            when (found) {
                is SearchOutcome.Direct -> openSmartCard(found.member)
                is SearchOutcome.Results -> {
                    status.text = "${found.members.size} legacy QR matches."
                    found.members.forEach(::renderSearchResult)
                }
                is SearchOutcome.Failure -> status.text = found.message
            }
            loading(false)
        }
    }

    private fun openApiMember(id: Long) {
        loading(true)
        status.text = "Loading YCTA SQL member profile…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { mobileApi.member(id).asMember() }
            }
            result.onSuccess(::openSmartCard)
                .onFailure {
                    status.text = "SQL member profile unavailable: ${it.message}"
                }
            loading(false)
        }
    }

    private fun renderSearchResult(item: MemberSummary) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = dp(2).toFloat()
            background = rounded(Color.WHITE, 16, "#D9E3EC", 1)
            isClickable = true
            isFocusable = true
            setOnClickListener { loadProfile(item.profileUrl) }
        }

        card.addView(TextView(this).apply {
            text = item.title
            textSize = 17f
            setTextColor(Color.parseColor("#173A5E"))
            setTypeface(typeface, Typeface.BOLD)
        })

        if (item.subtitle.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = item.subtitle
                textSize = 13f
                setTextColor(Color.parseColor("#66798C"))
                setPadding(0, dp(4), 0, 0)
            })
        }

        results.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun loadProfile(url: String) {
        loading(true)
        status.text = "Loading member profile…"

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.fetchByUrl(url) } }
            result.onSuccess(::openSmartCard)
                .onFailure { status.text = "Unable to load profile: " + (it.message ?: "Unknown error") }
            loading(false)
        }
    }

    private fun openSmartCard(member: Member) {
        currentMember = member
        currentPhoto = null
        currentQr = makeQr(member.profileUrl)

        searchArea.visibility = View.GONE
        profileArea.visibility = View.VISIBLE
        renderSmartCard(member)

        if (member.photoUrls.isNotEmpty()) {
            lifecycleScope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    repository.fetchBestImage(member.photoUrls, member.profileUrl)
                }
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        currentPhoto = bitmap
                        currentMember?.let(::renderSmartCard)
                    }
                }
            }
        }
    }

    private fun renderSmartCard(m: Member) {
        profileArea.removeAllViews()

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        nav.addView(Button(this).apply {
            text = "← Search"
            isAllCaps = false
            setTextColor(Color.parseColor("#245A8D"))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            setOnClickListener {
                profileArea.visibility = View.GONE
                searchArea.visibility = View.VISIBLE
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))

        nav.addView(Button(this).apply {
            text = "Sync"
            isAllCaps = false
            setOnClickListener { refreshCurrent() }
        }, LinearLayout.LayoutParams(-2, -2))
        profileArea.addView(nav)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            elevation = dp(8).toFloat()
            background = smartCardGradient()
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        titleRow.addView(yctaLogo(66), LinearLayout.LayoutParams(dp(66), dp(66)).apply {
            marginEnd = dp(9)
        })

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        brand.addView(TextView(this).apply {
            text = "YCTA TAXI"
            textSize = 12f
            letterSpacing = 0.18f
            setTextColor(Color.parseColor("#BFE8FF"))
            setTypeface(typeface, Typeface.BOLD)
        })
        brand.addView(TextView(this).apply {
            text = "DIGITAL MEMBER CARD"
            textSize = 19f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        titleRow.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        titleRow.addView(TextView(this).apply {
            text = "SMART"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#14334E"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = rounded(Color.parseColor("#D8B867"), 12, "#F5E7B9", 1)
        })
        card.addView(titleRow)

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, dp(14))
        }
        chipRow.addView(buildChip(), LinearLayout.LayoutParams(dp(50), dp(38)))
        chipRow.addView(TextView(this).apply {
            text = "Official profile source • session protected"
            textSize = 11f
            setTextColor(Color.parseColor("#C8E7F7"))
            setPadding(dp(10), 0, 0, 0)
        })
        card.addView(chipRow)

        val identityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val avatar = FrameLayout(this).apply {
            background = rounded(Color.WHITE, 18, "#FFFFFF", 0)
            elevation = dp(3).toFloat()
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }

        val initials = TextView(this).apply {
            text = initials(m.name)
            gravity = Gravity.CENTER
            textSize = 25f
            setTextColor(Color.parseColor("#155D9B"))
            setTypeface(typeface, Typeface.BOLD)
        }
        avatar.addView(initials, FrameLayout.LayoutParams(dp(92), dp(110)))

        currentPhoto?.let { bitmap ->
            avatar.addView(ImageView(this).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
            }, FrameLayout.LayoutParams(dp(92), dp(110)))
        }

        identityRow.addView(avatar, LinearLayout.LayoutParams(dp(92), dp(110)).apply { marginEnd = dp(14) })

        val identity = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        identity.addView(TextView(this).apply {
            text = m.name.ifBlank { "YCTA Member" }
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        identity.addView(TextView(this).apply {
            text = "Member ID  " + display(m.memberId)
            textSize = 14f
            setTextColor(Color.parseColor("#D3EFFF"))
            setPadding(0, dp(5), 0, 0)
            setOnLongClickListener {
                copyMemberId(m.memberId)
                true
            }
        })
        identity.addView(TextView(this).apply {
            text = "Taxi Digital Identity"
            textSize = 12f
            setTextColor(Color.parseColor("#9BD7F5"))
            setPadding(0, dp(7), 0, 0)
        })
        identityRow.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))

        val qr = currentQr
        if (qr != null) {
            val qrView = ImageView(this).apply {
                setImageBitmap(qr)
                setBackgroundColor(Color.WHITE)
                setPadding(dp(5), dp(5), dp(5), dp(5))
                setOnClickListener { showLargeQr(qr, m) }
            }
            identityRow.addView(qrView, LinearLayout.LayoutParams(dp(92), dp(92)).apply { marginStart = dp(8) })
        }

        card.addView(identityRow)

        card.addView(divider())

        val dataPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        addSmartRow(dataPanel, "Driver License", display(m.driverLicense))
        addSmartRow(dataPanel, "Joined Date", display(m.joinedDate))
        addSmartRow(dataPanel, "Vehicle No", display(m.vehicleNo))
        addSmartRow(dataPanel, "City No", display(m.cityNo))
        addSmartRow(dataPanel, "District / Township", display(m.district))
        card.addView(dataPanel)

        card.addView(divider())

        val privateTitle = TextView(this).apply {
            text = "PRIVATE INFORMATION"
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(Color.parseColor("#9BD7F5"))
            setTypeface(typeface, Typeface.BOLD)
        }
        card.addView(privateTitle)

        val privateBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        addSmartRow(privateBlock, "Phone", m.maskedPhone)
        addSmartRow(privateBlock, "NRC", m.maskedNrc)
        addSmartRow(privateBlock, "Address", m.maskedAddress)
        card.addView(privateBlock)

        card.addView(Button(this).apply {
            text = "Show private information"
            isAllCaps = false
            setTextColor(Color.parseColor("#123A63"))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E6F3FA"))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Protected member information")
                    .setMessage("Phone, NRC and address stay masked in this public APK. Full values require an authorized YCTA account or API.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        card.addView(TextView(this).apply {
            text = "QR ID • Tap QR to enlarge   |   Photo cache enabled   |   Synced " + syncTime()
            textSize = 10.5f
            setTextColor(Color.parseColor("#B9DEEF"))
            setPadding(0, dp(12), 0, 0)
        })

        profileArea.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val helper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, 16, "#D7E2EC", 1)
            elevation = dp(2).toFloat()
        }
        helper.addView(TextView(this).apply {
            text = "SMART CARD FUNCTIONS"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1F659A"))
        })
        helper.addView(TextView(this).apply {
            text = "• QR Scan → opens member card\n• Session cookies support protected photo requests\n• Multiple photo selectors with fallback\n• Disk image cache for faster repeat viewing\n• Sync button refreshes current profile\n• Long-press Member ID to copy"
            textSize = 14f
            setTextColor(Color.parseColor("#40586D"))
            setPadding(0, dp(8), 0, 0)
        })
        profileArea.addView(helper)
    }

    private fun refreshCurrent() {
        val url = currentMember?.profileUrl ?: return
        Toast.makeText(this, "Syncing member profile…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.fetchByUrl(url) } }
            result.onSuccess(::openSmartCard)
                .onFailure {
                    Toast.makeText(this@MainActivity, "Sync failed: " + (it.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun showLargeQr(bitmap: Bitmap, member: Member) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        box.addView(ImageView(this).apply { setImageBitmap(bitmap) }, LinearLayout.LayoutParams(dp(280), dp(280)))
        box.addView(TextView(this).apply {
            text = member.name.ifBlank { "YCTA Member" } + "\nID: " + display(member.memberId)
            gravity = Gravity.CENTER
            textSize = 16f
            setPadding(0, dp(8), 0, 0)
        })
        AlertDialog.Builder(this)
            .setTitle("YCTA Digital Member QR")
            .setView(box)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun makeQr(value: String): Bitmap? = runCatching {
        BarcodeEncoder().encodeBitmap(value, BarcodeFormat.QR_CODE, 640, 640)
    }.getOrNull()

    private fun buildChip(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(Color.parseColor("#D6B65D"), 8, "#F5E6A8", 1)
            repeat(3) {
                addView(View(this@MainActivity).apply {
                    setBackgroundColor(Color.parseColor("#8C742D"))
                }, LinearLayout.LayoutParams(-1, dp(2)).apply { topMargin = dp(2); bottomMargin = dp(2) })
            }
        }
    }

    private fun addSmartRow(parent: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#A9D7ED"))
        }, LinearLayout.LayoutParams(0, -2, 0.42f))

        row.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 0.58f))

        parent.addView(row)
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#337CA5C5"))
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(14)
            bottomMargin = dp(14)
        }
    }

    private fun copyMemberId(value: String) {
        if (value.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("YCTA Member ID", value))
        Toast.makeText(this, "Member ID copied", Toast.LENGTH_SHORT).show()
    }

    private fun loading(v: Boolean) {
        progress.visibility = if (v) View.VISIBLE else View.GONE
        searchBtn.isEnabled = !v
        scanBtn.isEnabled = !v
    }

    private fun display(value: String): String = value.ifBlank { "—" }

    private fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "Y"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
        }
    }

    private fun syncTime(): String =
        SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date())

    private fun rounded(fill: Int, radiusDp: Int, strokeHex: String, strokeDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), Color.parseColor(strokeHex))
        }
    }

    private fun yctaLogo(sizeDp: Int): ImageView =
        ImageView(this).apply {
            setImageResource(R.drawable.ycta_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Yangon City Taxi Association logo"
            adjustViewBounds = false
            minimumWidth = dp(sizeDp)
            minimumHeight = dp(sizeDp)
        }

    private fun smartCardGradient(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#082F55"),
                Color.parseColor("#0C5E8B"),
                Color.parseColor("#0B8093")
            )
        ).apply {
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), Color.parseColor("#4AAAC0"))
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
