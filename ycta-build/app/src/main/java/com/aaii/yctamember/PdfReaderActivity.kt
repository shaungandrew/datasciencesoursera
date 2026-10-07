package com.aaii.yctamember

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import java.io.File

class PdfReaderActivity : ComponentActivity() {
    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var pageIndex = 0
    private lateinit var image: ImageView
    private lateinit var pageText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra("path").orEmpty()
        val title = intent.getStringExtra("title").orEmpty()
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(10),dp(10),dp(10),dp(10)); setBackgroundColor(Color.parseColor("#ECEFF2")) }
        root.addView(TextView(this).apply { text=title.ifBlank { "PDF Reader" }; textSize=20f; gravity=Gravity.CENTER; setPadding(0,0,0,dp(8)) })
        image = ImageView(this).apply { adjustViewBounds=true; scaleType=ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.WHITE) }
        root.addView(image, LinearLayout.LayoutParams(-1,0,1f))
        val nav=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        nav.addView(Button(this).apply { text="Prev"; setOnClickListener { if(pageIndex>0){pageIndex--; render()} } }, LinearLayout.LayoutParams(0,-2,1f))
        pageText=TextView(this).apply { gravity=Gravity.CENTER }
        nav.addView(pageText, LinearLayout.LayoutParams(0,-2,1f))
        nav.addView(Button(this).apply { text="Next"; setOnClickListener { if(pageIndex+1<(renderer?.pageCount?:0)){pageIndex++; render()} } }, LinearLayout.LayoutParams(0,-2,1f))
        root.addView(nav)
        setContentView(root)
        runCatching {
            descriptor=ParcelFileDescriptor.open(File(path),ParcelFileDescriptor.MODE_READ_ONLY)
            renderer=PdfRenderer(descriptor!!)
            render()
        }.onFailure { Toast.makeText(this,"PDF error: ${it.message}",Toast.LENGTH_LONG).show() }
    }

    private fun render(){
        val r=renderer?:return
        if(r.pageCount==0)return
        val page=r.openPage(pageIndex)
        val width=resources.displayMetrics.widthPixels.coerceAtLeast(720)
        val height=(width.toFloat()/page.width*page.height).toInt().coerceAtLeast(900)
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        image.setImageBitmap(bitmap)
        pageText.text="${pageIndex+1} / ${r.pageCount}"
        page.close()
    }

    override fun onDestroy(){ renderer?.close(); descriptor?.close(); super.onDestroy() }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
