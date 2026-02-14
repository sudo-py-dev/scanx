package com.scanx.qrscanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.scanx.qrscanner.databinding.DialogLanguageSelectionBinding

class LanguageBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: DialogLanguageSelectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLanguageSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val languages = listOf(
            LanguageItem("English", "en", R.string.lang_en),
            LanguageItem("עברית", "iw", R.string.lang_iw),
            LanguageItem("Français", "fr", R.string.lang_fr),
            LanguageItem("Русский", "ru", R.string.lang_ru),
            LanguageItem("Español", "es", R.string.lang_es),
            LanguageItem("Deutsch", "de", R.string.lang_de),
            LanguageItem("Português", "pt", R.string.lang_pt),
            LanguageItem("中文", "zh", R.string.lang_zh),
            LanguageItem("हिन्दी", "hi", R.string.lang_hi),
            LanguageItem("العربية", "ar", R.string.lang_ar)
        )

        val currentLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()

        binding.rvLanguages.layoutManager = LinearLayoutManager(context)
        binding.rvLanguages.adapter = LanguageAdapter(languages, currentLocale) { selectedLang ->
            val appLocale = LocaleListCompat.forLanguageTags(selectedLang.code)
            AppCompatDelegate.setApplicationLocales(appLocale)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class LanguageItem(val name: String, val code: String, val resId: Int)

    private inner class LanguageAdapter(
        private val items: List<LanguageItem>,
        private val currentLocale: String,
        private val onItemClick: (LanguageItem) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvLanguageName)
            val ivCheck: ImageView = view.findViewById(R.id.ivCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_language, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = holder.itemView.context.getString(item.resId)
            
            val isSelected = currentLocale.startsWith(item.code)
            holder.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.itemView.isSelected = isSelected

            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        const val TAG = "LanguageBottomSheet"
    }
}
