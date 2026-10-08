package com.aaii.yctamember

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
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

class MoocCourseDetailActivity : ComponentActivity() {
    private lateinit var api: MoocApi
    private lateinit var root: LinearLayout
    private lateinit var image: ImageView
    private lateinit var titleView: TextView
    private lateinit var providerView: TextView
    private lateinit var categoryView: TextView
    private lateinit var typeView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var sourceView: TextView
    private lateinit var statusView: TextView
    private lateinit var bottomBar: LinearLayout
    private lateinit var openButton: Button
    private var lookingForLink = false
    private lateinit var current: MoocApi.Course

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        api = MoocApi(this)

        current = MoocApi.Course(
            id = intent.getStringExtra("id").orEmpty(),
            title = intent.getStringExtra("title").orEmpty(),
            provider = intent.getStringExtra("provider").orEmpty(),
            categoryId = intent.getStringExtra("category_id").orEmpty(),
            category = intent.getStringExtra("category").orEmpty(),
            sourceType = intent.getStringExtra("source").orEmpty(),
            courseType = intent.getStringExtra("course_type").orEmpty(),
            description = intent.getStringExtra("description").orEmpty(),
            url = intent.getStringExtra("url").orEmpty(),
            thumbnail = intent.getStringExtra("thumbnail").orEmpty(),
            certificate = intent.getStringExtra("certificate").orEmpty()
        )

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2F5F8"))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.WHITE)
            elevation = dp(3).toFloat()
        }

        top.addView(Button(this).apply {
            text = "← Courses"
            isAllCaps = false
            setOnClickListener { finish() }
        })
        top.addView(TextView(this).apply {
            text = "MOOC Course"
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#173F61"))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(TextView(this).apply {
            text = sourceLabel(current.sourceType)
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#1467B8"))
        })
        root.addView(top)

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(18))
        }

        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#DDE5EB"))
        }
        body.addView(image, LinearLayout.LayoutParams(-1, dp(210)))

        titleView = TextView(this).apply {
            textSize = 25f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#173F61"))
            setPadding(0, dp(14), 0, dp(8))
        }
        body.addView(titleView)

        providerView = infoLine()
        categoryView = infoLine()
        typeView = infoLine()
        sourceView = infoLine()
        body.addView(providerView)
        body.addView(categoryView)
        body.addView(typeView)
        body.addView(sourceView)

        descriptionView = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#40596D"))
            setLineSpacing(dp(2).toFloat(), 1.12f)
            setPadding(0, dp(14), 0, dp(12))
        }
        body.addView(descriptionView)

        statusView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#687E91"))
            setPadding(0, dp(6), 0, dp(6))
        }
        body.addView(statusView)

        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomBar = buildBottomBar()
        root.addView(bottomBar)
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(top) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(dp(12), dp(8) + bars.top, dp(12), dp(8))
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(dp(8) + bars.left, dp(7), dp(8) + bars.right, dp(7) + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        render(current)
        refreshDetail()
    }

    private fun buildBottomBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()

            openButton = Button(this@MoocCourseDetailActivity).apply {
                text = openButtonText(current.sourceType)
                isAllCaps = false
                setOnClickListener { openCourse() }
            }
            addView(openButton, LinearLayout.LayoutParams(0, -2, 1.4f).apply {
                marginEnd = dp(4)
            })

            addView(Button(this@MoocCourseDetailActivity).apply {
                text = "Certificate"
                isAllCaps = false
                setOnClickListener { openCertificate() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        }
    }

    private fun refreshDetail() {
        if (current.id.isBlank()) return
        lifecycleScope.launch {
            statusView.text = "Loading course information…"
            val result = withContext(Dispatchers.IO) { runCatching { api.detail(current) } }
            result.onSuccess {
                current = it
                render(it)
                statusView.text = "Native course detail ready."
            }.onFailure {
                statusView.text = "Using synced course information."
            }
        }
    }

    private fun render(course: MoocApi.Course) {
        titleView.text = course.title.ifBlank { "Course / Opportunity Information" }
        providerView.text = "Provider: " + course.provider.ifBlank { "—" }
        categoryView.text = "Category: " + course.category.ifBlank { "—" }
        typeView.text = "Course Type: " + course.courseType.ifBlank { sourceLabel(course.sourceType) }
        sourceView.text = "Source: " + sourceLabel(course.sourceType)
        descriptionView.text = course.description.ifBlank {
            when (course.sourceType.lowercase()) {
                "freehub" -> "Free course, scholarship, coupon or financial-aid information. Tap Open Free Hub to view the original source."
                "youtube" -> "Direct learning playlist/course from YouTube. Open the source to start learning."
                "drive" -> "Google Drive learning resource. Open the source to view lessons or course files."
                else -> "MOOC course information. Open the provider page to enroll or continue learning."
            }
        }

        if (course.thumbnail.isNotBlank()) {
            lifecycleScope.launch {
                val bytes = withContext(Dispatchers.IO) { api.fetchImage(course.thumbnail) }
                if (bytes != null) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { image.setImageBitmap(it) }
                }
            }
        }
    }

    private fun openCourse() {
        if (lookingForLink) return

        val currentUrl = safeHttpUrl(current.url)
        if (currentUrl != null) {
            launchCourseUrl(currentUrl)
            return
        }

        // Some legacy source records contain only a course ID. Never show
        // "source link unavailable" without checking the source API first.
        lookingForLink = true
        openButton.isEnabled = false
        openButton.text = "Finding video…"
        statusView.text = "Checking lesson/video source link…"
        lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                runCatching { api.detail(current) }
            }
            lookingForLink = false
            openButton.isEnabled = true
            openButton.text = openButtonText(current.sourceType)

            updated.onSuccess { latest ->
                current = latest
                render(latest)
                val url = safeHttpUrl(latest.url)
                if (url != null) {
                    statusView.text = "Source found."
                    launchCourseUrl(url)
                } else {
                    showLinkMissing()
                }
            }.onFailure {
                statusView.text = "Unable to check video source: ${it.message}"
                showLinkMissing()
            }
        }
    }

    private fun showLinkMissing() {
        val explanation = "No playable YouTube/Drive/provider URL was returned for this course." +
            "\n\nCourse ID: ${current.id.ifBlank { "Not provided" }}" +
            "\nSource: ${sourceLabel(current.sourceType)}" +
            "\n\nPlease check the source/video link in MOOC Admin."
        statusView.text = "This course has no video/source URL in its available data."
        android.app.AlertDialog.Builder(this)
            .setTitle("Video source missing")
            .setMessage(explanation)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun safeHttpUrl(raw: String): String? {
        val url = raw.trim().replace("&amp;", "&")
        val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        if (parsed.scheme?.lowercase() !in listOf("http", "https") ||
            parsed.host.isNullOrBlank()) return null
        return url
    }

    private fun launchCourseUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, "Unable to open video link: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openCertificate() {
        val url = when {
            current.certificate.startsWith("http", true) -> current.certificate
            current.id.isNotBlank() -> api.certificateUrl(current.id)
            else -> ""
        }
        if (url.isBlank()) {
            Toast.makeText(this, "Certificate is not available for this course.", Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, "Unable to open certificate.", Toast.LENGTH_LONG).show()
        }
    }

    private fun infoLine(): TextView =
        TextView(this).apply {
            textSize = 13.5f
            setTextColor(Color.parseColor("#5C7286"))
            setPadding(0, dp(2), 0, dp(2))
        }

    private fun sourceLabel(source: String): String =
        when (source.lowercase()) {
            "youtube" -> "YouTube"
            "drive" -> "Google Drive"
            "freehub" -> "Free Hub"
            "mooc" -> "MOOC"
            else -> source.ifBlank { "MOOC" }
        }

    private fun openButtonText(source: String): String =
        when (source.lowercase()) {
            "youtube" -> "Open Video"
            "drive" -> "Open Video"
            "freehub" -> "Open Free Hub"
            else -> "Join / Open Course"
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
