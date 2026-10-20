import 'dart:math' as math;
import 'dart:ui' as ui;
import 'table_icon.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../domain/models.dart';


class InteractiveMap extends StatefulWidget {
  const InteractiveMap({
    super.key,
    required this.assetPath,
    required this.tavoli,
    required this.onTableClick,
  });

  final String assetPath;
  final List<Tavolo> tavoli;
  final ValueChanged<Tavolo> onTableClick;

  @override
  State<InteractiveMap> createState() => _InteractiveMapState();
}


class _InteractiveMapState extends State<InteractiveMap> {
  //imposta il fattore massimo di zoom consentito sulla mappa (fino a 5x)
  static const _maxZoom = 5.0;

  //controller per gestire la matrice di trasformazione (pan e zoom) del widget InteractiveViewer
  final _tc = TransformationController();
  ui.Image? _img;
  Size? _lastViewport;
  bool _needsReset = true;

  //inizializzazione e caricamento immagine
  @override
  void initState() {
    super.initState();
    _loadImage();
  }

  //se il percorso dell'asset cambia (es. cambio piano o edificio), ricarica l'immagine
  @override
  void didUpdateWidget(InteractiveMap old) {
    super.didUpdateWidget(old);
    if (old.assetPath != widget.assetPath) {
      _loadImage();
    }
  }

  //funzione asincrona per caricare l'immagine della mappa dagli asset di Flutter
  Future<void> _loadImage() async {
    final path = widget.assetPath;
    final data = await rootBundle.load(path);
    final codec = await ui.instantiateImageCodec(data.buffer.asUint8List());
    final frame = await codec.getNextFrame();
    if (!mounted || path != widget.assetPath) return;
    setState(() {
      _img = frame.image;
      _needsReset = true;
    });
  }

  @override
  void dispose() {
    _tc.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final img = _img;
    //se l'immagine non è ancora caricata, mostra un indicatore di caricamento circolare
    if (img == null) return const Center(child: CircularProgressIndicator());

    return LayoutBuilder(builder: (context, box) {
      final vw = box.maxWidth;
      final vh = box.maxHeight;
      final iw = img.width.toDouble();
      final ih = img.height.toDouble();

      // minScale = max(scaleX, scaleY): l'immagine copre sempre la view
      final cover = math.max(vw / iw, vh / ih);
      final cw = iw * cover;
      final ch = ih * cover;

      final viewport = Size(vw, vh);
      if (_needsReset || _lastViewport != viewport) {
        _lastViewport = viewport;
        _needsReset = false;
        //centra inizialmente l'immagine della mappa all'interno del viewport
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (!mounted) return;
          _tc.value = Matrix4.translationValues((vw - cw) / 2, (vh - ch) / 2, 0);
        });
      }

      return ClipRect(
        child: InteractiveViewer(
          transformationController: _tc,
          constrained: false,
          boundaryMargin: EdgeInsets.zero,
          minScale: 1.0,
          maxScale: _maxZoom,
          child: GestureDetector(
            //rileva il tocco dell'utente sulla mappa
            onTapUp: (d) => _hitTest(d.localPosition, cw, ch),
            child: CustomPaint(
              size: Size(cw, ch),
              painter: _MapPainter(
                image: img,
                tavoli: widget.tavoli,
                controller: _tc,
              ),
            ),
          ),
        ),
      );
    });
  }

  //funzione di Hit Test per ricavare le coordinate dove andare ad aggiungere un tavolo
  void _hitTest(Offset p, double cw, double ch) {
    final zoom = _tc.value.getMaxScaleOnAxis();
    // raggio originale (0.75% della larghezza), ma mai sotto ~16 dp a schermo
    final radius = math.max(cw * _MapPainter.iconWidthPercent * 0.30, 16 / zoom);

    Tavolo? best;
    var bestDist = double.infinity;
    for (final t in widget.tavoli) {
      final dx = p.dx - t.x / 100 * cw;
      final dy = p.dy - t.y / 100 * ch;
      final d = math.sqrt(dx * dx + dy * dy);
      if (d < radius && d < bestDist) {
        best = t;
        bestDist = d;
      }
    }
    //se viene trovato un tavolo nel raggio del tocco, attiva la callback associata
    if (best != null) widget.onTableClick(best);
  }
}

//classe CustomPainter responsabile del disegno della mappa e dei tavoli sopra di essa
class _MapPainter extends CustomPainter {
  _MapPainter({
    required this.image,
    required this.tavoli,
    required this.controller,
  }) : super(repaint: controller);

  static const iconWidthPercent = 0.025;
  static const iconSizeDp = 24.0;
  static const dotRadiusDp = 6.0;

  //prima di questo zoom i puntini diventano icone (in Kotlin: scala > 1.2).
  static const zoomIcone = 1.8;

  //colori predefiniti per i diversi stati dei tavoli
  static const _libero = Color(0xFF4CAF50);
  static const _occupato = Color(0xFFFF9800);
  static const _pieno = Color(0xFFE53935);

  final ui.Image image;
  final List<Tavolo> tavoli;
  final TransformationController controller;
  //restituisce il colore corretto in base allo stato del tavolo
  Color _colore(Tavolo t) => t.isPieno ? _pieno : (t.isLibero ? _libero : _occupato);

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawImageRect(
      image,
      Rect.fromLTWH(0, 0, image.width.toDouble(), image.height.toDouble()),
      Offset.zero & size,
      Paint()..filterQuality = FilterQuality.medium,
    );

    final zoom = controller.value.getMaxScaleOnAxis();
    final fill = Paint()..isAntiAlias = true;

    //cicla attraverso tutti i tavoli per disegnarli sulla mappa
    for (final t in tavoli) {
      final c = Offset(t.x / 100 * size.width, t.y / 100 * size.height);
      fill.color = _colore(t);

      //se lo zoom è superiore alla soglia, disegna l'icona dettagliata del tavolo
      if (zoom > zoomIcone) {
        final s = iconSizeDp / zoom; // dimensione costante a schermo
        canvas.save();
        canvas.translate(c.dx, c.dy);
        canvas.rotate(t.rotazione * math.pi / 180);
        canvas.scale(s / tavoloViewport);
        canvas.translate(-tavoloViewport / 2, -tavoloViewport / 2);
        canvas.drawPath(tavoloPath, fill);
        canvas.restore();
      } else {
        //se lo zoom è basso disegna un semplice cerchio colorato
        canvas.drawCircle(c, dotRadiusDp / zoom, fill);
      }
    }
  }

  @override
  bool shouldRepaint(_MapPainter old) =>
      old.image != image || old.tavoli != tavoli;
}
