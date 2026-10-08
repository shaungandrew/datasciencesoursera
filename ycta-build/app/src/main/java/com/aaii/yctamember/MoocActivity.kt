package com.aaii.yctamember

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MoocActivity : ComponentActivity() {
    private lateinit var api: MoocApi
    private lateinit var root: LinearLayout
    private lateinit var contentBox: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var bottomBar: LinearLayout
    private lateinit var loginPanel: LinearLayout
    private lateinit var activationCode: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private var sourceKey = "MOOC"
    private var items: List<MoocApi.Item> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        api = MoocApi(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2F5F8"))
        }

        val header = header()
        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(18))
        }

        loginPanel = loginPanel()
        body.addView(loginPanel)
        body.addView(sourceDashboard())
        body.addView(searchPanel())

        status = TextView(this).apply {
            text = "Source: " + MoocApi.SOURCE
            textSize = 12f
            setTextColor(Color.parseColor("#667B8D"))
            setPadding(dp(2), dp(8), dp(2), dp(8))
        }
        body.addView(status)

        contentBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(contentBox)

        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(body)
        }, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomBar = footer()
        root.addView(bottomBar)
        setContentView(root)
        applyInsets(header, bottomBar)

        if (api.hasToken()) {
            loginPanel.visibility = View.GONE
            load("MOOC", false)
        } else {
            showLoginRequired("Activation Code / Login required to access MOOC.")
        }
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(6), dp(12), dp(10))
        setBackgroundColor(Color.WHITE)
        elevation = dp(4).toFloat()

        val row = LinearLayout(this@MoocActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(Button(this@MoocActivity).apply {
            text = "← YCTA"
            isAllCaps = false
            setOnClickListener { finish() }
        })

        row.addView(TextView(this@MoocActivity).apply {
            text = "YCTA MOOC"
            textSize = 20f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#173F61"))
        }, LinearLayout.LayoutParams(0, -2, 1f))

        row.addView(Button(this@MoocActivity).apply {
            text = "SYNC"
            isAllCaps = false
            setOnClickListener { syncAll() }
        })

        addView(row)
        addView(TextView(this@MoocActivity).apply {
            text = "Native Learning Hub • Activation Protected"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.parseColor("#6B7F91"))
        })
    }

    private fun loginPanel(): LinearLayout =
        panel().apply {
            addView(title("MOOC USER ACTIVATION", 18f))
            addView(TextView(this@MoocActivity).apply {
                text = "Enter Activation Code to activate this phone. You can also use Username / Email + Password."
                textSize = 12.5f
                setTextColor(Color.parseColor("#5E7387"))
                setPadding(0, 0, 0, dp(8))
            })

            activationCode = EditText(this@MoocActivity).apply {
                hint = "Activation Code"
                textSize = 16f
                isSingleLine = true
                setPadding(dp(10), dp(9), dp(10), dp(9))
                background = rounded(Color.parseColor("#F8FAFC"), 12, "#D4DFE9")
            }
            addView(activationCode, fieldParams())

            addView(Button(this@MoocActivity).apply {
                text = "ACTIVATE & LOGIN"
                isAllCaps = false
                setTextColor(Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0C6EA8"))
                setOnClickListener { activateLogin() }
            }, fieldParams())

            addView(TextView(this@MoocActivity).apply {
                text = "OR PASSWORD LOGIN"
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#758899"))
                setPadding(0, dp(4), 0, dp(6))
            })

            username = EditText(this@MoocActivity).apply {
                hint = "Username or Email"
                isSingleLine = true
                setPadding(dp(10), dp(9), dp(10), dp(9))
                background = rounded(Color.parseColor("#F8FAFC"), 12, "#D4DFE9")
            }
            addView(username, fieldParams())

            password = EditText(this@MoocActivity).apply {
                hint = "Password"
                isSingleLine = true
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(dp(10), dp(9), dp(10), dp(9))
                background = rounded(Color.parseColor("#F8FAFC"), 12, "#D4DFE9")
            }
            addView(password, fieldParams())

            addView(Button(this@MoocActivity).apply {
                text = "PASSWORD LOGIN"
                isAllCaps = false
                setOnClickListener { passwordLogin() }
            })
        }

    private fun sourceDashboard(): View {
        val card = panel()
        card.addView(title("LEARNING SOURCES", 12f))

        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r1.addView(sourceCard("MOOC LIST WEBSITE", "MOOC", "#0C6EA8"), LinearLayout.LayoutParams(0, dp(108), 1f).apply { marginEnd = dp(5) })
        r1.addView(sourceCard("YOUTUBE LIST", "YouTube", "#C63F3A"), LinearLayout.LayoutParams(0, dp(108), 1f).apply { marginStart = dp(5) })
        card.addView(r1)

        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r2.addView(sourceCard("DRIVE LIST", "Drive", "#2D7D5B"), LinearLayout.LayoutParams(0, dp(108), 1f).apply { marginEnd = dp(5); topMargin = dp(10) })
        r2.addView(sourceCard("FREE HUB", "Free Hub", "#7853A7"), LinearLayout.LayoutParams(0, dp(108), 1f).apply { marginStart = dp(5); topMargin = dp(10) })
        card.addView(r2)
        return card
    }

    private fun sourceCard(label: String, key: String, color: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor(color), darken(Color.parseColor(color)))
            ).apply { cornerRadius = dp(18).toFloat() }
            isClickable = true
            setOnClickListener {
                if (api.hasToken()) load(key, false)
                else showLoginRequired("Activation Code / Login required.")
            }

            addView(TextView(this@MoocActivity).apply {
                text = label
                textSize = 16f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })

            addView(TextView(this@MoocActivity).apply {
                text = "Native Categories → Courses"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#EAF6FC"))
                setPadding(0, dp(5), 0, 0)
            })
        }

    private fun searchPanel(): View {
        val card = panel()
        search = EditText(this).apply {
            hint = "Course / University / Category / Keyword"
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Color.parseColor("#F8FAFC"), 12, "#D4DFE9")
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    renderItems(api.filter(items, text.toString()))
                    true
                } else false
            }
        }
        card.addView(search)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Search"
            isAllCaps = false
            setOnClickListener { renderItems(api.filter(items, search.text.toString())) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply {
            text = "Categories"
            isAllCaps = false
            setOnClickListener { renderCategories(items) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row)
        return card
    }

    private fun footer() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(5), dp(6), dp(5))
        setBackgroundColor(Color.WHITE)
        elevation = dp(10).toFloat()

        addView(footerButton("HOME") {
            if (api.hasToken()) load("MOOC", false)
            else showLoginRequired("Activation Code / Login required.")
        }, LinearLayout.LayoutParams(0, -2, 1f))

        addView(footerButton("SEARCH") { search.requestFocus() }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(footerButton("SYNC ALL") { syncAll() }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(footerButton("SETTINGS") { settings() }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun footerButton(textValue: String, click: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 10.5f
        isAllCaps = false
        setOnClickListener { click() }
    }

    private fun applyInsets(top: View, bottom: View) {
        ViewCompat.setOnApplyWindowInsetsListener(top) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(dp(12), dp(6) + i.top, dp(12), dp(10))
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottom) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(dp(6) + i.left, dp(5), dp(6) + i.right, dp(5) + i.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun activateLogin() {
        val code = activationCode.text.toString().trim()
        if (code.isBlank()) {
            status.text = "Please enter Activation Code."
            return
        }

        status.text = "Activating this device …"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.loginActivation(code) }
            }
            result.onSuccess {
                loginPanel.visibility = View.GONE
                status.text = "Activation successful. Loading MOOC …"
                load("MOOC", true)
            }.onFailure {
                status.text = "Activation failed: " + (it.message ?: "Unknown error")
            }
        }
    }

    private fun passwordLogin() {
        status.text = "Logging in …"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    api.loginPassword(
                        username.text.toString(),
                        password.text.toString()
                    )
                }
            }
            result.onSuccess {
                loginPanel.visibility = View.GONE
                status.text = "Login successful. Loading MOOC …"
                load("MOOC", true)
            }.onFailure {
                status.text = "Login failed: " + (it.message ?: "Unknown error")
            }
        }
    }

    private fun showLoginRequired(message: String) {
        loginPanel.visibility = View.VISIBLE
        items = emptyList()
        contentBox.removeAllViews()
        status.text = message
        empty("Activate this phone with your MOOC user Activation Code.")
    }

    private fun load(key: String, force: Boolean) {
        if (!api.hasToken()) {
            showLoginRequired("Activation Code / Login required.")
            return
        }

        sourceKey = key
        contentBox.removeAllViews()
        status.text = "Loading " + key + " from " + MoocApi.SOURCE + " …"

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (key) {
                        "MOOC" -> api.fetchMooc(force)
                        "YouTube" -> {
                            val s = api.fetchDirectory(force)
                            MoocApi.Snapshot(api.youtubeItems(s.items), s.fromCache)
                        }
                        "Drive" -> {
                            val s = api.fetchDirectory(force)
                            MoocApi.Snapshot(api.driveItems(s.items), s.fromCache)
                        }
                        "Free Hub" -> api.fetchFreeHub(force)
                        else -> api.fetchMooc(force)
                    }
                }
            }

            result.onSuccess { snapshot ->
                items = snapshot.items
                status.text = key + " • " + items.size + " item(s)" +
                    if (snapshot.fromCache) " • Cached" else " • Server"
                if (items.isEmpty()) empty("No records found. Tap SYNC ALL NOW.")
                else renderCategories(items)
            }.onFailure {
                if (it is MoocApi.AuthException) {
                    showLoginRequired(it.message ?: "Activation required.")
                } else {
                    status.text = key + " error: " + (it.message ?: "Unknown error")
                    empty("Could not load data. Tap SYNC ALL NOW.")
                }
            }
        }
    }

    private fun renderCategories(list: List<MoocApi.Item>) {
        contentBox.removeAllViews()
        contentBox.addView(title(sourceKey + " Categories", 20f))
        contentBox.addView(categoryCard("All " + sourceKey, list.size) { renderItems(list) })

        val categories = api.categories(list)
        if (categories.isEmpty()) {
            renderItems(list)
            return
        }

        categories.forEach { category ->
            val filtered = list.filter { it.category.equals(category, true) }
            contentBox.addView(categoryCard(category, filtered.size) { renderItems(filtered) })
        }
    }

    private fun renderItems(list: List<MoocApi.Item>) {
        contentBox.removeAllViews()
        contentBox.addView(title(sourceKey + " Courses", 20f))
        if (list.isEmpty()) {
            empty("No matching courses.")
            return
        }
        list.take(120).forEach { contentBox.addView(courseCard(it)) }
    }

    private fun categoryCard(label: String, count: Int, click: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, 14, "#D8E3ED")
            isClickable = true
            setOnClickListener { click() }

            addView(TextView(this@MoocActivity).apply {
                text = label
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#173F61"))
            }, LinearLayout.LayoutParams(0, -2, 1f))

            addView(TextView(this@MoocActivity).apply {
                text = count.toString()
                setTextColor(Color.parseColor("#1F659A"))
            })
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }

    private fun courseCard(item: MoocApi.Item) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, 14, "#D8E3ED")
            isClickable = true
            setOnClickListener { openDetail(item) }

            addView(TextView(this@MoocActivity).apply {
                text = item.title.ifBlank { "Course / Opportunity" }
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#173F61"))
            })

            val meta = listOf(item.category, item.provider, item.sourceType.replace('_', ' '))
                .filter { it.isNotBlank() }
                .joinToString(" • ")

            if (meta.isNotBlank()) addView(TextView(this@MoocActivity).apply {
                text = meta
                textSize = 12f
                setTextColor(Color.parseColor("#667B8D"))
                setPadding(0, dp(4), 0, 0)
            })

            if (item.description.isNotBlank()) addView(TextView(this@MoocActivity).apply {
                text = item.description.take(220)
                textSize = 13f
                setTextColor(Color.parseColor("#415A6E"))
                setPadding(0, dp(7), 0, 0)
            })

            addView(Button(this@MoocActivity).apply {
                text = "Native Detail"
                isAllCaps = false
                setOnClickListener { openDetail(item) }
            })
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }

    private fun openDetail(item: MoocApi.Item) {
        startActivity(Intent(this, MoocDetailActivity::class.java).apply {
            putExtra("id", item.id)
            putExtra("title", item.title)
            putExtra("subtitle", item.subtitle)
            putExtra("category", item.category)
            putExtra("provider", item.provider)
            putExtra("description", item.description)
            putExtra("url", item.url)
            putExtra("type", item.sourceType)
            putExtra("certificate", item.certificate)
        })
    }

    private fun syncAll() {
        if (!api.hasToken()) {
            showLoginRequired("Activation Code / Login required before Sync.")
            return
        }

        status.text = "Syncing MOOC + YouTube + Drive + Free Hub …"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { api.syncAll() } }

            result.onSuccess { counts ->
                status.text = "Sync completed • " +
                    counts.entries.joinToString(" • ") { it.key + ": " + it.value }
                load(sourceKey, false)
            }.onFailure {
                if (it is MoocApi.AuthException) {
                    showLoginRequired(it.message ?: "Activation required.")
                } else {
                    status.text = "Sync failed: " + (it.message ?: "Unknown error")
                }
            }
        }
    }

    private fun settings() {
        val message =
            "MOOC Source\n" + MoocApi.SOURCE +
            "\n\nLogin Status\n" + if (api.hasToken()) "Activated / Logged In" else "Activation Required"

        val builder = android.app.AlertDialog.Builder(this)
            .setTitle("Native Settings")
            .setMessage(message)
            .setPositiveButton("SYNC ALL NOW") { _, _ -> syncAll() }

        if (api.hasToken()) {
            builder.setNeutralButton("LOG OUT") { _, _ ->
                api.clearToken()
                showLoginRequired("Logged out. Activation Code / Login required.")
            }
        }

        builder.setNegativeButton("Close", null).show()
    }

    private fun empty(message: String) {
        contentBox.removeAllViews()
        contentBox.addView(TextView(this).apply {
            text = message
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.parseColor("#667B8D"))
            setPadding(dp(12), dp(28), dp(12), dp(28))
        })
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = rounded(Color.WHITE, 16, "#D8E3ED")
        elevation = dp(2).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    private fun fieldParams() =
        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }

    private fun title(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#173F61"))
        setPadding(0, 0, 0, dp(8))
    }

    private fun rounded(fill: Int, radius: Int, stroke: String) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.parseColor(stroke))
        }

    private fun darken(color: Int): Int {
        val f = 0.78f
        return Color.rgb(
            (Color.red(color) * f).toInt(),
            (Color.green(color) * f).toInt(),
            (Color.blue(color) * f).toInt()
        )
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
