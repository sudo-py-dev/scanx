package com.scanx.qrscanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.scanx.qrscanner.databinding.ActivityErrorBinding

class ErrorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityErrorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: ""
        val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: getString(R.string.error_unexpected)

        binding.tvErrorMessage.text = errorMessage
        binding.tvStackTrace.text = stackTrace

        binding.btnRestart.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        }

        binding.btnCopyError.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.clipboard_label_error_log), "$errorMessage\n\n$stackTrace")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.error_log_copied), Toast.LENGTH_SHORT).show()
        }

        binding.btnToggleDetails.setOnClickListener {
            if (binding.scrollViewStackTrace.visibility == View.VISIBLE) {
                binding.scrollViewStackTrace.visibility = View.GONE
                binding.btnToggleDetails.text = getString(R.string.show_details)
                binding.btnToggleDetails.setIconResource(R.drawable.ic_expand_more)
            } else {
                binding.scrollViewStackTrace.visibility = View.VISIBLE
                binding.btnToggleDetails.text = getString(R.string.close) // Reuse close or add hide_details
                binding.btnToggleDetails.setIconResource(R.drawable.ic_expand_less)
            }
        }
    }

    companion object {
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
    }
}
