package com.scanx.qrscanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.scanx.qrscanner.databinding.ItemLogoBinding

import com.google.android.material.color.MaterialColors

sealed class LogoItem {
    object None : LogoItem()
    data class Resource(val resId: Int) : LogoItem()
}

class LogoPickerAdapter(
    private val items: List<LogoItem>,
    private var selectedItem: LogoItem,
    private val onLogoSelected: (LogoItem) -> Unit
) : RecyclerView.Adapter<LogoPickerAdapter.LogoViewHolder>() {

    inner class LogoViewHolder(private val binding: ItemLogoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LogoItem) {
            val isSelected = item == selectedItem
            
            binding.logoCard.strokeWidth = if (isSelected) 3 else 0
            binding.logoCard.setCardBackgroundColor(
                if (isSelected) 
                    MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimaryContainer)
                else 
                    binding.root.context.getColor(android.R.color.transparent)
            )

            when (item) {
                is LogoItem.None -> {
                    binding.ivLogo.setImageResource(R.drawable.ic_close)
                    binding.ivLogo.alpha = 0.5f
                }
                is LogoItem.Resource -> {
                    binding.ivLogo.setImageResource(item.resId)
                    binding.ivLogo.alpha = 1.0f
                }
            }

            binding.root.setOnClickListener {
                val oldSelected = selectedItem
                selectedItem = item
                onLogoSelected(item)
                
                notifyItemChanged(items.indexOf(oldSelected))
                notifyItemChanged(items.indexOf(item))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogoViewHolder {
        val binding = ItemLogoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogoViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
    
    fun setSelected(item: LogoItem) {
        val oldSelected = selectedItem
        selectedItem = item
        notifyItemChanged(items.indexOf(oldSelected))
        notifyItemChanged(items.indexOf(item))
    }
}
