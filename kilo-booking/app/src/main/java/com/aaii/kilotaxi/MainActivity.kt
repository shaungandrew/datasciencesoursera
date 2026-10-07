package com.aaii.kilotaxi

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    companion object {
        private const val PREFS = "kilo_taxi_v2"
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#083D6B")
        api = ApiClient { prefs.getString("api_url", DEFAULT_API) ?: DEFAULT_API }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(28))
            setBackgroundColor(Color.parseColor("#EEF4F8"))
        }
        setContentView(ScrollView(this).apply { isFillViewport=true; addView(root) })
        showHome()
    }

    private fun showHome() {
        root.removeAllViews()
        root.addView(title("KILO TAXI", "Booking + YCTA Membership V2"))
        root.addView(infoCard("CALL CENTER", "09 252 569 54", "Township dispatch • Booking • Driver tracking"))

        val grid=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        grid.addView(bigButton("CALL CENTER DASHBOARD") { showCallCenter() })
        grid.addView(bigButton("DRIVER APP") { showDriver() })
        grid.addView(bigButton("MEMBER REGISTER / ACTIVATION") { showMembership() })
        grid.addView(bigButton("SETTINGS") { showSettings() })
        root.addView(grid)
    }

    private fun showCallCenter() {
        root.removeAllViews()
        root.addView(nav("Call Center Dashboard"))
        val token=EditText(this).apply {
            hint="Operator API Token"
            setText(prefs.getString("operator_token",""))
            inputType=InputType.TYPE_CLASS_TEXT
        }
        root.addView(card().apply {
            addView(label("Operator Token"))
            addView(token)
            addView(Button(this@MainActivity).apply {
                text="Save Token"; isAllCaps=false
                setOnClickListener { prefs.edit().putString("operator_token",token.text.toString().trim()).apply(); toast("Saved") }
            })
        })

        val district=Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,districts)
        }
        val passengerName=edit("Passenger Name")
        val passengerPhone=edit("Passenger Phone")
        val pickup=edit("Pickup / Landmark")
        val destination=edit("Destination")
        val notes=edit("Notes")
        val status=TextView(this).apply { setPadding(0,dp(8),0,0) }

        val form=card().apply {
            addView(section("NEW BOOKING"))
            addView(label("Township / District"))
            addView(district)
            addView(passengerName); addView(passengerPhone); addView(pickup); addView(destination); addView(notes)
            addView(Button(this@MainActivity).apply {
                text="Create Booking"; isAllCaps=false
                setOnClickListener {
                    val t=prefs.getString("operator_token","").orEmpty()
                    val d=JSONObject()
                        .put("passenger_name",passengerName.text.toString())
                        .put("passenger_phone",passengerPhone.text.toString())
                        .put("district",district.selectedItem.toString())
                        .put("pickup",pickup.text.toString())
                        .put("destination",destination.text.toString())
                        .put("notes",notes.text.toString())
                    net(status) { api.post("operator_create_booking",d,t) }
                }
            })
            addView(status)
        }
        root.addView(form)

        val resultBox=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(card().apply {
            addView(section("DISPATCH"))
            addView(Button(this@MainActivity).apply {
                text="Load Online Drivers"; isAllCaps=false
                setOnClickListener {
                    val t=prefs.getString("operator_token","").orEmpty()
                    netJson({ api.get("operator_online_drivers",mapOf("district" to district.selectedItem.toString()),t) }) { j ->
                        renderDrivers(resultBox,j.optJSONArray("drivers")?:JSONArray())
                    }
                }
            })
            addView(Button(this@MainActivity).apply {
                text="Load Bookings"; isAllCaps=false
                setOnClickListener {
                    val t=prefs.getString("operator_token","").orEmpty()
                    netJson({ api.get("operator_bookings",mapOf("district" to district.selectedItem.toString()),t) }) { j ->
                        renderBookings(resultBox,j.optJSONArray("bookings")?:JSONArray())
                    }
                }
            })
            addView(resultBox)
        })
    }

    private fun renderDrivers(box: LinearLayout, a: JSONArray) {
        box.removeAllViews()
        if(a.length()==0){ box.addView(TextView(this).apply{text="No online available drivers.";setPadding(0,dp(8),0,0)}); return }
        for(i in 0 until a.length()){
            val d=a.getJSONObject(i)
            val c=card()
            c.addView(TextView(this).apply {
                text=(d.optString("name").ifBlank{"YCTA Driver"})+" • "+d.optString("member_id")
                textSize=17f; setTypeface(typeface,Typeface.BOLD)
            })
            c.addView(TextView(this).apply { text=d.optString("vehicle_no")+" • "+d.optString("district") })
            c.addView(Button(this).apply {
                text="Assign to Booking ID…";isAllCaps=false
                setOnClickListener {
                    promptNumber("Booking database ID") { bookingId ->
                        val t=prefs.getString("operator_token","").orEmpty()
                        val payload=JSONObject().put("booking_id",bookingId).put("member_id",d.optInt("member_id"))
                        netToast { api.post("operator_assign",payload,t) }
                    }
                }
            })
            box.addView(c,margin())
        }
    }

    private fun renderBookings(box: LinearLayout, a: JSONArray) {
        box.removeAllViews()
        if(a.length()==0){ box.addView(TextView(this).apply{text="No bookings.";setPadding(0,dp(8),0,0)}); return }
        for(i in 0 until a.length()){
            val b=a.getJSONObject(i)
            val c=card()
            c.addView(TextView(this).apply {
                text="#"+b.optInt("id")+"  "+b.optString("booking_code")+"  ["+b.optString("status")+"]"
                textSize=16f;setTypeface(typeface,Typeface.BOLD)
            })
            c.addView(TextView(this).apply { text=b.optString("passenger_name")+" • "+b.optString("passenger_phone") })
            c.addView(TextView(this).apply { text=b.optString("pickup")+" → "+b.optString("destination") })
            c.addView(TextView(this).apply { text="Driver: "+b.optString("driver_name")+" "+b.optString("driver_member_id") })
            if(b.optString("status")=="COMPLETED"){
                c.addView(TextView(this).apply { text="Distance: "+b.optString("distance_km")+" km • Fare: "+b.optString("fare") })
            }
            box.addView(c,margin())
        }
    }

    private fun showDriver() {
        root.removeAllViews()
        root.addView(nav("Driver App"))
        val token=prefs.getString("driver_token","").orEmpty()
        if(token.isBlank()){
            val code=edit("Activation Code (YCTA-XXXX-XXXX-XXXX)")
            val out=TextView(this)
            root.addView(card().apply {
                addView(section("YCTA MEMBER ACTIVATION"))
                addView(code)
                addView(Button(this@MainActivity).apply {
                    text="Activate & Login";isAllCaps=false
                    setOnClickListener {
                        netJson({ api.post("activate",JSONObject().put("activation_code",code.text.toString())) }) { j ->
                            if(j.optBoolean("ok")){
                                val tk=j.optString("token")
                                prefs.edit().putString("driver_token",tk).apply()
                                toast("Activation successful")
                                showDriver()
                            } else out.text=j.toString()
                        }
                    }
                })
                addView(out)
            })
            return
        }

        val profileBox=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val online=Switch(this).apply { text="Driver Online"; isChecked=false }
        val district=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,districts) }
        root.addView(card().apply {
            addView(section("DRIVER STATUS"))
            addView(district)
            addView(online)
            addView(Button(this@MainActivity).apply {
                text="Update Online Status";isAllCaps=false
                setOnClickListener {
                    val d=JSONObject().put("online",if(online.isChecked)1 else 0)
                        .put("availability",if(online.isChecked)"AVAILABLE" else "OFFLINE")
                        .put("district",district.selectedItem.toString())
                    netToast { api.post("driver_status",d,token) }
                }
            })
            addView(Button(this@MainActivity).apply {
                text="Load My YCTA Profile";isAllCaps=false
                setOnClickListener {
                    netJson({ api.get("member_profile",emptyMap(),token) }) { j ->
                        profileBox.removeAllViews()
                        val m=j.optJSONObject("member")?:JSONObject()
                        profileBox.addView(TextView(this@MainActivity).apply {
                            text=m.optString("name")+"\nMember ID: "+m.optString("member_id")+"\nLicense: "+m.optString("driver_license")+"\nVehicle: "+m.optString("vehicle_no")+"\nCity No: "+m.optString("city_no")+"\nTownship: "+m.optString("district")
                            textSize=15f
                        })
                    }
                }
            })
            addView(profileBox)
        })

        val jobsBox=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(card().apply {
            addView(section("MY ASSIGNED JOBS"))
            addView(Button(this@MainActivity).apply {
                text="Refresh Jobs";isAllCaps=false
                setOnClickListener {
                    netJson({ api.get("driver_jobs",emptyMap(),token) }) { j ->
                        renderDriverJobs(jobsBox,j.optJSONArray("bookings")?:JSONArray(),token)
                    }
                }
            })
            addView(jobsBox)
        })

        root.addView(Button(this).apply {
            text="Logout Driver Token";isAllCaps=false
            setOnClickListener { prefs.edit().remove("driver_token").apply(); showDriver() }
        })
    }

    private fun renderDriverJobs(box: LinearLayout, a: JSONArray, token:String) {
        box.removeAllViews()
        if(a.length()==0){ box.addView(TextView(this).apply{text="No assigned jobs.";setPadding(0,dp(8),0,0)}); return }
        for(i in 0 until a.length()){
            val b=a.getJSONObject(i)
            val id=b.optInt("id")
            val status=b.optString("status")
            val c=card()
            c.addView(TextView(this).apply { text=b.optString("booking_code")+" ["+status+"]";textSize=17f;setTypeface(typeface,Typeface.BOLD) })
            c.addView(TextView(this).apply { text=b.optString("pickup")+" → "+b.optString("destination")+"\nPassenger: "+b.optString("passenger_phone") })
            val row=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            fun actionButton(label:String,to:String,needsKm:Boolean=false,endKm:Boolean=false){
                row.addView(Button(this).apply {
                    text=label;isAllCaps=false
                    setOnClickListener {
                        if(needsKm){
                            promptDecimal(if (endKm) "End odometer KM" else "Start odometer KM") { km ->
                                val d=JSONObject().put("booking_id",id).put("status",to).put(if(endKm)"end_km" else "start_km",km)
                                netToast { api.post("driver_action",d,token) }
                            }
                        } else {
                            val d=JSONObject().put("booking_id",id).put("status",to)
                            netToast { api.post("driver_action",d,token) }
                        }
                    }
                })
            }
            when(status){
                "ASSIGNED"->{ actionButton("Accept","ACCEPTED"); actionButton("Reject","REJECTED") }
                "ACCEPTED"->actionButton("Passenger Pickup","PICKUP")
                "PICKUP"->actionButton("Trip Start","STARTED",true,false)
                "STARTED"->actionButton("Trip Complete","COMPLETED",true,true)
            }
            c.addView(row)
            box.addView(c,margin())
        }
    }

    private fun showMembership() {
        root.removeAllViews()
        root.addView(nav("Membership Register + Activation"))
        val name=edit("Name")
        val phone=edit("Phone")
        val memberId=edit("Existing Member ID (optional)")
        val license=edit("Driver License")
        val vehicle=edit("Taxi Vehicle No.")
        val city=edit("City No.")
        val district=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,districts) }
        val nrc=edit("NRC")
        val address=edit("Address")
        val out=TextView(this)

        root.addView(card().apply {
            addView(section("REGISTER"))
            addView(name);addView(phone);addView(memberId);addView(license);addView(vehicle);addView(city);addView(district);addView(nrc);addView(address)
            addView(Button(this@MainActivity).apply {
                text="Submit Membership Registration";isAllCaps=false
                setOnClickListener {
                    val d=JSONObject().put("name",name.text.toString()).put("phone",phone.text.toString())
                        .put("member_id",memberId.text.toString()).put("driver_license",license.text.toString())
                        .put("vehicle_no",vehicle.text.toString()).put("city_no",city.text.toString())
                        .put("district",district.selectedItem.toString()).put("nrc",nrc.text.toString()).put("address",address.text.toString())
                    net(out){ api.post("register",d) }
                }
            })
            addView(out)
        })

        val code=edit("Activation Code")
        root.addView(card().apply {
            addView(section("ACTIVATION"))
            addView(code)
            addView(Button(this@MainActivity).apply {
                text="Activate Driver Membership";isAllCaps=false
                setOnClickListener {
                    netJson({ api.post("activate",JSONObject().put("activation_code",code.text.toString())) }) { j ->
                        if(j.optBoolean("ok")){
                            prefs.edit().putString("driver_token",j.optString("token")).apply()
                            toast("Activated. Driver login saved.")
                        } else toast(j.optString("error","Activation failed"))
                    }
                }
            })
        })
    }

    private fun showSettings() {
        root.removeAllViews()
        root.addView(nav("Settings"))
        val apiUrl=edit("API URL").apply { setText(prefs.getString("api_url",DEFAULT_API)) }
        val op=edit("Operator API Token").apply { setText(prefs.getString("operator_token","")) }
        root.addView(card().apply {
            addView(label("Server API URL"));addView(apiUrl)
            addView(label("Call Center Operator Token"));addView(op)
            addView(TextView(this@MainActivity).apply { text="Default Call Center: 09 252 569 54";setPadding(0,dp(8),0,dp(8)) })
            addView(Button(this@MainActivity).apply {
                text="Save Settings";isAllCaps=false
                setOnClickListener {
                    prefs.edit().putString("api_url",apiUrl.text.toString().trim()).putString("operator_token",op.text.toString().trim()).apply()
                    toast("Settings saved")
                }
            })
        })
    }

    private fun net(out:TextView, fn:()->JSONObject){
        out.text="Loading…"
        thread {
            val r=runCatching(fn).getOrElse { JSONObject().put("ok",false).put("error",it.message?:"Network error") }
            runOnUiThread { out.text=r.toString(2) }
        }
    }

    private fun netJson(fn:()->JSONObject, done:(JSONObject)->Unit){
        thread {
            val r=runCatching(fn).getOrElse { JSONObject().put("ok",false).put("error",it.message?:"Network error") }
            runOnUiThread {
                if(!r.optBoolean("ok")) toast(r.optString("error","Request failed")) else done(r)
            }
        }
    }

    private fun netToast(fn:()->JSONObject){
        netJson(fn){ r-> toast(if(r.has("booking_code")) r.optString("booking_code") else "Success") }
    }

    private fun promptNumber(title:String, done:(Int)->Unit){
        val e=EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle(title).setView(e).setPositiveButton("OK"){_,_-> e.text.toString().toIntOrNull()?.let(done)}.setNegativeButton("Cancel",null).show()
    }

    private fun promptDecimal(title:String, done:(Double)->Unit){
        val e=EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        AlertDialog.Builder(this).setTitle(title).setView(e).setPositiveButton("OK"){_,_-> e.text.toString().toDoubleOrNull()?.let(done)}.setNegativeButton("Cancel",null).show()
    }

    private fun nav(name:String)=LinearLayout(this).apply {
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        addView(Button(this@MainActivity).apply { text="← Home";isAllCaps=false;setOnClickListener{showHome()} },LinearLayout.LayoutParams(0,-2,0.35f))
        addView(TextView(this@MainActivity).apply { text=name;textSize=20f;setTypeface(typeface,Typeface.BOLD);gravity=Gravity.END },LinearLayout.LayoutParams(0,-2,0.65f))
    }

    private fun title(kicker:String,main:String)=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;setPadding(0,dp(4),0,dp(12))
        addView(TextView(this@MainActivity).apply{text=kicker;textSize=13f;letterSpacing=.14f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#1481B9"))})
        addView(TextView(this@MainActivity).apply{text=main;textSize=28f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#133B61"))})
    }

    private fun infoCard(k:String,v:String,s:String)=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(16));background=gradient();elevation=dp(5).toFloat()
        addView(TextView(this@MainActivity).apply{text=k;textSize=12f;setTextColor(Color.parseColor("#B9E7FA"));setTypeface(typeface,Typeface.BOLD)})
        addView(TextView(this@MainActivity).apply{text=v;textSize=26f;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD);setPadding(0,dp(4),0,dp(4))})
        addView(TextView(this@MainActivity).apply{text=s;textSize=13f;setTextColor(Color.parseColor("#D9F3FF"))})
    }

    private fun bigButton(t:String)=Button(this).apply { text=t;isAllCaps=false;textSize=16f;setOnClickListener{} }
    private fun bigButton(t:String,on:()->Unit)=Button(this).apply { text=t;isAllCaps=false;textSize=16f;setOnClickListener{on()} }

    private fun card()=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(14));background=rounded(Color.WHITE,18,"#D8E3ED",1);elevation=dp(3).toFloat()
    }

    private fun section(t:String)=TextView(this).apply { text=t;textSize=12f;letterSpacing=.1f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#1F659A"));setPadding(0,0,0,dp(8)) }
    private fun label(t:String)=TextView(this).apply { text=t;textSize=12f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#526A7F"));setPadding(0,dp(4),0,dp(3)) }
    private fun edit(h:String)=EditText(this).apply { hint=h;textSize=15f;setPadding(dp(10),dp(9),dp(10),dp(9));background=rounded(Color.parseColor("#F8FAFC"),12,"#D5E0EA",1) }
    private fun margin()=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)}

    private fun rounded(fill:Int,r:Int,stroke:String,sw:Int)=GradientDrawable().apply {
        shape=GradientDrawable.RECTANGLE;setColor(fill);cornerRadius=dp(r).toFloat();if(sw>0)setStroke(dp(sw),Color.parseColor(stroke))
    }
    private fun gradient()=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.parseColor("#083D6B"),Color.parseColor("#0B7994"))).apply{cornerRadius=dp(22).toFloat()}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
