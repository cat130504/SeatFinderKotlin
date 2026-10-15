// Modelli di dominio + parsing JSON (in Kotlin erano divisi tra dto/ e domain/model/).

int _int(dynamic v) => (v as num).toInt();
double _dbl(dynamic v) => (v as num).toDouble();

class Edificio {
  final int id;
  final String nome;
  const Edificio({required this.id, required this.nome});

  factory Edificio.fromJson(Map<String, dynamic> j) =>
      Edificio(id: _int(j['id']), nome: j['nome'].toString());
}

class PianoQuota {
  final String quota;
  const PianoQuota(this.quota);

  factory PianoQuota.fromJson(Map<String, dynamic> j) =>
      PianoQuota(j['quota'].toString());
}

class PianoRow {
  final String quota;
  final int edificio;
  const PianoRow({required this.quota, required this.edificio});

  factory PianoRow.fromJson(Map<String, dynamic> j) =>
      PianoRow(quota: j['quota'].toString(), edificio: _int(j['edificio']));
}

class Tavolo {
  final int id;
  final double x; // percentuale 0..100 sulla planimetria
  final double y;
  final double rotazione;
  final String quota;
  final bool isOccupato;
  final bool presa;
  final int posti;
  final int postiDisponibili;

  const Tavolo({
    required this.id,
    required this.x,
    required this.y,
    this.rotazione = 0,
    required this.quota,
    this.isOccupato = false,
    this.presa = false,
    this.posti = 0,
    required this.postiDisponibili,
  });

  bool get isPieno => postiDisponibili == 0;
  bool get isLibero => postiDisponibili == posti;

  factory Tavolo.fromJson(Map<String, dynamic> j) => Tavolo(
        id: _int(j['id']),
        x: _dbl(j['x']),
        y: _dbl(j['y']),
        rotazione: j['rotazione'] == null ? 0 : _dbl(j['rotazione']),
        quota: j['quota'].toString(),
        isOccupato: (j['is_occupato'] as bool?) ?? false,
        presa: (j['presa'] as bool?) ?? false,
        posti: j['posti'] == null ? 0 : _int(j['posti']),
        postiDisponibili:
            j['postiDisponibili'] == null ? 4 : _int(j['postiDisponibili']),
      );
}

class StatoPiano {
  final String quota;
  final int edificioId;
  final String edificioNome;
  final int tavoliOccupati;
  final int tavoliTotali;
  final int postiLiberi;
  final int postiTotali;

  const StatoPiano({
    required this.quota,
    required this.edificioId,
    required this.edificioNome,
    required this.tavoliOccupati,
    required this.tavoliTotali,
    required this.postiLiberi,
    required this.postiTotali,
  });

  int get tavoliLiberi => tavoliTotali - tavoliOccupati;
  double get percentualeOccupazione =>
      tavoliTotali == 0 ? 0 : tavoliOccupati / tavoliTotali;
  double get disponibilita => 1 - percentualeOccupazione;
}

class OccupazioneUtente {
  final int tavoloId;
  final String edificioNome;
  final String quota;
  final DateTime inizio;
  final double? lat;
  final double? lng;

  const OccupazioneUtente({
    required this.tavoloId,
    required this.edificioNome,
    required this.quota,
    required this.inizio,
    this.lat,
    this.lng,
  });

  /// Risposta di:
  /// id_tavolo, data_occupazione, lat_occupazione, lng_occupazione,
  /// tavolo:id_tavolo(quota, piano(edificio(nome)))
  factory OccupazioneUtente.fromJson(Map<String, dynamic> j) {
    final tavolo = j['tavolo'] as Map<String, dynamic>;
    final piano = tavolo['piano'] as Map<String, dynamic>;
    final edificio = piano['edificio'] as Map<String, dynamic>;
    return OccupazioneUtente(
      tavoloId: _int(j['id_tavolo']),
      edificioNome: edificio['nome'].toString(),
      quota: tavolo['quota'].toString(),
      inizio: _parseTimestamp(j['data_occupazione'].toString()),
      lat: j['lat_occupazione'] == null ? null : _dbl(j['lat_occupazione']),
      lng: j['lng_occupazione'] == null ? null : _dbl(j['lng_occupazione']),
    );
  }
}

/// Se la colonna non ha fuso orario, Dart la interpreta come ora locale
/// del telefono (stesso comportamento della versione Kotlin).
DateTime _parseTimestamp(String s) => DateTime.parse(s.replaceFirst(' ', 'T'));

class StatsFlussoRow {
  final String quota;
  final int giornoSettimana; // 0 = domenica ... 6 = sabato
  final int ora; // 0..23 (Europe/Rome)
  final int occupazioniTotali;
  final int postiTotali;

  const StatsFlussoRow({
    required this.quota,
    required this.giornoSettimana,
    required this.ora,
    required this.occupazioniTotali,
    required this.postiTotali,
  });

  factory StatsFlussoRow.fromJson(Map<String, dynamic> j) => StatsFlussoRow(
        quota: j['quota'].toString(),
        giornoSettimana: _int(j['giorno_settimana']),
        ora: _int(j['ora']),
        occupazioniTotali: _int(j['occupazioni_totali']),
        postiTotali: _int(j['posti_totali']),
      );
}
