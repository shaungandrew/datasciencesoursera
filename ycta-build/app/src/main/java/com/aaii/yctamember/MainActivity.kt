package com.aaii.yctamember

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

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
    private var currentMember: Member? = null
    private var revealPrivate = false

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        val value = result.contents ?: return@registerForActivityResult
        input.setText(value)
        doSearch(value)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }

        searchArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        searchArea.addView(TextView(this).apply {
            text = "YCTA Member"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })
        searchArea.addView(TextView(this).apply {
            text = "Member Search • QR Scanner • Native Profile"
            setPadding(0, dp(4), 0, dp(12))
        })

        input = EditText(this).apply {
            hint = "Member ID / Name / Profile URL"
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    doSearch(text.toString())
                    true
                } else false
            }
        }
        searchArea.addView(input)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        searchBtn = Button(this).apply {
            text = "Search"
            isAllCaps = false
            setOnClickListener { doSearch(input.text.toString()) }
        }
        scanBtn = Button(this).apply {
            text = "Scan QR"
            isAllCaps = false
            setOnClickListener {
                qrLauncher.launch(
                    ScanOptions()
                        .setPrompt("Scan YCTA member QR / profile URL")
                        .setBeepEnabled(true)
                        .setOrientationLocked(false)
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                )
            }
        }
        actions.addView(searchBtn, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(scanBtn, LinearLayout.LayoutParams(0, -2, 1f))
        searchArea.addView(actions)

        progress = ProgressBar(this).apply { visibility = View.GONE }
        searchArea.addView(progress, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        status = TextView(this).apply {
            text = "Search 45/0118, 450118, ycta450118, a name, or scan QR."
            setPadding(0, dp(8), 0, dp(8))
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
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun doSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) {
            status.text = "Please enter a member ID, name, or URL."
            return
        }
        loading(true)
        results.removeAllViews()
        status.text = "Searching…"

        lifecycleScope.launch {
            when (val found = withContext(Dispatchers.IO) { repository.search(q) }) {
                is SearchOutcome.Direct -> showProfile(found.member)
                is SearchOutcome.Results -> {
                    status.text = found.members.size.toString() + " result(s) found."
                    found.members.forEach { item ->
                        results.addView(Button(this@MainActivity).apply {
                            isAllCaps = false
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            text = if (item.subtitle.isBlank()) item.title else item.title + "\n" + item.subtitle
                            setOnClickListener { loadProfile(item.profileUrl) }
                        })
                    }
                }
                is SearchOutcome.Failure -> status.text = found.message
            }
            loading(false)
        }
    }

    private fun loadProfile(url: String) {
        loading(true)
        status.text = "Loading profile…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.fetchByUrl(url) } }
            result.onSuccess { showProfile(it) }
                .onFailure { status.text = "Unable to load profile: " + (it.message ?: "Unknown error") }
            loading(false)
        }
    }

    private fun showProfile(m: Member) {
        currentMember = m
        revealPrivate = false
        renderProfile()
        searchArea.visibility = View.GONE
        profileArea.visibility = View.VISIBLE
    }

    private fun renderProfile() {
        val m = currentMember ?: return
        profileArea.removeAllViews()

        profileArea.addView(Button(this).apply {
            text = "← Back to Search"
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener {
                profileArea.visibility = View.GONE
                searchArea.visibility = View.VISIBLE
            }
        })

        profileArea.addView(TextView(this).apply {
            text = "Member Profile"
            textSize = 14f
            setPadding(0, dp(12), 0, dp(2))
        })
        profileArea.addView(TextView(this).apply {
            text = m.name.ifBlank { "YCTA Member" }
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        })

        if (m.photoUrl.isNotBlank()) {
            val image = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP
                minimumHeight = dp(180)
            }
            profileArea.addView(image, LinearLayout.LayoutParams(-1, dp(220)).apply {
                bottomMargin = dp(14)
            })
            lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching { URL(m.photoUrl).openStream().use(BitmapFactory::decodeStream) }.getOrNull()
                }
                if (bitmap != null) image.setImageBitmap(bitmap) else image.visibility = View.GONE
            }
        }

        section("Membership")
        field("ID.No", m.memberId)
        field("Driver License", m.driverLicense)
        field("Joined Date", m.joinedDate)

        section("Vehicle")
        field("Vehicle No", m.vehicleNo)
        field("City No", m.cityNo)

        section("Location")
        field("District / Township", m.district)

        section("Contact & Identity")
        field("Phone", if (revealPrivate) m.phone else maskPhone(m.phone))
        field("NRC", if (revealPrivate) m.nrc else maskGeneric(m.nrc))
        field("Address", if (revealPrivate) m.address else maskAddress(m.address))

        if (m.phone.isNotBlank() || m.nrc.isNotBlank() || m.address.isNotBlank()) {
            profileArea.addView(Button(this).apply {
                isAllCaps = false
                text = if (revealPrivate) "Hide private information" else "Show private information"
                setOnClickListener {
                    revealPrivate = !revealPrivate
                    renderProfile()
                }
            })
        }

        if (m.cvUrl.isNotBlank()) {
            section("CV")
            field("CV", "Available on member record")
        }

        profileArea.addView(TextView(this).apply {
            text = "Profile data is loaded from the official YCTA member page and displayed natively inside this app."
            textSize = 12f
            setPadding(0, dp(16), 0, dp(8))
        })
    }

    private fun section(title: String) {
        profileArea.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(8))
        })
    }

    private fun field(label: String, value: String) {
        if (value.isBlank()) return
        profileArea.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
        })
        profileArea.addView(TextView(this).apply {
            text = value
            textSize = 17f
            setPadding(0, dp(2), 0, dp(12))
        })
    }

    private fun maskPhone(v: String): String {
        if (v.isBlank()) return ""
        return if (v.length > 6) v.take(3) + "••••" + v.takeLast(3) else maskGeneric(v)
    }

    private fun maskGeneric(v: String): String {
        if (v.isBlank()) return ""
        return if (v.length > 4) v.take(2) + "••••••" + v.takeLast(2) else "••••"
    }

    private fun maskAddress(v: String) = if (v.isBlank()) "" else "•••••••••• (hidden)"

    private fun loading(v: Boolean) {
        progress.visibility = if (v) View.VISIBLE else View.GONE
        searchBtn.isEnabled = !v
        scanBtn.isEnabled = !v
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
