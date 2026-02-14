package com.scanx.qrscanner

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.scanx.qrscanner.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)


        settingsManager = SettingsManager(this)

        setupToolbar()
        setupSettings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupSettings() {
        // Theme selection
        updateThemeText()
        binding.btnTheme.setOnClickListener {
            showThemeSelectionDialog()
        }

        // Search Engine selection
        updateSearchEngineText()
        binding.btnSearchEngine.setOnClickListener {
            showSearchEngineSelectionDialog()
        }

        // Beep toggle
        binding.switchBeep.isChecked = settingsManager.isBeepEnabled
        binding.switchBeep.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isBeepEnabled = isChecked
            binding.btnBeepStyle.isEnabled = isChecked
            binding.btnBeepStyle.alpha = if (isChecked) 1.0f else 0.5f
        }

        // Beep Style selection
        updateBeepStyleText()
        binding.btnBeepStyle.isEnabled = settingsManager.isBeepEnabled
        binding.btnBeepStyle.alpha = if (settingsManager.isBeepEnabled) 1.0f else 0.5f
        binding.btnBeepStyle.setOnClickListener {
            showBeepStyleSelectionDialog()
        }

        // About button
        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun showThemeSelectionDialog() {
        val themes = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme)
            .setSingleChoiceItems(themes, settingsManager.theme) { dialog, which ->
                settingsManager.theme = which
                updateThemeText()
                dialog.dismiss()
                // Re-create activity to apply theme immediately
                recreate()
            }
            .show()
    }

    private fun updateThemeText() {
        binding.tvCurrentTheme.text = when (settingsManager.theme) {
            SettingsManager.THEME_LIGHT -> getString(R.string.theme_light)
            SettingsManager.THEME_DARK -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
    }

    private fun showSearchEngineSelectionDialog() {
        val engines = arrayOf(
            SettingsManager.SEARCH_ENGINE_GOOGLE,
            SettingsManager.SEARCH_ENGINE_BING,
            SettingsManager.SEARCH_ENGINE_DUCKDUCKGO,
            SettingsManager.SEARCH_ENGINE_YAHOO
        )

        val currentIndex = engines.indexOf(settingsManager.searchEngine).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search_engine)
            .setSingleChoiceItems(engines, currentIndex) { dialog, which ->
                settingsManager.searchEngine = engines[which]
                updateSearchEngineText()
                dialog.dismiss()
            }
            .show()
    }

    private fun updateSearchEngineText() {
        binding.tvCurrentSearchEngine.text = settingsManager.searchEngine
    }

    private fun showBeepStyleSelectionDialog() {
        val styles = arrayOf(
            getString(R.string.beep_style_standard),
            getString(R.string.beep_style_electronic),
            getString(R.string.beep_style_notification),
            getString(R.string.beep_style_blip)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.beep_sound)
            .setSingleChoiceItems(styles, settingsManager.beepStyle) { dialog, which ->
                settingsManager.beepStyle = which
                updateBeepStyleText()
                dialog.dismiss()
            }
            .show()
    }

    private fun updateBeepStyleText() {
        binding.tvCurrentBeepStyle.text = when (settingsManager.beepStyle) {
            SettingsManager.BEEP_ELECTRONIC -> getString(R.string.beep_style_electronic)
            SettingsManager.BEEP_NOTIFICATION -> getString(R.string.beep_style_notification)
            SettingsManager.BEEP_BLIP -> getString(R.string.beep_style_blip)
            else -> getString(R.string.beep_style_standard)
        }
    }
}
