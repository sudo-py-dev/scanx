package com.scanx.qrscanner

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.scanx.qrscanner.databinding.ItemStyleTemplateBinding

class QrStyleAdapter(
    private val styles: List<QrStyle>,
    private var selectedStyle: QrStyle?,
    private val onStyleSelected: (QrStyle) -> Unit
) : RecyclerView.Adapter<QrStyleAdapter.StyleViewHolder>() {

    inner class StyleViewHolder(private val binding: ItemStyleTemplateBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(style: QrStyle) {
            binding.tvStyleName.setText(style.nameResId)
            
            val gradient = if (style.isGradient && style.previewGradientColors != null) {
                GradientDrawable(GradientDrawable.Orientation.TL_BR, style.previewGradientColors)
            } else {
                GradientDrawable().apply { setColor(style.foregroundColor) }
            }
            gradient.cornerRadius = 12f
            binding.viewPreview.background = gradient

            val isSelected = selectedStyle == style
            binding.styleCard.strokeWidth = if (isSelected) 3 else 0
            
            binding.root.setOnClickListener {
                val oldSelected = selectedStyle
                selectedStyle = style
                onStyleSelected(style)
                
                // Refresh items
                val oldIndex = styles.indexOf(oldSelected)
                val newIndex = styles.indexOf(style)
                if (oldIndex != -1) notifyItemChanged(oldIndex)
                if (newIndex != -1) notifyItemChanged(newIndex)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StyleViewHolder {
        val binding = ItemStyleTemplateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StyleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StyleViewHolder, position: Int) {
        holder.bind(styles[position])
    }

    override fun getItemCount(): Int = styles.size
}
