import 'models.dart';

class StatsConfig {
  static const giorniStorico = 30;
  static const oreApertura = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18]; // 8..18
  static const giorniFeriali = [1, 2, 3, 4, 5];
  static const sogliaPochiDati = 100;
  static const sogliaPocoAffollata = 10;
}

enum LivelloAffluenza { libera, pocoAffollata, affollata }

/// Giorno corrente della settimana, 0 = domenica ... 6 = sabato.
int giornoSettimanaOggi([DateTime? now]) => (now ?? DateTime.now()).weekday % 7;

List<int> giorniDaOggi(int todayDow) {
  final out = <int>[];
  var d = StatsConfig.giorniFeriali.contains(todayDow) ? todayDow : 1;
  for (var i = 0; i < 5; i++) {
    out.add(d);
    d = d == 5 ? 1 : d + 1;
  }
  return out;
}

int? calcolaFasciaConsigliata(
    List<StatsFlussoRow> rows, int giornoSettimana, int oraMinima) {
  final perOra = <int, int>{};
  for (final r in rows) {
    if (r.giornoSettimana != giornoSettimana) continue;
    if (!StatsConfig.oreApertura.contains(r.ora)) continue;
    perOra[r.ora] = (perOra[r.ora] ?? 0) + r.occupazioniTotali;
  }
  final future = Map.fromEntries(perOra.entries.where((e) => e.key >= oraMinima));
  final candidati = future.isNotEmpty ? future : perOra;
  if (candidati.isEmpty) return null;

  MapEntry<int, int>? best;
  for (final e in candidati.entries) {
    if (best == null || e.value < best.value) best = e;
  }
  return best?.key;
}

int occupazioniFascia(List<StatsFlussoRow> rows, int giornoSettimana, int ora) =>
    rows
        .where((r) => r.giornoSettimana == giornoSettimana && r.ora == ora)
        .fold(0, (a, r) => a + r.occupazioniTotali);

LivelloAffluenza livelloAffluenza(int occupazioni) {
  if (occupazioni == 0) return LivelloAffluenza.libera;
  if (occupazioni <= StatsConfig.sogliaPocoAffollata) {
    return LivelloAffluenza.pocoAffollata;
  }
  return LivelloAffluenza.affollata;
}

double mediaGiornaliera(int occupazioni) =>
    occupazioni / (StatsConfig.giorniStorico / 7);
