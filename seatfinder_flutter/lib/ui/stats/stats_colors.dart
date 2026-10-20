import 'package:flutter/material.dart';

import '../../domain/stats.dart';

// definizione delle costanti di colore utilizzate per comporre la scala cromatica continua della heatmap
const colBassa = Color(0xFF4CAF50);
const colMedia = Color(0xFFFFEB3B);
const colAlta = Color(0xFFFF9800);
const colPiena = Color(0xFFE53935);
const colAdesso = Color(0xFF1976D2);
const colVuota = Color(0xFFB8BEC9);

// funzione matematica per calcolare una sfumatura cromatica continua in base al valore numerico normalizzato
Color coloreOccupazione(double v) {
  final x = v.clamp(0.0, 1.0);
  if (x < 0.33) return Color.lerp(colBassa, colMedia, x / 0.33)!;
  if (x < 0.66) return Color.lerp(colMedia, colAlta, (x - 0.33) / 0.33)!;
  return Color.lerp(colAlta, colPiena, (x - 0.66) / 0.34)!;
}

// funzione di utilità per associare direttamente un colore specifico a ciascun livello predefinito di affluenza
Color coloreLivello(LivelloAffluenza l) => switch (l) {
  LivelloAffluenza.libera => colBassa,
  LivelloAffluenza.pocoAffollata => colAlta,
  LivelloAffluenza.affollata => colPiena,
};

// array costante contenente le etichette abbreviate dei giorni della settimana
const labelGiorni = ['Dom', 'Lun', 'Mar', 'Mer', 'Gio', 'Ven', 'Sab'];

/// Barra orizzontale (traccia grigia + riempimento).
// componente grafico per visualizzare una barra di avanzamento o riempimento personalizzata
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
// contenitore scorrevole orizzontalmente progettato per disporre una lista dinamica di chip di filtro
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