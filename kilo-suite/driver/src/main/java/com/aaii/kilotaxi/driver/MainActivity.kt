package com.aaii.kilotaxi.driver

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aaii.kilotaxi.common.CommunicationActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    companion object {
        const val PREFS = "kilo_driver_v4"
        const val API = "https://ycta.aaii.asia/api/index.php"
        const val OLD_API = "https://ycta.yangoncity.net/kilotaxi/api/index.php"
        const val TAB_HOME = "home"
        const val TAB_JOBS = "jobs"
        const val TAB_PROFILE = "profile"
    }

    private val districts = listOf(
        "ကျောက်တံတားခရိုင်","ကမာရွတ်ခရိုင်","တိုက်ကြီးခရိုင်","တွံတေးခရိုင်",
        "ဒဂုံမြို့သစ်ခရိုင်","ဗိုလ်တထောင်ခရိုင်","မရမ်းကုန်းခရိုင်","မင်္ဂလာဒုံခရိုင်",
        "လှည်းကူးခရိုင်","သန်လျင်ခရိုင်","သင်္ဃန်းကျွန်းခရိုင်","အလုံခရိုင်",
        "အင်းစိန်ခရိုင်","မှော်ဘီခရိုင်"
    )

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private lateinit var api: ApiClient
    private lateinit var page: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private var pending: (() -> Unit)? = null
    private val jobHandler = Handler(Looper.getMainLooper())
    private var currentToken = ""
    private var currentTab = TAB_HOME
    private var jobsBox: LinearLayout? = null
    private var summaryBox: TextView? = null
    private var profileBox: LinearLayout? = null
    private var lastAssignedIds = emptySet<Int>()
    private var profileCache: JSONObject? = null

    private val perm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (ok) pending?.invoke() else toast("GPS permission is required.")
        pending = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        migrateOldApi()
        window.statusBarColor = Color.parseColor("#075E54")
        window.navigationBarColor = Color.WHITE
        api = ApiClient { prefs.getString("api", API) ?: API }
        buildShell()
        val token = prefs.getString("token", "").orEmpty()
        if (token.isBlank()) {
            showActivation()
        } else {
            currentToken = token
            showTab(TAB_HOME)
        }
    }

    override fun onResume() {
        super.onResume()
        scheduleJobPoll()
    }

    override fun onPause() {
        jobHandler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun buildShell() {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F8F7"))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(28))
        }
        scroll.addView(page)
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(66)
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()
            visibility = View.GONE
        }
        shell.addView(bottomBar, LinearLayout.LayoutParams(-1, -2))

        // Android 15 / target SDK 35 edge-to-edge:
        // keep the app footer safely ABOVE the phone's gesture / 3-button navigation area.
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { view, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(dp(8), dp(7), dp(8), dp(7) + nav.bottom)
            insets
        }

        setContentView(shell)
        ViewCompat.requestApplyInsets(bottomBar)
    }

    private fun showActivation() {
        currentToken = ""
        bottomBar.visibility = View.GONE
        page.removeAllViews()
        page.addView(appHeader("DRIVER", "Native Driver App"))

        val intro = card().apply {
            addView(pill("YCTA MEMBER ACTIVATION", "#E6F4F1", "#07695D"))
            addView(title("Activate Driver Account", 24f))
            addView(body("Enter the activation code issued from YCTA Admin. After activation, this phone connects directly to the native Driver workflow."))
        }
        page.addView(intro, margin())

        val code = edit("YCTA Activation Code")
        page.addView(card().apply {
            addView(section("ACTIVATION CODE"))
            addView(code)
            addView(caption("Format: YCTA-XXXX-XXXX-XXXX • spaces/smart dashes are cleaned automatically"))
            addView(primaryButton("Activate & Continue") {
                val v = normalizeActivationCode(code.text.toString())
                code.setText(v)
                if (v.isBlank()) {
                    toast("Activation code is required.")
                } else if (!v.matches(Regex("^YCTA-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$"))) {
                    toast("Activation code format must be YCTA-XXXX-XXXX-XXXX")
                } else {
                    call({ api.post("activate", JSONObject().put("activation_code", v)) }) { j ->
                        val token = j.optString("token")
                        if (token.isBlank()) {
                            toast("Activation response has no token.")
                        } else {
                            prefs.edit().putString("token", token).apply()
                            currentToken = token
                            toast("Activation successful")
                            showTab(TAB_HOME)
                        }
                    }
                }
            })
            addView(secondaryButton("Test Server Connection") {
                call({ api.get("public_config") }) { toast("Server connection OK") }
            })
            addView(caption("Server: " + (prefs.getString("api", API) ?: API)))
        }, margin())
    }

    private fun showTab(tab: String) {
        if (currentToken.isBlank()) {
            showActivation()
            return
        }
        currentTab = tab
        page.removeAllViews()
        bottomBar.visibility = View.VISIBLE
        buildBottomBar()
        ViewCompat.requestApplyInsets(bottomBar)

        when (tab) {
            TAB_HOME -> renderHome()
            TAB_JOBS -> renderJobsScreen()
            TAB_PROFILE -> renderProfileScreen()
            else -> renderHome()
        }
    }

    private fun buildBottomBar() {
        bottomBar.removeAllViews()
        bottomBar.addView(navButton("HOME", TAB_HOME, "⌂"))
        bottomBar.addView(navButton("JOBS", TAB_JOBS, "●"))
        bottomBar.addView(navButton("CHAT", "chat", "✉"))
        bottomBar.addView(navButton("PROFILE", TAB_PROFILE, "◎"))
    }

    private fun navButton(label: String, tab: String, icon: String): View {
        val active = currentTab == tab
        return TextView(this).apply {
            text = icon + "\n" + label
            gravity = Gravity.CENTER
            textSize = 11f
            setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(Color.parseColor(if (active) "#087B6E" else "#637873"))
            setPadding(dp(4), dp(3), dp(4), dp(3))
            background = if (active) round("#E8F5F2", 14, null) else null
            setOnClickListener {
                if (tab == "chat") {
                    openChat()
                } else {
                    showTab(tab)
                }
            }
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply {
                leftMargin = dp(3)
                rightMargin = dp(3)
            }
        }
    }

    private fun renderHome() {
        page.addView(appHeader("DRIVER", "KILO TAXI • Native V6.2.2"))

        val onlineNow = prefs.getBoolean("online", false)
        val statusCard = card().apply {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val left = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(pill(if (onlineNow) "ONLINE" else "OFFLINE",
                    if (onlineNow) "#DCF5EA" else "#F2F3F3",
                    if (onlineNow) "#087B50" else "#63706D"))
                addView(title(if (onlineNow) "Ready for taxi jobs" else "You are offline", 22f))
                addView(body(if (onlineNow) "Smart GPS is active. New assignments will appear automatically." else "Go online to receive nearby passenger bookings."))
            }
            row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            val sw = Switch(this@MainActivity).apply {
                isChecked = onlineNow
                setOnCheckedChangeListener { _, checked -> setDriverOnline(checked) }
            }
            row.addView(sw)
            addView(row)
        }
        page.addView(statusCard, margin())

        page.addView(workflowCard(), margin())

        summaryBox = TextView(this).apply {
            text = "Loading today summary…"
            textSize = 16f
            setTextColor(Color.parseColor("#445B56"))
            setPadding(0, dp(6), 0, 0)
        }
        page.addView(card().apply {
            addView(section("TODAY"))
            addView(summaryBox)
        }, margin())

        jobsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(card().apply {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(section("CURRENT JOB"), LinearLayout.LayoutParams(0, -2, 1f))
                addView(smallButton("Refresh") { jobsBox?.let { loadJobs(it, currentToken) } })
            }
            addView(row)
            addView(jobsBox)
        }, margin())

        loadTodaySummary(currentToken)
        jobsBox?.let { loadJobs(it, currentToken) }
        scheduleJobPoll()
    }

    private fun workflowCard(): View {
        return card().apply {
            addView(section("TRIP WORKFLOW"))
            addView(body("Passenger Booking → Driver Assignment → Accept → OTP Pickup → Trip Start → Complete"))
            addView(workflowStepper("WAITING"))
        }
    }

    private fun renderJobsScreen() {
        page.addView(appHeader("JOBS", "Active trip workflow"))
        page.addView(card().apply {
            addView(section("HOW IT WORKS"))
            addView(body("WAITING → ASSIGNED → ACCEPTED → PICKUP → STARTED → COMPLETED"))
        }, margin())

        jobsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(card().apply {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(section("MY TAXI JOBS"), LinearLayout.LayoutParams(0, -2, 1f))
                addView(smallButton("Refresh") { jobsBox?.let { loadJobs(it, currentToken) } })
            }
            addView(row)
            addView(jobsBox)
        }, margin())

        jobsBox?.let { loadJobs(it, currentToken) }
        scheduleJobPoll()
    }

    private fun renderProfileScreen() {
        page.addView(appHeader("PROFILE", "YCTA Driver Membership"))
        profileBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(card().apply {
            addView(section("YCTA MEMBER"))
            addView(profileBox)
            addView(secondaryButton("Refresh Profile") { profileBox?.let { loadProfile(it, currentToken) } })
            addView(secondaryButton("Request Membership Renewal") {
                numberPrompt("Renewal days (30-1095)") { days ->
                    call({ api.post("renew_request", JSONObject().put("requested_days", days).put("note", "Driver App renewal"), currentToken) }) {
                        toast("Renewal request sent")
                        profileBox?.let { loadProfile(it, currentToken) }
                    }
                }
            })
        }, margin())

        val district = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, districts)
            val saved = prefs.getString("district", "").orEmpty()
            val idx = districts.indexOf(saved)
            if (idx >= 0) setSelection(idx)
        }
        val lite = Switch(this).apply {
            text = "Lite Mode — save battery/data"
            isChecked = prefs.getBoolean("lite_mode", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("lite_mode", checked).apply()
                if (prefs.getBoolean("online", false)) restartGpsService()
                toast(if (checked) "Lite Mode ON" else "Standard Mode ON")
                scheduleJobPoll()
            }
        }
        val server = edit("Server API URL").apply { setText(prefs.getString("api", API)) }

        page.addView(card().apply {
            addView(section("DRIVER SETTINGS"))
            addView(caption("District"))
            addView(district)
            addView(lite)
            addView(caption("Server API"))
            addView(server)
            addView(secondaryButton("Save Settings") {
                val v = server.text.toString().trim().ifBlank { API }
                prefs.edit().putString("api", v).putString("district", district.selectedItem.toString()).apply()
                toast("Settings saved")
            })
            addView(secondaryButton("Test Server") {
                val test = ApiClient { server.text.toString().trim().ifBlank { API } }
                call({ test.get("public_config") }) { toast("Server connection OK") }
            })
        }, margin())

        page.addView(dangerButton("Logout") {
            val keepApi = prefs.getString("api", API)
            prefs.edit().clear().putString("api", keepApi).apply()
            stopService(Intent(this, DriverLocationService::class.java))
            showActivation()
        }, margin())

        profileBox?.let { loadProfile(it, currentToken) }
    }

    private fun setDriverOnline(online: Boolean) {
        val district = prefs.getString("district", "").orEmpty().ifBlank { profileCache?.optString("district").orEmpty() }
        if (online && district.isBlank()) {
            toast("Please set Driver District in Profile first.")
            showTab(TAB_PROFILE)
            return
        }
        if (online) {
            requestGps {
                prefs.edit().putBoolean("online", true).putString("district", district).apply()
                val d = JSONObject().put("online", 1).put("availability", "AVAILABLE").put("district", district)
                call({ api.post("driver_status", d, currentToken) }) {
                    restartGpsService()
                    toast("Driver Online")
                    showTab(TAB_HOME)
                }
            }
        } else {
            prefs.edit().putBoolean("online", false).apply()
            stopService(Intent(this, DriverLocationService::class.java))
            val d = JSONObject().put("online", 0).put("availability", "OFFLINE").put("district", district)
            call({ api.post("driver_status", d, currentToken) }) {
                toast("Driver Offline")
                showTab(TAB_HOME)
            }
        }
    }

    private fun restartGpsService() {
        stopService(Intent(this, DriverLocationService::class.java))
        ContextCompat.startForegroundService(this, Intent(this, DriverLocationService::class.java))
    }

    private fun loadTodaySummary(token: String) {
        call({ api.get("driver_today_summary", emptyMap(), token) }) { j ->
            val s = j.optJSONObject("summary") ?: JSONObject()
            summaryBox?.text =
                "Trips  " + s.optInt("trips") +
                "     Active  " + s.optInt("active_jobs") +
                "\nKM  " + s.optString("total_km") +
                "     Fare  " + s.optString("total_fare") + " MMK"
        }
    }

    private fun loadProfile(box: LinearLayout, token: String) {
        call({ api.get("member_profile", emptyMap(), token) }) { j ->
            box.removeAllViews()
            val m = j.optJSONObject("member") ?: JSONObject()
            profileCache = m
            val currentDistrict = prefs.getString("district", "").orEmpty()
            if (currentDistrict.isBlank() && m.optString("district").isNotBlank()) {
                prefs.edit().putString("district", m.optString("district")).apply()
            }
            box.addView(title(m.optString("name").ifBlank { "YCTA Driver" }, 20f))
            box.addView(body(
                "Member ID: " + m.optString("member_id") +
                "\nDriver License: " + m.optString("driver_license") +
                "\nVehicle: " + m.optString("vehicle_no") +
                "\nCity No: " + m.optString("city_no") +
                "\nDistrict: " + m.optString("district") +
                "\nMembership Expiry: " + m.optString("membership_expires_at").ifBlank { "No expiry set" } +
                "\nDays Remaining: " + m.optString("membership_days_remaining") +
                "\nRenewal: " + m.optString("renewal_status")
            ))
            if (m.optBoolean("membership_expired")) {
                box.addView(pill("MEMBERSHIP EXPIRED", "#FDE8E8", "#A33A3A"))
            } else {
                box.addView(pill("MEMBERSHIP ACTIVE", "#DCF5EA", "#087B50"))
            }
        }
    }

    private fun loadJobs(box: LinearLayout, token: String) {
        call({ api.get("driver_jobs", emptyMap(), token) }) { j ->
            val jobs = j.optJSONArray("bookings") ?: JSONArray()
            val assigned = mutableSetOf<Int>()
            for (i in 0 until jobs.length()) {
                val b = jobs.optJSONObject(i) ?: continue
                if (b.optString("status") == "ASSIGNED") assigned.add(b.optInt("id"))
            }
            val newIds = assigned - lastAssignedIds
            if (newIds.isNotEmpty() && lastAssignedIds.isNotEmpty()) notifyNewJob()
            lastAssignedIds = assigned
            renderJobs(box, jobs, token)
            loadTodaySummary(token)
        }
    }

    private fun renderJobs(box: LinearLayout, jobs: JSONArray, token: String) {
        box.removeAllViews()
        if (jobs.length() == 0) {
            box.addView(emptyState("No active taxi job", "Stay Online. New assigned jobs will appear automatically."))
            return
        }

        for (i in 0 until jobs.length()) {
            val b = jobs.getJSONObject(i)
            val id = b.optInt("id")
            val status = b.optString("status")
            val jobCard = card("#FFFFFF", "#D8E8E4")

            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(title(b.optString("booking_code"), 18f), LinearLayout.LayoutParams(0, -2, 1f))
                addView(statusPill(status))
            }
            jobCard.addView(top)
            jobCard.addView(workflowStepper(status), margin(6))
            jobCard.addView(infoLine("Pickup", b.optString("pickup")))
            jobCard.addView(infoLine("Destination", b.optString("destination")))
            jobCard.addView(infoLine("Passenger", b.optString("passenger_name") + "  " + b.optString("passenger_phone")))

            if (status == "ASSIGNED") {
                val countdown = TextView(this).apply {
                    textSize = 17f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#B96A00"))
                    setPadding(0, dp(10), 0, dp(4))
                }
                jobCard.addView(countdown)
                startAssignmentCountdown(countdown, b.optString("assignment_expires_at"))
            }

            val phone = b.optString("passenger_phone")
            if (phone.isNotBlank()) {
                jobCard.addView(secondaryButton("Call Passenger") {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.replace(" ", ""))))
                })
            }

            when (status) {
                "ASSIGNED" -> {
                    jobCard.addView(primaryButton("ACCEPT JOB") {
                        actionAndRefresh("driver_action", JSONObject().put("booking_id", id).put("status", "ACCEPTED"), token, box)
                    })
                    jobCard.addView(dangerButton("Reject Job") {
                        AlertDialog.Builder(this)
                            .setTitle("Reject this job?")
                            .setMessage("The booking will be sent to another available driver.")
                            .setPositiveButton("Reject") { _, _ ->
                                actionAndRefresh("driver_action", JSONObject().put("booking_id", id).put("status", "REJECTED"), token, box)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    })
                }
                "ACCEPTED" -> {
                    jobCard.addView(primaryButton("VERIFY PICKUP OTP") {
                        textPrompt("Enter 4-digit Pickup OTP", true) { otp ->
                            actionAndRefresh("driver_verify_otp", JSONObject().put("booking_id", id).put("otp", otp), token, box)
                        }
                    })
                }
                "PICKUP" -> {
                    jobCard.addView(primaryButton("START TRIP") {
                        decimalPrompt("Start Odometer KM") { km ->
                            actionAndRefresh("driver_action", JSONObject().put("booking_id", id).put("status", "STARTED").put("start_km", km), token, box)
                        }
                    })
                }
                "STARTED" -> {
                    val startKm = b.optString("start_km")
                    if (startKm.isNotBlank()) jobCard.addView(caption("Start odometer: " + startKm + " km"))
                    jobCard.addView(primaryButton("COMPLETE TRIP") {
                        decimalPrompt("End Odometer KM") { km ->
                            call({ api.post("driver_action", JSONObject().put("booking_id", id).put("status", "COMPLETED").put("end_km", km), token) }) { j ->
                                val done = j.optJSONObject("booking") ?: JSONObject()
                                AlertDialog.Builder(this)
                                    .setTitle("Trip Completed")
                                    .setMessage("Distance: " + done.optString("distance_km") + " km\nFare: " + done.optString("fare") + " MMK")
                                    .setPositiveButton("OK") { _, _ -> loadJobs(box, token) }
                                    .show()
                            }
                        }
                    })
                }
            }
            box.addView(jobCard, margin(10))
        }
    }

    private fun actionAndRefresh(action: String, data: JSONObject, token: String, box: LinearLayout) {
        call({ api.post(action, data, token) }) { j ->
            toast(j.optString("status").ifBlank { "Updated" })
            loadJobs(box, token)
        }
    }

    private fun openChat() {
        startActivity(
            Intent(this, CommunicationActivity::class.java)
                .putExtra(CommunicationActivity.EXTRA_API, prefs.getString("api", API) ?: API)
                .putExtra(CommunicationActivity.EXTRA_TOKEN, currentToken)
                .putExtra(CommunicationActivity.EXTRA_ROLE, "driver")
                .putExtra(CommunicationActivity.EXTRA_TITLE, "Driver Chat + Trip Rooms")
        )
    }

    private fun workflowStepper(current: String): View {
        val stages = listOf("WAITING", "ASSIGNED", "ACCEPTED", "PICKUP", "STARTED", "COMPLETED")
        val idx = stages.indexOf(current).coerceAtLeast(0)
        val wrap = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(8))
        }
        stages.forEachIndexed { i, s ->
            val done = i < idx
            val active = i == idx
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), 0, dp(4), 0)
            }
            box.addView(TextView(this).apply {
                text = if (done) "✓" else (i + 1).toString()
                gravity = Gravity.CENTER
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = round(
                    if (done) "#1C9B76" else if (active) "#087B6E" else "#AAB8B5",
                    99, null
                )
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            })
            box.addView(TextView(this).apply {
                text = s
                gravity = Gravity.CENTER
                textSize = 9f
                setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(Color.parseColor(if (active) "#075E54" else "#667B76"))
                setPadding(0, dp(4), 0, 0)
            })
            row.addView(box, LinearLayout.LayoutParams(dp(74), -2))
            if (i < stages.lastIndex) {
                row.addView(View(this).apply {
                    setBackgroundColor(Color.parseColor(if (i < idx) "#49AF93" else "#CFDAD7"))
                }, LinearLayout.LayoutParams(dp(18), dp(3)))
            }
        }
        wrap.addView(row)
        return wrap
    }

    private fun statusPill(status: String): TextView {
        val pair = when (status) {
            "ASSIGNED" -> "#FFF1D6" to "#A95D00"
            "ACCEPTED" -> "#DDF1FF" to "#17679A"
            "PICKUP" -> "#EEE7FF" to "#6747A8"
            "STARTED" -> "#E5F4EA" to "#20764D"
            "COMPLETED" -> "#DCF5EA" to "#087B50"
            else -> "#ECEFEE" to "#596B67"
        }
        return pill(status, pair.first, pair.second)
    }

    private fun scheduleJobPoll() {
        jobHandler.removeCallbacksAndMessages(null)
        if (currentToken.isBlank() || jobsBox == null) return
        val first = if (prefs.getBoolean("lite_mode", true)) 30000L else 15000L
        jobHandler.postDelayed(object : Runnable {
            override fun run() {
                val box = jobsBox
                if (box != null && currentToken.isNotBlank()) loadJobs(box, currentToken)
                val ms = if (prefs.getBoolean("lite_mode", true)) 30000L else 15000L
                jobHandler.postDelayed(this, ms)
            }
        }, first)
    }

    private fun notifyNewJob() {
        toast("New taxi job assigned")
        runCatching {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(600)
            }
        }
    }

    private fun startAssignmentCountdown(view: TextView, expires: String) {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Yangon")
        }
        val target = runCatching { format.parse(expires)?.time ?: 0L }.getOrDefault(0L)
        if (target <= 0L) {
            view.text = "Accept timer unavailable"
            return
        }
        view.post(object : Runnable {
            override fun run() {
                if (!view.isAttachedToWindow) return
                val sec = ((target - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                view.text = if (sec > 0) "Accept within " + sec + " sec" else "Assignment expired — waiting for reassignment"
                if (sec > 0) view.postDelayed(this, 1000L)
            }
        })
    }

    private fun normalizeActivationCode(raw: String): String {
        var s = raw.trim().uppercase(Locale.US)
        s = s
            .replace('–','-')
            .replace('—','-')
            .replace('−','-')
            .replace('‐','-')
            .replace('‑','-')
            .replace('﹘','-')
            .replace('﹣','-')
            .replace('－','-')
        s = s.replace(Regex("[\\s\\u00A0\\u200B\\u200C\\u200D\\u2060\\uFEFF]+"), "")
        val compact = s.replace("-", "")
        if (compact.matches(Regex("^YCTA[0-9A-F]{12}$"))) {
            val x = compact.substring(4)
            return "YCTA-" + x.substring(0,4) + "-" + x.substring(4,8) + "-" + x.substring(8,12)
        }
        return s
    }

    private fun migrateOldApi() {
        val saved = prefs.getString("api", "").orEmpty().trim()
        if (saved.isBlank() || saved == OLD_API) {
            prefs.edit().putString("api", API).apply()
        }
    }

    private fun requestGps(done: () -> Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            done()
        } else {
            pending = done
            perm.launch(permissions.toTypedArray())
        }
    }

    private fun call(fn: () -> JSONObject, done: (JSONObject) -> Unit) {
        thread {
            val result = runCatching(fn).getOrElse {
                JSONObject().put("ok", false).put("error", it.message ?: "Network error")
            }
            runOnUiThread {
                if (result.optBoolean("ok")) done(result)
                else toast(friendlyError(result.optString("error", "Request failed")))
            }
        }
    }

    private fun friendlyError(error: String): String {
        return when (error) {
            "ASSIGNMENT_EXPIRED" -> "This assignment expired and is being reassigned."
            "INVALID_TRANSITION" -> "Trip status changed. Refreshing is required."
            "INVALID_PICKUP_OTP" -> "Pickup OTP is incorrect."
            "OTP_LOCKED" -> "OTP is locked. Contact Call Center."
            "MEMBERSHIP_EXPIRED" -> "Membership expired. Please renew."
            "ACTIVE_TRIP_CANNOT_GO_OFFLINE" -> "Complete or reject the active job before going offline."
            "CODE_REQUIRED" -> "Enter your YCTA activation code."
            "CODE_FORMAT_INVALID" -> "Activation code format must be YCTA-XXXX-XXXX-XXXX."
            "INVALID_CODE" -> "Activation code was not found. Check the code or generate a new one in Admin."
            "INVALID_OR_USED_CODE" -> "Activation code is invalid or already used. Generate a new code if needed."
            "CODE_ALREADY_USED" -> "This activation code was already used. Generate a new code for this phone."
            "CODE_REVOKED" -> "This activation code was revoked. Generate a new code in Admin."
            "CODE_EXPIRED" -> "This activation code expired. Generate a new code in Admin."
            "MEMBERSHIP_SUSPENDED" -> "This membership is suspended. Admin must reactivate the member first."
            else -> error.replace("_", " ")
        }
    }

    private fun textPrompt(title: String, numeric: Boolean = false, done: (String) -> Unit) {
        val input = EditText(this)
        if (numeric) input.inputType = InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> done(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun numberPrompt(title: String, done: (Int) -> Unit) {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> input.text.toString().toIntOrNull()?.let(done) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun decimalPrompt(title: String, done: (Double) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> input.text.toString().toDoubleOrNull()?.let(done) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun appHeader(kicker: String, subtitle: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(8))
            addView(TextView(this@MainActivity).apply {
                text = "KILO TAXI  •  " + kicker
                letterSpacing = .08f
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#087B6E"))
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 27f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#183C36"))
            })
            addView(TextView(this@MainActivity).apply {
                text = "Native Android • Smart GPS • Low Data"
                textSize = 13f
                setTextColor(Color.parseColor("#71827E"))
            })
        }
    }

    private fun emptyState(head: String, sub: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(24), dp(12), dp(24))
            addView(TextView(this@MainActivity).apply {
                text = "✓"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#77A79A"))
            })
            addView(title(head, 18f).apply { gravity = Gravity.CENTER })
            addView(body(sub).apply { gravity = Gravity.CENTER })
        }
    }

    private fun infoLine(label: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(7), 0, dp(5))
            addView(caption(label.uppercase()))
            addView(TextView(this@MainActivity).apply {
                text = value.ifBlank { "—" }
                textSize = 16f
                setTextColor(Color.parseColor("#203F39"))
            })
        }
    }

    private fun card(bg: String = "#FFFFFF", stroke: String = "#D8E8E4"): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = round(bg, 20, stroke)
            elevation = dp(3).toFloat()
        }
    }

    private fun section(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 13f
            letterSpacing = .05f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#087B6E"))
            setPadding(0, 0, 0, dp(6))
        }
    }

    private fun title(textValue: String, size: Float): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = size
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#183C36"))
            setPadding(0, dp(3), 0, dp(3))
        }
    }

    private fun body(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 14f
            setTextColor(Color.parseColor("#566D68"))
            setLineSpacing(0f, 1.12f)
        }
    }

    private fun caption(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 12f
            setTextColor(Color.parseColor("#74847F"))
            setPadding(0, dp(6), 0, dp(3))
        }
    }

    private fun pill(textValue: String, bg: String, fg: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(fg))
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = round(bg, 99, null)
        }
    }

    private fun edit(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 15f
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = round("#F7FAF9", 12, "#D8E5E2")
        }
    }

    private fun primaryButton(label: String, action: () -> Unit): Button {
        return styledButton(label, "#087B6E", "#FFFFFF", action)
    }

    private fun secondaryButton(label: String, action: () -> Unit): Button {
        return styledButton(label, "#EEF5F3", "#234D45", action)
    }

    private fun dangerButton(label: String, action: () -> Unit): Button {
        return styledButton(label, "#FBEAEA", "#A33A3A", action)
    }

    private fun smallButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#087B6E"))
            background = round("#E8F5F2", 14, null)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-2, dp(38))
        }
    }

    private fun styledButton(label: String, bg: String, fg: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(fg))
            background = round(bg, 14, null)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(9) }
        }
    }

    private fun margin(top: Int = 12): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    }

    private fun round(bg: String, radius: Int, stroke: String?): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(bg))
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), Color.parseColor(stroke))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
