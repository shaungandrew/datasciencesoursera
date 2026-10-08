package com.aaii.yctamember

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity

/** Branded launch screen. All existing YCTA modules remain in MainActivity. */
class SplashActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val next = Runnable {
        if (!isFinishing && !isDestroyed) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#082F55")
        window.navigationBarColor = Color.parseColor("#082F55")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(40), dp(24), dp(40))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#082F55"),
                    Color.parseColor("#0B6088"),
                    Color.parseColor("#073758")
                )
            )
        }
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.ycta_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Yangon City Taxi Association official logo"
        }, LinearLayout.LayoutParams(dp(258), dp(258)))

        root.addView(TextView(this).apply {
            text = "YANGON CITY TAXI ASSOCIATION"
            gravity = Gravity.CENTER
            textSize = 19f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(26), 0, dp(5))
        }, LinearLayout.LayoutParams(-1, -2))

        root.addView(TextView(this).apply {
            text = "YCTA TAXI • Digital Member System"
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.parseColor("#C7E7F8"))
        }, LinearLayout.LayoutParams(-1, -2))

        root.addView(ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(Color.parseColor("#FFED00"))
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { topMargin = dp(36) })

        root.addView(TextView(this).apply {
            text = "Members • Districts • Library • MOOC"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.parseColor("#C7E7F8"))
            setPadding(0, dp(16), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
        handler.postDelayed(next, 950L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(next)
        super.onDestroy()
    }

    private fun dp(px: Int) = (px * resources.displayMetrics.density).toInt()
}
