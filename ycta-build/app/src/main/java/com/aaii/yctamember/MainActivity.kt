package com.aaii.yctamember

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
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

class MainActivity : ComponentActivity() {
    private lateinit var input: EditText
    private lateinit var searchBtn: Button
    private lateinit var scanBtn: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var detail: LinearLayout
    private val repository = YctaRepository()

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        val value = result.contents ?: return@registerForActivityResult
        input.setText(value)
        doSearch(value)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(28))
        }

        body.addView(TextView(this).apply {
            text = "YCTA Member"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })
        body.addView(TextView(this).apply {
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
        body.addView(input)

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
        body.addView(actions)

        progress = ProgressBar(this).apply { visibility = View.GONE }
        body.addView(progress, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        status = TextView(this).apply {
            text = "Search 45/0118, 450118, ycta450118, a name, or scan QR."
            setPadding(0, dp(8), 0, dp(8))
        }
        body.addView(status)

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        detail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        body.addView(results)
        body.addView(detail)

        setContentView(ScrollView(this).apply { addView(body) })
    }

    private fun doSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) {
            status.text = "Please enter a member ID, name, or URL."
            return
        }
        loading(true)
        results.removeAllViews()
        detail.removeAllViews()
        detail.visibility = View.GONE
        status.text = "Searching…"

        lifecycleScope.launch {
            when (val found = withContext(Dispatchers.IO) { repository.search(q) }) {
                is SearchOutcome.Direct -> {
                    status.text = "Member profile loaded."
                    showMember(found.member)
                }
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
            val r = withContext(Dispatchers.IO) { runCatching { repository.fetchByUrl(url) } }
            r.onSuccess {
                status.text = "Member profile loaded."
                showMember(it)
            }.onFailure {
                status.text = "Unable to load profile: " + (it.message ?: "Unknown error")
            }
            loading(false)
        }
    }

    private fun showMember(m: Member) {
        detail.removeAllViews()
        detail.visibility = View.VISIBLE
        detail.addView(TextView(this).apply {
            text = m.name.ifBlank { "YCTA Member" }
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(12))
        })

        field("ID.No", m.memberId)
        field("အသင်းဝင် သည့်နေ့", m.joinedDate)
        field("ယာဉ် အမှတ်", m.vehicleNo)
        field("City No", m.cityNo)
        field("ခရိုင်/မြို့နယ်", m.district)

        detail.addView(Button(this).apply {
            isAllCaps = false
            text = "Open official YCTA profile"
            setOnClickListener {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.profileUrl)))
                }
            }
        })
    }

    private fun field(label: String, value: String) {
        if (value.isBlank()) return
        detail.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
        })
        detail.addView(TextView(this).apply {
            text = value
            textSize = 17f
            setPadding(0, dp(2), 0, dp(12))
        })
    }

    private fun loading(v: Boolean) {
        progress.visibility = if (v) View.VISIBLE else View.GONE
        searchBtn.isEnabled = !v
        scanBtn.isEnabled = !v
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
