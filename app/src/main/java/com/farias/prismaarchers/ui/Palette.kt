package com.farias.prismaarchers.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** UI colours of the menus' backdrop. */
class GameTheme(val bgTop: Color, val bgBottom: Color, val aurora: List<Color>, val stars: Boolean, val accent: Color, val accent2: Color)

private val MENU = GameTheme(
    Color(0xFF140F2E), Color(0xFF2A1240),
    listOf(Color(0xFF7C3AED), Color(0xFFF97316), Color(0xFF22D3EE)),
    true, Color(0xFFFACC15), Color(0xFFF97316),
)

object Palette {
    /** Theme of the menu screens. */
    val theme: GameTheme get() = MENU

    val BgTop: Color get() = theme.bgTop
    val BgBottom: Color get() = theme.bgBottom

    val Glass = Color(0x14FFFFFF)
    val GlassBorder = Color(0x26FFFFFF)
    val EmptyCell = Color(0x0FFFFFFF)
    val EmptyCellInner = Color(0x33000000)

    val TextPrimary = Color(0xFFF5F3FF)
    val TextSecondary = Color(0xFFA5A3C9)
    val Gold = Color(0xFFFACC15)
    val Cyan = Color(0xFF22D3EE)
    val Magenta = Color(0xFFE879F9)
    val Danger = Color(0xFFF43F5E)

    fun lighter(c: Color, f: Float = 0.35f) = lerp(c, Color.White, f)
    fun darker(c: Color, f: Float = 0.35f) = lerp(c, Color.Black, f)

    /** Desaturated gray version, used when the game is over. */
    fun gray(c: Color): Color {
        val l = c.red * 0.3f + c.green * 0.59f + c.blue * 0.11f
        return Color(l * 0.55f, l * 0.55f, l * 0.62f, c.alpha)
    }
}
