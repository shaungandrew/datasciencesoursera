package com.aaii.yctamember

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookDetailActivity : ComponentActivity() {
    private lateinit var api: LibraryApi
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = LibraryApi(this)
        val id = intent.getStringExtra("id").orEmpty()
        val title = intent.getStringExtra("title").orEmpty()
        val author = intent.getStringExtra("author").orEmpty()
        val category = intent.getStringExtra("category").orEmpty()
        val description = intent.getStringExtra("description").orEmpty()
        val type = intent.getStringExtra("type").orEmpty()
        val cover = intent.getStringExtra("cover").orEmpty()
        val download = intent.getStringExtra("download").orEmpty()
        val raw = intent.getStringExtra("raw").orEmpty()
        val book = LibraryApi.Book(id,title,author,category,description,type,cover,download,raw)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
            setBackgroundColor(Color.parseColor("#F2F5F8"))
        }
        root.addView(Button(this).apply { text = "← Library"; isAllCaps = false; gravity = Gravity.START; setOnClickListener { finish() } })
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Color.parseColor("#E0E8EF")) }
        root.addView(image, LinearLayout.LayoutParams(-1, dp(260)))
        root.addView(TextView(this).apply { text = title.ifBlank { "Book Information" }; textSize = 26f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#173F61")); setPadding(0, dp(14), 0, dp(6)) })
        if (author.isNotBlank()) root.addView(info("Writer", author))
        if (category.isNotBlank()) root.addView(info("Category", category))
        if (type.isNotBlank()) root.addView(info("Format", type.uppercase()))
        if (description.isNotBlank()) root.addView(TextView(this).apply { text = description; textSize = 15f; setTextColor(Color.parseColor("#40596D")); setPadding(0, dp(12), 0, dp(12)) })

        status = TextView(this).apply { text = "Native Reader ready"; textSize = 12f; setTextColor(Color.parseColor("#667B8D")); setPadding(0, dp(8), 0, dp(8)) }
        root.addView(status)
        root.addView(Button(this).apply { text = "READ BOOK"; isAllCaps = false; setOnClickListener { readBook(book) } })

        setContentView(ScrollView(this).apply { addView(root) })

        val coverUrl = cover.ifBlank { if (id.isBlank()) "" else api.coverUrl(id) }
        if (coverUrl.isNotBlank()) lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) { api.fetchImage(coverUrl) }
            if (bytes != null) BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.let { image.setImageBitmap(it) }
        }
    }

    private fun readBook(book: LibraryApi.Book) {
        status.text = "Downloading / preparing native reader…"
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api.downloadBook(book) } }
            r.onSuccess { (file, ext) ->
                when (ext) {
                    "pdf" -> startActivity(Intent(this@BookDetailActivity, PdfReaderActivity::class.java).putExtra("path", file.absolutePath).putExtra("title", book.title))
                    "epub" -> startActivity(Intent(this@BookDetailActivity, EpubReaderActivity::class.java).putExtra("path", file.absolutePath).putExtra("title", book.title))
                    else -> status.text = "Unsupported book file type returned by server."
                }
            }.onFailure { status.text = "Reader error: ${it.message}" }
        }
    }

    private fun info(label:String,value:String)=TextView(this).apply { text = "$label: $value"; textSize=14f; setTextColor(Color.parseColor("#536C80")); setPadding(0,dp(2),0,dp(2)) }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
