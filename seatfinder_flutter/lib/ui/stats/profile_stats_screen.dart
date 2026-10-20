import 'package:flutter/material.dart';

import '../../domain/models.dart';
import '../../domain/stats.dart';
import 'stats_colors.dart';
import 'stats_controllers.dart';

// schermata principale delle statistiche del profilo utente
class ProfileStatsScreen extends StatefulWidget {
  const ProfileStatsScreen({super.key});

  @override
  State<ProfileStatsScreen> createState() => _ProfileStatsScreenState();
}

class _ProfileStatsScreenState extends State<ProfileStatsScreen> {
  late final ProfileStatsController _c;

  @override
  void initState() {
    super.initState();
    // inizializza il controller delle statistiche e si iscrive ai cambiamenti di stato per aggiornare la ui
    _c = ProfileStatsController()..addListener(() => setState(() {}));
    // attende il completamento del primo rendering per avviare il caricamento asincrono dei dati personali
    WidgetsBinding.instance.addPostFrameCallback((_) => _c.carica());
  }

  @override
  void dispose() {
    // rilascia le risorse allocate dal controller quando la schermata viene rimossa definitivamente
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // mostra un indicatore circolare di caricamento finché i dati non sono completamente pronti
    if (_c.loading) return const Center(child: CircularProgressIndicator());

    // gestisce lo stato di errore mostrando un avviso e un pulsante per ritentare l'operazione
    if (_c.errore) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Impossibile caricare le statistiche'),
            const SizedBox(height: 8),
            FilledButton(onPressed: _c.carica, child: const Text('Riprova')),
          ],
        ),
      );
    }

    // mostra un messaggio vuoto se l'utente non possiede alcuno storico di occupazione registrato
    if (_c.occupazioni.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text('Non hai ancora occupato nessun tavolo',
              textAlign: TextAlign.center),
        ),
      );
    }

    final occ = _c.occupazioni;
    // struttura principale scorrevole che racchiude tutte le sezioni statistiche del profilo
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          _SezioneRiepilogo(occ),
          const SizedBox(height: 20),
          _SezioneHeatmapProfilo(occ),
          const SizedBox(height: 20),
          _SezioneLuoghi(occ),
          const SizedBox(height: 20),
          _SezioneUltime(occ),
        ],
      ),
    );
  }
}

// ---------- helper ----------
// estensione di supporto per estrarre facilmente il giorno della settimana e l'ora locale da un'occupazione
extension _OccX on OccupazioneUtente {
  DateTime get _locale => inizio.toLocal();
  int get dow => _locale.weekday % 7; // 0 = domenica
  int get ora => _locale.hour;
}

// funzione di utilità generica per calcolare l'elemento con la frequenza maggiore in una collezione
K? _piuFrequente<K>(Iterable<K> chiavi) {
  final conteggio = <K, int>{};
  for (final k in chiavi) {
    conteggio[k] = (conteggio[k] ?? 0) + 1;
  }
  K? best;
  var bestN = -1;
  conteggio.forEach((k, n) {
    if (n > bestN) {
      best = k;
      bestN = n;
    }
  });
  return best;
}

// funzione di utilità per contare e mappare le occorrenze di ciascun elemento all'interno di un iterabile
Map<K, int> _conta<K>(Iterable<K> chiavi) {
  final m = <K, int>{};
  for (final k in chiavi) {
    m[k] = (m[k] ?? 0) + 1;
  }
  return m;
}

// ---------- 1. Riepilogo ----------

// sezione dedicata alla visualizzazione sintetica delle metriche chiave e delle preferenze utente
class _SezioneRiepilogo extends StatelessWidget {
  const _SezioneRiepilogo(this.occ);
  final List<OccupazioneUtente> occ;

  @override
  Widget build(BuildContext context) {
    final giornoTop = _piuFrequente(occ.map((o) => o.dow));
    final oraTop = _piuFrequente(occ.map((o) => o.ora));
    final pianoTop = _piuFrequente(occ.map((o) => (o.edificioNome, o.quota)));

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Le tue statistiche', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(child: _Tessera('Occupazioni', '${occ.length}')),
              const SizedBox(width: 12),
              Expanded(
                  child: _Tessera('Giorno preferito',
                      giornoTop == null ? '-' : labelGiorni[giornoTop])),
            ]),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(
                  child: _Tessera('Fascia preferita',
                      oraTop == null ? '-' : '$oraTop:00')),
              const SizedBox(width: 12),
              Expanded(
                  child: _Tessera('Piano preferito',
                      pianoTop == null ? '-' : '${pianoTop.$1} · ${pianoTop.$2}')),
            ]),
          ],
        ),
      ),
    );
  }
}

// componente grafico singolo a forma di tessera per presentare una singola metrica di riepilogo
class _Tessera extends StatelessWidget {
  const _Tessera(this.label, this.valore);
  final String label;
  final String valore;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final tt = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: cs.primaryContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: tt.labelMedium?.copyWith(color: cs.onPrimaryContainer)),
          Text(valore,
              style: tt.titleLarge?.copyWith(
                  fontWeight: FontWeight.bold, color: cs.onPrimaryContainer)),
        ],
      ),
    );
  }
}

// ---------- 2. Heatmap personale ----------

