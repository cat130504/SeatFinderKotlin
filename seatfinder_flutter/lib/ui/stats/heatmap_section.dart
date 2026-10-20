import 'package:flutter/material.dart';

import '../../domain/models.dart';
import '../../domain/stats.dart';
import 'stats_colors.dart';

//sezione principale che mostra la heatmap dell'affluenza settimanale
class SezioneHeatmap extends StatelessWidget {
  const SezioneHeatmap({
    super.key,
    required this.rows,
    required this.todayDow,
    required this.oraCorrente,
    required this.giornoSel,
    required this.fasciaConsigliata,
  });

  final List<StatsFlussoRow> rows;
  final int todayDow;
  final int oraCorrente;
  final int giornoSel;
  final int? fasciaConsigliata;

  @override
  Widget build(BuildContext context) {
    //ottiene l'ordine dei giorni della settimana partendo da oggi
    final ordine = giorniDaOggi(todayDow);

    //aggrega i dati di affluenza raggruppandoli per giorno e ora
    final aggregata = <(int, int), int>{};
    for (final r in rows) {
      final k = (r.giornoSettimana, r.ora);
      aggregata[k] = (aggregata[k] ?? 0) + r.occupazioniTotali;
    }
    //trova il valore massimo di occupazione per normalizzare i colori della heatmap
    final maxTot = aggregata.values.fold<int>(1, (m, v) => v > m ? v : m);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Affluenza settimanale',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            //griglia della heatmap che mostra giorni e fasce orarie
            _HeatmapGrid(
              aggregata: aggregata,
              ordine: ordine,
              maxTot: maxTot,
              oraCorrente: oraCorrente,
              fasciaConsigliata: fasciaConsigliata,
              todayDow: todayDow,
              giornoSel: giornoSel,
            ),
            const SizedBox(height: 8),
            //legenda dei colori della scala di affluenza
            const _Legenda(),
          ],
        ),
      ),
    );
  }
}

//widget privato per disegnare la griglia tabellare della heatmap
class _HeatmapGrid extends StatelessWidget {
  const _HeatmapGrid({
    required this.aggregata,
    required this.ordine,
    required this.maxTot,
    required this.oraCorrente,
    required this.fasciaConsigliata,
    required this.todayDow,
    required this.giornoSel,
  });

  final Map<(int, int), int> aggregata;
  final List<int> ordine;
  final int maxTot;
  final int oraCorrente;
  final int? fasciaConsigliata;
  final int todayDow;
  final int giornoSel;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final primary = Theme.of(context).colorScheme.primary;

    return Column(
      children: [
        //intestazione con i nomi dei giorni della settimana
        Row(
          children: [
            const SizedBox(width: 26),
            for (final dow in ordine)
              Expanded(
                child: Text(
                  labelGiorni[dow],
                  textAlign: TextAlign.center,
                  style: tt.labelSmall?.copyWith(
                    fontSize: 10,
                    fontWeight: dow == todayDow ? FontWeight.w800 : FontWeight.bold,
                    color: dow == todayDow ? colAdesso : null,
                  ),
                ),
              ),
          ],
        ),
        const SizedBox(height: 2),
        //righe della griglia corrispondenti alle ore di apertura
        for (final h in StatsConfig.oreApertura)
          Padding(
            padding: const EdgeInsets.only(bottom: 2),
            child: Container(
              //evidenzia visivamente la riga corrispondente all'ora corrente
              decoration: h == oraCorrente
                  ? BoxDecoration(
                color: colAdesso.withValues(alpha: 0.10),
                borderRadius: BorderRadius.circular(4),
              )
                  : null,
              padding: h == oraCorrente
                  ? const EdgeInsets.symmetric(vertical: 1)
                  : EdgeInsets.zero,
              child: Row(
                children: [
                  //etichetta oraria a sinistra
                  SizedBox(
                    width: 26,
                    child: Text(
                      '$h',
                      style: tt.labelSmall?.copyWith(
                        fontWeight: h == oraCorrente ? FontWeight.w800 : FontWeight.normal,
                        color: h == oraCorrente ? colAdesso : null,
                      ),
                    ),
                  ),
                  //celle della heatmap per ciascun giorno della riga oraria corrente
                  for (final dow in ordine)
                    Expanded(
                      child: _Cella(
                        colore: coloreOccupazione(
                            ((aggregata[(dow, h)] ?? 0) / maxTot).clamp(0.0, 1.0)),
                        consigliata: dow == giornoSel && fasciaConsigliata == h,
                        bordo: primary,
                      ),
                    ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

//widget privato per singola cella della griglia
class _Cella extends StatelessWidget {
  const _Cella({required this.colore, required this.consigliata, required this.bordo});
  final Color colore;
  final bool consigliata;
  final Color bordo;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(1),
      child: SizedBox(
        height: 16,
        //se la cella è quella consigliata, disegna un bordo evidenziato attorno
        child: consigliata
            ? Container(
          decoration: BoxDecoration(
            border: Border.all(color: bordo, width: 2.5),
            borderRadius: BorderRadius.circular(4),
          ),
          padding: const EdgeInsets.all(2),
          child: DecoratedBox(
            decoration: BoxDecoration(
                color: colore, borderRadius: BorderRadius.circular(2)),
          ),
        )
            : DecoratedBox(
          decoration: BoxDecoration(
              color: colore, borderRadius: BorderRadius.circular(3)),
        ),
      ),
    );
  }
}

//widget privato per la legenda dei colori dell'affluenza
class _Legenda extends StatelessWidget {
  const _Legenda();

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Scala affluenza', style: tt.labelSmall),
        const SizedBox(height: 4),
        //barra colorata graduale che indica i livelli di affluenza
        SizedBox(
          height: 12,
          child: Row(
            children: [
              for (var i = 0; i <= 10; i++) ...[
                if (i > 0) const SizedBox(width: 2),
                Expanded(
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: coloreOccupazione(i / 10),
                      borderRadius: BorderRadius.circular(2),
                    ),
                    child: const SizedBox.expand(),
                  ),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 4),
        //etichette descrittive della legenda (Bassa, Media, Alta)
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('Bassa', style: tt.labelSmall),
            Text('Media', style: tt.labelSmall),
            Text('Alta', style: tt.labelSmall),
          ],
        ),
      ],
    );
  }
}