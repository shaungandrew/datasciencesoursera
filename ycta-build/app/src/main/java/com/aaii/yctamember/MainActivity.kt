package com.aaii.yctamember

import android.app.AlertDialog
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
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var searchArea: LinearLayout
    private lateinit var profileArea: LinearLayout
    private lateinit var input: EditText
    private lateinit var searchBtn: Button
    private lateinit var scanBtn: Button
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var results: LinearLayout
    private val repository = YctaRepository()

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        val value = result.contents ?: return@registerForActivityResult
        input.setText(value)
        doSearch(value)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0B3B70")
        window.navigationBarColor = Color.WHITE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
            setBackgroundColor(Color.parseColor("#F4F7FB"))
        }

        searchArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        searchArea.addView(TextView(this).apply {
            text = "YCTA Member"
            textSize = 30f
            setTextColor(Color.parseColor("#123A63"))
            setTypeface(typeface, Typeface.BOLD)
        })
        searchArea.addView(TextView(this).apply {
            text = "Member Search • QR Scanner • Native Member Profile"
            textSize = 14f
            setTextColor(Color.parseColor("#60758A"))
            setPadding(0, dp(4), 0, dp(14))
        })

        val searchCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = dp(3).toFloat()
            background = rounded(Color.WHITE, 18f, "#D9E3EE", 1)
        }

        input = EditText(this).apply {
            hint = "Member ID / Name / Profile URL"
            textSize = 16f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.parseColor("#F7F9FC"), 14f, "#CED9E4", 1)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    doSearch(text.toString())
                    true
                } else false
            }
        }
        searchCard.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        searchBtn = Button(this).apply {
            text = "Search"
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1467B8"))
            setOnClickListener { doSearch(input.text.toString()) }
        }
        scanBtn = Button(this).apply {
            text = "Scan QR"
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0A8F73"))
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
        searchArea.addView(searchCard)

        progress = ProgressBar(this).apply { visibility = View.GONE }
        searchArea.addView(progress, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(10)
        })

        status = TextView(this).apply {
            text = "Search 45/0118, 450118, ycta450118, a member name, or scan QR."
            textSize = 13f
            setTextColor(Color.parseColor("#60758A"))
            setPadding(0, dp(12), 0, dp(10))
        }
        searchArea.addView(status)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        searchArea.addView(results)

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
    }

    private fun doSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) {
            status.text = "Please enter a member ID, name, or profile URL."
            return
        }
        loading(true)
        results.removeAllViews()
        status.text = "Searching member…"

        lifecycleScope.launch {
            when (val found = withContext(Dispatchers.IO) { repository.search(q) }) {
                is SearchOutcome.Direct -> showProfile(found.member)
                is SearchOutcome.Results -> {
                    status.text = found.members.size.toString() + " member(s) found."
                    found.members.forEach { item -> renderSearchResult(item) }
                }
                is SearchOutcome.Failure -> status.text = found.message
            }
            loading(false)
        }
    }

    private fun renderSearchResult(item: MemberSummary) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = dp(2).toFloat()
            background = rounded(Color.WHITE, 16f, "#DEE6EE", 1)
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
            result.onSuccess { showProfile(it) }
                .onFailure { status.text = "Unable to load profile: " + (it.message ?: "Unknown error") }
            loading(false)
        }
    }

    private fun showProfile(m: Member) {
        searchArea.visibility = View.GONE
        profileArea.visibility = View.VISIBLE
        profileArea.removeAllViews()

        profileArea.addView(Button(this).apply {
            text = "← Back to Search"
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#245A8D"))
            setOnClickListener {
                profileArea.visibility = View.GONE
                searchArea.visibility = View.VISIBLE
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(22), dp(18), dp(20))
            elevation = dp(5).toFloat()
            background = gradientHero()
        }

        val avatarFrame = FrameLayout(this).apply {
            background = rounded(Color.WHITE, 56f, "#FFFFFF", 0)
            elevation = dp(3).toFloat()
        }
        val initials = TextView(this).apply {
            text = initials(m.name)
            gravity = Gravity.CENTER
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#155D9B"))
        }
        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            background = rounded(Color.WHITE, 56f, "#FFFFFF", 0)
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        avatarFrame.addView(initials, FrameLayout.LayoutParams(dp(104), dp(104)))
        avatarFrame.addView(photo, FrameLayout.LayoutParams(dp(104), dp(104)))
        hero.addView(avatarFrame, LinearLayout.LayoutParams(dp(104), dp(104)).apply { bottomMargin = dp(12) })

        hero.addView(TextView(this).apply {
            text = m.name.ifBlank { "YCTA Member" }
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        hero.addView(TextView(this).apply {
            text = if (m.memberId.isBlank()) "Verified Member Profile" else "Member ID • ${m.memberId}"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#D7ECFF"))
            setPadding(0, dp(5), 0, 0)
        })

        profileArea.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        if (m.photoUrl.isNotBlank()) {
            lifecycleScope.launch {
                val bytes = withContext(Dispatchers.IO) { repository.fetchImage(m.photoUrl) }
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        photo.setImageBitmap(bitmap)
                        photo.visibility = View.VISIBLE
                    }
                }
            }
        }

        val info = cardContainer()
        info.addView(sectionTitle("PROFILE DATA"))
        addRow(info, "Name", m.name)
        addRow(info, "Member ID", m.memberId)
        addRow(info, "Driver License", m.driverLicense)
        addRow(info, "Joined Date", m.joinedDate)
        addRow(info, "Vehicle No", m.vehicleNo)
        addRow(info, "City No", m.cityNo)
        addRow(info, "District / Township", m.district)
        profileArea.addView(info, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val privacy = cardContainer()
        privacy.addView(sectionTitle("PRIVATE INFORMATION"))
        addRow(privacy, "Phone", m.maskedPhone)
        addRow(privacy, "NRC", m.maskedNrc)
        addRow(privacy, "Address", m.maskedAddress)
        privacy.addView(Button(this).apply {
            text = "Show private information"
            isAllCaps = false
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Protected member information")
                    .setMessage("Full phone, NRC and address are not exposed by this public APK. An authorized YCTA login/API can be connected for controlled access.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        })
        profileArea.addView(privacy, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val qrCard = cardContainer()
        qrCard.gravity = Gravity.CENTER_HORIZONTAL
        qrCard.addView(sectionTitle("QR ID CARD"))
        val qrImage = ImageView(this)
        runCatching {
            qrImage.setImageBitmap(BarcodeEncoder().encodeBitmap(m.profileUrl, BarcodeFormat.QR_CODE, 640, 640))
        }
        qrCard.addView(qrImage, LinearLayout.LayoutParams(dp(220), dp(220)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(6)
            bottomMargin = dp(8)
        })
        qrCard.addView(TextView(this).apply {
            text = m.name.ifBlank { "YCTA Member" }
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#153D63"))
        })
        qrCard.addView(TextView(this).apply {
            text = if (m.memberId.isBlank()) "Scan to open member profile" else "ID: ${m.memberId} • Scan to open profile"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#63798E"))
            setPadding(0, dp(4), 0, 0)
        })
        profileArea.addView(qrCard)
    }

    private fun cardContainer() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        elevation = dp(3).toFloat()
        background = rounded(Color.WHITE, 18f, "#DDE6EF", 1)
    }

    private fun sectionTitle(title: String) = TextView(this).apply {
        text = title
        textSize = 13f
        letterSpacing = 0.08f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#1E639C"))
        setPadding(0, 0, 0, dp(10))
    }

    private fun addRow(parent: LinearLayout, label: String, value: String) {
        if (value.isBlank()) return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(8))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#748596"))
            setTypeface(typeface, Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 17f
            setTextColor(Color.parseColor("#1A2F43"))
            setPadding(0, dp(2), 0, 0)
        })
        parent.addView(row)
    }

    private fun loading(v: Boolean) {
        progress.visibility = if (v) View.VISIBLE else View.GONE
        searchBtn.isEnabled = !v
        scanBtn.isEnabled = !v
    }

    private fun initials(name: String): String {
        val p = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            p.isEmpty() -> "Y"
            p.size == 1 -> p[0].take(2).uppercase()
            else -> (p.first().take(1) + p.last().take(1)).uppercase()
        }
    }

    private fun rounded(fill: Int, radiusDp: Float, strokeHex: String, strokeDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), Color.parseColor(strokeHex))
        }
    }

    private fun gradientHero(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#0E4E86"), Color.parseColor("#1583BC"))
        ).apply { cornerRadius = dp(22).toFloat() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
