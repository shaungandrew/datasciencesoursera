package com.aaii.kilotaxi.driver

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import kotlin.concurrent.thread

class DriverLocationService:Service(),LocationListener{
    companion object{const val PREFS="kilo_driver_v4";const val API="https://ycta.yangoncity.net/kilotaxi/api/index.php";const val CHANNEL="kilo_driver_gps"}
    private lateinit var lm:LocationManager
    private lateinit var api:ApiClient
    private var lastSentAt=0L
    private var lastLat=Double.NaN
    private var lastLng=Double.NaN
    override fun onCreate(){super.onCreate();lm=getSystemService(LOCATION_SERVICE) as LocationManager;val p=getSharedPreferences(PREFS,MODE_PRIVATE);api=ApiClient{p.getString("api",API)?:API};if(android.os.Build.VERSION.SDK_INT>=26)(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL,"KILO TAXI Driver GPS",NotificationManager.IMPORTANCE_LOW))}
    override fun onStartCommand(i:Intent?,f:Int,id:Int):Int{
        val p=getSharedPreferences(PREFS,MODE_PRIVATE);if(!p.getBoolean("online",false)||p.getString("token","").isNullOrBlank()){stopSelf();return START_NOT_STICKY}
        val pi=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val lite=p.getBoolean("lite_mode",true)
        startForeground(4204,NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("KILO TAXI Driver Online").setContentText(if(lite)"Lite GPS • battery/data saver" else "Live GPS sharing active").setOngoing(true).setContentIntent(pi).build())
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY}
        val gpsTime=if(lite)25000L else 10000L
        val gpsDistance=if(lite)30f else 15f
        val networkTime=if(lite)45000L else 15000L
        val networkDistance=if(lite)60f else 25f
        runCatching{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,gpsTime,gpsDistance,this)}
        runCatching{lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,networkTime,networkDistance,this)}
        return START_STICKY
    }
    override fun onLocationChanged(l:Location){
        val p=getSharedPreferences(PREFS,MODE_PRIVATE)
        if(!p.getBoolean("online",false))return
        val t=p.getString("token","").orEmpty()
        if(t.isBlank())return
        val lite=p.getBoolean("lite_mode",true)
        val now=SystemClock.elapsedRealtime()
        if(lite && !lastLat.isNaN() && now-lastSentAt<30000L){
            val result=FloatArray(1)
            Location.distanceBetween(lastLat,lastLng,l.latitude,l.longitude,result)
            if(result[0]<25f)return
        }
        lastSentAt=now;lastLat=l.latitude;lastLng=l.longitude
        val d=JSONObject().put("lat",l.latitude).put("lng",l.longitude).put("accuracy_m",l.accuracy.toDouble()).put("speed_kmh",if(l.hasSpeed())l.speed*3.6 else 0.0).put("heading",if(l.hasBearing())l.bearing.toDouble() else 0.0).put("lite_mode",if(lite)1 else 0)
        thread{runCatching{api.post("driver_location",d,t)}}
    }
    override fun onProviderEnabled(p:String){}
    override fun onProviderDisabled(p:String){}
    override fun onDestroy(){runCatching{lm.removeUpdates(this)};super.onDestroy()}
    override fun onBind(i:Intent?):IBinder?=null
}
