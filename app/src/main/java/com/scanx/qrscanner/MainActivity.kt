package com.scanx.qrscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.scanx.qrscanner.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchScanner()
        } else {
             Toast.makeText(
                this,
            getString(R.string.permission_required_message),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.btnScan.setOnClickListener {
            checkCameraPermissionAndScan()
        }

        binding.btnCreate.setOnClickListener {
            val intent = Intent(this, CreateQrActivity::class.java)
            startActivity(intent)
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, ScanHistoryActivity::class.java))
        }

        binding.btnLanguage.setOnClickListener {
            showLanguageDialog()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun showLanguageDialog() {
        LanguageBottomSheetFragment().show(supportFragmentManager, LanguageBottomSheetFragment.TAG)
    }

    private fun setLocale(languageCode: String) {
        val appLocale = androidx.core.os.LocaleListCompat.forLanguageTags(languageCode)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
        Toast.makeText(this, getString(R.string.language_updated), Toast.LENGTH_SHORT).show()
    }

    private fun checkCameraPermissionAndScan() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchScanner()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(
                    this,
                    getString(R.string.camera_access_denied_title),
                    Toast.LENGTH_LONG
                ).show()
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchScanner() {
        try {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_start_scanner, e.message), Toast.LENGTH_LONG).show()
        }
    }
}
