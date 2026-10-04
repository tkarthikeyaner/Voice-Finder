package com.karthi.voicefinder.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Colour themes for the "found" banner; the first colour also tints the STOP button text. */
object BannerThemes {
    class Theme(val name: String, val top: Color, val bottom: Color) {
        val brush: Brush get() = Brush.verticalGradient(listOf(top, bottom))
    }

    val all = listOf(
        Theme("Alarm red", Color(0xFFB71C1C), Color(0xFF4A0072)),
        Theme("Ocean", Color(0xFF0D47A1), Color(0xFF00838F)),
        Theme("Forest", Color(0xFF1B5E20), Color(0xFF004D40)),
        Theme("Sunset", Color(0xFFE65100), Color(0xFFAD1457)),
        Theme("Midnight", Color(0xFF263238), Color(0xFF000000)),
    )

    fun get(index: Int): Theme = all.getOrElse(index) { all.first() }
}
