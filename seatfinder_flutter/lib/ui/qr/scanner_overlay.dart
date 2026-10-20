import 'package:flutter/material.dart';

//scrim scuro con "buco" quadrato al centro e angoli evidenziati.
class ScannerOverlay extends StatelessWidget {
  const ScannerOverlay({
    super.key,
    this.boxSize = 260,
    this.cornerLength = 32,
    this.cornerRadius = 16,
    this.strokeWidth = 4,
    this.cornerColor = Colors.white,
    this.scrimColor = const Color(0x99000000), // nero 60%
  });

  //parametri di configurazione dell'overlay di scansione
  final double boxSize;
  final double cornerLength;
  final double cornerRadius;
  final double strokeWidth;
  final Color cornerColor;
  final Color scrimColor;

  @override
  Widget build(BuildContext context) => IgnorePointer(
    //utilizza IgnorePointer per fare in modo che i tocchi passino attraverso l'overlay
    child: CustomPaint(
      size: Size.infinite,
      //disegna l'overlay personalizzato usando un CustomPainter
      painter: _OverlayPainter(
          boxSize, cornerLength, cornerRadius, strokeWidth, cornerColor, scrimColor),
    ),
  );
}

class _OverlayPainter extends CustomPainter {
  _OverlayPainter(this.side, this.len, this.radius, this.stroke, this.cornerColor,
      this.scrim);

  final double side, len, radius, stroke;
  final Color cornerColor, scrim;

  @override
  void paint(Canvas canvas, Size size) {
    //calcola le coordinate per centrare il riquadro di scansione nello schermo
    final left = (size.width - side) / 2;
    final top = (size.height - side) / 2;
    final right = left + side;
    final bottom = top + side;

    //1) crea lo sfondo scuro e 2) sottrae il "buco" trasparente al centro con angoli arrotondati
    final full = Path()..addRect(Offset.zero & size);
    final hole = Path()
      ..addRRect(RRect.fromRectAndRadius(
          Rect.fromLTWH(left, top, side, side), Radius.circular(radius)));
    canvas.drawPath(
        Path.combine(PathOperation.difference, full, hole), Paint()..color = scrim);

    //3) configura lo stile del pennello per disegnare gli angoli evidenziati del box
    final p = Paint()
      ..color = cornerColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round;

    //disegna i 4 angoli
    canvas.drawPath(
        Path()
          ..moveTo(left, top + len)
          ..lineTo(left, top + radius)
          ..quadraticBezierTo(left, top, left + radius, top)
          ..lineTo(left + len, top),
        p);
    canvas.drawPath(
        Path()
          ..moveTo(right - len, top)
          ..lineTo(right - radius, top)
          ..quadraticBezierTo(right, top, right, top + radius)
          ..lineTo(right, top + len),
        p);
    canvas.drawPath(
        Path()
          ..moveTo(right, bottom - len)
          ..lineTo(right, bottom - radius)
          ..quadraticBezierTo(right, bottom, right - radius, bottom)
          ..lineTo(right - len, bottom),
        p);
    canvas.drawPath(
        Path()
          ..moveTo(left + len, bottom)
          ..lineTo(left + radius, bottom)
          ..quadraticBezierTo(left, bottom, left, bottom - radius)
          ..lineTo(left, bottom - len),
        p);
  }

  @override
  bool shouldRepaint(_OverlayPainter old) => false;
}