import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/stats.dart';
import 'heatmap_section.dart';
import 'miglior_orario_section.dart';
import 'stato_attuale_section.dart';
import 'stats_controllers.dart';

// schermata principale dedicata alla visualizzazione e all'analisi statistica dell'affluenza
class StatsScreen extends StatefulWidget {
  const StatsScreen({super.key});

  @override
  State<StatsScreen> createState() => _StatsScreenState();
}

class _StatsScreenState extends State<StatsScreen> {
  late final StatsController _c;
  Timer? _timer; //timer periodico per aggiornare in tempo reale lo stato attuale dei tavoli

  int? _edificioSel; //identificativo dell'edificio attualmente selezionato per il filtro
  String? _quotaSel; // quota o piano attualmente selezionato per le statistiche
  late int _giornoSel; // giorno della settimana selezionato per l'analisi oraria

  @override
  void initState() {
    super.initState();
    // inizializza il controller delle statistiche e si iscrive ai cambiamenti per aggiornare la ui
    _c = StatsController()..addListener(() => setState(() {}));
    // imposta il giorno selezionato di default su quello odierno
    _giornoSel = giorniDaOggi(giornoSettimanaOggi()).first;

    // effettua il primo aggiornamento immediato dello stato e avvia un timer periodico di trenta secondi
    _c.aggiornaStatoAttuale();
    _timer = Timer.periodic(
        const Duration(seconds: 30), (_) => _c.aggiornaStatoAttuale());
  }

  @override
  void dispose() {
    // cancella il timer periodico e rilascia le risorse del controller alla chiusura della schermata
    _timer?.cancel();
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final todayDow = giornoSettimanaOggi(now);
    final oraCorrente = now.hour;

    // filtra l'elenco dei piani in base all'edificio selezionato dall'utente
    final base = _edificioSel == null
        ? _c.pianoRows
        : _c.pianoRows.where((p) => p.edificio == _edificioSel).toList();
    final quote = (base.map((p) => p.quota).toSet().toList())..sort();

    // determina la quota effettiva da utilizzare, verificando che sia valida o prendendo la prima disponibile
    final quotaEff = (_quotaSel != null && quote.contains(_quotaSel))
        ? _quotaSel
        : (quote.isEmpty ? null : quote.first);

    // filtra le righe dello storico e lo stato attuale in base ai parametri selezionati
    final rowsFiltrate = _c.rows.where((r) => r.quota == quotaEff).toList();
    final statoFiltrato = _edificioSel == null
        ? _c.statoAttuale
        : _c.statoAttuale.where((s) => s.edificioId == _edificioSel).toList();

    // calcola l'ora minima e la fascia oraria consigliata per il giorno selezionato
    final oraMinima = _giornoSel == todayDow ? oraCorrente : 0;
    final fascia = calcolaFasciaConsigliata(rowsFiltrate, _giornoSel, oraMinima);

    // mostra un indicatore circolare di caricamento se i dati non sono ancora pronti
    if (_c.loading && _c.rows.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }

    final tt = Theme.of(context).textTheme;
    final totaleOcc = rowsFiltrate.fold<int>(0, (a, r) => a + r.occupazioniTotali);

    //struttura principale scorrevole contenente le varie sezioni di analisi e riepilogo
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          //sezione che mostra lo stato di occupazione in tempo reale suddiviso per edifici
          SezioneStatoAttuale(
            stato: statoFiltrato,
            edifici: _c.edifici,
            edificioSel: _edificioSel,
            onEdificioChange: (id) => setState(() => _edificioSel = id),
          ),
          const SizedBox(height: 20),
          Text('Analisi ultimi ${StatsConfig.giorniStorico} giorni',
              style: tt.titleMedium),
          const SizedBox(height: 20),
          // riga orizzontale di chip per la selezione del piano desiderato
          if (quote.isNotEmpty) ...[
            Text('Piano', style: tt.labelLarge),
            const SizedBox(height: 20),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  for (final q in quote)
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: FilterChip(
                        selected: q == quotaEff,
                        onSelected: (_) => setState(() => _quotaSel = q),
                        label: Text(q),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 20),
          ],
          //gestisce il caso in cui non siano presenti dati storici per i filtri applicati
          if (rowsFiltrate.isEmpty)
            Card(
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.all(24),
                alignment: Alignment.center,
                child: Text(
                  'Nessun dato storico disponibile per la selezione',
                  textAlign: TextAlign.center,
                  style: tt.bodyMedium,
                ),
              ),
            )
          else ...[
            //avviso informativo nel caso in cui i dati storici raccolti siano ancora troppo pochi
            if (totaleOcc < StatsConfig.sogliaPochiDati) ...[
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.errorContainer,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  'Pochi dati ($totaleOcc occupazioni). '
                      'Le statistiche diventano più affidabili con l\'uso.',
                  style: tt.bodySmall,
                ),
              ),
              const SizedBox(height: 20),
            ],
            //sezione della mappa di calore (heatmap) settimanale dei flussi
            SezioneHeatmap(
              rows: rowsFiltrate,
              todayDow: todayDow,
              oraCorrente: oraCorrente,
              giornoSel: _giornoSel,
              fasciaConsigliata: fascia,
            ),
            const SizedBox(height: 20),
            //sezione che evidenzia il miglior orario consigliato in base al giorno scelto
            SezioneMigliorOrario(
              rows: rowsFiltrate,
              todayDow: todayDow,
              giornoSel: _giornoSel,
              onGiornoChange: (g) => setState(() => _giornoSel = g),
              fasciaConsigliata: fascia,
            ),
          ],
        ],
      ),
    );
  }
}