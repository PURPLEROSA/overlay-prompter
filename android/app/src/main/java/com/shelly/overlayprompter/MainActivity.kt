package com.shelly.overlayprompter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

/** Script editor, saved scripts, paste / .txt import, share-intent receiver. */
class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var title: EditText
    private lateinit var editor: EditText
    private lateinit var list: LinearLayout
    private var editingId: Long = 0
    private lateinit var heebo: Typeface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        heebo = resources.getFont(R.font.heebo)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(32, 32, 32, 32)
        }
        title = EditText(this).apply { hint = "כותרת"; typeface = heebo; textDirection = View.TEXT_DIRECTION_RTL; setSingleLine() }
        editor = EditText(this).apply {
            hint = "הדביקי או כתבי כאן את הטקסט…"
            typeface = heebo; textSize = 18f
            gravity = Gravity.TOP or Gravity.START
            textDirection = View.TEXT_DIRECTION_RTL
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(store.currentText)
        }
        root.addView(title)
        root.addView(editor, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val row1 = LinearLayout(this)
        row1.addView(btn("▶ הפעלה מעל אפליקציות") { startOverlay() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 2f))
        row1.addView(btn("שמירה") { saveScript() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(row1)
        val row2 = LinearLayout(this)
        row2.addView(btn("הדבקה") { paste() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row2.addView(btn("ייבוא ‎.txt") { importTxt() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row2.addView(btn("חדש") { editingId = 0; title.setText(""); editor.setText("") }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(row2)
        root.addView(TextView(this).apply { text = "תסריטים שמורים (לחיצה ארוכה למחיקה)"; typeface = heebo; setPadding(0, 16, 0, 8) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 0.6f))
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        handleShare(intent)
        refreshList()
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleShare(intent) }

    override fun onPause() { super.onPause(); store.currentText = editor.text.toString() }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            i.getStringExtra(Intent.EXTRA_TEXT)?.let { editingId = 0; editor.setText(it) }
            i.getStringExtra(Intent.EXTRA_SUBJECT)?.let { title.setText(it) }
        }
    }

    private fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; typeface = heebo; isAllCaps = false; setOnClickListener { onClick() }
    }

    private fun startOverlay() {
        store.currentText = editor.text.toString()
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(this, PermissionActivity::class.java)); return
        }
        val i = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW)
        try {
            startForegroundService(i)
            moveTaskToBack(true)
        } catch (e: Exception) {
            Toast.makeText(this, "לא ניתן להפעיל את הפרומפטר: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveScript() {
        val t = editor.text.toString()
        if (t.isBlank()) return
        if (editingId == 0L) editingId = System.currentTimeMillis()
        val name = title.text.toString().ifBlank { t.lineSequence().first().take(40) }
        store.save(Script(editingId, name, t))
        Toast.makeText(this, "נשמר", Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun paste() {
        val cm = getSystemService(ClipboardManager::class.java)
        val txt = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (txt.isNullOrEmpty()) Toast.makeText(this, "הלוח ריק", Toast.LENGTH_SHORT).show()
        else editor.setText(txt)
    }

    private fun importTxt() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/*"
        }, 42)
    }

    @Deprecated("simple framework API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == RESULT_OK) data?.data?.let { uri ->
            val txt = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            if (txt != null) { editingId = 0; editor.setText(txt.removePrefix("\uFEFF")) }
        }
    }

    private fun refreshList() {
        list.removeAllViews()
        store.scripts().forEach { s ->
            list.addView(TextView(this).apply {
                text = "• ${s.title}"; textSize = 17f; typeface = heebo; setPadding(8, 20, 8, 20)
                textDirection = View.TEXT_DIRECTION_RTL
                setOnClickListener { editingId = s.id; title.setText(s.title); editor.setText(s.text) }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity).setMessage("למחוק את \"${s.title}\"?")
                        .setPositiveButton("מחיקה") { _, _ -> store.delete(s.id); refreshList() }
                        .setNegativeButton("ביטול", null).show(); true
                }
            })
        }
    }
}
