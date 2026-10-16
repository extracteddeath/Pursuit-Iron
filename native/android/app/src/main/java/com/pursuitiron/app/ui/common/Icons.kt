package com.pursuitiron.app.ui.common

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke icons on a 24-unit grid, the same paths the prototype draws. Tinted by Icon(). */
object PiIcons {
    private fun icon(name: String, d: String, stroke: Float = 2.2f): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()

    val Today = icon("today", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val Plan = icon("plan", "M4 5h16v16H4zM4 9h16M9 3v4M15 3v4")
    val Progress = icon("progress", "M4 19V9M10 19V5M16 19v-7M22 19H2")
    val Library = icon("library", "M6.5 6.5v11M17.5 6.5v11M3 9.5v5M21 9.5v5M6.5 12h11")
    val You = icon("you", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0")
    val ChevronDown = icon("down", "M6 9l6 6 6-6")
    val ChevronRight = icon("right", "M9 6l6 6-6 6", 2.4f)
    val ChevronLeft = icon("left", "M15 6l-6 6 6 6", 2.4f)
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7", 2.8f)
    val Plus = icon("plus", "M12 5v14M5 12h14", 2.4f)
    val Minus = icon("minus", "M5 12h14", 2.4f)
    val Close = icon("close", "M6 6l12 12M18 6 6 18", 2.4f)
    val More = icon("more", "M12 4.5v.01M12 12v.01M12 19.5v.01", 3.6f)
    val List = icon("list", "M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01")
    val Trash = icon("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3", 2f)
    val Info = icon("info", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 16v-4M12 8h.01")
    val Swap = icon("swap", "M7 4 3 8l4 4M3 8h14M17 20l4-4-4-4M21 16H7")
    val Shield = icon("shield", "M12 3l8 4v5c0 5-3.5 8-8 9-4.5-1-8-4-8-9V7z")
    val Timer = icon("timer", "M12 7v5l3 2M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z")
    val Help = icon("help", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.5v.7M12 17h.01")
    val Upload = icon("upload", "M12 15V3M7 8l5-5 5 5M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4")
    val Download = icon("download", "M12 3v12M7 10l5 5 5-5M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4")
    val Flame = icon("flame", "M12 22c4 0 7-3 7-7 0-4-3-6-4-9-1 2-2 3-3 3 0-3-1-5-3-7 0 4-4 7-4 13 0 4 3 7 7 7z")
    val Sparkle = icon("sparkle", "M12 3l2 5 5 2-5 2-2 5-2-5-5-2 5-2z")
    val Redo = icon("redo", "M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5")
    val Focus = icon("focus", "M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z")
    val Trophy = icon("trophy", "M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM17 5h3v2a3 3 0 0 1-3 3M7 5H4v2a3 3 0 0 0 3 3")
    val Calendar = icon("calendar", "M4 5h16v16H4zM4 9h16M9 3v4M15 3v4M8 13h.01M12 13h.01M16 13h.01M8 17h.01M12 17h.01")
    val Plates = icon("plates", "M2 12h3M19 12h3M5 7v10M8 5v14M16 5v14M19 7v10M8 12h8")
    val Gym = icon("gym", "M3 21V9l9-6 9 6v12M7 21v-6h10v6M7 13h10")
    val Calc = icon("calc", "M6 3h12a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM8 7h8M8 12h.01M12 12h.01M16 12h.01M8 16h.01M12 16h.01M16 16h.01")
}