// sezione che riproduce una mappa di calore basata esclusivamente sulle abitudini di occupazione dell'utente
class _SezioneHeatmapProfilo extends StatelessWidget {
  const _SezioneHeatmapProfilo(this.occ);
  final List<OccupazioneUtente> occ;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final todayDow = giornoSettimanaOggi();
    final ordine = giorniDaOggi(todayDow);
    final mappa = _conta(occ.map((o) => (o.dow, o.ora)));
    final maxN = mappa.values.fold<int>(1, (m, v) => v > m ? v : m);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Le tue fasce di occupazione', style: tt.titleMedium),
            const SizedBox(height: 8),
            // riga di intestazione contenente i giorni della settimana ordinati a partire da oggi
            Row(children: [
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
            ]),
            const SizedBox(height: 2),
            // iterazione sulle fasce orarie della giornata per costruire la griglia della heatmap
            for (final h in StatsConfig.oreApertura)
              Padding(
                padding: const EdgeInsets.only(bottom: 2),
                child: Row(children: [
                  SizedBox(width: 26, child: Text('$h', style: tt.labelSmall)),
                  for (final dow in ordine)
                    Expanded(child: _CellaProfilo(mappa[(dow, h)] ?? 0, maxN)),
                ]),
              ),
            const SizedBox(height: 8),
            const _LegendaProfilo(),
          ],
        ),
      ),
    );
  }
}

// componente che rappresenta una singola cella all'interno della griglia della heatmap personale
class _CellaProfilo extends StatelessWidget {
  const _CellaProfilo(this.n, this.maxN);
  final int n;
  final int maxN;

  @override
  Widget build(BuildContext context) {
    // assegna un colore neutro se il conteggio è zero, altrimenti calcola la tonalità cromatica in base al massimo
    final colore = n == 0 ? colVuota.withValues(alpha: 0.35) : coloreOccupazione(n / maxN);
    return Padding(
      padding: const EdgeInsets.all(1),
      child: Container(
        height: 16,
        decoration: BoxDecoration(
          color: colore,
          borderRadius: BorderRadius.circular(3),
          border: Border.all(
            width: 0.5,
            color: n == 0 ? colVuota.withValues(alpha: 0.7) : Colors.transparent,
          ),
        ),
      ),
    );
  }
}

// legenda esplicativa della scala cromatica utilizzata nella heatmap del profilo utente
class _LegendaProfilo extends StatelessWidget {
  const _LegendaProfilo();

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Scala occupazioni', style: tt.labelSmall),
        const SizedBox(height: 4),
        SizedBox(
          height: 12,
          child: Row(children: [
            // cella iniziale dedicata specificamente a rappresentare l'assenza di dati o zero occupazioni
            Expanded(
              child: Container(
                decoration: BoxDecoration(
                  color: colVuota.withValues(alpha: 0.35),
                  borderRadius: BorderRadius.circular(2),
                  border: Border.all(width: 0.5, color: colVuota.withValues(alpha: 0.7)),
                ),
              ),
            ),
            for (var i = 0; i <= 10; i++) ...[
              const SizedBox(width: 2),
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
          ]),
        ),
        const SizedBox(height: 4),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('Nessuna', style: tt.labelSmall),
            Text('Poche', style: tt.labelSmall),
            Text('Molte', style: tt.labelSmall),
          ],
        ),
      ],
    );
  }
}

// ---------- 3. Luoghi più usati ----------

// sezione che analizza e ordina gli edifici e i piani frequentati con maggiore assiduità
class _SezioneLuoghi extends StatelessWidget {
  const _SezioneLuoghi(this.occ);
  final List<OccupazioneUtente> occ;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final luoghi = _conta(occ.map((o) => (o.edificioNome, o.quota))).entries.toList()
      ..sort((a, b) => b.value.compareTo(a.value));
    final maxN = luoghi.isEmpty ? 1 : luoghi.first.value;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Dove occupi i tavoli', style: tt.titleMedium),
            const SizedBox(height: 12),
            // genera una barra di progresso proporzionale per ciascun luogo registrato nello storico
            for (final e in luoghi)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text('${e.key.$1} · ${e.key.$2}', style: tt.labelLarge),
                        Text('${e.value} volte',
                            style: tt.bodySmall?.copyWith(color: cs.onSurfaceVariant)),
                      ],
                    ),
                    const SizedBox(height: 4),
                    BarraProgresso(valore: e.value / maxN, colore: cs.primary),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

// ---------- 4. Ultime occupazioni ----------

// sezione cronologica che elenca in ordine inverso le ultime attività di occupazione svolte
class _SezioneUltime extends StatelessWidget {
  const _SezioneUltime(this.occ);
  final List<OccupazioneUtente> occ;

  // funzione interna per formattare la data e l'orario in una stringa leggibile e compatta
  String _fmt(DateTime d) {
    String p(int n) => n.toString().padLeft(2, '0');
    final l = d.toLocal();
    return '${p(l.day)}/${p(l.month)} · ${p(l.hour)}:${p(l.minute)}';
  }

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final ultime = ([...occ]..sort((a, b) => b.inizio.compareTo(a.inizio))).take(10);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Ultime occupazioni', style: tt.titleMedium),
            const SizedBox(height: 8),
            // cicla sulle ultime dieci occupazioni ordinate cronologicamente dalla più recente alla meno recente
            for (final o in ultime) ...[
              Text('${o.edificioNome} · ${o.quota}', style: tt.labelLarge),
              Text('Tavolo ${o.tavoloId} · ${_fmt(o.inizio)}',
                  style: tt.bodySmall?.copyWith(color: cs.onSurfaceVariant)),
              const Divider(),
            ],
          ],
        ),
      ),
    );
  }
}