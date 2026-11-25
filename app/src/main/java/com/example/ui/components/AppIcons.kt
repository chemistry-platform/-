package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val VolumeUp: ImageVector by lazy {
        vector24("VolumeUp") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 9f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(4f)
                lineToRelative(5f, 5f)
                verticalLineTo(4f)
                lineTo(7f, 9f)
                horizontalLineTo(3f)
                close()
                moveTo(16.5f, 12f)
                curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(8.05f)
                curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
                close()
                moveTo(14f, 3.23f)
                verticalLineToRelative(2.06f)
                curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
                reflectiveCurveToRelative(-2.11f, 5.85f, -5f, 6.71f)
                verticalLineToRelative(2.06f)
                curveToRelative(4.01f, -0.91f, 7f, -4.49f, 7f, -8.77f)
                reflectiveCurveToRelative(-2.99f, -7.86f, -7f, -8.77f)
                close()
            }
        }
    }

    val SwapHoriz: ImageVector by lazy {
        vector24("SwapHoriz") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6.99f, 11f)
                lineTo(3f, 15f)
                lineToRelative(3.99f, 4f)
                verticalLineToRelative(-3f)
                horizontalLineTo(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(6.99f)
                verticalLineToRelative(-3f)
                close()
                moveTo(21f, 9f)
                lineToRelative(-3.99f, -4f)
                verticalLineToRelative(3f)
                horizontalLineTo(10f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(7.01f)
                verticalLineToRelative(3f)
                lineTo(21f, 9f)
                close()
            }
        }
    }

    val Translate: ImageVector by lazy {
        vector24("Translate") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.87f, 15.07f)
                lineToRelative(-2.54f, -2.51f)
                lineToRelative(0.03f, -0.03f)
                curveToRelative(1.74f, -1.94f, 2.98f, -4.17f, 3.71f, -6.53f)
                horizontalLineTo(17f)
                verticalLineTo(4f)
                horizontalLineToRelative(-7f)
                verticalLineTo(2f)
                horizontalLineTo(8f)
                verticalLineToRelative(2f)
                horizontalLineTo(1f)
                verticalLineToRelative(1.99f)
                horizontalLineToRelative(11.17f)
                curveTo(11.5f, 7.92f, 10.44f, 9.75f, 9f, 11.35f)
                curveTo(8.07f, 10.32f, 7.3f, 9.19f, 6.69f, 8f)
                horizontalLineToRelative(-2f)
                curveToRelative(0.73f, 1.63f, 1.73f, 3.17f, 2.98f, 4.56f)
                lineToRelative(-5.09f, 5.02f)
                lineTo(4f, 19f)
                lineToRelative(5f, -5f)
                lineToRelative(3.11f, 3.11f)
                lineToRelative(0.76f, -2.04f)
                close()
                moveTo(18.5f, 10f)
                horizontalLineToRelative(-2f)
                lineTo(12f, 22f)
                horizontalLineToRelative(2f)
                lineToRelative(1.12f, -3f)
                horizontalLineToRelative(4.75f)
                lineTo(21f, 22f)
                horizontalLineToRelative(2f)
                lineToRelative(-4.5f, -12f)
                close()
                moveTo(15.88f, 17f)
                lineToRelative(1.62f, -4.33f)
                lineTo(19.12f, 17f)
                horizontalLineToRelative(-3.24f)
                close()
            }
        }
    }

    val Language: ImageVector by lazy {
        vector24("Language") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.99f, 2f)
                curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
                close()
                moveTo(18.92f, 8f)
                horizontalLineToRelative(-2.95f)
                curveToRelative(-0.32f, -1.25f, -0.78f, -2.45f, -1.38f, -3.56f)
                curveToRelative(1.84f, 0.63f, 3.37f, 1.91f, 4.33f, 3.56f)
                close()
                moveTo(12f, 4.04f)
                curveToRelative(0.83f, 1.2f, 1.48f, 2.53f, 1.91f, 3.96f)
                horizontalLineToRelative(-3.82f)
                curveToRelative(0.43f, -1.43f, 1.08f, -2.76f, 1.91f, -3.96f)
                close()
                moveTo(4.26f, 14f)
                curveTo(4.1f, 13.36f, 4f, 12.69f, 4f, 12f)
                reflectiveCurveToRelative(0.1f, -1.36f, 0.26f, -2f)
                horizontalLineToRelative(3.38f)
                curveToRelative(-0.08f, 0.66f, -0.14f, 1.32f, -0.14f, 2f)
                reflectiveCurveToRelative(0.06f, 1.34f, 0.14f, 2f)
                horizontalLineTo(4.26f)
                close()
                moveTo(5.08f, 16f)
                horizontalLineToRelative(2.95f)
                curveToRelative(0.32f, 1.25f, 0.78f, 2.45f, 1.38f, 3.56f)
                curveToRelative(-1.84f, -0.63f, -3.37f, -1.9f, -4.33f, -3.56f)
                close()
                moveTo(8.03f, 8f)
                horizontalLineTo(5.08f)
                curveToRelative(0.96f, -1.66f, 2.49f, -2.93f, 4.33f, -3.56f)
                curveTo(8.81f, 5.55f, 8.35f, 6.75f, 8.03f, 8f)
                close()
                moveTo(12f, 19.96f)
                curveToRelative(-0.83f, -1.2f, -1.48f, -2.53f, -1.91f, -3.96f)
                horizontalLineToRelative(3.82f)
                curveToRelative(-0.43f, 1.43f, -1.08f, 2.76f, -1.91f, 3.96f)
                close()
                moveTo(14.34f, 14f)
                horizontalLineTo(9.66f)
                curveToRelative(-0.09f, -0.66f, -0.16f, -1.32f, -0.16f, -2f)
                reflectiveCurveToRelative(0.07f, -1.35f, 0.16f, -2f)
                horizontalLineToRelative(4.68f)
                curveToRelative(0.09f, 0.65f, 0.16f, 1.32f, 0.16f, 2f)
                reflectiveCurveToRelative(-0.07f, 1.34f, -0.16f, 2f)
                close()
                moveTo(14.59f, 19.56f)
                curveToRelative(0.6f, -1.11f, 1.06f, -2.31f, 1.38f, -3.56f)
                horizontalLineToRelative(2.95f)
                curveToRelative(-0.96f, 1.65f, -2.49f, 2.93f, -4.33f, 3.56f)
                close()
                moveTo(16.36f, 14f)
                curveToRelative(0.08f, -0.66f, 0.14f, -1.32f, 0.14f, -2f)
                reflectiveCurveToRelative(-0.06f, -1.34f, -0.14f, -2f)
                horizontalLineToRelative(3.38f)
                curveToRelative(0.16f, 0.64f, 0.26f, 1.31f, 0.26f, 2f)
                reflectiveCurveToRelative(-0.1f, 1.36f, -0.26f, 2f)
                horizontalLineToRelative(-3.38f)
                close()
            }
        }
    }

    val CloudDownload: ImageVector by lazy {
        vector24("CloudDownload") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
                curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
                curveToRelative(0f, 3.31f, 2.69f, 6f, 6f, 6f)
                horizontalLineToRelative(13f)
                curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                curveToRelative(0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                close()
                moveTo(17f, 13f)
                lineToRelative(-5f, 5f)
                lineToRelative(-5f, -5f)
                horizontalLineToRelative(3f)
                verticalLineTo(9f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(3f)
                close()
            }
        }
    }

    val Explore: ImageVector by lazy {
        vector24("Explore") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 10.9f)
                curveToRelative(-0.61f, 0f, -1.1f, 0.49f, -1.1f, 1.1f)
                reflectiveCurveToRelative(0.49f, 1.1f, 1.1f, 1.1f)
                curveToRelative(0.61f, 0f, 1.1f, -0.49f, 1.1f, -1.1f)
                reflectiveCurveToRelative(-0.49f, -1.1f, -1.1f, -1.1f)
                close()
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(14.19f, 14.19f)
                lineTo(6f, 18f)
                lineToRelative(3.81f, -8.19f)
                lineTo(18f, 6f)
                lineToRelative(-3.81f, 8.19f)
                close()
            }
        }
    }

    val Bookmark: ImageVector by lazy {
        vector24("Bookmark") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 3f)
                horizontalLineTo(7f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(16f)
                lineToRelative(7f, -3f)
                lineToRelative(7f, 3f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
            }
        }
    }

    val BookmarkBorder: ImageVector by lazy {
        vector24("BookmarkBorder") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 3f)
                horizontalLineTo(7f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(16f)
                lineToRelative(7f, -3f)
                lineToRelative(7f, 3f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(17f, 18f)
                lineToRelative(-5f, -2.18f)
                lineTo(7f, 18f)
                verticalLineTo(5f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(13f)
                close()
            }
        }
    }

    val StarOutline: ImageVector by lazy {
        vector24("StarOutline") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(22f, 9.24f)
                lineToRelative(-7.19f, -0.62f)
                lineTo(12f, 2f)
                lineTo(9.19f, 8.63f)
                lineTo(2f, 9.24f)
                lineToRelative(5.46f, 4.73f)
                lineTo(5.82f, 21f)
                lineTo(12f, 17.27f)
                lineTo(18.18f, 21f)
                lineToRelative(-1.63f, -7.03f)
                lineTo(22f, 9.24f)
                close()
                moveTo(12f, 15.4f)
                lineToRelative(-3.76f, 2.27f)
                lineToRelative(1f, -4.28f)
                lineToRelative(-3.32f, -2.88f)
                lineToRelative(4.38f, -0.38f)
                lineTo(12f, 6.1f)
                lineToRelative(1.71f, 4.04f)
                lineToRelative(4.38f, 0.38f)
                lineToRelative(-3.32f, 2.88f)
                lineToRelative(1f, 4.28f)
                lineTo(12f, 15.4f)
                close()
            }
        }
    }

    val History: ImageVector by lazy {
        vector24("History") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13f, 3f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                horizontalLineTo(1f)
                lineToRelative(3.89f, 3.89f)
                lineToRelative(0.07f, 0.14f)
                lineTo(9f, 12f)
                horizontalLineTo(6f)
                curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f)
                reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
                reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
                curveToRelative(-1.93f, 0f, -3.68f, -0.79f, -4.94f, -2.06f)
                lineToRelative(-1.42f, 1.42f)
                curveTo(8.27f, 19.99f, 10.51f, 21f, 13f, 21f)
                curveToRelative(4.97f, 0f, 9f, -4.03f, 9f, -9f)
                reflectiveCurveToRelative(-4.03f, -9f, -9f, -9f)
                close()
                moveTo(12f, 8f)
                verticalLineToRelative(5f)
                lineToRelative(4.28f, 2.54f)
                lineToRelative(0.72f, -1.21f)
                lineToRelative(-3.5f, -2.08f)
                verticalLineTo(8f)
                horizontalLineTo(12f)
                close()
            }
        }
    }

    val WifiOff: ImageVector by lazy {
        vector24("WifiOff") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(22.99f, 9f)
                curveTo(19.15f, 5.16f, 13.8f, 3.76f, 8.84f, 4.78f)
                lineToRelative(2.52f, 2.52f)
                curveToRelative(3.47f, -0.17f, 6.99f, 1.05f, 9.63f, 3.7f)
                lineToRelative(2f, -2f)
                close()
                moveTo(18.99f, 13f)
                curveToRelative(-1.29f, -1.29f, -2.84f, -2.13f, -4.49f, -2.56f)
                lineToRelative(3.53f, 3.53f)
                lineToRelative(0.96f, -0.97f)
                close()
                moveTo(2f, 3.05f)
                lineTo(5.07f, 6.1f)
                curveTo(3.6f, 6.82f, 2.22f, 7.78f, 1f, 9f)
                lineToRelative(1.99f, 2f)
                curveToRelative(1.24f, -1.24f, 2.67f, -2.16f, 4.2f, -2.77f)
                lineToRelative(2.24f, 2.24f)
                curveTo(7.81f, 10.89f, 6.27f, 11.73f, 5f, 13f)
                verticalLineToRelative(0.01f)
                lineTo(6.99f, 15f)
                curveToRelative(1.36f, -1.36f, 3.14f, -2.04f, 4.92f, -2.06f)
                lineTo(18.98f, 20f)
                lineToRelative(1.27f, -1.26f)
                lineTo(3.29f, 1.79f)
                lineTo(2f, 3.05f)
                close()
                moveTo(9f, 17f)
                lineToRelative(3f, 3f)
                lineToRelative(3f, -3f)
                curveToRelative(-1.65f, -1.66f, -4.34f, -1.66f, -6f, 0f)
                close()
            }
        }
    }

    val ContentCopy: ImageVector by lazy {
        vector24("ContentCopy") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 1f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                horizontalLineToRelative(12f)
                verticalLineTo(1f)
                close()
                moveTo(19f, 5f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(11f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(19f, 21f)
                horizontalLineTo(8f)
                verticalLineTo(7f)
                horizontalLineToRelative(11f)
                verticalLineToRelative(14f)
                close()
            }
        }
    }

    val OpenInNew: ImageVector by lazy {
        vector24("OpenInNew") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 19f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineToRelative(7f)
                verticalLineTo(3f)
                horizontalLineTo(5f)
                curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-7f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(7f)
                close()
                moveTo(14f, 3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(3.59f)
                lineToRelative(-9.83f, 9.83f)
                lineToRelative(1.41f, 1.41f)
                lineTo(19f, 6.41f)
                verticalLineTo(10f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                horizontalLineToRelative(-7f)
                close()
            }
        }
    }

    val UploadFile: ImageVector by lazy {
        vector24("UploadFile") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 2f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(4f, 20f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
                horizontalLineTo(18f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                lineToRelative(-6f, -6f)
                close()
                moveTo(18f, 20f)
                horizontalLineTo(6f)
                verticalLineTo(4f)
                horizontalLineToRelative(7f)
                verticalLineToRelative(5f)
                horizontalLineToRelative(5f)
                verticalLineToRelative(11f)
                close()
                moveTo(8f, 15.01f)
                lineToRelative(1.41f, 1.41f)
                lineTo(11f, 14.84f)
                verticalLineTo(19f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-4.16f)
                lineToRelative(1.59f, 1.59f)
                lineTo(16f, 15.01f)
                lineTo(12.01f, 11f)
                lineTo(8f, 15.01f)
                close()
            }
        }
    }

    val SaveAlt: ImageVector by lazy {
        vector24("SaveAlt") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 12f)
                verticalLineToRelative(7f)
                horizontalLineTo(5f)
                verticalLineToRelative(-7f)
                horizontalLineTo(3f)
                verticalLineToRelative(7f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-7f)
                horizontalLineToRelative(-2f)
                close()
                moveTo(13f, 12.67f)
                lineToRelative(2.59f, -2.58f)
                lineTo(17f, 11.5f)
                lineToRelative(-5f, 5f)
                lineToRelative(-5f, -5f)
                lineToRelative(1.41f, -1.41f)
                lineTo(11f, 12.67f)
                verticalLineTo(3f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(9.67f)
                close()
            }
        }
    }

    val Mic: ImageVector by lazy {
        vector24("Mic") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 14f)
                curveToRelative(1.66f, 0f, 2.99f, -1.34f, 2.99f, -3f)
                lineTo(15f, 5f)
                curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
                reflectiveCurveTo(9f, 3.34f, 9f, 5f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
                close()
                moveTo(17.3f, 11f)
                curveToRelative(0f, 3f, -2.54f, 5.1f, -5.3f, 5.1f)
                reflectiveCurveTo(6.7f, 14f, 6.7f, 11f)
                horizontalLineTo(5f)
                curveToRelative(0f, 3.41f, 2.72f, 6.23f, 6f, 6.72f)
                verticalLineTo(21f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-3.28f)
                curveToRelative(3.28f, -0.48f, 6f, -3.3f, 6f, -6.72f)
                horizontalLineToRelative(-1.7f)
                close()
            }
        }
    }

    val SystemUpdate: ImageVector by lazy {
        vector24("SystemUpdate") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 1.01f)
                lineTo(7f, 1f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(10f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(3f)
                curveToRelative(0f, -1.1f, -0.9f, -1.99f, -2f, -1.99f)
                close()
                moveTo(17f, 19f)
                horizontalLineTo(7f)
                verticalLineTo(5f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(14f)
                close()
                moveTo(16f, 13f)
                horizontalLineToRelative(-3f)
                verticalLineTo(8f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(5f)
                horizontalLineTo(8f)
                lineToRelative(4f, 4f)
                lineToRelative(4f, -4f)
                close()
            }
        }
    }

    val Storage: ImageVector by lazy {
        vector24("Storage") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(2f, 20f)
                horizontalLineToRelative(20f)
                verticalLineToRelative(-4f)
                horizontalLineTo(2f)
                verticalLineToRelative(4f)
                close()
                moveTo(4f, 17f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                verticalLineToRelative(-2f)
                close()
                moveTo(2f, 4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(20f)
                verticalLineTo(4f)
                horizontalLineTo(2f)
                close()
                moveTo(6f, 7f)
                horizontalLineTo(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveTo(2f, 14f)
                horizontalLineToRelative(20f)
                verticalLineToRelative(-4f)
                horizontalLineTo(2f)
                verticalLineToRelative(4f)
                close()
                moveTo(4f, 11f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                verticalLineToRelative(-2f)
                close()
            }
        }
    }

    val DeleteOutline: ImageVector by lazy {
        vector24("DeleteOutline") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                verticalLineToRelative(12f)
                close()
                moveTo(8f, 9f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(10f)
                horizontalLineTo(8f)
                verticalLineTo(9f)
                close()
                moveTo(15.5f, 4f)
                lineToRelative(-1f, -1f)
                horizontalLineToRelative(-5f)
                lineToRelative(-1f, 1f)
                horizontalLineTo(5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineTo(4f)
                horizontalLineToRelative(-3.5f)
                close()
            }
        }
    }

    val Lightbulb: ImageVector by lazy {
        vector24("Lightbulb") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 21f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(4f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-1f)
                horizontalLineTo(9f)
                verticalLineToRelative(1f)
                close()
                moveTo(12f, 2f)
                curveTo(8.14f, 2f, 5f, 5.14f, 5f, 9f)
                curveToRelative(0f, 2.38f, 1.19f, 4.47f, 3f, 5.74f)
                verticalLineTo(17f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(6f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-2.26f)
                curveToRelative(1.81f, -1.27f, 3f, -3.36f, 3f, -5.74f)
                curveToRelative(0f, -3.86f, -3.14f, -7f, -7f, -7f)
                close()
            }
        }
    }

    val Palette: ImageVector by lazy {
        vector24("Palette") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
                curveToRelative(0.83f, 0f, 1.5f, -0.67f, 1.5f, -1.5f)
                curveToRelative(0f, -0.39f, -0.15f, -0.74f, -0.39f, -1.01f)
                curveToRelative(-0.23f, -0.26f, -0.38f, -0.61f, -0.38f, -0.99f)
                curveToRelative(0f, -0.83f, 0.67f, -1.5f, 1.5f, -1.5f)
                horizontalLineTo(16f)
                curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                curveToRelative(0f, -4.42f, -4.03f, -8f, -9f, -8f)
                close()
            }
        }
    }

    val Description: ImageVector by lazy {
        vector24("Description") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 2f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(4f, 20f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
                horizontalLineTo(18f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                lineToRelative(-6f, -6f)
                close()
                moveTo(16f, 18f)
                horizontalLineTo(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                close()
                moveTo(16f, 14f)
                horizontalLineTo(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                close()
                moveTo(13f, 9f)
                verticalLineTo(3.5f)
                lineTo(18.5f, 9f)
                horizontalLineTo(13f)
                close()
            }
        }
    }

    val PictureAsPdf: ImageVector by lazy {
        vector24("PictureAsPdf") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 2f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(11.5f, 9.5f)
                curveToRelative(0f, 0.83f, -0.67f, 1.5f, -1.5f, 1.5f)
                horizontalLineTo(9f)
                verticalLineToRelative(2f)
                horizontalLineTo(7.5f)
                verticalLineTo(7f)
                horizontalLineTo(10f)
                curveToRelative(0.83f, 0f, 1.5f, 0.67f, 1.5f, 1.5f)
                verticalLineToRelative(1f)
                close()
                moveTo(16.5f, 11.5f)
                curveToRelative(0f, 0.83f, -0.67f, 1.5f, -1.5f, 1.5f)
                horizontalLineToRelative(-2.5f)
                verticalLineTo(7f)
                horizontalLineTo(15f)
                curveToRelative(0.83f, 0f, 1.5f, 0.67f, 1.5f, 1.5f)
                verticalLineToRelative(3f)
                close()
                moveTo(20.5f, 8.5f)
                horizontalLineTo(19f)
                verticalLineToRelative(1f)
                horizontalLineToRelative(1.5f)
                verticalLineTo(11f)
                horizontalLineTo(19f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-1.5f)
                verticalLineTo(7f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(1.5f)
                close()
                moveTo(4f, 6f)
                horizontalLineTo(2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(4f)
                verticalLineTo(6f)
                close()
            }
        }
    }

    val ImageFile: ImageVector by lazy {
        vector24("ImageFile") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(21f, 19f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                close()
                moveTo(8.5f, 13.5f)
                lineToRelative(2.5f, 3.01f)
                lineTo(14.5f, 12f)
                lineToRelative(4.5f, 6f)
                horizontalLineTo(5f)
                lineToRelative(3.5f, -4.5f)
                close()
            }
        }
    }

    val PlayArrow: ImageVector by lazy {
        vector24("PlayArrow") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 5f)
                verticalLineToRelative(14f)
                lineToRelative(11f, -7f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        vector24("Pause") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                verticalLineToRelative(14f)
                close()
                moveTo(14f, 5f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(-4f)
                close()
            }
        }
    }

    val Stop: ImageVector by lazy {
        vector24("Stop") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 6f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(12f)
                horizontalLineTo(6f)
                close()
            }
        }
    }

    val SkipNext: ImageVector by lazy {
        vector24("SkipNext") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 18f)
                lineToRelative(8.5f, -6f)
                lineTo(6f, 6f)
                verticalLineToRelative(12f)
                close()
                moveTo(16f, 6f)
                verticalLineToRelative(12f)
                horizontalLineToRelative(2f)
                verticalLineTo(6f)
                horizontalLineToRelative(-2f)
                close()
            }
        }
    }

    val SkipPrevious: ImageVector by lazy {
        vector24("SkipPrevious") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(12f)
                horizontalLineTo(6f)
                close()
                moveTo(9.5f, 12f)
                lineToRelative(8.5f, 6f)
                verticalLineTo(6f)
                close()
            }
        }
    }

    private inline fun vector24(
        name: String,
        block: ImageVector.Builder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()
}
