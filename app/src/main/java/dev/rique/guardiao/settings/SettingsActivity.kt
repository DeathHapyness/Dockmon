package dev.rique.guardiao.settings

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import dev.rique.guardiao.kiosk.Kiosk

class SettingsActivity : Activity() {
    private lateinit var settings: Settings
    private lateinit var exitKioskButton: Button

    private lateinit var url: EditText
    private lateinit var cpu: EditText
    private lateinit var disk: EditText
    private lateinit var temp: EditText
    private lateinit var brightness: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(getSharedPreferences("guardiao", MODE_PRIVATE))
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            setBackgroundColor(Color.parseColor("#0B0B0D"))
            isFocusableInTouchMode = true
        }
        url = field(column, "URL do Glances", settings.glancesUrl, InputType.TYPE_TEXT_VARIATION_URI)
        cpu = field(column, "CPU alta (%)", settings.cpuPercent.toInt().toString(), InputType.TYPE_CLASS_NUMBER)
        disk = field(column, "Disco cheio (%)", settings.diskPercent.toInt().toString(), InputType.TYPE_CLASS_NUMBER)
        temp = field(column, "Temperatura padrão (°C)", settings.defaultTempC.toInt().toString(), InputType.TYPE_CLASS_NUMBER)

        column.addView(label("Brilho"))
        brightness = SeekBar(this).apply {
            max = 100
            progress = (settings.brightness * 100).toInt()
        }
        column.addView(brightness)

        column.addView(button("Salvar") { save(); finish() })
        exitKioskButton = button("Sair do modo kiosk") { Kiosk.exit(this); finish() }
        column.addView(exitKioskButton)
        column.addView(button("Voltar") { finish() })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0B0D"))
            addView(column)
        })
    }

    private fun save() {
        settings.glancesUrl = url.text.toString().trim()
        cpu.text.toString().toDoubleOrNull()?.let { settings.cpuPercent = it }
        disk.text.toString().toDoubleOrNull()?.let { settings.diskPercent = it }
        temp.text.toString().toDoubleOrNull()?.let { settings.defaultTempC = it }
        settings.brightness = (brightness.progress / 100f).coerceAtLeast(0.02f)
    }

    private fun field(parent: LinearLayout, title: String, value: String, type: Int): EditText {
        parent.addView(label(title))
        return EditText(this).apply {
            setText(value)
            inputType = type
            setTextColor(Color.WHITE)
            setSingleLine()
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        }.also { parent.addView(it) }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#99FFFFFF"))
        setPadding(0, 24, 0, 4)
    }

    private fun button(text: String, onClick: (View) -> Unit) = Button(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setOnClickListener(onClick)
    }
}
