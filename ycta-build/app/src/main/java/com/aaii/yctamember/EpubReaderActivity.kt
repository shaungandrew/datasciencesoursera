package com.aaii.yctamember

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

class EpubReaderActivity : ComponentActivity() {
    private lateinit var text: TextView
    private lateinit var page: TextView
    private lateinit var bookmarkButton: Button
    private lateinit var scroll: ScrollView
    private lateinit var root: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private var chapters: List<String> = emptyList()
    private var index = 0
    private var fontSize = 18f
    private var darkMode = false
    private lateinit var path: String
    private lateinit var title: String
    private val prefs by lazy { getSharedPreferences("panyar_reader_epub", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        path = intent.getStringExtra("path").orEmpty()
        title = intent.getStringExtra("title").orEmpty()
        darkMode = prefs.getBoolean(themeKey(), false)
        fontSize = prefs.getFloat(fontKey(), 18f).coerceIn(14f, 30f)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor())
        }

        val topBar = buildTopBar()
        root.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        text = TextView(this).apply {
            textSize = fontSize
            setTextColor(fgColor())
            setLineSpacing(dp(3).toFloat(), 1.18f)
            setPadding(dp(18), dp(14), dp(18), dp(24))
            setTextIsSelectable(true)
        }

        scroll = ScrollView(this).apply {
            setBackgroundColor(bgColor())
            addView(text)
        }

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (
                    abs(dx) > abs(dy) * 1.4f &&
                    abs(dx) > resources.displayMetrics.widthPixels * 0.20f &&
                    abs(velocityX) > 500
                ) {
                    if (dx < 0) nextChapter() else prevChapter()
                    return true
                }
                return false
            }
        })

        scroll.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomBar = buildBottomBar()
        root.addView(bottomBar, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
        applyInsets(topBar, bottomBar)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { loadEpub(File(path)) }
            }
            result.onSuccess {
                chapters = it
                if (chapters.isEmpty()) {
                    text.text = "EPUB contains no readable chapters."
                } else {
                    index = prefs.getInt(lastChapterKey(), 0).coerceIn(0, chapters.lastIndex)
                    showChapter()
                }
            }.onFailure {
                text.text = "EPUB could not open: ${it.message}"
            }
        }
    }

    private fun buildTopBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(8))
            setBackgroundColor(toolbarColor())

            val row1 = LinearLayout(this@EpubReaderActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            row1.addView(Button(this@EpubReaderActivity).apply {
                text = "← Library"
                isAllCaps = false
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(-2, -2))

            row1.addView(TextView(this@EpubReaderActivity).apply {
                text = title.ifBlank { "EPUB Reader" }
                maxLines = 1
                textSize = 18f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(fgColor())
            }, LinearLayout.LayoutParams(0, -2, 1f))

            row1.addView(Button(this@EpubReaderActivity).apply {
                text = if (darkMode) "Day" else "Dark"
                isAllCaps = false
                setOnClickListener {
                    darkMode = !darkMode
                    prefs.edit().putBoolean(themeKey(), darkMode).apply()
                    recreate()
                }
            })

            addView(row1)

            val row2 = LinearLayout(this@EpubReaderActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            }

            row2.addView(Button(this@EpubReaderActivity).apply {
                text = "A−"
                isAllCaps = false
                setOnClickListener { changeFont(-1f) }
            }, LinearLayout.LayoutParams(0, -2, 1f))

            row2.addView(TextView(this@EpubReaderActivity).apply {
                text = "Native EPUB • Swipe left / right"
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(subtleColor())
            }, LinearLayout.LayoutParams(0, -2, 2f))

            row2.addView(Button(this@EpubReaderActivity).apply {
                text = "A+"
                isAllCaps = false
                setOnClickListener { changeFont(1f) }
            }, LinearLayout.LayoutParams(0, -2, 1f))

            addView(row2)
        }
    }

    private fun buildBottomBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundColor(toolbarColor())
            elevation = dp(10).toFloat()

            addView(Button(this@EpubReaderActivity).apply {
                text = "‹ Prev"
                isAllCaps = false
                setOnClickListener { prevChapter() }
            }, LinearLayout.LayoutParams(0, -2, 1f))

            page = TextView(this@EpubReaderActivity).apply {
                gravity = Gravity.CENTER
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(fgColor())
            }
            addView(page, LinearLayout.LayoutParams(0, -2, 1.05f))

            addView(Button(this@EpubReaderActivity).apply {
                text = "Next ›"
                isAllCaps = false
                setOnClickListener { nextChapter() }
            }, LinearLayout.LayoutParams(0, -2, 1f))

            bookmarkButton = Button(this@EpubReaderActivity).apply {
                text = "☆ Bookmark"
                isAllCaps = false
                setOnClickListener { toggleBookmark() }
            }
            addView(bookmarkButton, LinearLayout.LayoutParams(0, -2, 1.25f))
        }
    }

    private fun applyInsets(top: View, bottom: View) {
        ViewCompat.setOnApplyWindowInsetsListener(top) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(dp(10), dp(6) + bars.top, dp(10), dp(8))
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottom) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(dp(8) + bars.left, dp(7), dp(8) + bars.right, dp(7) + bars.bottom)
            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun prevChapter() {
        if (index > 0) {
            index--
            showChapter()
        }
    }

    private fun nextChapter() {
        if (index + 1 < chapters.size) {
            index++
            showChapter()
        }
    }

    private fun changeFont(delta: Float) {
        fontSize = (fontSize + delta).coerceIn(14f, 30f)
        text.textSize = fontSize
        prefs.edit().putFloat(fontKey(), fontSize).apply()
    }

    private fun toggleBookmark() {
        val set = prefs.getStringSet(bookmarksKey(), emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = index.toString()

        if (set.contains(key)) {
            set.remove(key)
            Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show()
        } else {
            set.add(key)
            Toast.makeText(this, "Bookmark chapter ${index + 1}", Toast.LENGTH_SHORT).show()
        }

        prefs.edit().putStringSet(bookmarksKey(), set).apply()
        updateBookmarkButton()
    }

    private fun showChapter() {
        if (chapters.isEmpty()) return
        text.text = Html.fromHtml(chapters[index], Html.FROM_HTML_MODE_LEGACY)
        text.textSize = fontSize
        page.text = "Chapter ${index + 1} / ${chapters.size}"
        scroll.post { scroll.scrollTo(0, 0) }
        prefs.edit().putInt(lastChapterKey(), index).apply()
        updateBookmarkButton()
    }

    private fun updateBookmarkButton() {
        val bookmarked =
            prefs.getStringSet(bookmarksKey(), emptySet())?.contains(index.toString()) == true
        bookmarkButton.text = if (bookmarked) "★ Bookmarked" else "☆ Bookmark"
    }

    private fun loadEpub(file: File): List<String> {
        ZipFile(file).use { zip ->
            val container =
                zip.getEntry("META-INF/container.xml") ?: error("EPUB container.xml missing.")

            val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = zip.getInputStream(container).use { db.parse(it) }

            val rootfile =
                doc.getElementsByTagName("rootfile").item(0) ?: error("EPUB package path missing.")
            val opfPath =
                rootfile.attributes.getNamedItem("full-path")?.nodeValue
                    ?: error("EPUB package path missing.")

            val opfEntry = zip.getEntry(opfPath) ?: error("EPUB package missing.")
            val opf = zip.getInputStream(opfEntry).use { db.parse(it) }

            val manifest = mutableMapOf<String, String>()
            val items = opf.getElementsByTagName("item")

            for (i in 0 until items.length) {
                val n = items.item(i)
                val id = n.attributes?.getNamedItem("id")?.nodeValue.orEmpty()
                val href = n.attributes?.getNamedItem("href")?.nodeValue.orEmpty()
                if (id.isNotBlank()) manifest[id] = href
            }

            val base = opfPath.substringBeforeLast('/', "")
            val out = mutableListOf<String>()
            val refs = opf.getElementsByTagName("itemref")

            for (i in 0 until refs.length) {
                val idref =
                    refs.item(i).attributes?.getNamedItem("idref")?.nodeValue.orEmpty()
                val href = manifest[idref].orEmpty()
                if (href.isBlank()) continue

                val chapterPath = if (base.isBlank()) href else "$base/$href"
                val entry = zip.getEntry(chapterPath) ?: continue
                val rawHtml =
                    zip.getInputStream(entry).bufferedReader().use { it.readText() }

                val parsed = Jsoup.parse(rawHtml)
                parsed.select("script,style,noscript").remove()
                val bodyHtml = parsed.body().html().trim()

                if (bodyHtml.isNotBlank()) out += bodyHtml
            }

            return out
        }
    }

    private fun lastChapterKey() = "last_${path.hashCode()}"
    private fun bookmarksKey() = "bookmarks_${path.hashCode()}"
    private fun themeKey() = "dark_${path.hashCode()}"
    private fun fontKey() = "font_${path.hashCode()}"

    private fun bgColor() =
        if (darkMode) Color.parseColor("#11161B") else Color.parseColor("#FAFAF8")

    private fun toolbarColor() =
        if (darkMode) Color.parseColor("#1B232B") else Color.WHITE

    private fun fgColor() =
        if (darkMode) Color.parseColor("#EEF3F6") else Color.parseColor("#263642")

    private fun subtleColor() =
        if (darkMode) Color.parseColor("#B0BEC8") else Color.parseColor("#647A8D")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
