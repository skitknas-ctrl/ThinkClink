package io.github.skitknasctrl.thinkclink

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.widget.FrameLayout
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

/**
 * The two settings screens, built as full-screen dialogs with a fixed button bar at the bottom
 * (an AlertDialog with a long scrolling form hid its buttons on some phones).
 */
object Dialogs {
    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun label(ctx: Context, text: String, size: Float = 14f) = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.DKGRAY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setPadding(0, dp(ctx, 10), 0, dp(ctx, 2))
    }

    /** Full-screen dialog: title, scrolling body, button row. Content stays below the status bar. */
    /** A dialog with the light theme; all its views must be created with dialog.context (the game's theme is dark). */
    private fun newDialog(host: Activity) = Dialog(host, android.R.style.Theme_Material_Light_NoActionBar)

    private fun frame(dialog: Dialog, title: String, body: View, buttons: List<Pair<String, (Dialog) -> Unit>>): Dialog {
        val ctx = dialog.context
        val root = FrameLayout(ctx).apply { setBackgroundColor(Color.WHITE) }
        root.setOnApplyWindowInsetsListener { v, insets -> applyInsets(v, insets); insets }
        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(ctx).apply {
            text = title
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 8))
        })
        column.addView(body.apply { setPadding(dp(ctx, 20), 0, dp(ctx, 20), dp(ctx, 80)) })
        root.addView(ScrollView(ctx).apply { addView(column) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6))
            setBackgroundColor(Color.rgb(240, 240, 244))
        }
        for ((text, action) in buttons) bar.addView(Button(ctx).apply { this.text = text; setOnClickListener { action(dialog) } })
        root.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return dialog
    }

    @Suppress("DEPRECATION")
    private fun applyInsets(view: View, insets: WindowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            view.setPadding(b.left, b.top, b.right, b.bottom)
        } else {
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
    }

    /** Ordinary settings: language, grade, sound, statistics. onSaved gets the new language. */
    fun settings(host: Activity, prefs: SharedPreferences, rules: Rules, language: String, onSaved: (String, Map<String, Int>) -> Unit): Dialog {
        val dialog = newDialog(host)
        val host = dialog.context
        val body = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        body.addView(label(host, T.s("set.language")))
        val langSpinner = Spinner(host).apply {
            adapter = ArrayAdapter(host, android.R.layout.simple_spinner_dropdown_item, T.LANGUAGES.map { it.second })
            setSelection(max(0, T.LANGUAGES.indexOfFirst { it.first == language }))
        }
        body.addView(langSpinner)
        body.addView(label(host, T.s("set.grade")))
        val gradeSpinner = Spinner(host).apply {
            adapter = ArrayAdapter(host, android.R.layout.simple_spinner_dropdown_item, (1..12).map { it.toString() })
            setSelection(rules.grade - 1)
        }
        body.addView(gradeSpinner)
        val sound = Switch(host).apply { text = T.s("set.sound"); isChecked = rules.sound; setPadding(0, dp(host, 18), 0, 0); setTextColor(Color.DKGRAY) }
        body.addView(sound)
        val stats = Switch(host).apply { text = T.s("set.stats"); isChecked = rules.sendStats; setPadding(0, dp(host, 18), 0, 0); setTextColor(Color.DKGRAY) }
        body.addView(stats)
        body.addView(label(host, T.s("set.statsInfo"), 12f))
        return frame(dialog, T.s("set.title"), body, listOf(
            T.s("set.cancel") to { d -> d.dismiss() },
            T.s("set.save") to { d ->
                val lang = T.LANGUAGES[langSpinner.selectedItemPosition].first
                val values = mapOf(
                    "grade" to gradeSpinner.selectedItemPosition + 1,
                    "sound" to if (sound.isChecked) 1 else 0,
                    "sendStats" to if (stats.isChecked) 1 else 0,
                )
                val edit = prefs.edit().putString("language", lang)
                values.forEach { (k, v) -> edit.putInt("set_$k", v) }
                edit.apply()
                onSaved(lang, values)
                d.dismiss()
            },
        ))
    }

    /** Developer settings: every non-user value in SettingsSpec. onSaved gets the complete value map. */
    fun testSettings(host: Activity, prefs: SharedPreferences, rules: Rules, onSaved: (Map<String, Int>) -> Unit): Dialog {
        val dialog = newDialog(host)
        val host = dialog.context
        val body = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        body.addView(label(host, T.s("test.info"), 12f))
        val fields = SettingsSpec.test.map { spec ->
            body.addView(label(host, "${spec.label} (${spec.min}–${spec.max})"))
            EditText(host).apply {
                setText(rules[spec.key].toString())
                setTextColor(Color.BLACK)
                inputType = InputType.TYPE_CLASS_NUMBER
                setSingleLine()
                body.addView(this)
            }
        }
        return frame(dialog, T.s("test.title"), body, listOf(
            T.s("test.defaults") to { _ -> SettingsSpec.test.forEachIndexed { i, spec -> fields[i].setText(spec.default.toString()); fields[i].error = null } },
            T.s("set.cancel") to { d -> d.dismiss() },
            T.s("test.save") to { d ->
                var valid = true
                val values = HashMap(rules.asMap())
                SettingsSpec.test.forEachIndexed { i, spec ->
                    val v = fields[i].text.toString().toIntOrNull()
                    if (v == null || v !in spec.min..spec.max) { fields[i].error = T.f("test.range", spec.min, spec.max); valid = false }
                    else values[spec.key] = v
                }
                if (valid) {
                    val edit = prefs.edit()
                    values.forEach { (k, v) -> edit.putInt("set_$k", v) }
                    edit.apply()
                    onSaved(values)
                    d.dismiss()
                }
            },
        ))
    }

    private fun max(a: Int, b: Int) = if (a > b) a else b
}
