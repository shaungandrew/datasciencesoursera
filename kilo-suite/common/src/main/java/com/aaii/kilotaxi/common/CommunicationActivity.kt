package com.aaii.kilotaxi.common

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.Base64
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class CommunicationActivity : ComponentActivity() {
    companion object {
        const val EXTRA_API = "api_url"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_ROLE = "role"
        const val EXTRA_TITLE = "title"
    }

    private lateinit var root: LinearLayout
    private lateinit var api: CommApi
    private var apiUrl = ""
    private var token = ""
    private var role = ""
    private var selectedRoomId = 0
    private var recorder: MediaRecorder? = null
    private var recordFile: File? = null
    private var recordButton: Button? = null
    private var pendingVoiceStart = false
    private var pendingLocationRoom = 0
    private val handler = Handler(Looper.getMainLooper())
    private var incomingDialogShownFor = 0
    private val litePrefs by lazy { getSharedPreferences("kilo_comm_v6", MODE_PRIVATE) }
    private var liteMode = true
    private var networkBadge: TextView? = null
    private var roomsBox: LinearLayout? = null
    private val chatHandler = Handler(Looper.getMainLooper())
    private var activeRoomId = 0
    private var activeMessageList: LinearLayout? = null
    private var lastMessageId = 0

    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && selectedRoomId > 0) uploadUri(uri, selectedRoomId, "image")
    }

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingVoiceStart) startVoiceRecording()
        pendingVoiceStart = false
    }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingLocationRoom > 0) sendCurrentLocation(pendingLocationRoom)
        pendingLocationRoom = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiUrl = intent.getStringExtra(EXTRA_API).orEmpty()
        token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        role = intent.getStringExtra(EXTRA_ROLE).orEmpty()
        liteMode = litePrefs.getBoolean("lite_mode", true)
        api = CommApi(apiUrl, token)
        window.statusBarColor = Color.parseColor("#263A57")
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(28))
            setBackgroundColor(Color.parseColor("#F1F4F8"))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        showRooms()
    }

    override fun onResume() {
        super.onResume()
        handler.post(incomingPoll)
        if (activeRoomId > 0 && activeMessageList != null) chatHandler.post(chatPoll)
    }

    override fun onPause() {
        handler.removeCallbacks(incomingPoll)
        chatHandler.removeCallbacks(chatPoll)
        super.onPause()
    }

    override fun onDestroy() {
        stopRecorderQuietly(false)
        super.onDestroy()
    }

    private val incomingPoll = object : Runnable {
        override fun run() {
            if (token.isNotBlank()) {
                net({ api.get("call_incoming") }) { j ->
                    val calls = j.optJSONArray("calls") ?: JSONArray()
                    if (calls.length() > 0) {
                        val c = calls.getJSONObject(0)
                        val id = c.optInt("id")
                        if (id != incomingDialogShownFor) {
                            incomingDialogShownFor = id
                            AlertDialog.Builder(this@CommunicationActivity)
                                .setTitle("Incoming " + c.optString("call_type").uppercase() + " Call")
                                .setMessage("KILO TAXI communication call")
                                .setPositiveButton("Accept") { _, _ ->
                                    net({ api.post("call_action", JSONObject().put("call_id", id).put("action", "accept")) }) {
                                        openCall(id, c.optString("call_type","audio"), false)
                                    }
                                }
                                .setNegativeButton("Reject") { _, _ ->
                                    netToast { api.post("call_action", JSONObject().put("call_id", id).put("action", "reject")) }
                                }.show()
                        }
                    }
                }
            }
            handler.postDelayed(this, if (liteMode) 10000 else 4000)
        }
    }

    private fun showRooms() {
        activeRoomId = 0
        activeMessageList = null
        lastMessageId = 0
        chatHandler.removeCallbacks(chatPoll)
        selectedRoomId = 0
        root.removeAllViews()
        root.addView(header("KILO TAXI", intent.getStringExtra(EXTRA_TITLE) ?: "Communication V6 Lite"))

        networkBadge = TextView(this).apply {
            text = if (liteMode) "● Lite Mode • Network checking…" else "● Standard Mode • Network checking…"
            textSize = 12f
            setTextColor(Color.parseColor("#5F7488"))
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
        root.addView(networkBadge)

        root.addView(card().apply {
            addView(TextView(this@CommunicationActivity).apply {
                text = "Lightweight Taxi Communication\nIncremental Chat • Quick Reply • Voice • Photo Compress • Location • Calls"
                textSize = 14f
                setTextColor(Color.parseColor("#52667A"))
            })
        }, margin())

        if (token.isBlank()) {
            root.addView(card().apply {
                addView(TextView(this@CommunicationActivity).apply {
                    text = "Login / Activation token is required before opening chatrooms."
                    setTextColor(Color.parseColor("#A32A2A"))
                })
            }, margin())
            root.addView(Button(this).apply { text="Close"; isAllCaps=false; setOnClickListener { finish() } })
            return
        }

        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        actions.addView(Switch(this).apply {
            text = "Lite Mode — save battery / data"
            isChecked = liteMode
            setOnCheckedChangeListener { _, checked ->
                liteMode = checked
                litePrefs.edit().putBoolean("lite_mode", checked).apply()
                toast(if (checked) "Lite Mode ON" else "Standard Mode ON")
                showRooms()
            }
        })
        actions.addView(button("Refresh Chatrooms") { loadRooms() })
        actions.addView(button("KILO Chatbot") { chatbotDialog() })
        if (role == "driver" || role == "passenger") actions.addView(button("Wallet / Payment") { walletDialog() })
        root.addView(card().apply { addView(actions) }, margin())

        roomsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(roomsBox)
        loadRooms()
    }

    private fun loadRooms() {
        val box = roomsBox ?: return
        box.removeAllViews()
        box.addView(TextView(this).apply { text = "Loading chatrooms…"; setPadding(dp(6),dp(10),dp(6),dp(10)) })
        net({ api.get("chat_rooms") }) { j ->
            box.removeAllViews()
            val rooms = j.optJSONArray("rooms") ?: JSONArray()
            if (rooms.length() == 0) {
                box.addView(TextView(this).apply {
                    text = "No chatrooms yet. Admin can create Driver / Township / Custom rooms. Booking rooms are created automatically."
                    setPadding(dp(6),dp(12),dp(6),dp(12))
                })
                return@net
            }
            for (i in 0 until rooms.length()) {
                val r = rooms.getJSONObject(i)
                val unread = maxOf(0, r.optInt("last_message_id") - r.optInt("last_read_message_id"))
                box.addView(card().apply {
                    addView(TextView(this@CommunicationActivity).apply {
                        text = r.optString("name")
                        textSize = 17f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#243E5B"))
                    })
                    addView(TextView(this@CommunicationActivity).apply {
                        text = r.optString("room_type") +
                            (r.optString("district").takeIf { it.isNotBlank() }?.let { " • " + it } ?: "") +
                            (if (unread > 0) " • Unread " + unread else "") +
                            "\n" + r.optString("last_message")
                        setTextColor(Color.parseColor("#667A8D"))
                    })
                    addView(button("Open Chat") { openRoom(r.optInt("id"), r.optString("name")) })
                }, margin())
            }
        }
    }

    private fun openRoom(roomId: Int, roomName: String) {
        selectedRoomId = roomId
        activeRoomId = roomId
        lastMessageId = 0
        chatHandler.removeCallbacks(chatPoll)
        root.removeAllViews()
        root.addView(header("CHAT ROOM", roomName))
        networkBadge = TextView(this).apply {
            text = if (liteMode) "● Lite Mode" else "● Standard Mode"
            textSize = 12f
            setTextColor(Color.parseColor("#5F7488"))
        }
        root.addView(networkBadge)
        root.addView(button("← Chatrooms") { showRooms() })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        activeMessageList = list

        root.addView(card().apply {
            addView(button("Refresh Messages") { loadMessages(roomId, list, true) })

            val quick = LinearLayout(this@CommunicationActivity).apply { orientation = LinearLayout.HORIZONTAL }
            quick.addView(button("I'm here") { sendText(roomId, "I’m here.", list) }, LinearLayout.LayoutParams(0,-2,1f))
            quick.addView(button("Wait 5 min") { sendText(roomId, "Please wait 5 minutes.", list) }, LinearLayout.LayoutParams(0,-2,1f))
            quick.addView(button("OK") { sendText(roomId, "OK", list) }, LinearLayout.LayoutParams(0,-2,1f))
            addView(quick)

            val row = LinearLayout(this@CommunicationActivity).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(button("📷 Photo") { photoPicker.launch("image/*") }, LinearLayout.LayoutParams(0,-2,1f))
            row.addView(button("📍 Location") { requestLocationFor(roomId) }, LinearLayout.LayoutParams(0,-2,1f))
            addView(row)

            val mediaRow = LinearLayout(this@CommunicationActivity).apply { orientation = LinearLayout.HORIZONTAL }
            val voice = button("🎙 Voice") { toggleVoice(roomId) }
            recordButton = voice
            mediaRow.addView(voice, LinearLayout.LayoutParams(0,-2,1f))
            mediaRow.addView(button("📞 Audio") { startCall(roomId, "audio") }, LinearLayout.LayoutParams(0,-2,1f))
            mediaRow.addView(button("🎥 Video") {
                if (liteMode) {
                    AlertDialog.Builder(this@CommunicationActivity)
                        .setTitle("Video uses more data")
                        .setMessage("Lite Mode is ON. Start video call anyway?")
                        .setPositiveButton("Start") { _,_ -> startCall(roomId, "video") }
                        .setNegativeButton("Cancel", null).show()
                } else startCall(roomId, "video")
            }, LinearLayout.LayoutParams(0,-2,1f))
            addView(mediaRow)

            val pending = pendingText(roomId)
            if (pending.isNotBlank()) addView(button("Retry pending message") { retryPending(roomId, list) })
        }, margin())

        root.addView(list, margin())

        val entry = EditText(this).apply {
            hint = "Message"
            minLines = 2
            setPadding(dp(10),dp(8),dp(10),dp(8))
            background = rounded(Color.WHITE)
        }
        root.addView(card().apply {
            addView(entry)
            addView(button("Send Message") {
                val t = entry.text.toString().trim()
                if (t.isNotBlank()) {
                    entry.text.clear()
                    sendText(roomId, t, list)
                }
            })
        }, margin())

        loadMessages(roomId, list, true)
        chatHandler.postDelayed(chatPoll, if (liteMode) 12000 else 6000)
    }

    private fun loadMessages(roomId:Int, list:LinearLayout, reset:Boolean=false) {
        val after = if (reset) 0 else lastMessageId
        val limit = if (liteMode) 40 else 80
        net({ api.get("chat_messages", mapOf("room_id" to roomId.toString(), "after_id" to after.toString(), "limit" to limit.toString())) }) { j ->
            if (reset) {
                list.removeAllViews()
                lastMessageId = 0
            }
            val a = j.optJSONArray("messages") ?: JSONArray()
            var newest = lastMessageId
            for (i in 0 until a.length()) {
                val m = a.getJSONObject(i)
                newest = maxOf(newest, m.optInt("id"))
                val own = m.optString("sender_type") == role
                val c = card()
                c.setPadding(dp(12),dp(9),dp(12),dp(9))
                c.addView(TextView(this).apply {
                    text = m.optString("sender_type") + " • " + m.optString("created_at") +
                        " • " + m.optString("message_type")
                    textSize = 11f
                    setTextColor(Color.parseColor("#7A8996"))
                })
                if (m.optString("message_text").isNotBlank()) c.addView(TextView(this).apply {
                    text = m.optString("message_text")
                    textSize = 15f
                    setTextColor(Color.parseColor("#24384A"))
                })
                if (!m.isNull("lat") && !m.isNull("lng")) c.addView(TextView(this).apply {
                    text = "📍 " + m.optString("lat") + ", " + m.optString("lng")
                })
                val media = m.optString("media_url")
                if (media.isNotBlank()) c.addView(button("Open Attachment") {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(media))) }
                })
                if (own) c.addView(TextView(this).apply {
                    text = "Delivered: " + m.optInt("delivered_count") + " • Read: " + m.optInt("read_count")
                    textSize = 11f
                    setTextColor(Color.parseColor("#4B7D74"))
                })
                list.addView(c, margin())
            }
            lastMessageId = newest
            if (newest > 0) net({ api.post("chat_mark_read", JSONObject().put("room_id",roomId).put("message_id",newest)) }) { }
        }
    }

    private val chatPoll = object : Runnable {
        override fun run() {
            val rid = activeRoomId
            val box = activeMessageList
            if (rid > 0 && box != null) {
                loadMessages(rid, box, false)
                chatHandler.postDelayed(this, if (liteMode) 12000 else 6000)
            }
        }
    }

    private fun sendText(roomId:Int, text:String, list:LinearLayout) {
        thread {
            val started = SystemClock.elapsedRealtime()
            val r = runCatching {
                api.post("chat_send", JSONObject().put("room_id",roomId).put("message_type","text").put("message_text",text))
            }.getOrElse { JSONObject().put("ok",false).put("error",it.message?:"Network error") }
            val ms = SystemClock.elapsedRealtime() - started
            runOnUiThread {
                updateNetworkBadge(ms, r.optBoolean("ok"))
                if (r.optBoolean("ok")) {
                    clearPending(roomId)
                    loadMessages(roomId, list, false)
                } else {
                    litePrefs.edit().putString("pending_text_" + roomId, text).apply()
                    toast("Network problem. Message saved for retry.")
                }
            }
        }
    }

    private fun pendingText(roomId:Int)=litePrefs.getString("pending_text_" + roomId, "").orEmpty()
    private fun clearPending(roomId:Int)=litePrefs.edit().remove("pending_text_" + roomId).apply()
    private fun retryPending(roomId:Int,list:LinearLayout) {
        val p=pendingText(roomId)
        if(p.isNotBlank()) sendText(roomId,p,list)
    }

    private fun toggleVoice(roomId:Int) {
        if (recorder != null) {
            val f = recordFile
            stopRecorderQuietly(true)
            recordButton?.text = "🎙 Start Voice"
            if (f != null && f.exists() && f.length() > 0) uploadFile(f, roomId, "voice", "audio/mp4")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingVoiceStart = true
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else startVoiceRecording()
    }

    private fun startVoiceRecording() {
        val f = File(cacheDir, "voice_" + System.currentTimeMillis() + ".m4a")
        recordFile = f
        try {
            recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            recorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(if(liteMode)64000 else 96000)
                setAudioSamplingRate(if(liteMode)32000 else 44100)
                setOutputFile(f.absolutePath)
                prepare()
                start()
            }
            recordButton?.text = "⏹ Stop & Send"
            Toast.makeText(this,"Voice recording…",Toast.LENGTH_SHORT).show()
        } catch (e:Exception) {
            stopRecorderQuietly(false)
            Toast.makeText(this,"Voice recorder error: " + e.message,Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecorderQuietly(stop:Boolean) {
        val r = recorder ?: return
        runCatching { if (stop) r.stop() }
        runCatching { r.reset() }
        runCatching { r.release() }
        recorder = null
    }

    private fun uploadUri(uri:Uri, roomId:Int, kind:String) {
        thread {
            try {
                val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                if (mime.startsWith("image/")) {
                    val prepared = prepareImage(uri)
                    uploadBytes(prepared.first, "photo_" + System.currentTimeMillis() + ".jpg", "image/jpeg", roomId, kind)
                } else {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@thread
                    uploadBytes(bytes, "upload_" + System.currentTimeMillis() + ".bin", mime, roomId, kind)
                }
            } catch(e:Exception) { runOnUiThread { toast("Photo upload error: " + e.message) } }
        }
    }

    private fun prepareImage(uri:Uri):Pair<ByteArray,String> {
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
        val maxSide=if(liteMode)1280 else 1920
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxSide) sample*=2
        val opts=BitmapFactory.Options().apply{inSampleSize=maxOf(1,sample)}
        val bmp=contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,opts)}
            ?: throw IllegalArgumentException("Cannot decode image")
        val out=ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, if(liteMode)68 else 82, out)
        bmp.recycle()
        return out.toByteArray() to "image/jpeg"
    }

    private fun uploadFile(file:File, roomId:Int, kind:String, mime:String) {
        thread {
            try { uploadBytes(file.readBytes(), file.name, mime, roomId, kind) }
            catch(e:Exception){ runOnUiThread { toast("Upload error: " + e.message) } }
        }
    }

    private fun uploadBytes(bytes:ByteArray,fileName:String,mime:String,roomId:Int,kind:String) {
        val u = api.post("chat_upload", JSONObject()
            .put("room_id",roomId)
            .put("file_name",fileName)
            .put("mime",mime)
            .put("base64", Base64.encodeToString(bytes,Base64.NO_WRAP)))
        if (!u.optBoolean("ok")) { runOnUiThread { toast(u.optString("error","Upload failed")) }; return }
        val media = u.optString("media_url")
        val meta = JSONObject().put("mime",mime).put("size",bytes.size)
        val s = api.post("chat_send", JSONObject()
            .put("room_id",roomId)
            .put("message_type",kind)
            .put("media_url",media)
            .put("metadata",meta))
        runOnUiThread { if(s.optBoolean("ok")) openRoom(roomId, "Chat Room #" + roomId) else toast(s.optString("error","Send failed")) }
    }

    private fun requestLocationFor(roomId:Int) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingLocationRoom = roomId
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else sendCurrentLocation(roomId)
    }

    private fun sendCurrentLocation(roomId:Int) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val l = runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()
        if (l == null) { toast("Current location is not available yet."); return }
        net({ api.post("chat_send", JSONObject()
            .put("room_id",roomId)
            .put("message_type","location")
            .put("message_text","Current location")
            .put("lat",l.latitude)
            .put("lng",l.longitude)) }) {
            toast("Location shared.")
        }
    }

    private fun startCall(roomId:Int,type:String) {
        net({ api.post("call_start", JSONObject().put("room_id",roomId).put("call_type",type)) }) { j ->
            val call = j.optJSONObject("call") ?: return@net
            openCall(call.optInt("id"), type, true)
        }
    }

    private fun openCall(callId:Int,type:String,caller:Boolean) {
        startActivity(Intent(this,WebRtcCallActivity::class.java)
            .putExtra(WebRtcCallActivity.EXTRA_API,apiUrl)
            .putExtra(WebRtcCallActivity.EXTRA_TOKEN,token)
            .putExtra(WebRtcCallActivity.EXTRA_CALL_ID,callId)
            .putExtra(WebRtcCallActivity.EXTRA_CALL_TYPE,type)
            .putExtra(WebRtcCallActivity.EXTRA_CALLER,caller))
    }

    private fun chatbotDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this).setTitle("KILO TAXI Chatbot").setView(input)
            .setPositiveButton("Ask") { _,_->
                val q=input.text.toString()
                thread {
                    val r=runCatching { CommApi(apiUrl,"").post("chatbot_message",JSONObject().put("message",q)) }.getOrElse { JSONObject().put("ok",false).put("error",it.message) }
                    runOnUiThread { AlertDialog.Builder(this).setTitle("Assistant").setMessage(if(r.optBoolean("ok"))r.optString("reply") else r.optString("error")).setPositiveButton("OK",null).show() }
                }
            }.setNegativeButton("Cancel",null).show()
    }

    private fun walletDialog() {
        net({ api.get("wallet_balance") }) { j ->
            val w=j.optJSONObject("wallet")?:JSONObject()
            val tx=j.optJSONArray("transactions")?:JSONArray()
            val sb=StringBuilder("Balance: " + w.optString("balance") + " " + w.optString("currency","MMK") + "\n\n")
            for(i in 0 until minOf(tx.length(),10)){
                val t=tx.getJSONObject(i);sb.append(t.optString("created_at")).append(" • ").append(t.optString("txn_type")).append(" • ").append(t.optString("amount")).append("\n")
            }
            AlertDialog.Builder(this).setTitle("Wallet")
                .setMessage(sb.toString())
                .setPositiveButton("Payment Request"){_,_-> paymentPrompt() }
                .setNegativeButton("Close",null).show()
        }
    }

    private fun paymentPrompt() {
        val e=EditText(this).apply{hint="Amount MMK";inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL}
        AlertDialog.Builder(this).setTitle("Create Payment Request").setView(e)
            .setPositiveButton("Create"){_,_->
                val amount=e.text.toString().toDoubleOrNull()?:0.0
                net({ api.post("payment_request",JSONObject().put("amount",amount)) }) { r ->
                    AlertDialog.Builder(this).setTitle(r.optString("request_code"))
                        .setMessage("Amount: " + r.optString("amount") + "\nProvider: " + r.optString("provider") + "\nQR: " + r.optString("qr_payload") + "\n" + r.optString("instructions"))
                        .setPositiveButton("OK",null).show()
                }
            }.setNegativeButton("Cancel",null).show()
    }

    private fun net(fn:()->JSONObject,done:(JSONObject)->Unit) {
        thread {
            val started=SystemClock.elapsedRealtime()
            val r=runCatching(fn).getOrElse { JSONObject().put("ok",false).put("error",it.message?:"Network error") }
            val ms=SystemClock.elapsedRealtime()-started
            runOnUiThread {
                updateNetworkBadge(ms,r.optBoolean("ok"))
                if(r.optBoolean("ok"))done(r) else toast(r.optString("error","Request failed"))
            }
        }
    }

    private fun updateNetworkBadge(ms:Long, ok:Boolean) {
        val label = when {
            !ok -> "● Offline / Retry"
            ms < 700 -> "● Network Fast • " + ms + "ms"
            ms < 1800 -> "● Network Normal • " + ms + "ms"
            else -> "● Network Slow • " + ms + "ms"
        }
        networkBadge?.text = (if(liteMode) "Lite • " else "") + label
        networkBadge?.setTextColor(Color.parseColor(if(ok) "#4D776E" else "#A33A32"))
    }

    private fun netToast(fn:()->JSONObject) { net(fn){toast("Success")} }

    private fun header(k:String,t:String)=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        addView(TextView(this@CommunicationActivity).apply{text=k;textSize=12f;letterSpacing=.14f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#47729B"))})
        addView(TextView(this@CommunicationActivity).apply{text=t;textSize=26f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor("#223B57"))})
    }
    private fun card()=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12));background=rounded(Color.WHITE);elevation=dp(2).toFloat()
    }
    private fun button(t:String,on:()->Unit)=Button(this).apply{text=t;isAllCaps=false;setOnClickListener{on()}}
    private fun rounded(c:Int)=GradientDrawable().apply{setColor(c);cornerRadius=dp(14).toFloat();setStroke(dp(1),Color.parseColor("#D7E0E8"))}
    private fun margin()=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
