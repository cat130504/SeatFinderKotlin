import 'package:flutter/material.dart';

import '../../domain/stats.dart';

// Scala cromatica continua
const colBassa = Color(0xFF4CAF50); // verde
const colMedia = Color(0xFFFFEB3B); // giallo
const colAlta = Color(0xFFFF9800); // arancio
const colPiena = Color(0xFFE53935); // rosso
const colAdesso = Color(0xFF1976D2); // blu "adesso"/oggi
const colVuota = Color(0xFFB8BEC9);

Color coloreOccupazione(double v) {
  final x = v.clamp(0.0, 1.0);
  if (x < 0.33) return Color.lerp(colBassa, colMedia, x / 0.33)!;
  if (x < 0.66) return Color.lerp(colMedia, colAlta, (x - 0.33) / 0.33)!;
  return Color.lerp(colAlta, colPiena, (x - 0.66) / 0.34)!;
}

Color coloreLivello(LivelloAffluenza l) => switch (l) {
      LivelloAffluenza.libera => colBassa,
      LivelloAffluenza.pocoAffollata => colAlta,
      LivelloAffluenza.affollata => colPiena,
    };

const labelGiorni = ['Dom', 'Lun', 'Mar', 'Mer', 'Gio', 'Ven', 'Sab'];

/// Barra orizzontale (traccia grigia + riempimento).
class BarraProgresso extends StatelessWidget {
  const BarraProgresso({super.key, required this.valore, required this.colore});
  final double valore; // 0..1
  final Color colore;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 10,
      width: double.infinity,
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(5),
      ),
      child: FractionallySizedBox(
        alignment: Alignment.centerLeft,
        widthFactor: valore.clamp(0.0, 1.0),
        child: Container(
          decoration: BoxDecoration(color: colore, borderRadius: BorderRadius.circular(5)),
        ),
      ),
    );
  }
}

/// Scorrimento orizzontale di chip (LazyRow).
class RigaChip extends StatelessWidget {
  const RigaChip({super.key, required this.children});
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            for (var i = 0; i < children.length; i++) ...[
              if (i > 0) const SizedBox(width: 8),
              children[i],
            ],
          ],
        ),
      );
}
