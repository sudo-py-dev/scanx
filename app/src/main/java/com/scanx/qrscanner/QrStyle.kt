package com.scanx.qrscanner

import android.graphics.Color

data class QrStyle(
    val nameResId: Int,
    val foregroundColor: Int = Color.BLACK,
    val secondaryColor: Int = Color.BLACK,
    val isGradient: Boolean = false,
    val isRounded: Boolean = false,
    val isSleek: Boolean = false,
    val previewGradientColors: IntArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as QrStyle
        if (nameResId != other.nameResId) return false
        return true
    }

    override fun hashCode(): Int = nameResId
}
