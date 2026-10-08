package com.aaii.kilotaxi.passenger

import android.Manifest
import com.aaii.kilotaxi.common.CommunicationActivity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.concurrent.thread

class MainActivity:ComponentActivity(){
    companion object{const val PREFS="kilo_passenger_v4";const val API="https://ycta.yangoncity.net/kilotaxi/api/index.php";const val CALL="0925256954"}
    private val districts=listOf("ကျောက်တံတားခရိုင်","ကမာရွတ်ခရိုင်","တိုက်ကြီးခရိုင်","တွံတေးခရိုင်","ဒဂုံမြို့သစ်ခရိုင်","ဗိုလ်တထောင်ခရိုင်","မရမ်းကုန်းခရိုင်","မင်္ဂလာဒုံခရိုင်","လှည်းကူးခရိုင်","သန်လျင်ခရိုင်","သင်္ဃန်းကျွန်းခရိုင်","အလုံခရိုင်","အင်းစိန်ခရိုင်","မှော်ဘီခရိုင်")
    private val prefs by lazy{getSharedPreferences(PREFS,MODE_PRIVATE)}
    private lateinit var root:LinearLayout
    private lateinit var api:ApiClient
    private var map:MapView?=null
    private var locationDone:((Double,Double)->Unit)?=null
    private val perm=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){g->
        val ok=g[Manifest.permission.ACCESS_FINE_LOCATION]==true||ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(ok)useLastLocation() else toast("Location permission denied.")
    }

    override fun onCreate(b:Bundle?){super.onCreate(b);Configuration.getInstance().userAgentValue=packageName;window.statusBarColor=Color.parseColor("#7A4A0A");api=ApiClient{prefs.getString("api",API)?:API};root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(30));setBackgroundColor(Color.parseColor("#FFF8ED"))};setContentView(ScrollView(this).apply{addView(root)});home()}
    override fun onResume(){super.onResume();map?.onResume()}
    override fun onPause(){map?.onPause();super.onPause()}

    private fun home(){
        map?.onDetach();map=null;root.removeAllViews()
        root.addView(head())
        root.addView(card().apply{
            addView(TextView(this@MainActivity).apply{text="Call Center 09 252 569 54";textSize=21f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#7A4A0A"))})
            addView(Button(this@MainActivity).apply{text="Call KILO TAXI";isAllCaps=false;setOnClickListener{startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:$CALL")))}})
        },margin())
        val url=edit("Server API URL").apply{setText(prefs.getString("api",API))}
        root.addView(card().apply{
            addView(section("SERVER / LITE MODE"));addView(url)
            addView(Switch(this@MainActivity).apply{
                text="Lite Mode — load map only when needed"
                isChecked=prefs.getBoolean("lite_mode",true)
                setOnCheckedChangeListener{_,checked->prefs.edit().putBoolean("lite_mode",checked).apply();toast(if(checked)"Lite Mode ON" else "Standard Mode ON")}
            })
            addView(Button(this@MainActivity).apply{text="Save Server";isAllCaps=false;setOnClickListener{prefs.edit().putString("api",url.text.toString().trim()).apply();toast("Saved")}})
        },margin())
        favoritePlacesCard()
        bookingCard()
        trackingCard()
        fareCard()
    }

    private fun favoritePlacesCard(){
        root.addView(card().apply{
            addView(section("FAVORITE PLACES"))
            addView(TextView(this@MainActivity).apply{
                text="Home: "+prefs.getString("fav_home","Not set")+"\nWork: "+prefs.getString("fav_work","Not set")
                textSize=14f
            })
            addView(Button(this@MainActivity).apply{text="Set Home";isAllCaps=false;setOnClickListener{textPrompt("Home address"){v->prefs.edit().putString("fav_home",v).apply();home()}}})
            addView(Button(this@MainActivity).apply{text="Set Work";isAllCaps=false;setOnClickListener{textPrompt("Work address"){v->prefs.edit().putString("fav_work",v).apply();home()}}})
        },margin())
    }

    private fun bookingCard(){
        val name=edit("Passenger Name");val phone=edit("Passenger Phone").apply{setText(prefs.getString("phone",""))};val district=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,districts)};val pickup=edit("Pickup / Landmark");val lat=edit("Pickup Latitude");val lng=edit("Pickup Longitude");val dest=edit("Destination");val dlat=edit("Destination Latitude (optional)");val dlng=edit("Destination Longitude (optional)");val out=TextView(this)
        root.addView(card().apply{
            addView(section("BOOK A TAXI"));addView(name);addView(phone);addView(district);addView(pickup)
            val favRow=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL}
            favRow.addView(Button(this@MainActivity).apply{text="Use Home";isAllCaps=false;setOnClickListener{val v=prefs.getString("fav_home","").orEmpty();if(v.isBlank())toast("Set Home first")else pickup.setText(v)}},LinearLayout.LayoutParams(0,-2,1f))
            favRow.addView(Button(this@MainActivity).apply{text="Use Work";isAllCaps=false;setOnClickListener{val v=prefs.getString("fav_work","").orEmpty();if(v.isBlank())toast("Set Work first")else dest.setText(v)}},LinearLayout.LayoutParams(0,-2,1f))
            addView(favRow)
            addView(Button(this@MainActivity).apply{text="Use My Current GPS";isAllCaps=false;setOnClickListener{requestLocation{a,b->lat.setText(a.toString());lng.setText(b.toString());toast("Pickup GPS added")}}})
            addView(lat);addView(lng);addView(dest);addView(dlat);addView(dlng)
            addView(Button(this@MainActivity).apply{text="Book + Auto Find Driver";isAllCaps=false;setOnClickListener{
                val d=JSONObject().put("passenger_name",name.text.toString()).put("passenger_phone",phone.text.toString()).put("district",district.selectedItem.toString()).put("pickup",pickup.text.toString()).put("destination",dest.text.toString())
                lat.text.toString().toDoubleOrNull()?.let{d.put("pickup_lat",it)};lng.text.toString().toDoubleOrNull()?.let{d.put("pickup_lng",it)};dlat.text.toString().toDoubleOrNull()?.let{d.put("destination_lat",it)};dlng.text.toString().toDoubleOrNull()?.let{d.put("destination_lng",it)}
                out.text="Booking…";call({api.post("passenger_create_booking",d)}){j->
                    val code=j.optString("booking_code");val otp=j.optString("pickup_otp");prefs.edit().putString("code",code).putString("phone",phone.text.toString()).putString("otp",otp).putString("passenger_token",j.optString("passenger_token")).putInt("room_id",j.optInt("room_id")).apply()
                    val dr=j.optJSONObject("driver")?:JSONObject();out.text="Booking: $code\nPickup OTP: $otp\nStatus: "+j.optString("status")+"\nDriver: "+dr.optString("name")+" • "+dr.optString("member_id")+" • "+dr.optString("vehicle_no")
                }
            }})
            addView(out)
        },margin())
    }

    private fun trackingCard(){
        val code=edit("Booking Code").apply{setText(prefs.getString("code",""))};val phone=edit("Passenger Phone").apply{setText(prefs.getString("phone",""))};val out=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(card().apply{
            addView(section("TRACK MY TAXI"));addView(code);addView(phone)
            addView(Button(this@MainActivity).apply{text="Refresh Trip Status";isAllCaps=false;setOnClickListener{track(code.text.toString(),phone.text.toString(),out)}})
            addView(Button(this@MainActivity).apply{text="Cancel Booking";isAllCaps=false;setOnClickListener{call({api.post("passenger_cancel",JSONObject().put("booking_code",code.text.toString()).put("passenger_phone",phone.text.toString()).put("reason","Passenger cancelled"))}){toast("Booking cancelled");track(code.text.toString(),phone.text.toString(),out)}}})
            addView(out)
        },margin())
        if(code.text.isNotBlank()&&phone.text.isNotBlank())track(code.text.toString(),phone.text.toString(),out)
    }

    private fun track(code:String,phone:String,box:LinearLayout){call({api.get("passenger_track",mapOf("booking_code" to code,"passenger_phone" to phone))}){j->
        box.removeAllViews();val b=j.optJSONObject("booking")?:JSONObject();val d=b.optJSONObject("driver")?:JSONObject();box.addView(TextView(this).apply{text="Status: "+b.optString("status")+"\nPickup OTP: "+prefs.getString("otp","")+"\n"+b.optString("pickup")+" → "+b.optString("destination")+"\nDriver: "+d.optString("name")+" • "+d.optString("member_id")+" • "+d.optString("vehicle_no")+"\nDistance: "+b.optString("distance_km")+" km\nFare: "+b.optString("fare");textSize=16f})
        val lat=d.optDouble("lat",Double.NaN);val lng=d.optDouble("lng",Double.NaN)
        if(!lat.isNaN()&&!lng.isNaN()){
            val pLat=b.optDouble("pickup_lat",Double.NaN);val pLng=b.optDouble("pickup_lng",Double.NaN)
            if(prefs.getBoolean("lite_mode",true)){
                box.addView(Button(this).apply{text="Open Live Driver Map";isAllCaps=false;setOnClickListener{showTrackingMap(box,lat,lng,d.optString("vehicle_no"),pLat,pLng)}})
            }else showTrackingMap(box,lat,lng,d.optString("vehicle_no"),pLat,pLng)
        }
    }}

    private fun openCommunication(){
        val existing=prefs.getString("passenger_token","").orEmpty()
        if(existing.isNotBlank()){
            startActivity(Intent(this,CommunicationActivity::class.java)
                .putExtra(CommunicationActivity.EXTRA_API,prefs.getString("api",API)?:API)
                .putExtra(CommunicationActivity.EXTRA_TOKEN,existing)
                .putExtra(CommunicationActivity.EXTRA_ROLE,"passenger")
                .putExtra(CommunicationActivity.EXTRA_TITLE,"Booking Chat + KILO Assistant V6 Lite"))
            return
        }
        val code=prefs.getString("code","").orEmpty()
        val phone=prefs.getString("phone","").orEmpty()
        if(code.isBlank()||phone.isBlank()){toast("Create or track a booking first.");return}
        call({api.post("passenger_claim_booking",JSONObject().put("booking_code",code).put("passenger_phone",phone))}){j->
            val t=j.optString("passenger_token")
            prefs.edit().putString("passenger_token",t).putInt("room_id",j.optInt("room_id")).apply()
            startActivity(Intent(this,CommunicationActivity::class.java)
                .putExtra(CommunicationActivity.EXTRA_API,prefs.getString("api",API)?:API)
                .putExtra(CommunicationActivity.EXTRA_TOKEN,t)
                .putExtra(CommunicationActivity.EXTRA_ROLE,"passenger")
                .putExtra(CommunicationActivity.EXTRA_TITLE,"Booking Chat + KILO Assistant V5"))
        }
    }

    private fun showTrackingMap(box:LinearLayout,lat:Double,lng:Double,vehicle:String,pLat:Double,pLng:Double){
        map?.onDetach()
        val m=MapView(this).apply{setMultiTouchControls(true);controller.setZoom(15.0);controller.setCenter(GeoPoint(lat,lng))}
        map=m
        Marker(m).apply{position=GeoPoint(lat,lng);title="Your Driver";snippet=vehicle;setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);m.overlays.add(this)}
        if(!pLat.isNaN()&&!pLng.isNaN())Marker(m).apply{position=GeoPoint(pLat,pLng);title="Pickup";setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);m.overlays.add(this)}
        box.addView(m,LinearLayout.LayoutParams(-1,dp(300)))
    }

    private fun fareCard(){
        val km=edit("Estimated Distance KM");val wait=edit("Waiting Minutes (optional)");val out=TextView(this)
        root.addView(card().apply{addView(section("FARE ESTIMATE"));addView(km);addView(wait);addView(Button(this@MainActivity).apply{text="Estimate Fare";isAllCaps=false;setOnClickListener{val d=JSONObject().put("distance_km",km.text.toString().toDoubleOrNull()?:0.0).put("waiting_minutes",wait.text.toString().toDoubleOrNull()?:0.0);call({api.post("passenger_fare_estimate",d)}){j->out.text="Base: "+j.optString("base_fare")+"\nRate/KM: "+j.optString("rate_per_km")+"\nEstimated Fare: "+j.optString("estimated_fare")}}});addView(out)},margin())
    }

    private fun textPrompt(title:String,done:(String)->Unit){
        val e=EditText(this)
        android.app.AlertDialog.Builder(this).setTitle(title).setView(e)
            .setPositiveButton("Save"){_,_->val v=e.text.toString().trim();if(v.isNotBlank())done(v)}
            .setNegativeButton("Cancel",null).show()
    }

    private fun requestLocation(done:(Double,Double)->Unit){locationDone=done;if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)useLastLocation()else perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
    private fun useLastLocation(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;val lm=getSystemService(LOCATION_SERVICE) as LocationManager;val l=runCatching{lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?:lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)}.getOrNull();if(l!=null)locationDone?.invoke(l.latitude,l.longitude)else toast("Current GPS not available yet.");locationDone=null}
    private fun call(fn:()->JSONObject,done:(JSONObject)->Unit){thread{val r=runCatching(fn).getOrElse{JSONObject().put("ok",false).put("error",it.message?:"Network error")};runOnUiThread{if(r.optBoolean("ok"))done(r)else toast(r.optString("error","Request failed"))}}}
    private fun head()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(TextView(this@MainActivity).apply{text="KILO TAXI";letterSpacing=.15f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#B36A12"))});addView(TextView(this@MainActivity).apply{text="PASSENGER V6 LITE";textSize=29f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#674313"))});addView(TextView(this@MainActivity).apply{text="Low Data • Favorite Places • Booking Chat • Track"})}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(14));background=round(Color.WHITE);elevation=dp(3).toFloat()}
    private fun section(s:String)=TextView(this).apply{text=s;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#9B5C0B"));setPadding(0,0,0,dp(6))}
    private fun edit(h:String)=EditText(this).apply{hint=h;setPadding(dp(10),dp(9),dp(10),dp(9))}
    private fun margin()=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)}
    private fun round(c:Int)=GradientDrawable().apply{setColor(c);cornerRadius=dp(16).toFloat();setStroke(dp(1),Color.parseColor("#E7D8C5"))}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
