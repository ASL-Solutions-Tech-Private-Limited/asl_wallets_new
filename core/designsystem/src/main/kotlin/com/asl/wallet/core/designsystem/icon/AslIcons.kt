package com.asl.wallet.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's stroke icon set (24×24, 1.8 stroke, round caps), the same drawings as the approved design.
 * Hand-built so the app needs no icon library (smaller APK, consistent look). Tint with Icon(tint = …).
 */
object AslIcons {
    private val cache = HashMap<String, ImageVector>()

    private fun icon(name: String, vararg paths: String): ImageVector = cache.getOrPut(name) {
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
    }

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},$y h${w - 2 * r} a$r,$r 0 0 1 $r,$r v${h - 2 * r} a$r,$r 0 0 1 ${-r},$r h${-(w - 2 * r)} a$r,$r 0 0 1 ${-r},${-r} v${-(h - 2 * r)} a$r,$r 0 0 1 $r,${-r} z"

    val Fingerprint get() = icon("finger", "M12 11v4a6 6 0 0 1-1 3.3", "M8.5 7.6A5 5 0 0 1 17 11v2.5", "M7 11a5 5 0 0 1 .4-2", "M7 14.5V11",
        "M15 15a10 10 0 0 1-1.2 4.5", "M9.6 19.5A9 9 0 0 0 10 15v-4a2 2 0 0 1 4 0v1.5", "M5 9.5a7 7 0 0 1 11.8-4")
    val Atm get() = icon("atm", rect(3f, 5f, 18f, 12f, 2.5f), "M3 9h18", "M7 14h4")
    val Send get() = icon("send", "M4 12h13", "M12 6l6 6-6 6")
    val Upi get() = icon("upi", "M5 4l5 16", "M11 4l5 16", "M17 4l3 8")
    val Phone get() = icon("phone", rect(7f, 3f, 10f, 18f, 2.5f), "M11 18h2")
    val Bill get() = icon("bill", "M6 3h12v18l-3-2-3 2-3-2-3 2z", "M9 8h6M9 12h6")
    val Aadhaar get() = icon("aadhaar", circle(12f, 9f, 3.2f), "M5.5 20a6.5 6.5 0 0 1 13 0")
    val More get() = icon("more", rect(4f, 4f, 6.5f, 6.5f, 1.8f), rect(13.5f, 4f, 6.5f, 6.5f, 1.8f), rect(4f, 13.5f, 6.5f, 6.5f, 1.8f), rect(13.5f, 13.5f, 6.5f, 6.5f, 1.8f))
    val Home get() = icon("home", "M4 11l8-7 8 7v9H4z", "M10 20v-5h4v5")
    val Report get() = icon("report", "M5 20V10M12 20V4M19 20v-7")
    /** Arrange Home: stacked bars with up/down arrows. */
    val Arrange get() = icon("arrange", "M4 6h10", "M4 12h10", "M4 18h10", "M18 4v16", "M15.5 6.5L18 4l2.5 2.5", "M15.5 17.5L18 20l2.5-2.5")
    val ArrowUp get() = icon("arrow_up", "M12 19V5", "M6 11l6-6 6 6")
    val ArrowDown get() = icon("arrow_down", "M12 5v14", "M6 13l6 6 6-6")
    val DragHandle get() = icon("drag", "M5 9h14", "M5 15h14")
    val Earnings get() = icon("earn", circle(12f, 12f, 8f), "M9.5 9h5M9.5 12h5M12 9c2 0 2 3 0 3l3 4")
    val User get() = icon("user", circle(12f, 8.5f, 3.5f), "M5 20a7 7 0 0 1 14 0")
    val Bell get() = icon("bell", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0")
    val Back get() = icon("back", "M15 5l-7 7 7 7")
    val Chevron get() = icon("chev", "M9 5l7 7-7 7")
    val Close get() = icon("close", "M6 6l12 12M18 6L6 18")
    val Eye get() = icon("eye", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", circle(12f, 12f, 2.8f))
    val EyeOff get() = icon("eyeoff", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", circle(12f, 12f, 2.8f), "M4 4l16 16")
    val Shield get() = icon("shield", "M12 3l7 3v5.5c0 4.5-3 7.8-7 9.5-4-1.7-7-5-7-9.5V6z", "M9 12l2 2 4-4")
    val Lock get() = icon("lock", rect(5f, 10.5f, 14f, 10f, 2.5f), "M8 10.5V8a4 4 0 0 1 8 0v2.5")
    val Check get() = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val Clock get() = icon("clock", circle(12f, 12f, 8.5f), "M12 7.5V12l3 2")
    val Alert get() = icon("alert", "M12 4l9 16H3z", "M12 10v4M12 17h.01")
    val Info get() = icon("info", circle(12f, 12f, 8.5f), "M12 11v5M12 8h.01")
    val Share get() = icon("share", circle(6f, 12f, 2.5f), circle(18f, 6f, 2.5f), circle(18f, 18f, 2.5f), "M8.3 11l7.4-3.8M8.3 13l7.4 3.8")
    val Print get() = icon("print", "M7 9V4h10v5", rect(4f, 9f, 16f, 7f, 2f), "M7 14h10v6H7z")
    val Document get() = icon("doc", "M7 3h7l5 5v13H7z", "M14 3v5h5M10 13h6M10 17h6")
    val Help get() = icon("help", circle(12f, 12f, 8.5f), "M9.6 9.5a2.5 2.5 0 0 1 4.8 1c0 1.7-2.4 2-2.4 3.5M12 17h.01")
    val Chat get() = icon("chat", "M5 18l-1 3 4-1.5A8 8 0 1 0 5 18z")
    val Search get() = icon("search", circle(11f, 11f, 6.5f), "M16 16l4.5 4.5")
    val Plus get() = icon("plus", "M12 5v14M5 12h14")
    val Bank get() = icon("bank", "M3 10l9-6 9 6", "M5 10v8M10 10v8M14 10v8M19 10v8M3 20h18")
    val Wallet get() = icon("wallet", "M4 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z", "M4 7l11-3v3M16 13.5h2")
    val Qr get() = icon("qr", rect(4f, 4f, 6f, 6f, 1f), rect(14f, 4f, 6f, 6f, 1f), rect(4f, 14f, 6f, 6f, 1f), "M14 14h2v2h-2zM18 18h2v2h-2zM14 18h2M18 14h2")
    val WifiOff get() = icon("wifioff", "M2.5 9a14 14 0 0 1 19 0M5.5 12.5a9.5 9.5 0 0 1 13 0M8.5 16a5 5 0 0 1 7 0", circle(12f, 19f, 0.8f), "M3 3l18 18")
    val Refresh get() = icon("refresh", "M20 11a8 8 0 0 0-14.5-4.5L4 8M4 4v4h4M4 13a8 8 0 0 0 14.5 4.5L20 16M20 20v-4h-4")
    val Update get() = icon("update", "M12 4v11M7.5 10.5L12 15l4.5-4.5", "M5 19h14")
    val Location get() = icon("pin", "M12 21s6.5-6 6.5-11A6.5 6.5 0 0 0 5.5 10c0 5 6.5 11 6.5 11z", circle(12f, 10f, 2.3f))
    val Moon get() = icon("moon", "M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z")
    val Globe get() = icon("globe", circle(12f, 12f, 8.5f), "M3.5 12h17M12 3.5c3 3 3 14 0 17M12 3.5c-3 3-3 14 0 17")
    val Star get() = icon("star", "M12 4l2.4 5 5.4.6-4 3.7 1.1 5.3L12 16l-4.9 2.6 1.1-5.3-4-3.7 5.4-.6z")
    val Trash get() = icon("trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")
    val Upload get() = icon("upload", "M12 16V5M7 9.5L12 5l4.5 4.5", "M5 15v3a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3")
    val Copy get() = icon("copy", "M9 9h10v10H9z", "M5 15V5h10")
    val Edit get() = icon("edit", "M4 20h4L19 9l-4-4L4 16v4z", "M13.5 6.5l4 4")
    val Calendar get() = icon("calendar", "M4 6h16v14H4z", "M4 10h16M8 3v4M16 3v4")
    val Camera get() = icon("camera", "M4 8h3l2-3h6l2 3h3v11H4z", "M12 17a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z")
    val Tv get() = icon("tv", rect(3f, 6f, 18f, 12f, 2f), "M9 21h6M8 3l4 3 4-3")
    val Contacts get() = icon("contacts", rect(5f, 3f, 14f, 18f, 2.5f), circle(12f, 10f, 2.5f), "M8.5 16a3.5 3.5 0 0 1 7 0")
}
