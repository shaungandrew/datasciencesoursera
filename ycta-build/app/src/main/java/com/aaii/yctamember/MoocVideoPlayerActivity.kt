package com.aaii.yctamember

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.URI

/**
 * Video playback is hosted inside the native YCTA app.
 * MP4/HLS = fully native Media3/ExoPlayer.
 * YouTube = official embedded iframe, Drive share = Drive preview (WebView).
 * The lower navigation row is Android-safe-area aware.
 */
class MoocVideoPlayerActivity : ComponentActivity() {
    private lateinit var root: LinearLayout
    private lateinit var center: FrameLayout
    private lateinit var bottom: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var status: TextView
    private lateinit var mediaUrl: String
    private lateinit var label: String
    private var mediaPlayer: ExoPlayer? = null
    private var mediaView: PlayerView? = null
    private var embedded: WebView? = null
    private var playButton: Button? = null
    private var mode = "none"
    private var fullScreen = false
    private val prefs by lazy { getSharedPreferences("ycta_mooc_video_progress", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        mediaUrl = intent.getStringExtra("video_url").orEmpty().trim()
        label = intent.getStringExtra("video_title").orEmpty().ifBlank { "MOOC Video" }
        val source = intent.getStringExtra("video_source").orEmpty().lowercase()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101820"))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setBackgroundColor(Color.parseColor("#172D40"))
        }
        top.addView(Button(this).apply {
            text = "← Course"
            isAllCaps = false
            setOnClickListener { finish() }
        })
        titleView = TextView(this).apply {
            text = label
            textSize = 17f
            maxLines = 2
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        top.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))

