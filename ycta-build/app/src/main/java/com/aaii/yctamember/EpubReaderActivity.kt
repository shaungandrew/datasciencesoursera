package com.aaii.yctamember

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class EpubReaderActivity : ComponentActivity() {
    private lateinit var text: TextView
    private lateinit var page: TextView
    private var chapters: List<String> = emptyList()
    private var index=0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path=intent.getStringExtra("path").orEmpty()
        val title=intent.getStringExtra("title").orEmpty()
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(10),dp(14),dp(10)); setBackgroundColor(Color.parseColor("#FAFAF8")) }
        root.addView(TextView(this).apply { this.text=title.ifBlank { "EPUB Reader" }; textSize=20f; gravity=Gravity.CENTER; setPadding(0,0,0,dp(8)) })
        text=TextView(this).apply { textSize=17f; setTextColor(Color.parseColor("#263642")); setLineSpacing(3f,1.15f); setPadding(0,dp(8),0,dp(8)) }
        root.addView(ScrollView(this).apply { addView(text) },LinearLayout.LayoutParams(-1,0,1f))
        val nav=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        nav.addView(Button(this).apply { text="Prev"; setOnClickListener { if(index>0){index--;showChapter()} } },LinearLayout.LayoutParams(0,-2,1f))
        page=TextView(this).apply { gravity=Gravity.CENTER }
        nav.addView(page,LinearLayout.LayoutParams(0,-2,1f))
        nav.addView(Button(this).apply { text="Next"; setOnClickListener { if(index+1<chapters.size){index++;showChapter()} } },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(nav)
        setContentView(root)
        lifecycleScope.launch {
            val r=withContext(Dispatchers.IO){ runCatching { loadEpub(File(path)) } }
            r.onSuccess { chapters=it; if(chapters.isEmpty()) text.text="EPUB contains no readable chapters." else showChapter() }
                .onFailure { text.text="EPUB error: ${it.message}" }
        }
    }

    private fun showChapter(){ text.text=chapters[index]; page.text="${index+1} / ${chapters.size}" }

    private fun loadEpub(file:File):List<String>{
        ZipFile(file).use { zip ->
            val container=zip.getEntry("META-INF/container.xml")?:error("EPUB container.xml missing")
            val db=DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc=zip.getInputStream(container).use { db.parse(it) }
            val rootfile=doc.getElementsByTagName("rootfile").item(0)?:error("EPUB package path missing")
            val opfPath=rootfile.attributes.getNamedItem("full-path")?.nodeValue?:error("EPUB package path missing")
            val opfEntry=zip.getEntry(opfPath)?:error("EPUB package missing")
            val opf=zip.getInputStream(opfEntry).use { db.parse(it) }
            val manifest=mutableMapOf<String,String>()
            val items=opf.getElementsByTagName("item")
            for(i in 0 until items.length){ val n=items.item(i); val id=n.attributes?.getNamedItem("id")?.nodeValue.orEmpty(); val href=n.attributes?.getNamedItem("href")?.nodeValue.orEmpty(); if(id.isNotBlank()) manifest[id]=href }
            val base=opfPath.substringBeforeLast('/',"")
            val out=mutableListOf<String>()
            val refs=opf.getElementsByTagName("itemref")
            for(i in 0 until refs.length){
                val idref=refs.item(i).attributes?.getNamedItem("idref")?.nodeValue.orEmpty()
                val href=manifest[idref].orEmpty(); if(href.isBlank())continue
                val path=if(base.isBlank())href else "$base/$href"
                val entry=zip.getEntry(path)?:continue
                val html=zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val body=Jsoup.parse(html).body().wholeText().trim()
                if(body.isNotBlank())out+=body
            }
            return out
        }
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
