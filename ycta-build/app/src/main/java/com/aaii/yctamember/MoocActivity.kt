package com.aaii.yctamember

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MoocActivity : ComponentActivity() {
    private lateinit var api: MoocApi
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var bottomBar: LinearLayout
    private lateinit var searchInput: EditText
    private var activeSource = "home"
    private var loginOpen = false
    private var pendingSource: String? = null
    private var pendingSearch = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        api = MoocApi(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2F5F8"))
        }

        val header = buildHeader()
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        val scroller = ScrollView(this).apply {
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(20))
        }
        scroller.addView(content)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomBar = buildBottomBar()
        root.addView(bottomBar, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
        applyInsets(header, bottomBar)
        showHome()
    }

    private fun buildHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(10))
            setBackgroundColor(Color.WHITE)
            elevation = dp(3).toFloat()

            val top = LinearLayout(this@MoocActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(Button(this@MoocActivity).apply {
                text = "← YCTA"
                isAllCaps = false
                setOnClickListener { finish() }
            })

            val brand = LinearLayout(this@MoocActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            brand.addView(TextView(this@MoocActivity).apply {
                text = "YCTA MOOC"
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#173F61"))
                gravity = Gravity.CENTER
            })
            brand.addView(TextView(this@MoocActivity).apply {
                text = "Native Learning Hub"
                textSize = 11f
                setTextColor(Color.parseColor("#667B8D"))
                gravity = Gravity.CENTER
            })
            top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

            top.addView(Button(this@MoocActivity).apply {
                text = "Login"
                isAllCaps = false
                setOnClickListener { showLogin() }
            })

            addView(top)

            status = TextView(this@MoocActivity).apply {
                text = "Source: https://aaii.asia/edu/mooc/"
                textSize = 11.5f
                setTextColor(Color.parseColor("#667B8D"))
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, 0)
            }
            addView(status)
        }
    }

    private fun buildBottomBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(6), dp(7), dp(6))
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()

            addView(bottomButton("Home") { showHome() })
            addView(bottomButton("Search") { showSearch() })
            addView(bottomButton("Sync") { syncActive() })
            addView(bottomButton("YCTA") { finish() })
        }
    }

    private fun bottomButton(label: String, click: () -> Unit): View =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12.5f
            setOnClickListener { click() }
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

    private fun applyInsets(header: View, bottom: View) {
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(dp(14), dp(8) + bars.top, dp(14), dp(10))
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottom) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(dp(7) + bars.left, dp(6), dp(7) + bars.right, dp(6) + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun showHome() {
        activeSource = "home"
        content.removeAllViews()

        content.addView(TextView(this).apply {
            text = "MOOC • YouTube • Drive • Free Hub"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#173F61"))
        })
        content.addView(TextView(this).apply {
            text = "Choose a native learning source. Categories open first, then courses."
            textSize = 13.5f
            setTextColor(Color.parseColor("#60758A"))
            setPadding(0, dp(4), 0, dp(12))
        })

        searchInput = EditText(this).apply {
            hint = "Search course / provider / category"
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = rounded(Color.WHITE, 14, "#D3DEE8", 1)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    runSearch(text.toString())
                    true
                } else false
            }
        }
        content.addView(searchInput, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(sourceCard(
            "MOOC LIST WEBSITE",
            "MOOC websites • university • provider • enrollment",
            "mooc",
            "#1467B8"
        ), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })

        row1.addView(sourceCard(
            "YOUTUBE LIST",
            "Playlists • direct learning • category browsing",
            "youtube",
            "#D64545"
        ), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
        grid.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        row2.addView(sourceCard(
            "DRIVE LIST",
            "Google Drive lessons • files • direct learning",
            "drive",
            "#198754"
        ), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })

        row2.addView(sourceCard(
            "FREE HUB",
            "Free course • scholarship • coupon • financial aid",
            "freehub",
            "#7A4AA3"
        ), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
        grid.addView(row2)
        content.addView(grid)

        content.addView(infoCard())

        if (!api.hasToken()) {
            status.text = "MOOC • Login / Activation required"
            content.addView(Button(this).apply {
                text = "Login / Activate to load MOOC courses"
                isAllCaps = false
                setOnClickListener { showLogin() }
            })
            return
        }

        lifecycleScope.launch {
            val countResult = withContext(Dispatchers.IO) {
                runCatching {
                    listOf("mooc", "youtube", "drive", "freehub").associateWith { source ->
                        try {
                            api.fetchCourses(source, false).size
                        } catch (e: MoocApi.AuthException) {
                            throw e
                        } catch (e: Exception) {
                            -1
                        }
                    }
                }
            }
            countResult.onSuccess { counts ->
                fun display(key: String): String =
                    counts[key]?.takeIf { it >= 0 }?.toString() ?: "—"
                status.text = "MOOC ${display("mooc")} • YouTube ${display("youtube")} • Drive ${display("drive")} • Free Hub ${display("freehub")}"
            }.onFailure(::handleError)
        }
    }

    private fun sourceCard(
        title: String,
        subtitle: String,
        source: String,
        color: String
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(16), dp(12), dp(16))
            background = rounded(Color.WHITE, 18, "#D8E2EB", 1)
            elevation = dp(3).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { showSource(source) }

            addView(TextView(this@MoocActivity).apply {
                text = when (source) {
                    "youtube" -> "▶"
                    "drive" -> "▣"
                    "freehub" -> "★"
                    else -> "MOOC"
                }
                textSize = if (source == "mooc") 16f else 30f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(color))
                setPadding(0, 0, 0, dp(8))
            })

            addView(TextView(this@MoocActivity).apply {
                text = title
                textSize = 15f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#173F61"))
            })
            addView(TextView(this@MoocActivity).apply {
                text = subtitle
                textSize = 11.5f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#667B8D"))
                setPadding(0, dp(5), 0, 0)
            })
        }
    }

    private fun infoCard(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(Color.WHITE, 16, "#D8E2EB", 1)
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }

            addView(TextView(this@MoocActivity).apply {
                text = "NATIVE MOOC MODULE"
                textSize = 12f
                letterSpacing = 0.08f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#1E639C"))
            })
            addView(TextView(this@MoocActivity).apply {
                text = "Cached data is shown first. Sync refreshes server information. Course detail opens natively before the original learning source."
                textSize = 13.5f
                setTextColor(Color.parseColor("#40596D"))
                setPadding(0, dp(7), 0, 0)
            })
        }

    private fun showSource(source: String) {
        if (!api.hasToken()) {
            pendingSource = source
            status.text = "Sign in to view ${sourceTitle(source)} courses."
            showLogin()
            return
        }
        activeSource = source
        content.removeAllViews()
        content.addView(sectionHeader(sourceTitle(source), "Native Categories"))

        lifecycleScope.launch {
            status.text = "Loading ${sourceTitle(source)} categories…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val courses = api.fetchCourses(source, false)
                    val cats = api.fetchCategories(source, false)
                    courses to cats
                }
            }

            result.onSuccess { (courses, categories) ->
                status.text = "${courses.size} course(s) • ${categories.size} categories • ${syncLabel(api.lastSync(source))}"
                if (categories.isEmpty()) {
                    renderCourses(courses, sourceTitle(source))
                } else {
                    content.addView(Button(this@MoocActivity).apply {
                        text = "All ${sourceTitle(source)} Courses (${courses.size})"
                        isAllCaps = false
                        setOnClickListener { renderCourses(courses, sourceTitle(source)) }
                    })
                    categories.forEach { cat ->
                        content.addView(categoryCard(cat, source, courses))
                    }
                }
            }.onFailure(::handleError)
        }
    }

    private fun categoryCard(
        category: MoocApi.Category,
        source: String,
        courses: List<MoocApi.Course>
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, 15, "#D9E3EC", 1)
            elevation = dp(2).toFloat()
            isClickable = true
            setOnClickListener {
                val filtered = api.filterByCategory(courses, category)
                renderCourses(filtered, category.name)
            }

            addView(TextView(this@MoocActivity).apply {
                text = category.name
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#173F61"))
            })
            addView(TextView(this@MoocActivity).apply {
                val derivedCount = api.filterByCategory(courses, category).size
                val count = if (category.count > 0) category.count else derivedCount
                text = "$count course(s) • ${sourceTitle(source)}"
                textSize = 12f
                setTextColor(Color.parseColor("#687E91"))
                setPadding(0, dp(4), 0, 0)
            })

            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
        }

    private fun renderCourses(courses: List<MoocApi.Course>, title: String) {
        content.removeAllViews()
        content.addView(sectionHeader(title, "${courses.size} course(s)"))

        if (courses.isEmpty()) {
            content.addView(emptyText("No courses found. Tap Sync to refresh this source."))
            return
        }

        courses.forEach { course ->
            content.addView(courseCard(course))
        }
    }

    private fun courseCard(course: MoocApi.Course): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.WHITE, 16, "#D9E3EC", 1)
            elevation = dp(2).toFloat()
            isClickable = true
            setOnClickListener { openCourse(course) }
        }

        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#E0E7ED"))
        }
        card.addView(image, LinearLayout.LayoutParams(dp(86), dp(96)).apply { marginEnd = dp(12) })

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(TextView(this).apply {
            text = course.title.ifBlank { "MOOC Course" }
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#173F61"))
        })
        if (course.provider.isNotBlank()) {
            info.addView(smallText(course.provider))
        }
        info.addView(smallText(
            listOf(course.category, sourceTitle(course.sourceType))
                .filter { it.isNotBlank() }
                .joinToString(" • ")
        ))
        info.addView(TextView(this).apply {
            text = "Course Information ›"
            textSize = 12.5f
            setTextColor(Color.parseColor("#1467B8"))
            setPadding(0, dp(7), 0, 0)
        })
        card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))

        if (course.thumbnail.isNotBlank()) {
            lifecycleScope.launch {
                val bytes = withContext(Dispatchers.IO) { api.fetchImage(course.thumbnail) }
                if (bytes != null) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { image.setImageBitmap(it) }
                }
            }
        }

        card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        return card
    }

    private fun showSearch() {
        activeSource = "search"
        content.removeAllViews()
        content.addView(sectionHeader("Course Search", "MOOC + YouTube + Drive + Free Hub"))

        searchInput = EditText(this).apply {
            hint = "Course / university / provider / keyword"
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = rounded(Color.WHITE, 14, "#D3DEE8", 1)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    runSearch(text.toString())
                    true
                } else false
            }
        }
        content.addView(searchInput)

        content.addView(Button(this).apply {
            text = "Search All Sources"
            isAllCaps = false
            setOnClickListener { runSearch(searchInput.text.toString()) }
        })
    }

    private fun runSearch(query: String) {
        if (!api.hasToken()) {
            pendingSource = "search"
            pendingSearch = query
            showLogin()
            return
        }
        val q = query.trim()
        if (q.isBlank()) {
            Toast.makeText(this, "Enter a search keyword.", Toast.LENGTH_SHORT).show()
            return
        }
        activeSource = "search"
        content.removeAllViews()
        content.addView(sectionHeader("Search: $q", "Native Search & Filter"))

        lifecycleScope.launch {
            status.text = "Searching all MOOC sources…"
            val result = withContext(Dispatchers.IO) {
                runCatching { api.searchAll(q) }
            }
            result.onSuccess {
                status.text = "${it.size} result(s)"
                if (it.isEmpty()) content.addView(emptyText("No matching course found."))
                else it.forEach { course -> content.addView(courseCard(course)) }
            }.onFailure(::handleError)
        }
    }

    private fun syncActive() {
        if (!api.hasToken()) {
            pendingSource = activeSource
            showLogin()
            return
        }
        val source = activeSource.takeIf { it in listOf("mooc", "youtube", "drive", "freehub") }
        if (source == null) {
            syncAll()
            return
        }

        lifecycleScope.launch {
            status.text = "Syncing ${sourceTitle(source)}…"
            val result = withContext(Dispatchers.IO) {
                runCatching { api.fetchCourses(source, true) }
            }
            result.onSuccess {
                Toast.makeText(this@MoocActivity, "Synced ${it.size} records", Toast.LENGTH_SHORT).show()
                showSource(source)
            }.onFailure(::handleError)
        }
    }

    private fun syncAll() {
        if (!api.hasToken()) {
            pendingSource = "home"
            showLogin()
            return
        }
        lifecycleScope.launch {
            status.text = "Syncing MOOC, YouTube, Drive and Free Hub…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    listOf("mooc", "youtube", "drive", "freehub").associateWith {
                        api.fetchCourses(it, true).size
                    }
                }
            }
            result.onSuccess {
                status.text = "Sync complete • MOOC ${it["mooc"] ?: 0} • YouTube ${it["youtube"] ?: 0} • Drive ${it["drive"] ?: 0} • Free Hub ${it["freehub"] ?: 0}"
                showHome()
            }.onFailure(::handleError)
        }
    }

    private fun openCourse(course: MoocApi.Course) {
        startActivity(Intent(this, MoocCourseDetailActivity::class.java).apply {
            putExtra("id", course.id)
            putExtra("title", course.title)
            putExtra("provider", course.provider)
            putExtra("category_id", course.categoryId)
            putExtra("category", course.category)
            putExtra("source", course.sourceType)
            putExtra("course_type", course.courseType)
            putExtra("description", course.description)
            putExtra("url", course.url)
            putExtra("thumbnail", course.thumbnail)
            putExtra("certificate", course.certificate)
        })
    }

    private fun showLogin() {
        if (loginOpen) return
        loginOpen = true
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), 0)
        }
        val activation = EditText(this).apply { hint = "Activation Code" }
        val username = EditText(this).apply { hint = "Username or Email" }
        val password = EditText(this).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        box.addView(activation)
        box.addView(username)
        box.addView(password)

        val dialog = AlertDialog.Builder(this)
            .setTitle("MOOC Login / Activation")
            .setMessage("Use Activation Code OR Username + Password.")
            .setView(box)
            .setPositiveButton("Login", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnDismissListener { loginOpen = false }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                lifecycleScope.launch {
                    status.text = "Logging in…"
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            api.login(
                                activation.text.toString().trim(),
                                username.text.toString().trim(),
                                password.text.toString()
                            )
                        }
                    }
                    result.onSuccess {
                        status.text = "MOOC login ready."
                        dialog.dismiss()
                        Toast.makeText(this@MoocActivity, "Login successful", Toast.LENGTH_SHORT).show()
                        val next = pendingSource
                        pendingSource = null
                        when (next) {
                            "mooc", "youtube", "drive", "freehub" -> showSource(next)
                            "search" -> if (pendingSearch.isNotBlank()) {
                                val saved = pendingSearch
                                pendingSearch = ""
                                runSearch(saved)
                            } else showSearch()
                            else -> showHome()
                        }
                    }.onFailure { issue ->
                        val isDnsFailure = generateSequence(issue) { it.cause }
                            .any { it is java.net.UnknownHostException }
                        status.text = if (isDnsFailure)
                            "MOOC network/DNS error. Try opening the website in Chrome."
                        else "Login failed: ${issue.message}"
                        val safeDiagnostics = api.loginDiagnostics()
                        val guide = if (isDnsFailure)
                            "\n\nThis is a DNS/network issue, NOT a wrong password or Device ID. " +
                            "Try Chrome, switch between Wi-Fi and mobile data, or check Private DNS settings."
                        else ""
                        AlertDialog.Builder(this@MoocActivity)
                            .setTitle(if (isDnsFailure) "MOOC Network / DNS Error" else "MOOC Login Diagnostics")
                            .setMessage((issue.message ?: "Login failed") + "\n\n" +
                                safeDiagnostics + guide +
                                "\n\nNo passwords, tokens, or cookie values are displayed.")
                            .setNeutralButton("Open MOOC Website") { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MoocApi.WEB_BASE)))
                            }
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun handleError(t: Throwable) {
        status.text = t.message ?: "MOOC module error"
        if (t is MoocApi.AuthException) {
            api.clearToken()
            if (pendingSource == null) pendingSource = activeSource
            status.text = "MOOC session expired. Please log in again."
            Toast.makeText(this, "Login / Activation Code required.", Toast.LENGTH_LONG).show()
            showLogin()
        } else {
            content.addView(emptyText("Could not load MOOC data. Cached data is used when available."))
        }
    }

    private fun sectionHeader(title: String, subtitle: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
            addView(TextView(this@MoocActivity).apply {
                text = title
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#173F61"))
            })
            addView(TextView(this@MoocActivity).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.parseColor("#667B8D"))
                setPadding(0, dp(3), 0, 0)
            })
        }

    private fun smallText(value: String): View =
        TextView(this).apply {
            text = value
            textSize = 12.5f
            setTextColor(Color.parseColor("#667B8D"))
            setPadding(0, dp(3), 0, 0)
        }

    private fun emptyText(value: String): View =
        TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(Color.parseColor("#667B8D"))
            setPadding(dp(8), dp(18), dp(8), dp(18))
        }

    private fun sourceTitle(source: String): String =
        when (source.lowercase()) {
            "youtube" -> "YouTube"
            "drive" -> "Google Drive"
            "freehub" -> "Free Hub"
            "mooc" -> "MOOC"
            else -> source
        }

    private fun syncLabel(time: Long): String =
        if (time <= 0L) "Not synced"
        else "Synced " + SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(time))

    private fun rounded(fill: Int, radius: Int, stroke: String, width: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (width > 0) setStroke(dp(width), Color.parseColor(stroke))
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
