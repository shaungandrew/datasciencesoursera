package com.aaii.kilotaxi.callcenter

import android.content.Intent
import android.graphics.Color
import com.aaii.kilotaxi.common.CommunicationActivity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.concurrent.thread

class MainActivity:ComponentActivity(){
    companion object{const val PREFS="kilo_callcenter_v4";const val API="https://ycta.yangoncity.net/kilotaxi/api/index.php"}
    private val districts=listOf("ကျောက်တံတားခရိုင်","ကမာရွတ်ခရိုင်","တိုက်ကြီးခရိုင်","တွံတေးခရိုင်","ဒဂုံမြို့သစ်ခရိုင်","ဗိုလ်တထောင်ခရိုင်","မရမ်းကုန်းခရိုင်","မင်္ဂလာဒုံခရိုင်","လှည်းကူးခရိုင်","သန်လျင်ခရိုင်","သင်္ဃန်းကျွန်းခရိုင်","အလုံခရိုင်","အင်းစိန်ခရိုင်","မှော်ဘီခရိုင်")
    private val prefs by lazy{getSharedPreferences(PREFS,MODE_PRIVATE)}
    private lateinit var root:LinearLayout
    private lateinit var api:ApiClient
    private var map:MapView?=null

    override fun onCreate(b:Bundle?){super.onCreate(b);Configuration.getInstance().userAgentValue=packageName;window.statusBarColor=Color.parseColor("#073D6B");api=ApiClient{prefs.getString("api",API)?:API};root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(30));setBackgroundColor(Color.parseColor("#EEF4F8"))};setContentView(ScrollView(this).apply{addView(root)});home()}
    override fun onResume(){super.onResume();map?.onResume()}
    override fun onPause(){map?.onPause();super.onPause()}

    private fun home(){
        map?.onDetach();map=null;root.removeAllViews()
        root.addView(head("KILO TAXI","CALL CENTER V5"))
        root.addView(card().apply{
            addView(TextView(this@MainActivity).apply{text="09 252 569 54";textSize=27f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#0B6C81"))})
            addView(TextView(this@MainActivity).apply{text="Township booking • Live drivers • Auto dispatch • OTP • Fare tracking"})
        })
        val token=edit("Operator API Token").apply{setText(prefs.getString("token",""))}
        val url=edit("Server API URL").apply{setText(prefs.getString("api",API))}
        root.addView(card().apply{
            addView(section("CONNECTION"))
            addView(token);addView(url)
            addView(Button(this@MainActivity).apply{text="Save Settings";isAllCaps=false;setOnClickListener{prefs.edit().putString("token",token.text.toString().trim()).putString("api",url.text.toString().trim()).apply();toast("Saved")}})
        },margin())

        val district=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,districts)}
        val mv=MapView(this).apply{setMultiTouchControls(true);controller.setZoom(11.5);controller.setCenter(GeoPoint(16.8409,96.1735))}
        map=mv
        root.addView(card().apply{
            addView(section("LIVE DRIVER GPS MAP"));addView(district);addView(mv,LinearLayout.LayoutParams(-1,dp(330)))
            addView(Button(this@MainActivity).apply{text="Refresh Drivers";isAllCaps=false;setOnClickListener{loadMap(mv,district.selectedItem.toString())}})
        },margin())

        val name=edit("Passenger Name");val phone=edit("Passenger Phone");val pickup=edit("Pickup / Landmark");val plat=edit("Pickup Latitude");val plng=edit("Pickup Longitude");val dest=edit("Destination");val dlat=edit("Destination Latitude (optional)");val dlng=edit("Destination Longitude (optional)");val notes=edit("Notes");val out=TextView(this)
        root.addView(card().apply{
            addView(section("NEW BOOKING + AUTO DISPATCH"));addView(name);addView(phone);addView(pickup)
            addView(Button(this@MainActivity).apply{text="Use Map Center as Pickup";isAllCaps=false;setOnClickListener{val c=mv.mapCenter;plat.setText(c.latitude.toString());plng.setText(c.longitude.toString());marker(mv,c.latitude,c.longitude,"Pickup")}})
            addView(plat);addView(plng);addView(dest);addView(dlat);addView(dlng);addView(notes)
            addView(Button(this@MainActivity).apply{text="Create Booking";isAllCaps=false;setOnClickListener{
                val d=JSONObject().put("passenger_name",name.text.toString()).put("passenger_phone",phone.text.toString()).put("district",district.selectedItem.toString()).put("pickup",pickup.text.toString()).put("destination",dest.text.toString()).put("notes",notes.text.toString()).put("auto_dispatch",1)
                plat.text.toString().toDoubleOrNull()?.let{d.put("pickup_lat",it)};plng.text.toString().toDoubleOrNull()?.let{d.put("pickup_lng",it)};dlat.text.toString().toDoubleOrNull()?.let{d.put("destination_lat",it)};dlng.text.toString().toDoubleOrNull()?.let{d.put("destination_lng",it)}
                call({api.post("operator_create_booking",d,tok())}){j->out.text="Booking: "+j.optString("booking_code")+"\nPickup OTP: "+j.optString("pickup_otp")+"\nStatus: "+j.optString("status");loadMap(mv,district.selectedItem.toString())}
            }})
            addView(out)
        },margin())

        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(card().apply{
            addView(section("BOOKING STATUS"))
            addView(Button(this@MainActivity).apply{text="Load Bookings";isAllCaps=false;setOnClickListener{call({api.get("operator_bookings",mapOf("district" to district.selectedItem.toString()),tok())}){renderBookings(box,it.optJSONArray("bookings")?:JSONArray())}}})
            addView(box)
        },margin())
    }

    private fun renderBookings(box: LinearLayout, a: JSONArray) {
        box.removeAllViews()
        if (a.length() == 0) {
            box.addView(TextView(this).apply { text = "No bookings" })
            return
        }

        for (i in 0 until a.length()) {
            val b = a.getJSONObject(i)
            val id = b.optInt("id")
            val c = card()

            c.addView(TextView(this).apply {
                text = b.optString("booking_code") + " [" + b.optString("status") + "]"
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
            })

            c.addView(TextView(this).apply {
                text = b.optString("passenger_name") + " • " + b.optString("passenger_phone") +
                    "\n" + b.optString("pickup") + " → " + b.optString("destination") +
                    "\nDriver: " + b.optString("driver_name") + " " + b.optString("vehicle_no") +
                    "\nKM: " + b.optString("distance_km") + " • Fare: " + b.optString("fare")
            })

            if (b.optString("status") !in listOf("COMPLETED", "CANCELLED")) {
                c.addView(Button(this).apply {
                    text = "Auto Reassign"
                    isAllCaps = false
                    setOnClickListener {
                        postToast("operator_reassign", JSONObject().put("booking_id", id))
                    }
                })

                c.addView(Button(this).apply {
                    text = "Reset Pickup OTP"
                    isAllCaps = false
                    setOnClickListener {
                        call({
                            api.post(
                                "operator_reset_otp",
                                JSONObject().put("booking_id", id),
                                tok()
                            )
                        }) { result ->
                            toast("New OTP: " + result.optString("pickup_otp"))
                        }
                    }
                })

                c.addView(Button(this).apply {
                    text = "Cancel"
                    isAllCaps = false
                    setOnClickListener {
                        postToast(
                            "operator_cancel",
                            JSONObject()
                                .put("booking_id", id)
                                .put("reason", "Call Center cancelled")
                        )
                    }
                })
            }

            box.addView(c, margin())
        }
    }

    private fun loadMap(m:MapView,district:String){call({api.get("operator_map_drivers",mapOf("district" to district),tok())}){j->m.overlays.clear();val a=j.optJSONArray("drivers")?:JSONArray();var first:GeoPoint?=null;for(i in 0 until a.length()){val d=a.getJSONObject(i);val lat=d.optDouble("lat",Double.NaN);val lng=d.optDouble("lng",Double.NaN);if(lat.isNaN()||lng.isNaN())continue;val p=GeoPoint(lat,lng);if(first==null)first=p;Marker(m).apply{position=p;title=(d.optString("name").ifBlank{"YCTA Driver"})+" • "+d.optString("ycta_member_id")+" • "+d.optString("vehicle_no");snippet=d.optString("availability")+" • "+d.optString("last_seen");setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);m.overlays.add(this)}};first?.let{m.controller.animateTo(it)};m.invalidate();toast(a.length().toString()+" driver(s)")}}
    private fun marker(m:MapView,lat:Double,lng:Double,t:String){Marker(m).apply{position=GeoPoint(lat,lng);title=t;setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);m.overlays.add(this)};m.invalidate()}
    private fun postToast(a:String,d:JSONObject)=call({api.post(a,d,tok())}){toast(if(it.has("pickup_otp"))"OTP "+it.optString("pickup_otp") else "Success")}
    private fun tok()=prefs.getString("token","").orEmpty()
    private fun call(fn:()->JSONObject,done:(JSONObject)->Unit){thread{val r=runCatching(fn).getOrElse{JSONObject().put("ok",false).put("error",it.message?:"Network error")};runOnUiThread{if(r.optBoolean("ok"))done(r)else toast(r.optString("error","Request failed"))}}}
    private fun head(a:String,b:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(TextView(this@MainActivity).apply{text=a;textSize=13f;letterSpacing=.15f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#1382B6"))});addView(TextView(this@MainActivity).apply{text=b;textSize=29f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#143B60"))})}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(14));background=round(Color.WHITE);elevation=dp(3).toFloat()}
    private fun section(s:String)=TextView(this).apply{text=s;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#176792"));setPadding(0,0,0,dp(6))}
    private fun edit(h:String)=EditText(this).apply{hint=h;setPadding(dp(10),dp(9),dp(10),dp(9))}
    private fun margin()=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)}
    private fun round(c:Int)=GradientDrawable().apply{setColor(c);cornerRadius=dp(16).toFloat();setStroke(dp(1),Color.parseColor("#D6E2EB"))}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
