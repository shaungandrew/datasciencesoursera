package com.aaii.kilotaxi.common

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.*
import kotlin.concurrent.thread

class WebRtcCallActivity : ComponentActivity() {
    companion object {
        const val EXTRA_API = "api_url"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_CALL_TYPE = "call_type"
        const val EXTRA_CALLER = "caller"
    }

    private lateinit var api: CommApi
    private var callId = 0
    private var callType = "audio"
    private var caller = false
    private lateinit var status: TextView
    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var egl: EglBase? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var audioSource: AudioSource? = null
    private var videoSource: VideoSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoTrack: VideoTrack? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastSignalId = 0
    private var remoteDescriptionSet = false
    private var offerSent = false
    private var answerSent = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasPermissions()) setupRtc() else {
            toast("Microphone/camera permission required.")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apiUrl=intent.getStringExtra(EXTRA_API).orEmpty()
        val token=intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        api=CommApi(apiUrl,token)
        callId=intent.getIntExtra(EXTRA_CALL_ID,0)
        callType=intent.getStringExtra(EXTRA_CALL_TYPE)?:"audio"
        caller=intent.getBooleanExtra(EXTRA_CALLER,false)

        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(10),dp(10),dp(10),dp(18))
            setBackgroundColor(Color.parseColor("#101826"))
        }
        status=TextView(this).apply{
            text="Connecting " + callType + " call…"
            setTextColor(Color.WHITE);textSize=16f;gravity=Gravity.CENTER
            setPadding(0,dp(8),0,dp(8))
        }
        root.addView(status)
        if(callType=="video"){
            remoteRenderer=SurfaceViewRenderer(this)
            localRenderer=SurfaceViewRenderer(this)
            root.addView(remoteRenderer,LinearLayout.LayoutParams(-1,0,1f))
            root.addView(localRenderer,LinearLayout.LayoutParams(-1,dp(150)))
        } else {
            root.addView(TextView(this).apply{
                text="KILO TAXI\nVoIP Audio Call"
                setTextColor(Color.WHITE);textSize=30f;gravity=Gravity.CENTER
            },LinearLayout.LayoutParams(-1,0,1f))
        }
        val end=Button(this).apply{
            text="End Call";isAllCaps=false
            setOnClickListener{ endCall() }
        }
        root.addView(end)
        setContentView(root)

        if(hasPermissions())setupRtc() else {
            val p=mutableListOf(Manifest.permission.RECORD_AUDIO)
            if(callType=="video")p.add(Manifest.permission.CAMERA)
            permissionLauncher.launch(p.toTypedArray())
        }
    }

    private fun hasPermissions():Boolean {
        val mic=ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
        val cam=callType!="video" || ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        return mic&&cam
    }

    private fun setupRtc(){
        status.text="Loading ICE / TURN configuration…"
        thread{
            val cfg=runCatching{api.get("call_config")}.getOrElse{JSONObject().put("ok",false).put("error",it.message)}
            runOnUiThread{
                if(!cfg.optBoolean("ok")){status.text=cfg.optString("error","Call configuration failed");return@runOnUiThread}
                initPeer(cfg)
            }
        }
    }

    private fun initPeer(cfg:JSONObject){
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        )
        egl=EglBase.create()
        localRenderer?.init(egl!!.eglBaseContext,null)
        remoteRenderer?.init(egl!!.eglBaseContext,null)
        localRenderer?.setMirror(true)
        remoteRenderer?.setMirror(false)

        factory=PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl!!.eglBaseContext,true,true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl!!.eglBaseContext))
            .createPeerConnectionFactory()

        val ice=mutableListOf<PeerConnection.IceServer>()
        val stun=cfg.optJSONArray("stun_urls")?:JSONArray()
        for(i in 0 until stun.length()){
            val u=stun.optString(i);if(u.isNotBlank())ice.add(PeerConnection.IceServer.builder(u).createIceServer())
        }
        val turns=cfg.optJSONArray("turn_urls")?:JSONArray()
        val tu=cfg.optString("turn_username");val tp=cfg.optString("turn_password")
        for(i in 0 until turns.length()){
            val u=turns.optString(i);if(u.isNotBlank())ice.add(PeerConnection.IceServer.builder(u).setUsername(tu).setPassword(tp).createIceServer())
        }
        if(ice.isEmpty())ice.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())

        val rtc=PeerConnection.RTCConfiguration(ice)
        rtc.sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN
        pc=factory!!.createPeerConnection(rtc,observer)
        if(pc==null){status.text="Peer connection failed";return}

        audioSource=factory!!.createAudioSource(MediaConstraints())
        audioTrack=factory!!.createAudioTrack("KILO_AUDIO",audioSource)
        pc!!.addTrack(audioTrack,listOf("KILO_STREAM"))

        if(callType=="video"){
            val enumerator=Camera2Enumerator(this)
            val names=enumerator.deviceNames
            var cap:VideoCapturer?=null
            for(n in names)if(enumerator.isFrontFacing(n)){cap=enumerator.createCapturer(n,null);if(cap!=null)break}
            if(cap==null)for(n in names){cap=enumerator.createCapturer(n,null);if(cap!=null)break}
            videoCapturer=cap
            if(cap!=null){
                videoSource=factory!!.createVideoSource(false)
                surfaceHelper=SurfaceTextureHelper.create("KiloCapture",egl!!.eglBaseContext)
                cap.initialize(surfaceHelper,this,videoSource!!.capturerObserver)
                cap.startCapture(640,480,24)
                videoTrack=factory!!.createVideoTrack("KILO_VIDEO",videoSource)
                videoTrack!!.addSink(localRenderer)
                pc!!.addTrack(videoTrack,listOf("KILO_STREAM"))
            }
        }

        status.text=if(caller)"Calling… waiting for answer" else "Connected to signaling…"
        if(caller)createOffer()
        handler.post(pollSignals)
    }

    private val observer=object:PeerConnection.Observer{
        override fun onSignalingChange(newState:PeerConnection.SignalingState){}
        override fun onIceConnectionChange(newState:PeerConnection.IceConnectionState){runOnUiThread{status.text="ICE: "+newState.name}}
        override fun onIceConnectionReceivingChange(receiving:Boolean){}
        override fun onIceGatheringChange(newState:PeerConnection.IceGatheringState){}
        override fun onIceCandidate(candidate:IceCandidate){
            val p=JSONObject().put("sdpMid",candidate.sdpMid).put("sdpMLineIndex",candidate.sdpMLineIndex).put("candidate",candidate.sdp)
            signal("ice",p)
        }
        override fun onIceCandidatesRemoved(candidates:Array<IceCandidate>){}
        override fun onAddStream(stream:MediaStream){
            if(stream.videoTracks.isNotEmpty())runOnUiThread{stream.videoTracks[0].addSink(remoteRenderer)}
        }
        override fun onRemoveStream(stream:MediaStream){}
        override fun onDataChannel(dataChannel:DataChannel){}
        override fun onRenegotiationNeeded(){}
        override fun onAddTrack(receiver:RtpReceiver,mediaStreams:Array<MediaStream>){
            val t=receiver.track()
            if(t is VideoTrack)runOnUiThread{t.addSink(remoteRenderer)}
        }
        override fun onTrack(transceiver:RtpTransceiver){
            val t=transceiver.receiver.track()
            if(t is VideoTrack)runOnUiThread{t.addSink(remoteRenderer)}
        }
        override fun onConnectionChange(newState:PeerConnection.PeerConnectionState){
            runOnUiThread{status.text="Call: "+newState.name}
        }
    }

    private fun createOffer(){
        if(offerSent)return
        pc?.createOffer(object:SdpObserver{
            override fun onCreateSuccess(sdp:SessionDescription){
                pc?.setLocalDescription(simpleSdpObserver(),sdp)
                signal("offer",JSONObject().put("type","offer").put("sdp",sdp.description))
                offerSent=true
            }
            override fun onSetSuccess(){}
            override fun onCreateFailure(error:String){runOnUiThread{status.text="Offer error: "+error}}
            override fun onSetFailure(error:String){}
        },MediaConstraints())
    }

    private fun createAnswer(){
        if(answerSent)return
        pc?.createAnswer(object:SdpObserver{
            override fun onCreateSuccess(sdp:SessionDescription){
                pc?.setLocalDescription(simpleSdpObserver(),sdp)
                signal("answer",JSONObject().put("type","answer").put("sdp",sdp.description))
                answerSent=true
            }
            override fun onSetSuccess(){}
            override fun onCreateFailure(error:String){runOnUiThread{status.text="Answer error: "+error}}
            override fun onSetFailure(error:String){}
        },MediaConstraints())
    }

    private fun simpleSdpObserver()=object:SdpObserver{
        override fun onCreateSuccess(sdp:SessionDescription){}
        override fun onSetSuccess(){}
        override fun onCreateFailure(error:String){}
        override fun onSetFailure(error:String){runOnUiThread{status.text="SDP error: "+error}}
    }

    private fun signal(type:String,payload:JSONObject){
        thread{runCatching{api.post("call_signal",JSONObject().put("call_id",callId).put("signal_type",type).put("payload",payload))}}
    }

    private val pollSignals=object:Runnable{
        override fun run(){
            thread{
                val j=runCatching{api.get("call_poll",mapOf("call_id" to callId.toString(),"after_id" to lastSignalId.toString()))}.getOrElse{JSONObject()}
                if(j.optBoolean("ok")){
                    val call=j.optJSONObject("call")?:JSONObject()
                    val state=call.optString("status")
                    if(state in listOf("rejected","ended","missed","failed")){
                        runOnUiThread{status.text="Call "+state;handler.postDelayed({finish()},800)}
                        return@thread
                    }
                    val a=j.optJSONArray("signals")?:JSONArray()
                    for(i in 0 until a.length()){
                        val s=a.getJSONObject(i);lastSignalId=maxOf(lastSignalId,s.optInt("id"))
                        val type=s.optString("signal_type")
                        val payload=runCatching{JSONObject(s.optString("payload_json","{}"))}.getOrElse{JSONObject()}
                        when(type){
                            "offer"->{
                                val sd=SessionDescription(SessionDescription.Type.OFFER,payload.optString("sdp"))
                                pc?.setRemoteDescription(object:SdpObserver{
                                    override fun onCreateSuccess(sdp:SessionDescription){}
                                    override fun onSetSuccess(){remoteDescriptionSet=true;createAnswer()}
                                    override fun onCreateFailure(error:String){}
                                    override fun onSetFailure(error:String){runOnUiThread{status.text="Remote offer error: "+error}}
                                },sd)
                            }
                            "answer"->{
                                val sd=SessionDescription(SessionDescription.Type.ANSWER,payload.optString("sdp"))
                                pc?.setRemoteDescription(simpleSdpObserver(),sd);remoteDescriptionSet=true
                            }
                            "ice"->{
                                val c=IceCandidate(payload.optString("sdpMid"),payload.optInt("sdpMLineIndex"),payload.optString("candidate"))
                                pc?.addIceCandidate(c)
                            }
                            "hangup"->{runOnUiThread{finish()};return@thread}
                        }
                    }
                }
                runOnUiThread{handler.postDelayed(this,900)}
            }
        }
    }

    private fun endCall(){
        signal("hangup",JSONObject())
        thread{runCatching{api.post("call_action",JSONObject().put("call_id",callId).put("action","end"))}}
        finish()
    }

    override fun onDestroy(){
        handler.removeCallbacks(pollSignals)
        runCatching{videoCapturer?.stopCapture()}
        videoCapturer?.dispose();surfaceHelper?.dispose();videoTrack?.dispose();videoSource?.dispose();audioTrack?.dispose();audioSource?.dispose()
        pc?.dispose();factory?.dispose();localRenderer?.release();remoteRenderer?.release();egl?.release()
        super.onDestroy()
    }

    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
