import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/stats.dart';
import 'heatmap_section.dart';
import 'miglior_orario_section.dart';
import 'stato_attuale_section.dart';
import 'stats_controllers.dart';

class StatsScreen extends StatefulWidget {
  const StatsScreen({super.key});

  @override
  State<StatsScreen> createState() => _StatsScreenState();
}

class _StatsScreenState extends State<StatsScreen> {
  late final StatsController _c;
  Timer? _timer;

  int? _edificioSel;
  String? _quotaSel;
  late int _giornoSel;

  @override
  void initState() {
    super.initState();
    _c = StatsController()..addListener(() => setState(() {}));
    _giornoSel = giorniDaOggi(giornoSettimanaOggi()).first;

    _c.aggiornaStatoAttuale();
    _timer = Timer.periodic(
        const Duration(seconds: 30), (_) => _c.aggiornaStatoAttuale());
  }

  @override
  void dispose() {
    _timer?.cancel();
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final todayDow = giornoSettimanaOggi(now);
    final oraCorrente = now.hour;

    final base = _edificioSel == null
        ? _c.pianoRows
        : _c.pianoRows.where((p) => p.edificio == _edificioSel).toList();
    final quote = (base.map((p) => p.quota).toSet().toList())..sort();

    final quotaEff = (_quotaSel != null && quote.contains(_quotaSel))
        ? _quotaSel
        : (quote.isEmpty ? null : quote.first);

    final rowsFiltrate = _c.rows.where((r) => r.quota == quotaEff).toList();
    final statoFiltrato = _edificioSel == null
        ? _c.statoAttuale
        : _c.statoAttuale.where((s) => s.edificioId == _edificioSel).toList();

    final oraMinima = _giornoSel == todayDow ? oraCorrente : 0;
    final fascia = calcolaFasciaConsigliata(rowsFiltrate, _giornoSel, oraMinima);

    if (_c.loading && _c.rows.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }

    final tt = Theme.of(context).textTheme;
    final totaleOcc = rowsFiltrate.fold<int>(0, (a, r) => a + r.occupazioniTotali);

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
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
            SezioneHeatmap(
              rows: rowsFiltrate,
              todayDow: todayDow,
              oraCorrente: oraCorrente,
              giornoSel: _giornoSel,
              fasciaConsigliata: fascia,
            ),
            const SizedBox(height: 20),
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
