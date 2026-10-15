import 'package:flutter/material.dart';

import '../../domain/models.dart';
import '../../domain/stats.dart';
import 'stats_colors.dart';

const _nomiGiorni = [
  'domenica', 'lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato'
];

class SezioneMigliorOrario extends StatelessWidget {
  const SezioneMigliorOrario({
    super.key,
    required this.rows,
    required this.todayDow,
    required this.giornoSel,
    required this.onGiornoChange,
    required this.fasciaConsigliata,
  });

  final List<StatsFlussoRow> rows;
  final int todayDow;
  final int giornoSel;
  final ValueChanged<int> onGiornoChange;
  final int? fasciaConsigliata;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final giorni = giorniDaOggi(todayDow);

    final occ = fasciaConsigliata == null
        ? 0
        : occupazioniFascia(rows, giornoSel, fasciaConsigliata!);

    final titolo = giornoSel == todayDow
        ? 'Miglior orario per oggi'
        : 'Miglior orario per ${_nomiGiorni[giornoSel]}';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(titolo, style: tt.titleMedium),
            const SizedBox(height: 8),
            Text(
              'Basato sugli ultimi ${StatsConfig.giorniStorico} giorni',
              style: tt.bodySmall?.copyWith(color: cs.onSurfaceVariant),
            ),
            const SizedBox(height: 8),
            RigaChip(children: [
              for (final g in giorni)
                FilterChip(
                  selected: g == giornoSel,
                  onSelected: (_) => onGiornoChange(g),
                  label: Text(g == todayDow ? 'Oggi' : labelGiorni[g]),
                ),
            ]),
            const SizedBox(height: 8),
            if (fasciaConsigliata != null)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: cs.primaryContainer,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Fascia consigliata',
                        style: tt.labelMedium?.copyWith(color: cs.onPrimaryContainer)),
                    Text(
                      '$fasciaConsigliata:00 – ${fasciaConsigliata! + 1}:00',
                      style: tt.headlineSmall?.copyWith(
                          fontWeight: FontWeight.bold, color: cs.onPrimaryContainer),
                    ),
                    // Colore del livello come in Kotlin (resta leggibile su blu pieno
                    // solo per i toni chiari: se serve, metti un chip bianco dietro)
                    Text(
                      _testoAffluenza(occ),
                      style: tt.bodySmall
                          ?.copyWith(color: coloreLivello(livelloAffluenza(occ))),
                    ),
                  ],
                ),
              )
            else
              Text('Nessun dato per questo giorno.', style: tt.bodyMedium),
          ],
        ),
      ),
    );
  }
}

String _testoAffluenza(int n) {
  final media = mediaGiornaliera(n).toStringAsFixed(1);
  return switch (livelloAffluenza(n)) {
    LivelloAffluenza.libera =>
      'Fascia storicamente libera · 0 occupazioni negli ultimi ${StatsConfig.giorniStorico} giorni',
    LivelloAffluenza.pocoAffollata => 'Poco affollata · $n occupazioni (~$media/giorno)',
    LivelloAffluenza.affollata => 'Fascia affollata · $n occupazioni (~$media/giorno)',
  };
}