        center = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        status = TextView(this).apply {
            text = "Preparing in-app video player…"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        center.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))

        bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundColor(Color.parseColor("#192E40"))
            elevation = dp(12).toFloat()
        }

        playButton = Button(this).apply {
            text = "Play / Pause"
            textSize = 12f
            isAllCaps = false
            isEnabled = false
            setOnClickListener {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) player.pause() else player.play()
                }
            }
        }
        bottom.addView(playButton, LinearLayout.LayoutParams(0, -2, 1f))

        bottom.addView(Button(this).apply {
            text = "Full Screen"
            textSize = 12f
            isAllCaps = false
            setOnClickListener { toggleOrientation() }
        }, LinearLayout.LayoutParams(0, -2, 1.1f))

        bottom.addView(Button(this).apply {
            text = "Open Source"
            textSize = 12f
            isAllCaps = false
            setOnClickListener { openOriginal() }
        }, LinearLayout.LayoutParams(0, -2, 1.1f))

        root.addView(top, LinearLayout.LayoutParams(-1, -2))
        root.addView(center, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(top) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(dp(12) + bars.left, dp(6) + bars.top, dp(12) + bars.right, dp(6))
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottom) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(dp(8) + bars.left, dp(7), dp(8) + bars.right, dp(7) + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        val parsed = runCatching { URI(mediaUrl) }.getOrNull()
        if (parsed == null || parsed.scheme?.lowercase() !in listOf("https", "http") ||
            parsed.host.isNullOrBlank()) {
            setStatus("No valid video URL. Please check the course video/source link.")
            return
        }

        val host = parsed.host.lowercase()
        when {
            isYoutube(host) -> showYouTube(parsed)
            isDrive(host) -> showDrive(parsed)
            looksLikeDirectVideo(parsed, source) -> showNativePlayer()
            else -> setStatus(
                "This course URL is a provider page, not a direct MP4/HLS video. " +
                "Use Open Source to visit it."
            )
        }
    }

    private fun isYoutube(host: String) =
        host == "youtu.be" || host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")

    private fun isDrive(host: String) =
        host == "drive.google.com" || host == "docs.google.com"

    private fun looksLikeDirectVideo(uri: URI, source: String): Boolean {
        val path = uri.path.orEmpty().lowercase()
        val host = uri.host.orEmpty().lowercase()
        return listOf(".mp4", ".m4v", ".mov", ".webm", ".m3u8", ".mpd")
            .any { path.endsWith(it) } ||
            host.endsWith(".googlevideo.com") ||
            (source == "video" && (path.contains("/stream/") || path.contains("/video/")))
    }

    private fun showNativePlayer() {
        mode = "native"
        status.text = "Loading native video…"
        playButton?.isEnabled = true
        val player = ExoPlayer.Builder(this).build()
        mediaPlayer = player
        val view = PlayerView(this).apply {
            this.player = player
            useController = true
            setBackgroundColor(Color.BLACK)
        }
        mediaView = view
        center.addView(view, 0, FrameLayout.LayoutParams(-1, -1))
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> setStatus("Buffering…")
                    Player.STATE_READY -> status.visibility = View.GONE
                    Player.STATE_ENDED -> {
                        status.visibility = View.VISIBLE
                        status.text = "Video finished"
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                setStatus("Unable to play video natively: ${error.errorCodeName}. " +
                    "Check that the URL is a playable stream, or tap Open Source.")
            }
        })
        runCatching {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(mediaUrl)))
            player.prepare()
            val saved = prefs.getLong(positionKey(), 0L)
            if (saved > 0L) player.seekTo(saved)
            player.playWhenReady = true
        }.onFailure { setStatus("Cannot load video: ${it.message}") }
    }

    private fun showYouTube(uri: URI) {
        mode = "youtube"
        val segments = uri.path.orEmpty().trim('/').split('/')
        val query = Uri.parse(mediaUrl)
        val id = when {
            uri.host.equals("youtu.be", true) -> segments.firstOrNull()
            segments.firstOrNull() in listOf("embed", "shorts", "live", "v") ->
                segments.getOrNull(1)
            else -> query.getQueryParameter("v")
        }
        val videoId = id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        val playlistId = query.getQueryParameter("list")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,100}")) }

        val embed = when {
            videoId != null ->
                "https://www.youtube-nocookie.com/embed/$videoId?playsinline=1&rel=0&enablejsapi=1"
            playlistId != null ->
                "https://www.youtube-nocookie.com/embed/videoseries?list=$playlistId&playsinline=1"
            else -> null
        }
        if (embed == null) {
            setStatus("This YouTube link is not a supported video/playlist URL. Tap Open Source.")
            return
        }
        val frame = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"/>
            <style>
              html,body {margin:0;background:#000;width:100%;height:100%;overflow:hidden;}
              iframe {border:0;width:100%;height:100%;}
            </style></head><body>
            <iframe src="$embed" allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
                    allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe>
            </body></html>
        """.trimIndent()
        showTrustedEmbeddedPage { web ->
            // The real source origin helps YouTube receive a Referer,
            // avoiding the 153 missing-referrer playback error.
            web.loadDataWithBaseURL(
                "https://aaii.asia/edu/mooc/",
                frame,
                "text/html",
                "UTF-8",
                null
            )
        }
        setStatus("YouTube embedded player loading…")
    }

    private fun showDrive(uri: URI) {
        mode = "drive"
        val parsed = Uri.parse(mediaUrl)
        val path = parsed.path.orEmpty()
        val fileId = Regex("/file/d/([A-Za-z0-9_-]+)").find(path)?.groupValues?.getOrNull(1)
            ?: parsed.getQueryParameter("id")
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{15,100}")) }

        if (fileId == null) {
            setStatus(
                "This Google Drive URL is not a video file preview. " +
                "Folders and restricted files must be opened from their source."
            )
            return
        }

        val preview = "https://drive.google.com/file/d/$fileId/preview"
        showTrustedEmbeddedPage { web -> web.loadUrl(preview) }
        setStatus("Google Drive preview loading… File permission may be required.")
    }

    private fun showTrustedEmbeddedPage(load: (WebView) -> Unit) {
        val view = WebView(this)
        embedded = view
        view.setBackgroundColor(Color.BLACK)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.mediaPlaybackRequiresUserGesture = true
        view.settings.loadsImagesAutomatically = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.webChromeClient = WebChromeClient()
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                status.visibility = View.GONE
            }
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    setStatus("Embedded player network error. Check connection or use Open Source.")
                }
            }
        }
        center.addView(view, 0, FrameLayout.LayoutParams(-1, -1))
        load(view)
    }

    private fun toggleOrientation() {
        fullScreen = !fullScreen
        requestedOrientation = if (fullScreen)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun openOriginal() {
        val valid = runCatching {
            val u = Uri.parse(mediaUrl)
            u.scheme in listOf("http", "https") && !u.host.isNullOrBlank()
        }.getOrDefault(false)
        if (!valid) {
            Toast.makeText(this, "Source link missing", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mediaUrl)))
        }.onFailure {
            Toast.makeText(this, "Browser could not open this link.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setStatus(message: String) {
        status.text = message
        status.visibility = View.VISIBLE
    }

    private fun positionKey() = "pos_" + mediaUrl.hashCode()

    override fun onStop() {
        mediaPlayer?.let { player ->
            if (player.currentPosition > 0L) {
                prefs.edit().putLong(positionKey(), player.currentPosition).apply()
            }
            player.pause()
        }
        embedded?.onPause()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        embedded?.onResume()
    }

    override fun onDestroy() {
        mediaView?.player = null
        mediaPlayer?.release()
        mediaPlayer = null
        embedded?.let { web ->
            web.stopLoading()
            center.removeView(web)
            web.destroy()
        }
        embedded = null
        super.onDestroy()
    }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()
}
