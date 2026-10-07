package com.aaii.kilotaxi

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import kotlin.concurrent.thread

class DriverLocationService : Service(), LocationListener {
    companion object {
        const val CHANNEL_ID = "kilo_driver_location"
        const val NOTIFICATION_ID = 4201
        const val PREFS = "kilo_taxi_v3"
        const val DEFAULT_API = "https://ycta.yangoncity.net/kilotaxi/api/index.php"
    }

    private lateinit var locationManager: LocationManager
    private lateinit var api: ApiClient

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        api = ApiClient { prefs.getString("api_url", DEFAULT_API) ?: DEFAULT_API }
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean("driver_online", false) || prefs.getString("driver_token", "").isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification("Driver Online • GPS sharing active"))
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        runCatching {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10_000L, 15f, this)
        }
        runCatching {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 15_000L, 25f, this)
        }
        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean("driver_online", false)) return
        val token = prefs.getString("driver_token", "").orEmpty()
        if (token.isBlank()) return
        val payload = JSONObject()
            .put("lat", location.latitude)
            .put("lng", location.longitude)
            .put("accuracy_m", location.accuracy.toDouble())
            .put("speed_kmh", if (location.hasSpeed()) location.speed * 3.6 else 0.0)
            .put("heading", if (location.hasBearing()) location.bearing.toDouble() else 0.0)
        thread {
            runCatching { api.post("driver_location", payload, token) }
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(this) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "KILO TAXI Driver GPS", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Live driver GPS while driver is online"
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("KILO TAXI")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }
}
