package com.scanx.qrscanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scanx.qrscanner.databinding.ActivityScanHistoryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class ScanHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanHistoryBinding
    private lateinit var historyRepository: HistoryRepository
    private lateinit var adapter: ScanHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)


        historyRepository = HistoryRepository(this)
        
        lifecycleScope.launch {
            val results = historyRepository.getHistory()
            binding.toolbar.title = resources.getQuantityString(R.plurals.codes_scanned_count, results.size, results.size)
            binding.toolbar.setNavigationOnClickListener { finish() }
            refreshHistory()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_all -> {
                showClearHistoryDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showClearHistoryDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_history)
            .setMessage(R.string.clear_history_confirmation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { dialog, _ ->
                lifecycleScope.launch {
                    historyRepository.clearHistory()
                    refreshHistory()
                    Toast.makeText(this@ScanHistoryActivity, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            .show()
    }

    private fun refreshHistory() {
        lifecycleScope.launch {
            val results = historyRepository.getHistory()
            binding.toolbar.title = resources.getQuantityString(R.plurals.codes_scanned_count, results.size, results.size)
            
            processHistoryItems(results)
        }
    }

    private fun processHistoryItems(results: List<BarcodeResult>) {
        
        // No-op for bar card as it's removed

        // Group into HistoryItems
        val historyItems = mutableListOf<HistoryItem>()
        val groupedByBatch = mutableMapOf<String?, MutableList<BarcodeResult>>()
        
        results.forEach { result ->
            groupedByBatch.getOrPut(result.batchId) { mutableListOf() }.add(result)
        }

        val processedBatches = mutableSetOf<String?>()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        results.forEach { result ->
            val bid = result.batchId
            if (bid !in processedBatches) {
                processedBatches.add(bid)
                val batchList = groupedByBatch[bid]
                if (batchList != null) {
                    if (bid != null) {
                        val firstItemTime = timeFormat.format(Date(batchList.first().timestamp))
                        historyItems.add(HistoryItem.Header(bid, getString(R.string.batch_scan_template, firstItemTime), batchList))
                    }
                    batchList.forEach { batchResult ->
                        historyItems.add(HistoryItem.Scan(batchResult))
                    }
                }
            }
        }

        binding.rvResults.layoutManager = LinearLayoutManager(this)
        adapter = ScanHistoryAdapter(
            historyItems,
            onItemClick = { result ->
                val intent = Intent(this, ScanResultActivity::class.java).apply {
                    putExtra(ScanResultActivity.EXTRA_RAW_VALUE, result.rawValue)
                    putExtra(ScanResultActivity.EXTRA_DISPLAY_VALUE, result.displayValue)
                    putExtra(ScanResultActivity.EXTRA_FORMAT, result.format)
                    putExtra(ScanResultActivity.EXTRA_TYPE, result.type)
                }
                startActivity(intent)
            },
            onDeleteClick = { result ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete)
                    .setMessage(R.string.delete_item_confirmation)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { dialog, _ ->
                        lifecycleScope.launch {
                            historyRepository.deleteResult(result)
                            refreshHistory()
                            dialog.dismiss()
                        }
                    }
                    .show()
            }
        )
        binding.rvResults.adapter = adapter
    }
}

sealed class HistoryItem {
    data class Header(val batchId: String, val title: String, val results: List<BarcodeResult>) : HistoryItem()
    data class Scan(val result: BarcodeResult) : HistoryItem()
}

class ScanHistoryAdapter(
    private val items: List<HistoryItem>,
    private val onItemClick: (BarcodeResult) -> Unit,
    private val onDeleteClick: (BarcodeResult) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SCAN = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is HistoryItem.Header -> TYPE_HEADER
            is HistoryItem.Scan -> TYPE_SCAN
        }
    }

    class ScanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIndex: TextView = view.findViewById(R.id.tvIndex)
        val tvValue: TextView = view.findViewById(R.id.tvValue)
        val tvMeta: TextView = view.findViewById(R.id.tvMeta)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val btnCopy: View = view.findViewById(R.id.btnItemCopy)
        val btnDelete: View = view.findViewById(R.id.btnItemDelete)
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvBatchTitle)
        val btnCopyBatch: View = view.findViewById(R.id.btnCopyBatch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_batch_header, parent, false))
        } else {
            ScanViewHolder(inflater.inflate(R.layout.item_scan_result, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HistoryItem.Header -> {
                val h = holder as HeaderViewHolder
                h.tvTitle.text = item.title
                h.btnCopyBatch.setOnClickListener {
                    val batchText = item.results.joinToString("\n") { it.rawValue }
                    val clipboard = h.itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(h.itemView.context.getString(R.string.batch_results_title), batchText))
                    Toast.makeText(h.itemView.context, h.itemView.context.getString(R.string.batch_results_copied), Toast.LENGTH_SHORT).show()
                }
            }
            is HistoryItem.Scan -> {
                val s = holder as ScanViewHolder
                val result = item.result
                
                // Calculate global scan index (excluding headers)
                val scanIndex = items.take(position).count { it is HistoryItem.Scan } + 1
                
                s.tvIndex.text = s.itemView.context.getString(R.string.index_template, scanIndex)
                s.tvValue.text = result.displayValue
                s.tvMeta.text = s.itemView.context.getString(R.string.scan_meta_template, result.format, result.type)
                s.tvTime.text = timeFormat.format(Date(result.timestamp))

                s.itemView.setOnClickListener { onItemClick(result) }

                s.btnCopy.setOnClickListener {
                    val clipboard = s.itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(s.itemView.context.getString(R.string.clipboard_label_single_qr), result.rawValue))
                    Toast.makeText(s.itemView.context, s.itemView.context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                }

                s.btnDelete.setOnClickListener { onDeleteClick(result) }
            }
        }
    }

    override fun getItemCount() = items.size
}
