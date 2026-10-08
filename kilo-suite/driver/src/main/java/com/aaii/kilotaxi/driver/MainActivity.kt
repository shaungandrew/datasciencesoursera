package com.aaii.kilotaxi.driver

import android.Manifest
import com.aaii.kilotaxi.common.CommunicationActivity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity:ComponentActivity(){
    companion object{const val PREFS="kilo_driver_v4";const val API="https://ycta.yangoncity.net/kilotaxi/api/index.php"}
    private val districts=listOf("ကျောက်တံတားခရိုင်","ကမာရွတ်ခရိုင်","တိုက်ကြီးခရိုင်","တွံတေးခရိုင်","ဒဂုံမြို့သစ်ခရိုင်","ဗိုလ်တထောင်ခရိုင်","မရမ်းကုန်းခရိုင်","မင်္ဂလာဒုံခရိုင်","လှည်းကူးခရိုင်","သန်လျင်ခရိုင်","သင်္ဃန်းကျွန်းခရိုင်","အလုံခရိုင်","အင်းစိန်ခရိုင်","မှော်ဘီခရိုင်")
    private val prefs by lazy{getSharedPreferences(PREFS,MODE_PRIVATE)}
    private lateinit var api:ApiClient
    private lateinit var root:LinearLayout
    private var pending:(()->Unit)?=null
    private val perm=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){g->
        val ok=g[Manifest.permission.ACCESS_FINE_LOCATION]==true||ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(ok)pending?.invoke() else toast("GPS permission is required.");pending=null
    }

    override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=Color.parseColor("#0A5A52");api=ApiClient{prefs.getString("api",API)?:API};root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(30));setBackgroundColor(Color.parseColor("#F0F6F4"))};setContentView(ScrollView(this).apply{addView(root)});home()}

    private fun home(){
        root.removeAllViews();root.addView(head())
        val url=edit("Server API URL").apply{setText(prefs.getString("api",API))}
        root.addView(card().apply{addView(section("SERVER"));addView(url);addView(Button(this@MainActivity).apply{text="Save Server";isAllCaps=false;setOnClickListener{prefs.edit().putString("api",url.text.toString().trim()).apply();toast("Saved")}})},margin())
        val token=prefs.getString("token","").orEmpty()
        if(token.isBlank()){activation();return}
        driverPanel(token)
    }

    private fun activation(){
        val code=edit("YCTA Activation Code")
        root.addView(card().apply{
            addView(section("MEMBER ACTIVATION"))
            addView(TextView(this@MainActivity).apply{text="Use the activation code approved by YCTA Admin."})
            addView(code)
            addView(Button(this@MainActivity).apply{text="Activate & Login";isAllCaps=false;setOnClickListener{call({api.post("activate",JSONObject().put("activation_code",code.text.toString()))}){j->prefs.edit().putString("token",j.optString("token")).apply();toast("Activation successful");home()}}})
        },margin())
    }

    private fun driverPanel(token:String){
        val profile=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val district=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,districts)}
        val online=Switch(this).apply{text="Driver Online + Live GPS";isChecked=prefs.getBoolean("online",false)}
        root.addView(card().apply{
            addView(section("DRIVER STATUS"))
            addView(district);addView(online)
            addView(Button(this@MainActivity).apply{text="Apply Online / Offline";isAllCaps=false;setOnClickListener{
                if(online.isChecked)requestGps{prefs.edit().putBoolean("online",true).putString("district",district.selectedItem.toString()).apply();val d=JSONObject().put("online",1).put("availability","AVAILABLE").put("district",district.selectedItem.toString());call({api.post("driver_status",d,token)}){ContextCompat.startForegroundService(this@MainActivity,Intent(this@MainActivity,DriverLocationService::class.java));toast("Driver Online")}}
                else{prefs.edit().putBoolean("online",false).apply();stopService(Intent(this@MainActivity,DriverLocationService::class.java));call({api.post("driver_status",JSONObject().put("online",0).put("availability","OFFLINE").put("district",district.selectedItem.toString()),token)}){toast("Driver Offline")}}
            }})
            addView(Button(this@MainActivity).apply{text="Refresh YCTA Profile";isAllCaps=false;setOnClickListener{loadProfile(profile,token)}})
            addView(profile)
        },margin())

        root.addView(Button(this).apply {
            text="DRIVER CHAT / GROUP CHANNELS"; isAllCaps=false
            setOnClickListener {
                startActivity(Intent(this@MainActivity, CommunicationActivity::class.java)
                    .putExtra(CommunicationActivity.EXTRA_API, prefs.getString("api",API)?:API)
                    .putExtra(CommunicationActivity.EXTRA_TOKEN, token)
                    .putExtra(CommunicationActivity.EXTRA_ROLE, "driver")
                    .putExtra(CommunicationActivity.EXTRA_TITLE, "Driver Chat + Township Groups V5"))
            }
        }, margin())

        val jobs=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(card().apply{
            addView(section("ASSIGNED TAXI JOBS"))
            addView(Button(this@MainActivity).apply{text="Refresh Jobs";isAllCaps=false;setOnClickListener{loadJobs(jobs,token)}})
            addView(jobs)
        },margin())

        root.addView(Button(this).apply{text="Logout";isAllCaps=false;setOnClickListener{prefs.edit().clear().putString("api",prefs.getString("api",API)).apply();stopService(Intent(this@MainActivity,DriverLocationService::class.java));home()}},margin())
        loadProfile(profile,token);loadJobs(jobs,token)
    }

    private fun loadProfile(box:LinearLayout,token:String){call({api.get("member_profile",emptyMap(),token)}){j->box.removeAllViews();val m=j.optJSONObject("member")?:JSONObject();box.addView(TextView(this).apply{text=m.optString("name")+"\nMember ID: "+m.optString("member_id")+"\nDriver License: "+m.optString("driver_license")+"\nVehicle: "+m.optString("vehicle_no")+"\nCity No: "+m.optString("city_no")+"\nDistrict: "+m.optString("district")+"\nMembership Expiry: "+m.optString("membership_expires_at").ifBlank{"No expiry set"}+"\nDays Remaining: "+m.optString("membership_days_remaining")+"\nRenewal: "+m.optString("renewal_status");textSize=15f;setTextColor(if(m.optBoolean("membership_expired"))Color.parseColor("#A52A2A") else Color.parseColor("#214A45"))});box.addView(Button(this).apply{text="Request Membership Renewal";isAllCaps=false;setOnClickListener{numberPrompt("Renewal days (30-1095)"){days->postToast("renew_request",JSONObject().put("requested_days",days).put("note","Driver App renewal"),token)}}})}}

    private fun loadJobs(box:LinearLayout,token:String){call({api.get("driver_jobs",emptyMap(),token)}){j->renderJobs(box,j.optJSONArray("bookings")?:JSONArray(),token)}}
    private fun renderJobs(box:LinearLayout,a:JSONArray,token:String){
        box.removeAllViews();if(a.length()==0){box.addView(TextView(this).apply{text="No assigned jobs."});return}
        for(i in 0 until a.length()){val b=a.getJSONObject(i);val id=b.optInt("id");val st=b.optString("status");val c=card();c.addView(TextView(this).apply{text=b.optString("booking_code")+" ["+st+"]";textSize=17f;setTypeface(typeface,Typeface.BOLD)});c.addView(TextView(this).apply{text=b.optString("pickup")+" → "+b.optString("destination")+"\nPassenger: "+b.optString("passenger_phone")+"\nAssignment expires: "+b.optString("assignment_expires_at")})
            when(st){
                "ASSIGNED"->{c.addView(btn("Accept Job"){postToast("driver_action",JSONObject().put("booking_id",id).put("status","ACCEPTED"),token)});c.addView(btn("Reject Job"){postToast("driver_action",JSONObject().put("booking_id",id).put("status","REJECTED"),token)})}
                "ACCEPTED"->c.addView(btn("Verify Pickup OTP"){textPrompt("Enter 4-digit Pickup OTP",true){otp->postToast("driver_verify_otp",JSONObject().put("booking_id",id).put("otp",otp),token)}})
                "PICKUP"->c.addView(btn("Trip Start"){decimalPrompt("Start Odometer KM"){km->postToast("driver_action",JSONObject().put("booking_id",id).put("status","STARTED").put("start_km",km),token)}})
                "STARTED"->c.addView(btn("Trip Complete"){decimalPrompt("End Odometer KM"){km->call({api.post("driver_action",JSONObject().put("booking_id",id).put("status","COMPLETED").put("end_km",km),token)}){j->val x=j.optJSONObject("booking")?:JSONObject();toast("Completed • "+x.optString("distance_km")+" km • Fare "+x.optString("fare"));loadJobs(box,token)}}})
            }
            box.addView(c,margin())
        }
    }

    private fun requestGps(done:()->Unit){val ps=mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION);if(Build.VERSION.SDK_INT>=33)ps.add(Manifest.permission.POST_NOTIFICATIONS);if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)done()else{pending=done;perm.launch(ps.toTypedArray())}}
    private fun postToast(a:String,d:JSONObject,t:String)=call({api.post(a,d,t)}){toast(it.optString("status","Success"))}
    private fun call(fn:()->JSONObject,done:(JSONObject)->Unit){thread{val r=runCatching(fn).getOrElse{JSONObject().put("ok",false).put("error",it.message?:"Network error")};runOnUiThread{if(r.optBoolean("ok"))done(r)else toast(r.optString("error","Request failed"))}}}
    private fun textPrompt(t:String,num:Boolean=false,done:(String)->Unit){val e=EditText(this);if(num)e.inputType=InputType.TYPE_CLASS_NUMBER;AlertDialog.Builder(this).setTitle(t).setView(e).setPositiveButton("OK"){_,_->done(e.text.toString().trim())}.setNegativeButton("Cancel",null).show()}
    private fun numberPrompt(t:String,done:(Int)->Unit){val e=EditText(this).apply{inputType=InputType.TYPE_CLASS_NUMBER};AlertDialog.Builder(this).setTitle(t).setView(e).setPositiveButton("OK"){_,_->e.text.toString().toIntOrNull()?.let(done)}.setNegativeButton("Cancel",null).show()}
    private fun decimalPrompt(t:String,done:(Double)->Unit){val e=EditText(this).apply{inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL};AlertDialog.Builder(this).setTitle(t).setView(e).setPositiveButton("OK"){_,_->e.text.toString().toDoubleOrNull()?.let(done)}.setNegativeButton("Cancel",null).show()}
    private fun btn(t:String,on:()->Unit)=Button(this).apply{text=t;isAllCaps=false;setOnClickListener{on()}}
    private fun head()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(TextView(this@MainActivity).apply{text="KILO TAXI";letterSpacing=.15f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#158274"))});addView(TextView(this@MainActivity).apply{text="DRIVER V5";textSize=29f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#164D46"))});addView(TextView(this@MainActivity).apply{text="Live GPS • YCTA Membership • OTP • Trip KM";setTextColor(Color.parseColor("#5D7772"))})}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(14));background=round(Color.WHITE);elevation=dp(3).toFloat()}
    private fun section(s:String)=TextView(this).apply{text=s;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#18766B"));setPadding(0,0,0,dp(6))}
    private fun edit(h:String)=EditText(this).apply{hint=h;setPadding(dp(10),dp(9),dp(10),dp(9))}
    private fun margin()=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)}
    private fun round(c:Int)=GradientDrawable().apply{setColor(c);cornerRadius=dp(16).toFloat();setStroke(dp(1),Color.parseColor("#D5E5E1"))}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
