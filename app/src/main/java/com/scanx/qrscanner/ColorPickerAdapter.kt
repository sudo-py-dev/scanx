package com.scanx.qrscanner

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.scanx.qrscanner.databinding.ItemColorBinding

class ColorPickerAdapter(
    private val colors: List<Int>,
    private var selectedColor: Int,
    private val onColorSelected: (Int) -> Unit
) : RecyclerView.Adapter<ColorPickerAdapter.ColorViewHolder>() {

    inner class ColorViewHolder(private val binding: ItemColorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(color: Int) {
            binding.colorView.setBackgroundColor(color)
            val isSelected = color == selectedColor
            binding.colorCard.strokeWidth = if (isSelected) 4 else 0
            binding.ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            binding.root.setOnClickListener {
                val oldSelected = selectedColor
                selectedColor = color
                onColorSelected(color)
                
                // Notify changes
                val oldIndex = colors.indexOf(oldSelected)
                val newIndex = colors.indexOf(color)
                if (oldIndex != -1) notifyItemChanged(oldIndex)
                if (newIndex != -1) notifyItemChanged(newIndex)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val binding = ItemColorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ColorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        holder.bind(colors[position])
    }

    override fun getItemCount() = colors.size
}
