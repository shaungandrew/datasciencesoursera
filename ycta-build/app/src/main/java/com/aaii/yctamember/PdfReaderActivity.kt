package com.aaii.yctamember

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

class PdfReaderActivity : ComponentActivity() {
    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var pageIndex = 0
    private lateinit var image: ZoomImageView
    private lateinit var pageText: TextView
    private lateinit var bookmarkButton: Button
    private lateinit var readerRoot: LinearLayout
    private lateinit var canvasFrame: FrameLayout
    private lateinit var bottomBar: LinearLayout
    private var currentBitmap: Bitmap? = null
    private var darkMode = false
    private lateinit var path: String
    private lateinit var title: String
    private val prefs by lazy { getSharedPreferences("panyar_reader_pdf", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        path = intent.getStringExtra("path").orEmpty()
        title = intent.getStringExtra("title").orEmpty()
        darkMode = prefs.getBoolean(themeKey(), false)

        readerRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor())
        }

        val topBar = buildTopBar()
        readerRoot.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        canvasFrame = FrameLayout(this).apply {
            setBackgroundColor(canvasColor())
        }
        image = ZoomImageView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            onSwipeLeft = { nextPage() }
            onSwipeRight = { prevPage() }
        }
        canvasFrame.addView(image, FrameLayout.LayoutParams(-1, -1).apply {
            gravity = Gravity.CENTER
        })
        readerRoot.addView(canvasFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomBar = buildBottomBar()
        readerRoot.addView(bottomBar, LinearLayout.LayoutParams(-1, -2))

        setContentView(readerRoot)
        applyInsets(topBar, bottomBar)

        runCatching {
            descriptor = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor!!)
            val saved = prefs.getInt(lastPageKey(), 0)
            pageIndex = saved.coerceIn(0, maxOf(0, (renderer?.pageCount ?: 1) - 1))
            render()
        }.onFailure {
            Toast.makeText(this, "PDF could not open: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildTopBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(8))
            setBackgroundColor(toolbarColor())

            val row1 = LinearLayout(this@PdfReaderActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            row1.addView(Button(this@PdfReaderActivity).apply {
                text = "← Library"
                isAllCaps = false
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(-2, -2))

            row1.addView(TextView(this@PdfReaderActivity).apply {
                text = title.ifBlank { "PDF Reader" }
                textSize = 18f
                maxLines = 1
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(fgColor())
            }, LinearLayout.LayoutParams(0, -2, 1f))

            row1.addView(Button(this@PdfReaderActivity).apply {
                text = if (darkMode) "Day" else "Dark"
                isAllCaps = false
                setOnClickListener {
                    darkMode = !darkMode
                    prefs.edit().putBoolean(themeKey(), darkMode).apply()
                    recreate()
                }
            }, LinearLayout.LayoutParams(-2, -2))

            addView(row1)

            val row2 = LinearLayout(this@PdfReaderActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, 0)
            }

            row2.addView(Button(this@PdfReaderActivity).apply {
                text = "Go to page"
                isAllCaps = false
                setOnClickListener { showGoToPage() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })

            row2.addView(TextView(this@PdfReaderActivity).apply {
                text = "Pinch / Double-tap Zoom • Swipe Page"
                gravity = Gravity.CENTER
                textSize = 11.5f
                setTextColor(subtleColor())
            }, LinearLayout.LayoutParams(0, -2, 1.5f))

            row2.addView(Button(this@PdfReaderActivity).apply {
                text = "Fit"
                isAllCaps = false
                setOnClickListener { image.resetZoom() }
            }, LinearLayout.LayoutParams(0, -2, 0.8f).apply { marginStart = dp(4) })

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

            addView(Button(this@PdfReaderActivity).apply {
                text = "‹ Prev"
                isAllCaps = false
                setOnClickListener { prevPage() }
            }, LinearLayout.LayoutParams(0, -2, 1f))

            pageText = TextView(this@PdfReaderActivity).apply {
                gravity = Gravity.CENTER
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(fgColor())
            }
            addView(pageText, LinearLayout.LayoutParams(0, -2, 1.05f))

            addView(Button(this@PdfReaderActivity).apply {
                text = "Next ›"
                isAllCaps = false
                setOnClickListener { nextPage() }
            }, LinearLayout.LayoutParams(0, -2, 1f))

            bookmarkButton = Button(this@PdfReaderActivity).apply {
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
        ViewCompat.requestApplyInsets(readerRoot)
    }

    private fun prevPage() {
        if (pageIndex > 0) {
            pageIndex--
            render()
        }
    }

    private fun nextPage() {
        val count = renderer?.pageCount ?: 0
        if (pageIndex + 1 < count) {
            pageIndex++
            render()
        }
    }

    private fun showGoToPage() {
        val count = renderer?.pageCount ?: return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "1 - $count"
            setText((pageIndex + 1).toString())
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Go to page")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val requested = input.text.toString().toIntOrNull()
                if (requested != null && requested in 1..count) {
                    pageIndex = requested - 1
                    render()
                } else {
                    Toast.makeText(this, "Page must be between 1 and $count", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleBookmark() {
        val set = prefs.getStringSet(bookmarksKey(), emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = pageIndex.toString()
        if (set.contains(key)) {
            set.remove(key)
            Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show()
        } else {
            set.add(key)
            Toast.makeText(this, "Bookmarked page ${pageIndex + 1}", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putStringSet(bookmarksKey(), set).apply()
        updateBookmarkButton()
    }

    private fun updateBookmarkButton() {
        val bookmarked = prefs.getStringSet(bookmarksKey(), emptySet())?.contains(pageIndex.toString()) == true
        bookmarkButton.text = if (bookmarked) "★ Bookmarked" else "☆ Bookmark"
    }

    private fun render() {
        val r = renderer ?: return
        if (r.pageCount == 0) return

        val page = r.openPage(pageIndex)
        val maxWidth = resources.displayMetrics.widthPixels.coerceAtLeast(720)
        val maxHeight = resources.displayMetrics.heightPixels.coerceAtLeast(1000)
        val baseScale = minOf(
            maxWidth.toFloat() / page.width,
            maxHeight.toFloat() / page.height
        ).coerceAtLeast(1f)

        val width = (page.width * baseScale).toInt().coerceAtLeast(720)
        val height = (page.height * baseScale).toInt().coerceAtLeast(900)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        currentBitmap?.takeIf { !it.isRecycled }?.recycle()
        currentBitmap = bitmap
        image.setImageBitmap(bitmap)
        image.post { image.resetZoom() }

        pageText.text = "Page ${pageIndex + 1} / ${r.pageCount}"
        prefs.edit().putInt(lastPageKey(), pageIndex).apply()
        updateBookmarkButton()
    }

    private fun lastPageKey() = "last_${path.hashCode()}"
    private fun bookmarksKey() = "bookmarks_${path.hashCode()}"
    private fun themeKey() = "dark_${path.hashCode()}"

    private fun bgColor() =
        if (darkMode) Color.parseColor("#11161B") else Color.parseColor("#EEF2F5")

    private fun canvasColor() =
        if (darkMode) Color.parseColor("#0B0F13") else Color.parseColor("#DDE3E8")

    private fun toolbarColor() =
        if (darkMode) Color.parseColor("#1B232B") else Color.WHITE

    private fun fgColor() =
        if (darkMode) Color.WHITE else Color.parseColor("#173A58")

    private fun subtleColor() =
        if (darkMode) Color.parseColor("#B0BEC8") else Color.parseColor("#647A8D")

    override fun onDestroy() {
        currentBitmap?.takeIf { !it.isRecycled }?.recycle()
        renderer?.close()
        descriptor?.close()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
