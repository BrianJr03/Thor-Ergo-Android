package jr.brian.thorkbm.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Key glyphs, drawn as 24-unit vectors so they stay sharp at any key size. */
object ErgoIcons {
    val Backspace: ImageVector by lazy {
        icon("backspace", "M22 3H7c-.69 0-1.23.35-1.59.88L0 12l5.41 8.11c.36.53.9.89 1.59.89h15c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H7.07L2.4 12l4.66-7H22v14zM10.41 17L14 13.41 17.59 17 19 15.59 15.41 12 19 8.41 17.59 7 14 10.59 10.41 7 9 8.41 12.59 12 9 15.59z")
    }
    val Shift: ImageVector by lazy { outline("shift", "M12 3.5L3.5 12.5H8.5V20.5H15.5V12.5H20.5Z") }
    val ShiftLocked: ImageVector by lazy { icon("shift_locked", "M12 3.5L3.5 12.5H8.5V20.5H15.5V12.5H20.5Z") }
    val Enter: ImageVector by lazy { icon("enter", "M19 7v4H5.83l3.58-3.59L8 6l-6 6 6 6 1.41-1.41L5.83 13H21V7z") }
    val Left: ImageVector by lazy { icon("left", "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z") }
    val Up: ImageVector by lazy { icon("up", "M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z") }
    val Down: ImageVector by lazy { icon("down", "M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z") }
    val Right: ImageVector by lazy { icon("right", "M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z") }
    val Dropdown: ImageVector by lazy { icon("dropdown", "M7 10l5 5 5-5z") }
    val Gear: ImageVector by lazy {
        icon(
            "gear",
            "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96" +
                "c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96" +
                "a.49.49 0 0 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61" +
                "l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54" +
                "c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6" +
                "s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z",
        )
    }
    val MouseLeft: ImageVector by lazy { mouse("mouse_left", "M12 2.5C9.2 2.5 7 4.7 7 7.5V9.5H12Z") }
    val MouseRight: ImageVector by lazy { mouse("mouse_right", "M12 2.5C14.8 2.5 17 4.7 17 7.5V9.5H12Z") }

    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.White))
        .build()

    private fun outline(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), stroke = SolidColor(Color.White), strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round)
        .build()

    private fun mouse(name: String, button: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            PathParser().parsePathString("M12 2.5C9.2 2.5 7 4.7 7 7.5V16.5C7 19.3 9.2 21.5 12 21.5S17 19.3 17 16.5V7.5C17 4.7 14.8 2.5 12 2.5ZM7 9.5H17M12 2.5V9.5").toNodes(),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineJoin = StrokeJoin.Round,
        )
        .addPath(PathParser().parsePathString(button).toNodes(), fill = SolidColor(Color.White))
        .build()
}
