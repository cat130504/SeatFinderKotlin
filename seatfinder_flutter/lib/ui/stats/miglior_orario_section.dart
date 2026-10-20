import 'package:flutter/material.dart';

import '../../domain/models.dart';
import '../../domain/stats.dart';
import 'stats_colors.dart';

// Array costante con i nomi dei giorni della settimana per formattare i titoli
const _nomiGiorni = [
  'domenica', 'lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato'
];

// Sezione che mostra il miglior orario consigliato per un determinato giorno in base alle statistiche
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
    //ottiene l'elenco ordinato dei giorni partendo da oggi
    final giorni = giorniDaOggi(todayDow);

    //calcola il numero di occupazioni per la fascia consigliata (se presente)
    final occ = fasciaConsigliata == null
        ? 0
        : occupazioniFascia(rows, giornoSel, fasciaConsigliata!);

    //genera il titolo dinamico in base al giorno selezionato (Oggi o nome del giorno)
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
            //sottotitolo che indica la base temporale dello storico analizzato
            Text(
              'Basato sugli ultimi ${StatsConfig.giorniStorico} giorni',
              style: tt.bodySmall?.copyWith(color: cs.onSurfaceVariant),
            ),
            const SizedBox(height: 8),
            //riga di chip filtrabili per selezionare il giorno della settimana desiderato
            RigaChip(children: [
              for (final g in giorni)
                FilterChip(
                  selected: g == giornoSel,
                  onSelected: (_) => onGiornoChange(g),
                  label: Text(g == todayDow ? 'Oggi' : labelGiorni[g]),
                ),
            ]),
            const SizedBox(height: 8),
            //se esiste una fascia oraria consigliata, mostra un box colorato con i dettagli
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
                    //mostra l'intervallo orario (es. 10:00 - 11:00)
                    Text(
                      '$fasciaConsigliata:00 – ${fasciaConsigliata! + 1}:00',
                      style: tt.headlineSmall?.copyWith(
                          fontWeight: FontWeight.bold, color: cs.onPrimaryContainer),
                    ),
                    //testo descrittivo del livello di affluenza stimato
                    Text(
                      _testoAffluenza(occ),
                      style: tt.bodySmall
                          ?.copyWith(color: coloreLivello(livelloAffluenza(occ))),
                    ),
                  ],
                ),
              )
            else
            //messaggio di fallback se non ci sono dati disponibili per il giorno selezionato
              Text('Nessun dato per questo giorno.', style: tt.bodyMedium),
          ],
        ),
      ),
    );
  }
}

//funzione di utilità per generare la stringa descrittiva del livello di affluenza
String _testoAffluenza(int n) {
  final media = mediaGiornaliera(n).toStringAsFixed(1);
  return switch (livelloAffluenza(n)) {
    LivelloAffluenza.libera =>
    'Fascia storicamente libera · 0 occupazioni negli ultimi ${StatsConfig.giorniStorico} giorni',
    LivelloAffluenza.pocoAffollata => 'Poco affollata · $n occupazioni (~$media/giorno)',
    LivelloAffluenza.affollata => 'Fascia affollata · $n occupazioni (~$media/giorno)',
  };
}