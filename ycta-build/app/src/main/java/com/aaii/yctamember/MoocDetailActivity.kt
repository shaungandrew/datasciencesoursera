package com.aaii.yctamember

import android.content.Intent
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

class MoocDetailActivity : ComponentActivity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var api: MoocApi
    private var item: MoocApi.Item = MoocApi.Item()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        api = MoocApi(this)

        item = MoocApi.Item(
            id = intent.getStringExtra("id").orEmpty(),
            title = intent.getStringExtra("title").orEmpty(),
            subtitle = intent.getStringExtra("subtitle").orEmpty(),
            category = intent.getStringExtra("category").orEmpty(),
            provider = intent.getStringExtra("provider").orEmpty(),
            description = intent.getStringExtra("description").orEmpty(),
            url = intent.getStringExtra("url").orEmpty(),
            sourceType = intent.getStringExtra("type").orEmpty(),
            certificate = intent.getStringExtra("certificate").orEmpty()
        )

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2F5F8"))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(8))
            setBackgroundColor(Color.WHITE)
            elevation = dp(4).toFloat()
        }

        top.addView(Button(this).apply {
            text = "← MOOC"
            isAllCaps = false
            setOnClickListener { finish() }
        })
        top.addView(TextView(this).apply {
            text = "Course Detail"
            gravity = Gravity.CENTER
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#173F61"))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }

        body.addView(TextView(this).apply {
            text = item.title.ifBlank { "Course / Opportunity Information" }
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#173F61"))
        })

        addField(body, "Source Type", item.sourceType.replace('_', ' '))
        addField(body, "Category", item.category)
        addField(body, "Provider / University", item.provider)
        addField(body, "Certificate", item.certificate)

        if (item.description.isNotBlank()) {
            body.addView(TextView(this).apply {
                text = "ABOUT THIS COURSE"
                textSize = 12f
                letterSpacing = 0.08f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#1F659A"))
                setPadding(0, dp(16), 0, dp(6))
            })
            body.addView(TextView(this).apply {
                text = item.description
                textSize = 15f
                setTextColor(Color.parseColor("#40596D"))
                setLineSpacing(dp(3).toFloat(), 1.12f)
            })
        }

        status = TextView(this).apply {
            text = "Native Detail • Source: " + MoocApi.SOURCE
            textSize = 12f
            setTextColor(Color.parseColor("#667B8D"))
            setPadding(0, dp(16), 0, dp(8))
        }
        body.addView(status)

        body.addView(Button(this).apply {
            text = "JOIN / OPEN COURSE"
            isAllCaps = false
            textSize = 16f
            isEnabled = item.url.isNotBlank()
            setOnClickListener { openUrl(item.url) }
        })

        if (item.id.isNotBlank()) {
            body.addView(Button(this).apply {
                text = "Refresh Course Information"
                isAllCaps = false
                setOnClickListener { refreshDetail(body) }
            })
        }

        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(top) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(dp(12), dp(6) + i.top, dp(12), dp(8))
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(0, 0, 0, i.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun refreshDetail(body: LinearLayout) {
        status.text = "Refreshing native course detail …"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.fetchMoocDetail(item.id) }
            }
            result.onSuccess { fresh ->
                if (fresh == null) {
                    status.text = "No additional detail returned by server."
                } else {
                    item = merge(item, fresh)
                    status.text = "Course information refreshed."
                    Toast.makeText(
                        this@MoocDetailActivity,
                        "Updated. Reopen detail to render refreshed fields.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure {
                status.text = "Refresh failed: " + (it.message ?: "Unknown error")
            }
        }
    }

    private fun merge(old: MoocApi.Item, fresh: MoocApi.Item): MoocApi.Item =
        MoocApi.Item(
            id = fresh.id.ifBlank { old.id },
            title = fresh.title.ifBlank { old.title },
            subtitle = fresh.subtitle.ifBlank { old.subtitle },
            category = fresh.category.ifBlank { old.category },
            provider = fresh.provider.ifBlank { old.provider },
            description = fresh.description.ifBlank { old.description },
            url = fresh.url.ifBlank { old.url },
            sourceType = fresh.sourceType.ifBlank { old.sourceType },
            certificate = fresh.certificate.ifBlank { old.certificate },
            imageUrl = fresh.imageUrl.ifBlank { old.imageUrl }
        )

    private fun openUrl(url: String) {
        if (url.isBlank()) {
            Toast.makeText(this, "Course enrollment link is not available yet.", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, "Unable to open course link.", Toast.LENGTH_LONG).show()
        }
    }

    private fun addField(parent: LinearLayout, label: String, value: String) {
        if (value.isBlank()) return
        parent.addView(TextView(this).apply {
            text = label
            textSize = 11.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#708395"))
            setPadding(0, dp(10), 0, dp(2))
        })
        parent.addView(TextView(this).apply {
            text = value
            textSize = 16f
            setTextColor(Color.parseColor("#263E52"))
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
