package com.shelly.overlayprompter

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Hebrew onboarding: explains and opens the "display over other apps" settings page. */
class PermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val heebo = resources.getFont(R.font.heebo)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        fun tv(s: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = size; typeface = Typeface.create(heebo, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textDirection = View.TEXT_DIRECTION_RTL; gravity = Gravity.CENTER; setPadding(0, 16, 0, 16)
        }
        root.addView(tv("כדי שהפרומפטר יופיע מעל כל אפליקציה", 24f, true))
        root.addView(tv("צריך לאשר את ההרשאה \"הצגה מעל אפליקציות אחרות\".\n\n1. לחצי על הכפתור למטה\n2. מצאי את \"פרומפטר צף\" ברשימה\n3. הפעילי את המתג\n4. חזרי לאפליקציה", 17f))
        root.addView(Button(this).apply {
            text = "פתיחת הגדרות ההרשאה"; typeface = heebo
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) finish()
    }
}
