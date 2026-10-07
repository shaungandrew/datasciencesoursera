package com.aaii.yctamember

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryActivity : ComponentActivity() {
    private lateinit var api: LibraryApi
    private lateinit var status: TextView
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private lateinit var librarySpinner: Spinner
    private lateinit var loginPanel: LinearLayout
    private lateinit var activation: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText

    private val libraryCode: String
        get() = if (librarySpinner.selectedItemPosition == 1) "english" else "myanmar"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = LibraryApi(this)
        window.statusBarColor = Color.parseColor("#102B46")
        window.navigationBarColor = Color.WHITE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(28))
            setBackgroundColor(Color.parseColor("#F2F5F8"))
        }

        root.addView(header())
        root.addView(librarySwitcher())
        root.addView(loginBox())
        root.addView(searchBox())
        root.addView(quickActions())

        status = TextView(this).apply {
            text = "Library source: https://www.aaii.asia/edu/lib"
            textSize = 12f
            setTextColor(Color.parseColor("#64798D"))
            setPadding(dp(2), dp(10), dp(2), dp(8))
        }
        root.addView(status)

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content)

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
        if (api.hasToken()) {
            loginPanel.visibility = View.GONE
            loadBooks("")
        } else {
            loginPanel.visibility = View.VISIBLE
            status.text = "Library Login / Activation Code required."
            empty("Enter Activation Code OR Username + Password, then tap LOGIN / ACTIVATE.")
        }
    }

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(Button(this@LibraryActivity).apply {
            text = "← YCTA"
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { finish() }
        })
        addView(TextView(this@LibraryActivity).apply {
            text = "YCTA LIBRARY MODULE"
            textSize = 12f
            letterSpacing = 0.12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B739E"))
        })
        addView(TextView(this@LibraryActivity).apply {
            text = "Native eLibrary"
            textSize = 29f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#183D5E"))
        })
        addView(TextView(this@LibraryActivity).apply {
            text = "Panyar Hub-style native search, categories, writers and readers"
            textSize = 13f
            setTextColor(Color.parseColor("#687E91"))
            setPadding(0, dp(3), 0, dp(10))
        })
    }

    private fun librarySwitcher(): View {
        val card = card()
        card.addView(label("Choose Library"))
        librarySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@LibraryActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Myanmar Library", "English Library"))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (::content.isInitialized && api.hasToken()) loadBooks(search.text.toString())
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        }
        card.addView(librarySpinner)
        return card
    }

    private fun loginBox(): View {
        loginPanel = card()
        loginPanel.addView(TextView(this).apply {
            text = "Native Reader Login"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E547D"))
        })
        loginPanel.addView(TextView(this).apply {
            text = "Use Activation Code OR Username + Password when the library API requires login."
            textSize = 12f
            setTextColor(Color.parseColor("#6C8091"))
            setPadding(0, dp(4), 0, dp(8))
        })
        activation = edit("Activation Code")
        username = edit("Username")
        password = edit("Password").apply { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        loginPanel.addView(activation)
        loginPanel.addView(username)
        loginPanel.addView(password)
        loginPanel.addView(Button(this).apply {
            text = "LOGIN / ACTIVATE"
            isAllCaps = false
            setOnClickListener { doLogin() }
        })
        return loginPanel
    }

    private fun searchBox(): View {
        val card = card()
        search = edit("Book Title / Writer / Category / Keyword").apply {
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { loadBooks(text.toString()); true } else false
            }
        }
        card.addView(search)
        card.addView(Button(this).apply {
            text = "Search Library"
            isAllCaps = false
            setOnClickListener { loadBooks(search.text.toString()) }
        })
        return card
    }

    private fun quickActions(): View {
        val card = card()
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(action("All Books") { loadBooks("") }, LinearLayout.LayoutParams(0, -2, 1f))
        row1.addView(action("Categories") { loadCategories() }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row1)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(action("Writers") { loadAuthors() }, LinearLayout.LayoutParams(0, -2, 1f))
        row2.addView(action("Audio Books") { loadAudio() }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row2)
        card.addView(action("Sync / Refresh") { loadBooks(search.text.toString()) })
        return card
    }

    private fun doLogin() {
        status.text = "Logging in…"
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { api.login(activation.text.toString().trim(), username.text.toString().trim(), password.text.toString()) }
            }
            r.onSuccess {
                loginPanel.visibility = View.GONE
                status.text = "Library login ready."
                loadBooks(search.text.toString())
            }.onFailure { status.text = "Login failed: ${it.message}" }
        }
    }

    private fun loadBooks(query: String, categoryId: String = "", authorId: String = "") {
        if (!::content.isInitialized) return
        if (!api.hasToken()) {
            loginPanel.visibility = View.VISIBLE
            content.removeAllViews()
            status.text = "Library Login / Activation Code required."
            empty("Enter Activation Code OR Username + Password first.")
            return
        }
        content.removeAllViews()
        status.text = "Loading native books…"
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api.fetchBooks(libraryCode, query.trim(), categoryId, authorId) } }
            r.onSuccess { books ->
                status.text = "${books.size} book(s) • ${if (libraryCode == "english") "English" else "Myanmar"} Library"
                if (books.isEmpty()) empty("No books returned by the API.") else books.forEach { content.addView(bookCard(it)) }
            }.onFailure(::handleError)
        }
    }

    private fun loadCategories() {
        if (!api.hasToken()) {
            loginPanel.visibility = View.VISIBLE
            content.removeAllViews()
            status.text = "Library Login / Activation Code required."
            empty("Login first to load categories.")
            return
        }
        content.removeAllViews(); status.text = "Loading categories…"
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api.fetchCategories(libraryCode) } }
            r.onSuccess { items ->
                status.text = "${items.size} categories"
                if (items.isEmpty()) empty("No categories returned.") else items.forEach { item ->
                    content.addView(namedCard(item.name, item.count) { loadBooks("", categoryId = item.id) })
                }
            }.onFailure(::handleError)
        }
    }

    private fun loadAuthors() {
        if (!api.hasToken()) {
            loginPanel.visibility = View.VISIBLE
            content.removeAllViews()
            status.text = "Library Login / Activation Code required."
            empty("Login first to load writers.")
            return
        }
        content.removeAllViews(); status.text = "Loading writers…"
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api.fetchAuthors(libraryCode) } }
            r.onSuccess { items ->
                status.text = "${items.size} writers"
                if (items.isEmpty()) empty("No writers returned.") else items.forEach { item ->
                    content.addView(namedCard(item.name, item.count) { loadBooks("", authorId = item.id) })
                }
            }.onFailure(::handleError)
        }
    }

    private fun loadAudio() {
        if (!api.hasToken()) {
            loginPanel.visibility = View.VISIBLE
            content.removeAllViews()
            status.text = "Library Login / Activation Code required."
            empty("Login first to load audio books.")
            return
        }
        content.removeAllViews(); status.text = "Loading audio books…"
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api.fetchAudio(libraryCode) } }
            r.onSuccess { items ->
                status.text = "${items.size} audio item(s)"
                if (items.isEmpty()) empty("No audio books returned.") else items.forEach { item ->
                    content.addView(namedCard(item.title, item.type) {
                        if (item.url.isNotBlank()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                    })
                }
            }.onFailure(::handleError)
        }
    }

    private fun bookCard(book: LibraryApi.Book): View {
        val card = card()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#E4EBF1"))
        }
        row.addView(image, LinearLayout.LayoutParams(dp(84), dp(116)).apply { marginEnd = dp(12) })
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(TextView(this).apply { text = book.title; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#173F61")) })
        if (book.author.isNotBlank()) info.addView(small("Writer: ${book.author}"))
        if (book.category.isNotBlank()) info.addView(small("Category: ${book.category}"))
        if (book.fileType.isNotBlank()) info.addView(small(book.fileType.uppercase()))
        info.addView(Button(this).apply {
            text = "Book Information / Reader"
            isAllCaps = false
            setOnClickListener { openBook(book) }
        })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row)
        val cover = book.coverUrl.ifBlank { if (book.id.isBlank()) "" else api.coverUrl(book.id) }
        if (cover.isNotBlank()) lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) { api.fetchImage(cover) }
            if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { image.setImageBitmap(it) }
        }
        return card
    }

    private fun openBook(book: LibraryApi.Book) {
        startActivity(Intent(this, BookDetailActivity::class.java).apply {
            putExtra("id", book.id); putExtra("title", book.title); putExtra("author", book.author)
            putExtra("category", book.category); putExtra("description", book.description); putExtra("type", book.fileType)
            putExtra("cover", book.coverUrl); putExtra("download", book.downloadUrl); putExtra("raw", book.rawUrl)
        })
    }

    private fun namedCard(title: String, meta: String, click: () -> Unit): View = card().apply {
        isClickable = true; isFocusable = true; setOnClickListener { click() }
        addView(TextView(this@LibraryActivity).apply { text = title; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#173F61")) })
        if (meta.isNotBlank()) addView(small(meta))
    }

    private fun handleError(t: Throwable) {
        status.text = t.message ?: "Library error"
        if (t is LibraryApi.AuthException) loginPanel.visibility = View.VISIBLE
        empty(if (t is LibraryApi.AuthException) "Login / Activation Code is required for this library source." else "Could not load library data.")
    }

    private fun empty(text: String) { content.addView(TextView(this).apply { this.text = text; setPadding(dp(6), dp(16), dp(6), dp(16)); setTextColor(Color.parseColor("#657A8E")) }) }
    private fun action(text: String, click: () -> Unit) = Button(this).apply { this.text = text; isAllCaps = false; setOnClickListener { click() } }
    private fun label(text: String) = TextView(this).apply { this.text = text; textSize = 12f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#536C80")) }
    private fun small(text: String) = TextView(this).apply { this.text = text; textSize = 13f; setTextColor(Color.parseColor("#667B8D")); setPadding(0, dp(3), 0, 0) }
    private fun edit(hintText: String) = EditText(this).apply { hint = hintText; textSize = 15f; setPadding(dp(10), dp(8), dp(10), dp(8)); background = rounded(Color.parseColor("#F8FAFC"), 12, "#D6E0E9", 1) }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13)); elevation = dp(2).toFloat(); background = rounded(Color.WHITE, 16, "#D8E2EC", 1); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) } }
    private fun rounded(fill: Int, radius: Int, stroke: String, width: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(radius).toFloat(); if (width > 0) setStroke(dp(width), Color.parseColor(stroke)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
