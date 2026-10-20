package com.example.seatfinder.ui.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

//Sovrapposizione disegnata sopra la fotocamera durante la scansione QR.
@Composable
fun ScannerOverlay(
    modifier: Modifier = Modifier,
    boxSize: androidx.compose.ui.unit.Dp = 260.dp,       //Lato del "buco" centrale
    cornerLength: androidx.compose.ui.unit.Dp = 32.dp,  //Lunghezza di ogni angolo bianco
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,  //Raggio degli angoli del buco
    strokeWidth: androidx.compose.ui.unit.Dp = 4.dp,    //Spessore degli angoli bianchi
    cornerColor: Color = Color.White,
    scrimColor: Color = Color.Black.copy(alpha = 0.6f)  //Scurimento fuori dal buco
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val side = boxSize.toPx()
        //Coordinate del quadrato centrale, centrato nella view
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f

        val radius = cornerRadius.toPx()
        val len = cornerLength.toPx()
        val stroke = strokeWidth.toPx()

        //1) Sfondo scuro su tutto lo schermo
        drawRect(color = scrimColor)

        //2) "Buco" trasparente al centro
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(side, side),
            cornerRadius = CornerRadius(radius, radius),
            blendMode = BlendMode.Clear
        )

        //3) Angoli bianchi del mirino
        val right = left + side
        val bottom = top + side

        fun corner(path: Path.() -> Unit) {
            drawPath(
                path = Path().apply(path),
                color = cornerColor,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        corner {
            moveTo(left, top + len)
            lineTo(left, top + radius)
            quadraticBezierTo(left, top, left + radius, top)
            lineTo(left + len, top)
        }
        corner {
            moveTo(right - len, top)
            lineTo(right - radius, top)
            quadraticBezierTo(right, top, right, top + radius)
            lineTo(right, top + len)
        }
        corner {
            moveTo(right, bottom - len)
            lineTo(right, bottom - radius)
            quadraticBezierTo(right, bottom, right - radius, bottom)
            lineTo(right - len, bottom)
        }
        corner {
            moveTo(left + len, bottom)
            lineTo(left + radius, bottom)
            quadraticBezierTo(left, bottom, left, bottom - radius)
            lineTo(left, bottom - len)
        }
    }
}