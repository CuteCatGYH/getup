package com.getup.oneui.display

/**
 * Data model representing metadata of a detected physical or virtual display.
 */
data class DisplayInfo(
    val id: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val isPresentationDisplay: Boolean
)
