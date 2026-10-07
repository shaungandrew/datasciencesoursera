package com.aaii.kilotaxi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    companion object {
        private const val CALL_CENTER = "0925256954"
        private const val PREFS = "kilo_taxi_booking"
        private const val KEY_BOOKINGS = "bookings"
    }

    private val townships = listOf(
        "Downtown", "Hlaing", "Kamayut", "Sanchaung", "Bahan", "Yankin",
        "Tamwe", "Thingangyun", "Insein", "Mayangone", "North Dagon", "South Dagon"
    )

    private lateinit var townshipSpinner: Spinner
    private lateinit var passengerName: EditText
    private lateinit var passengerPhone: EditText
    private lateinit var pickup: EditText
    private lateinit var destination: EditText
    private lateinit var driverId: EditText
    private lateinit var vehicleNo: EditText
    private lateinit var notes: EditText
    private lateinit var statusText: TextView
    private lateinit var bookingList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#082F55")
        window.navigationBarColor = Color.WHITE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(28))
            setBackgroundColor(Color.parseColor("#EFF4F8"))
        }

        root.addView(header())
        root.addView(callCenterCard())
        root.addView(sectionTitle("NEW CALL CENTER BOOKING"))
        root.addView(bookingForm())
        root.addView(sectionTitle("RECENT BOOKINGS"))
        bookingList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(bookingList)

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })

        renderBookings()
    }

    private fun header(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(12))
            addView(TextView(this@MainActivity).apply {
                text = "KILO TAXI"
                textSize = 13f
                letterSpacing = 0.16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#1481B9"))
            })
            addView(TextView(this@MainActivity).apply {
                text = "Call Center Booking"
                textSize = 30f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#133B61"))
            })
            addView(TextView(this@MainActivity).apply {
                text = "Township-based taxi dispatch • Native Android"
                textSize = 14f
                setTextColor(Color.parseColor("#657A8E"))
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun callCenterCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            elevation = dp(5).toFloat()
            background = gradientCard()
        }

        card.addView(TextView(this).apply {
            text = "CALL CENTER"
            textSize = 12f
            letterSpacing = 0.12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#B9E7FA"))
        })

        card.addView(TextView(this).apply {
            text = "09 252 569 54"
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(5), 0, dp(10))
        })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        actions.addView(Button(this).apply {
            text = "Call Now"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$CALL_CENTER")))
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })

        actions.addView(Button(this).apply {
            text = "Copy Number"
            isAllCaps = false
            setOnClickListener {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("KILO TAXI Call Center", CALL_CENTER))
                Toast.makeText(this@MainActivity, "Call center number copied", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })

        card.addView(actions)
        return card
    }

    private fun bookingForm(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = dp(3).toFloat()
            background = rounded(Color.WHITE, 18, "#D8E3ED", 1)
        }

        card.addView(label("Township / Dispatch Zone"))
        townshipSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                townships
            )
        }
        card.addView(townshipSpinner, fieldParams())

        passengerName = edit("Passenger Name")
        passengerPhone = edit("Passenger Phone")
        pickup = edit("Pickup Location / Landmark")
        destination = edit("Destination")
        driverId = edit("Driver / Member ID (optional)")
        vehicleNo = edit("Taxi Vehicle No. (optional)")
        notes = edit("Operator Notes (optional)")

        listOf(
            passengerName, passengerPhone, pickup, destination,
            driverId, vehicleNo, notes
        ).forEach { card.addView(it, fieldParams()) }

        val save = Button(this).apply {
            text = "Create Booking"
            isAllCaps = false
            textSize = 17f
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0B7C68"))
            setOnClickListener { saveBooking() }
        }
        card.addView(save, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        statusText = TextView(this).apply {
            text = "Call center operator selects township first, then records trip details."
            textSize = 12f
            setTextColor(Color.parseColor("#647A8D"))
            setPadding(0, dp(8), 0, 0)
        }
        card.addView(statusText)

        return card
    }

    private fun saveBooking() {
        val phone = passengerPhone.text.toString().trim()
        val pickupText = pickup.text.toString().trim()
        val destinationText = destination.text.toString().trim()

        if (phone.isBlank() || pickupText.isBlank() || destinationText.isBlank()) {
            statusText.text = "Passenger Phone, Pickup and Destination are required."
            statusText.setTextColor(Color.parseColor("#B3261E"))
            return
        }

        val bookingId = "KT-" + UUID.randomUUID().toString().take(8).uppercase()
        val created = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date())
        val values = listOf(
            bookingId,
            created,
            townshipSpinner.selectedItem.toString(),
            passengerName.text.toString().trim(),
            phone,
            pickupText,
            destinationText,
            driverId.text.toString().trim(),
            vehicleNo.text.toString().trim(),
            notes.text.toString().trim(),
            "WAITING"
        ).map(::escape).joinToString("|")

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val current = prefs.getString(KEY_BOOKINGS, "").orEmpty()
        val updated = if (current.isBlank()) values else values + "\n" + current
        prefs.edit().putString(KEY_BOOKINGS, updated.lines().take(30).joinToString("\n")).apply()

        statusText.setTextColor(Color.parseColor("#0B7C68"))
        statusText.text = "$bookingId created • ${townshipSpinner.selectedItem} dispatch queue"

        passengerName.text.clear()
        passengerPhone.text.clear()
        pickup.text.clear()
        destination.text.clear()
        driverId.text.clear()
        vehicleNo.text.clear()
        notes.text.clear()
        renderBookings()
    }

    private fun renderBookings() {
        bookingList.removeAllViews()
        val raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_BOOKINGS, "").orEmpty()

        if (raw.isBlank()) {
            bookingList.addView(TextView(this).apply {
                text = "No bookings yet."
                textSize = 14f
                setTextColor(Color.parseColor("#718396"))
                setPadding(dp(4), dp(8), dp(4), dp(8))
            })
            return
        }

        raw.lines().filter { it.isNotBlank() }.take(20).forEach { line ->
            val p = line.split("|").map(::unescape)
            if (p.size >= 11) bookingList.addView(bookingCard(p), LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
        }
    }

    private fun bookingCard(p: List<String>): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            elevation = dp(2).toFloat()
            background = rounded(Color.WHITE, 16, "#D8E3ED", 1)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        top.addView(TextView(this).apply {
            text = p[0]
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#153E65"))
        }, LinearLayout.LayoutParams(0, -2, 1f))

        top.addView(TextView(this).apply {
            text = p[10]
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0B7C68"))
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = rounded(Color.parseColor("#E1F4EF"), 10, "#B8E1D6", 1)
        })
        card.addView(top)

        card.addView(TextView(this).apply {
            text = p[2] + " • " + p[1]
            textSize = 12f
            setTextColor(Color.parseColor("#6A7E91"))
            setPadding(0, dp(4), 0, dp(8))
        })

        addInfo(card, "Passenger", (p[3].ifBlank { "Unnamed" }) + " • " + p[4])
        addInfo(card, "Pickup", p[5])
        addInfo(card, "Destination", p[6])
        if (p[7].isNotBlank()) addInfo(card, "Driver / Member", p[7])
        if (p[8].isNotBlank()) addInfo(card, "Taxi", p[8])

        val dispatch = Button(this).apply {
            text = if (p[7].isBlank() && p[8].isBlank()) "Assign Taxi" else "Taxi Assigned"
            isAllCaps = false
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "V2 will connect this button to live township driver dispatch.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        card.addView(dispatch, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        return card
    }

    private fun addInfo(parent: LinearLayout, title: String, value: String) {
        parent.addView(TextView(this).apply {
            text = "$title: $value"
            textSize = 14f
            setTextColor(Color.parseColor("#344D62"))
            setPadding(0, dp(2), 0, dp(2))
        })
    }

    private fun sectionTitle(textValue: String): View =
        TextView(this).apply {
            text = textValue
            textSize = 12f
            letterSpacing = 0.10f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1F659A"))
            setPadding(dp(2), dp(16), 0, dp(8))
        }

    private fun label(textValue: String): View =
        TextView(this).apply {
            text = textValue
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#526A7F"))
            setPadding(dp(2), 0, 0, dp(5))
        }

    private fun edit(hintText: String): EditText =
        EditText(this).apply {
            hint = hintText
            textSize = 15f
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = rounded(Color.parseColor("#F8FAFC"), 12, "#D5E0EA", 1)
        }

    private fun fieldParams() = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dp(9)
    }

    private fun escape(v: String): String =
        v.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A")

    private fun unescape(v: String): String =
        v.replace("%0A", "\n").replace("%7C", "|").replace("%25", "%")

    private fun rounded(fill: Int, radiusDp: Int, strokeHex: String, strokeDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), Color.parseColor(strokeHex))
        }

    private fun gradientCard(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#083D6B"), Color.parseColor("#0B7994"))
        ).apply { cornerRadius = dp(22).toFloat() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
