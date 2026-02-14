package com.scanx.qrscanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.scanx.qrscanner.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)


        setupToolbar()
        displayVersion()
        setupLicenseButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun displayVersion() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            binding.tvVersion.text = getString(R.string.version_template, getString(R.string.version), versionName)
        } catch (e: Exception) {
            binding.tvVersion.text = getString(R.string.version_template, getString(R.string.version), "1.0.0")
        }
    }

    private fun setupLicenseButtons() {
        val dependencies = listOf(
            Dependency(getString(R.string.tech_stack_jetpack), "Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
            Dependency(getString(R.string.tech_stack_material), "Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
            Dependency(getString(R.string.tech_stack_mlkit), getString(R.string.terms_of_service), "https://developers.google.com/ml-kit/terms"),
            Dependency("Gson", "Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
            Dependency("ZXing", "Apache-2.0", "https://github.com/zxing/zxing/blob/master/LICENSE")
        )

        dependencies.forEach { dep ->
            val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.dependency_template, dep.name, dep.license)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8.dpToPx()
                }
                setOnClickListener { openUrl(dep.url) }
            }
            binding.llLicenses.addView(button)
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback or log error
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private data class Dependency(val name: String, val license: String, val url: String)
}
