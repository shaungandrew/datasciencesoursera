package com.aaii.kilotaxi

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    companion object {
        private const val PREFS = "kilo_taxi_v3"
        private const val DEFAULT_API = "https://ycta.yangoncity.net/kilotaxi/api/index.php"
        private const val CALL_CENTER = "0925256954"
    }

    private val districts = listOf(
        "ကျောက်တံတားခရိုင်","ကမာရွတ်ခရိုင်","တိုက်ကြီးခရိုင်","တွံတေးခရိုင်",
        "ဒဂုံမြို့သစ်ခရိုင်","ဗိုလ်တထောင်ခရိုင်","မရမ်းကုန်းခရိုင်","မင်္ဂလာဒုံခရိုင်",
        "လှည်းကူးခရိုင်","သန်လျင်ခရိုင်","သင်္ဃန်းကျွန်းခရိုင်","အလုံခရိုင်",
        "အင်းစိန်ခရိုင်","မှော်ဘီခရိုင်"
    )

    private lateinit var root: LinearLayout
    private lateinit var api: ApiClient
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var mapView: MapView? = null
    private var pendingOnlineStart: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine) pendingOnlineStart?.invoke() else toast("GPS permission is required for Driver Online mode.")
        pendingOnlineStart = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        window.statusBarColor = Color.parseColor("#083D6B")
        window.navigationBarColor = Color.WHITE
        api = ApiClient { prefs.getString("api_url", DEFAULT_API) ?: DEFAULT_API }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(28))
            setBackgroundColor(Color.parseColor("#EEF4F8"))
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
        showHome()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    private fun clearScreen() {
        mapView?.onDetach()
        mapView = null
        root.removeAllViews()
    }

    private fun showHome() {
        clearScreen()
        root.addView(title("KILO TAXI", "Core Booking System V3"))
        root.addView(infoCard("CALL CENTER", "09 252 569 54", "GPS Dispatch • OTP • KM Fare • YCTA Membership"))
        root.addView(bigButton("CALL CENTER + LIVE MAP") { showCallCenter() })
        root.addView(bigButton("DRIVER APP + LIVE GPS") { showDriver() })
        root.addView(bigButton("YCTA MEMBER REGISTER / ACTIVATION") { showMembership() })
        root.addView(bigButton("SETTINGS") { showSettings() })
    }

    private fun showCallCenter() {
        clearScreen()
        root.addView(nav("Call Center + Live Map"))
        val token = edit("Operator API Token").apply { setText(prefs.getString("operator_token", "")) }
        root.addView(card().apply {
            addView(section("CALL CENTER AUTH"))
            addView(token)
            addView(Button(this@MainActivity).apply {
                text = "Save Operator Token"; isAllCaps = false
                setOnClickListener {
                    prefs.edit().putString("operator_token", token.text.toString().trim()).apply()
                    toast("Operator token saved.")
                }
            })
        }, margin())

        val district = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, districts)
        }

        val map = makeMap()
        mapView = map
        root.addView(card().apply {
            addView(section("LIVE DRIVER GPS MAP"))
            addView(map, LinearLayout.LayoutParams(-1, dp(330)))
            addView(Button(this@MainActivity).apply {
                text = "Refresh Online Drivers Map"; isAllCaps = false
                setOnClickListener { refreshDriverMap(map, district.selectedItem.toString()) }
            })
        }, margin())

        val passengerName = edit("Passenger Name")
        val passengerPhone = edit("Passenger Phone")
        val pickup = edit("Pickup / Landmark")
        val pickupLat = edit("Pickup Latitude")
        val pickupLng = edit("Pickup Longitude")
        val destination = edit("Destination")
        val destinationLat = edit("Destination Latitude (optional)")
        val destinationLng = edit("Destination Longitude (optional)")
        val notes = edit("Operator Notes")
        val result = TextView(this).apply { setPadding(0, dp(8), 0, 0) }

        root.addView(card().apply {
            addView(section("NEW BOOKING + AUTO DISPATCH"))
            addView(label("Township / District"))
            addView(district)
            addView(passengerName); addView(passengerPhone); addView(pickup)
            val mapCenter = Button(this@MainActivity).apply {
                text = "Use Map Center as Pickup GPS"; isAllCaps = false
                setOnClickListener {
                    val c = map.mapCenter
                    pickupLat.setText(c.latitude.toString())
                    pickupLng.setText(c.longitude.toString())
                    addSingleMarker(map, c.latitude, c.longitude, "Pickup")
                    toast("Pickup GPS set from map center.")
                }
            }
            addView(mapCenter)
            addView(pickupLat); addView(pickupLng)
            addView(destination); addView(destinationLat); addView(destinationLng); addView(notes)
            addView(Button(this@MainActivity).apply {
                text = "Create Booking + Auto Dispatch"; isAllCaps = false
                setTextColor(Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#087B77"))
                setOnClickListener {
                    val t = prefs.getString("operator_token", "").orEmpty()
                    val d = JSONObject()
                        .put("passenger_name", passengerName.text.toString())
                        .put("passenger_phone", passengerPhone.text.toString())
                        .put("district", district.selectedItem.toString())
                        .put("pickup", pickup.text.toString())
                        .put("destination", destination.text.toString())
                        .put("notes", notes.text.toString())
                        .put("auto_dispatch", 1)
                    pickupLat.text.toString().toDoubleOrNull()?.let { d.put("pickup_lat", it) }
                    pickupLng.text.toString().toDoubleOrNull()?.let { d.put("pickup_lng", it) }
                    destinationLat.text.toString().toDoubleOrNull()?.let { d.put("destination_lat", it) }
                    destinationLng.text.toString().toDoubleOrNull()?.let { d.put("destination_lng", it) }
                    result.text = "Creating booking…"
                    netJson({ api.post("operator_create_booking", d, t) }) { j ->
                        val assignment = j.optJSONObject("assignment")
                        result.text = "Booking: " + j.optString("booking_code") +
                            "\nPickup OTP: " + j.optString("pickup_otp") +
                            "\nStatus: " + j.optString("status") +
                            (if (assignment != null && assignment.optBoolean("ok"))
                                "\nDriver: " + assignment.optString("driver_name") +
                                " • " + assignment.optString("driver_member_id") +
                                " • " + assignment.optString("vehicle_no") else
                                "\nDispatch: waiting / no available driver")
                        refreshDriverMap(map, district.selectedItem.toString())
                    }
                }
            })
            addView(result)
        }, margin())

        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(card().apply {
            addView(section("DISPATCH / BOOKING STATUS"))
            addView(Button(this@MainActivity).apply {
                text = "Load Available Drivers"; isAllCaps = false
                setOnClickListener {
                    val t = prefs.getString("operator_token", "").orEmpty()
                    netJson({ api.get("operator_online_drivers", mapOf("district" to district.selectedItem.toString()), t) }) {
                        renderDrivers(listBox, it.optJSONArray("drivers") ?: JSONArray())
                    }
                }
            })
            addView(Button(this@MainActivity).apply {
                text = "Load Bookings"; isAllCaps = false
                setOnClickListener {
                    val t = prefs.getString("operator_token", "").orEmpty()
                    netJson({ api.get("operator_bookings", mapOf("district" to district.selectedItem.toString()), t) }) {
                        renderBookings(listBox, it.optJSONArray("bookings") ?: JSONArray())
                    }
                }
            })
            addView(listBox)
        }, margin())

        val fareBox = TextView(this).apply { text = "Loading fare settings…"; textSize = 14f }
        root.addView(card().apply {
            addView(section("CURRENT FARE SETTINGS"))
            addView(fareBox)
            netJson({ api.get("public_config") }) { j ->
                fareBox.text = "Base Fare: " + j.optString("base_fare") +
                    "\nRate/KM: " + j.optString("rate_per_km") +
                    "\nMinimum Fare: " + j.optString("minimum_fare") +
                    "\nWaiting/Minute: " + j.optString("waiting_per_minute") +
                    "\nAuto Dispatch: " + if (j.optBoolean("auto_dispatch_enabled")) "ON" else "OFF"
            }
        }, margin())
    }

    private fun refreshDriverMap(map: MapView, district: String) {
        val t = prefs.getString("operator_token", "").orEmpty()
        netJson({ api.get("operator_map_drivers", mapOf("district" to district), t) }) { j ->
            val keep = map.overlays.filterNot { it is Marker }.toMutableList()
            map.overlays.clear()
            map.overlays.addAll(keep)
            val a = j.optJSONArray("drivers") ?: JSONArray()
            var first: GeoPoint? = null
            for (i in 0 until a.length()) {
                val d = a.getJSONObject(i)
                val lat = d.optDouble("lat", Double.NaN)
                val lng = d.optDouble("lng", Double.NaN)
                if (lat.isNaN() || lng.isNaN()) continue
                val p = GeoPoint(lat, lng)
                if (first == null) first = p
                Marker(map).apply {
                    position = p
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = (d.optString("name").ifBlank { "YCTA Driver" }) +
                        " • " + d.optString("ycta_member_id") +
                        " • " + d.optString("vehicle_no")
                    snippet = d.optString("district") + " • " + d.optString("availability") +
                        " • " + d.optString("last_seen")
                    map.overlays.add(this)
                }
            }
            first?.let { map.controller.animateTo(it) }
            map.invalidate()
            toast(a.length().toString() + " online driver(s) mapped.")
        }
    }

    private fun renderDrivers(box: LinearLayout, a: JSONArray) {
        box.removeAllViews()
        if (a.length() == 0) {
            box.addView(TextView(this).apply { text = "No available drivers."; setPadding(0, dp(8), 0, 0) })
            return
        }
        for (i in 0 until a.length()) {
            val d = a.getJSONObject(i)
            val c = card()
            c.addView(TextView(this).apply {
                text = (d.optString("name").ifBlank { "YCTA Driver" }) + " • " + d.optString("ycta_member_id")
                textSize = 17f; setTypeface(typeface, Typeface.BOLD)
            })
            c.addView(TextView(this).apply {
                text = d.optString("vehicle_no") + " • " + d.optString("district") +
                    (if (d.has("distance_km")) " • " + d.optString("distance_km") + " km" else "")
            })
            c.addView(Button(this).apply {
                text = "Manual Assign to Booking ID"; isAllCaps = false
                setOnClickListener {
                    promptNumber("Booking database ID") { bookingId ->
                        val t = prefs.getString("operator_token", "").orEmpty()
                        val payload = JSONObject().put("booking_id", bookingId).put("member_id", d.optInt("member_pk"))
                        netToast { api.post("operator_assign", payload, t) }
                    }
                }
            })
            box.addView(c, margin())
        }
    }

    private fun renderBookings(box: LinearLayout, a: JSONArray) {
        box.removeAllViews()
        if (a.length() == 0) {
            box.addView(TextView(this).apply { text = "No bookings."; setPadding(0, dp(8), 0, 0) })
            return
        }
        for (i in 0 until a.length()) {
            val b = a.getJSONObject(i)
            val id = b.optInt("id")
            val status = b.optString("status")
            val c = card()
            c.addView(TextView(this).apply {
                text = "#" + id + "  " + b.optString("booking_code") + "  [" + status + "]"
                textSize = 16f; setTypeface(typeface, Typeface.BOLD)
            })
            c.addView(TextView(this).apply {
                text = b.optString("passenger_name") + " • " + b.optString("passenger_phone") +
                    "\n" + b.optString("pickup") + " → " + b.optString("destination") +
                    "\nDriver: " + b.optString("driver_name") + " " + b.optString("driver_member_id") +
                    "\nDispatch attempts: " + b.optInt("dispatch_attempts") +
                    " • Assignment expires: " + b.optString("assignment_expires_at")
            })
            if (status == "COMPLETED") {
                c.addView(TextView(this).apply {
                    text = "Distance: " + b.optString("distance_km") + " km • Fare: " + b.optString("fare")
                    setTypeface(typeface, Typeface.BOLD)
                })
            }
            if (status !in listOf("COMPLETED", "CANCELLED")) {
                c.addView(Button(this).apply {
                    text = "Auto Reassign"; isAllCaps = false
                    setOnClickListener {
                        val t = prefs.getString("operator_token", "").orEmpty()
                        netToast { api.post("operator_reassign", JSONObject().put("booking_id", id), t) }
                    }
                })
                c.addView(Button(this).apply {
                    text = "Cancel Booking"; isAllCaps = false
                    setOnClickListener {
                        val t = prefs.getString("operator_token", "").orEmpty()
                        netToast { api.post("operator_cancel", JSONObject().put("booking_id", id).put("reason", "Call Center cancelled"), t) }
                    }
                })
            }
            box.addView(c, margin())
        }
    }

    private fun showDriver() {
        clearScreen()
        root.addView(nav("Driver App + Live GPS"))
        val token = prefs.getString("driver_token", "").orEmpty()
        if (token.isBlank()) {
            val code = edit("Activation Code (YCTA-XXXX-XXXX-XXXX)")
            root.addView(card().apply {
                addView(section("YCTA MEMBER ACTIVATION"))
                addView(code)
                addView(Button(this@MainActivity).apply {
                    text = "Activate & Login"; isAllCaps = false
                    setOnClickListener {
                        netJson({ api.post("activate", JSONObject().put("activation_code", code.text.toString())) }) { j ->
                            prefs.edit().putString("driver_token", j.optString("token")).apply()
                            toast("Activation successful.")
                            showDriver()
                        }
                    }
                })
            }, margin())
            return
        }

        val profileBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val district = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, districts)
        }
        val online = Switch(this).apply {
            text = "Driver Online + Live GPS"
            isChecked = prefs.getBoolean("driver_online", false)
        }

        root.addView(card().apply {
            addView(section("DRIVER STATUS / MEMBERSHIP"))
            addView(district)
            addView(online)
            addView(Button(this@MainActivity).apply {
                text = "Apply Online Status"; isAllCaps = false
                setOnClickListener {
                    if (online.isChecked) {
                        requestGpsThen {
                            prefs.edit()
                                .putBoolean("driver_online", true)
                                .putString("driver_district", district.selectedItem.toString())
                                .apply()
                            val d = JSONObject()
                                .put("online", 1)
                                .put("availability", "AVAILABLE")
                                .put("district", district.selectedItem.toString())
                            netJson({ api.post("driver_status", d, token) }) {
                                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, DriverLocationService::class.java))
                                toast("Driver Online. Foreground GPS started.")
                            }
                        }
                    } else {
                        prefs.edit().putBoolean("driver_online", false).apply()
                        stopService(Intent(this@MainActivity, DriverLocationService::class.java))
                        val d = JSONObject().put("online", 0).put("availability", "OFFLINE").put("district", district.selectedItem.toString())
                        netToast { api.post("driver_status", d, token) }
                    }
                }
            })
            addView(Button(this@MainActivity).apply {
                text = "Load My YCTA Profile / Membership"; isAllCaps = false
                setOnClickListener { loadDriverProfile(profileBox, token) }
            })
            addView(profileBox)
        }, margin())

        val jobsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(card().apply {
            addView(section("MY ASSIGNED JOBS"))
            addView(Button(this@MainActivity).apply {
                text = "Refresh Jobs"; isAllCaps = false
                setOnClickListener {
                    netJson({ api.get("driver_jobs", emptyMap(), token) }) { j ->
                        renderDriverJobs(jobsBox, j.optJSONArray("bookings") ?: JSONArray(), token)
                    }
                }
            })
            addView(jobsBox)
        }, margin())

        root.addView(Button(this).apply {
            text = "Logout Driver"; isAllCaps = false
            setOnClickListener {
                prefs.edit().remove("driver_token").putBoolean("driver_online", false).apply()
                stopService(Intent(this@MainActivity, DriverLocationService::class.java))
                showDriver()
            }
        })
        loadDriverProfile(profileBox, token)
    }

    private fun loadDriverProfile(box: LinearLayout, token: String) {
        netJson({ api.get("member_profile", emptyMap(), token) }) { j ->
            box.removeAllViews()
            val m = j.optJSONObject("member") ?: JSONObject()
            val expired = m.optBoolean("membership_expired")
            box.addView(TextView(this).apply {
                text = m.optString("name") +
                    "\nMember ID: " + m.optString("member_id") +
                    "\nLicense: " + m.optString("driver_license") +
                    "\nVehicle: " + m.optString("vehicle_no") +
                    "\nCity No: " + m.optString("city_no") +
                    "\nTownship: " + m.optString("district") +
                    "\nMembership Expiry: " + m.optString("membership_expires_at").ifBlank { "No expiry set" } +
                    "\nDays Remaining: " + m.optString("membership_days_remaining") +
                    "\nRenewal: " + m.optString("renewal_status")
                textSize = 15f
                setTextColor(if (expired) Color.parseColor("#A12424") else Color.parseColor("#24465D"))
            })
            box.addView(Button(this).apply {
                text = "Request Membership Renewal"; isAllCaps = false
                setOnClickListener {
                    promptNumber("Renewal days (example 365)") { days ->
                        netToast { api.post("renew_request", JSONObject().put("requested_days", days).put("note", "Requested from Driver App"), token) }
                    }
                }
            })
        }
    }

    private fun renderDriverJobs(box: LinearLayout, a: JSONArray, token: String) {
        box.removeAllViews()
        if (a.length() == 0) {
            box.addView(TextView(this).apply { text = "No assigned jobs."; setPadding(0, dp(8), 0, 0) })
            return
        }
        for (i in 0 until a.length()) {
            val b = a.getJSONObject(i)
            val id = b.optInt("id")
            val status = b.optString("status")
            val c = card()
            c.addView(TextView(this).apply {
                text = b.optString("booking_code") + " [" + status + "]"
                textSize = 17f; setTypeface(typeface, Typeface.BOLD)
            })
            c.addView(TextView(this).apply {
                text = b.optString("pickup") + " → " + b.optString("destination") +
                    "\nPassenger: " + b.optString("passenger_phone") +
                    "\nAssignment expires: " + b.optString("assignment_expires_at")
            })

            if (b.has("pickup_lat") && !b.isNull("pickup_lat")) {
                c.addView(Button(this).apply {
                    text = "Show Pickup on Map"; isAllCaps = false
                    setOnClickListener {
                        showJobMap(
                            b.optDouble("pickup_lat"), b.optDouble("pickup_lng"),
                            if (!b.isNull("destination_lat")) b.optDouble("destination_lat") else null,
                            if (!b.isNull("destination_lng")) b.optDouble("destination_lng") else null
                        )
                    }
                })
            }

            when (status) {
                "ASSIGNED" -> {
                    c.addView(actionButton("Accept Job") {
                        netToast { api.post("driver_action", JSONObject().put("booking_id", id).put("status", "ACCEPTED"), token) }
                    })
                    c.addView(actionButton("Reject Job") {
                        netToast { api.post("driver_action", JSONObject().put("booking_id", id).put("status", "REJECTED"), token) }
                    })
                }
                "ACCEPTED" -> {
                    c.addView(actionButton("Verify Passenger Pickup OTP") {
                        promptText("Enter 4-digit Pickup OTP", true) { otp ->
                            netToast { api.post("driver_verify_otp", JSONObject().put("booking_id", id).put("otp", otp), token) }
                        }
                    })
                }
                "PICKUP" -> {
                    c.addView(actionButton("Trip Start") {
                        promptDecimal("Start Odometer KM") { km ->
                            netToast { api.post("driver_action", JSONObject().put("booking_id", id).put("status", "STARTED").put("start_km", km), token) }
                        }
                    })
                }
                "STARTED" -> {
                    c.addView(actionButton("Trip Complete") {
                        promptDecimal("End Odometer KM") { km ->
                            netJson({ api.post("driver_action", JSONObject().put("booking_id", id).put("status", "COMPLETED").put("end_km", km), token) }) { j ->
                                val done = j.optJSONObject("booking") ?: JSONObject()
                                toast("Trip complete • " + done.optString("distance_km") + " km • Fare " + done.optString("fare"))
                            }
                        }
                    })
                }
            }
            box.addView(c, margin())
        }
    }

    private fun showMembership() {
        clearScreen()
        root.addView(nav("YCTA Register + Activation"))
        val name = edit("Name")
        val phone = edit("Phone")
        val memberId = edit("Existing Member ID (optional)")
        val license = edit("Driver License")
        val vehicle = edit("Taxi Vehicle No.")
        val city = edit("City No.")
        val district = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, districts)
        }
        val nrc = edit("NRC")
        val address = edit("Address")
        val out = TextView(this)

        root.addView(card().apply {
            addView(section("MEMBERSHIP REGISTER"))
            addView(name); addView(phone); addView(memberId); addView(license); addView(vehicle); addView(city); addView(district); addView(nrc); addView(address)
            addView(Button(this@MainActivity).apply {
                text = "Submit Registration"; isAllCaps = false
                setOnClickListener {
                    val d = JSONObject()
                        .put("name", name.text.toString()).put("phone", phone.text.toString())
                        .put("member_id", memberId.text.toString()).put("driver_license", license.text.toString())
                        .put("vehicle_no", vehicle.text.toString()).put("city_no", city.text.toString())
                        .put("district", district.selectedItem.toString()).put("nrc", nrc.text.toString()).put("address", address.text.toString())
                    out.text = "Submitting…"
                    netJson({ api.post("register", d) }) { j ->
                        out.text = "Application ID: " + j.optInt("application_id") + "\nStatus: pending"
                    }
                }
            })
            addView(out)
        }, margin())

        val code = edit("Activation Code")
        root.addView(card().apply {
            addView(section("ACTIVATION"))
            addView(code)
            addView(Button(this@MainActivity).apply {
                text = "Activate Driver Membership"; isAllCaps = false
                setOnClickListener {
                    netJson({ api.post("activate", JSONObject().put("activation_code", code.text.toString())) }) { j ->
                        prefs.edit().putString("driver_token", j.optString("token")).apply()
                        toast("Membership activated. Driver login saved.")
                    }
                }
            })
        }, margin())
    }

    private fun showSettings() {
        clearScreen()
        root.addView(nav("Settings"))
        val apiUrl = edit("API URL").apply { setText(prefs.getString("api_url", DEFAULT_API)) }
        val op = edit("Operator API Token").apply { setText(prefs.getString("operator_token", "")) }
        root.addView(card().apply {
            addView(label("Server API URL")); addView(apiUrl)
            addView(label("Call Center Operator Token")); addView(op)
            addView(TextView(this@MainActivity).apply {
                text = "Default Call Center: 09 252 569 54"
                setPadding(0, dp(8), 0, dp(8))
            })
            addView(Button(this@MainActivity).apply {
                text = "Save Settings"; isAllCaps = false
                setOnClickListener {
                    prefs.edit()
                        .putString("api_url", apiUrl.text.toString().trim())
                        .putString("operator_token", op.text.toString().trim())
                        .apply()
                    toast("Settings saved.")
                }
            })
        }, margin())
    }

    private fun requestGpsThen(done: () -> Unit) {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            done()
        } else {
            pendingOnlineStart = done
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun makeMap(): MapView {
        return MapView(this).apply {
            setMultiTouchControls(true)
            controller.setZoom(11.5)
            controller.setCenter(GeoPoint(16.8409, 96.1735))
        }
    }

    private fun addSingleMarker(map: MapView, lat: Double, lng: Double, title: String) {
        val removable = map.overlays.filterIsInstance<Marker>().filter { it.title == title }
        map.overlays.removeAll(removable.toSet())
        Marker(map).apply {
            position = GeoPoint(lat, lng)
            this.title = title
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(this)
        }
        map.invalidate()
    }

    private fun showJobMap(pLat: Double, pLng: Double, dLat: Double?, dLng: Double?) {
        val map = makeMap()
        map.controller.setZoom(14.0)
        map.controller.setCenter(GeoPoint(pLat, pLng))
        Marker(map).apply {
            position = GeoPoint(pLat, pLng); title = "Pickup"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); map.overlays.add(this)
        }
        if (dLat != null && dLng != null) {
            Marker(map).apply {
                position = GeoPoint(dLat, dLng); title = "Destination"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); map.overlays.add(this)
            }
        }
        AlertDialog.Builder(this).setTitle("Trip Map").setView(map).setPositiveButton("Close") { _, _ -> map.onDetach() }.show()
    }

    private fun netJson(fn: () -> JSONObject, done: (JSONObject) -> Unit) {
        thread {
            val r = runCatching(fn).getOrElse { JSONObject().put("ok", false).put("error", it.message ?: "Network error") }
            runOnUiThread {
                if (!r.optBoolean("ok")) toast(r.optString("error", "Request failed")) else done(r)
            }
        }
    }

    private fun netToast(fn: () -> JSONObject) {
        netJson(fn) { r ->
            val b = r.optJSONObject("booking")
            toast(
                when {
                    b != null -> b.optString("status", "Success")
                    r.has("status") -> r.optString("status")
                    r.has("booking_code") -> r.optString("booking_code")
                    else -> "Success"
                }
            )
        }
    }

    private fun promptNumber(title: String, done: (Int) -> Unit) {
        val e = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle(title).setView(e)
            .setPositiveButton("OK") { _, _ -> e.text.toString().toIntOrNull()?.let(done) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun promptDecimal(title: String, done: (Double) -> Unit) {
        val e = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        AlertDialog.Builder(this).setTitle(title).setView(e)
            .setPositiveButton("OK") { _, _ -> e.text.toString().toDoubleOrNull()?.let(done) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun promptText(title: String, numeric: Boolean = false, done: (String) -> Unit) {
        val e = EditText(this)
        if (numeric) e.inputType = InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this).setTitle(title).setView(e)
            .setPositiveButton("OK") { _, _ -> done(e.text.toString().trim()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun actionButton(textValue: String, on: () -> Unit) = Button(this).apply {
        text = textValue; isAllCaps = false; setOnClickListener { on() }
    }

    private fun nav(name: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(Button(this@MainActivity).apply {
            text = "← Home"; isAllCaps = false; setOnClickListener { showHome() }
        }, LinearLayout.LayoutParams(0, -2, 0.35f))
        addView(TextView(this@MainActivity).apply {
            text = name; textSize = 20f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.END
        }, LinearLayout.LayoutParams(0, -2, 0.65f))
    }

    private fun title(kicker: String, main: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(12))
        addView(TextView(this@MainActivity).apply {
            text = kicker; textSize = 13f; letterSpacing = .14f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#1481B9"))
        })
        addView(TextView(this@MainActivity).apply {
            text = main; textSize = 28f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#133B61"))
        })
    }

    private fun infoCard(k: String, v: String, s: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)); background = gradient(); elevation = dp(5).toFloat()
        addView(TextView(this@MainActivity).apply { text = k; textSize = 12f; setTextColor(Color.parseColor("#B9E7FA")); setTypeface(typeface, Typeface.BOLD) })
        addView(TextView(this@MainActivity).apply { text = v; textSize = 26f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(4), 0, dp(4)) })
        addView(TextView(this@MainActivity).apply { text = s; textSize = 13f; setTextColor(Color.parseColor("#D9F3FF")) })
    }

    private fun bigButton(t: String, on: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 16f; setOnClickListener { on() }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); background = rounded(Color.WHITE, 18, "#D8E3ED", 1); elevation = dp(3).toFloat()
    }

    private fun section(t: String) = TextView(this).apply {
        text = t; textSize = 12f; letterSpacing = .1f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#1F659A")); setPadding(0, 0, 0, dp(8))
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 12f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#526A7F")); setPadding(0, dp(4), 0, dp(3))
    }

    private fun edit(h: String) = EditText(this).apply {
        hint = h; textSize = 15f; setPadding(dp(10), dp(9), dp(10), dp(9)); background = rounded(Color.parseColor("#F8FAFC"), 12, "#D5E0EA", 1)
    }

    private fun margin() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }

    private fun rounded(fill: Int, r: Int, stroke: String, sw: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(r).toFloat(); if (sw > 0) setStroke(dp(sw), Color.parseColor(stroke))
    }

    private fun gradient() = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor("#083D6B"), Color.parseColor("#0B7994"))
    ).apply { cornerRadius = dp(22).toFloat() }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
