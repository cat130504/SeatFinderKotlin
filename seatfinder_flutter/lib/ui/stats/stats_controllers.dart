import 'package:flutter/foundation.dart';

import '../../auth/session_manager.dart';
import '../../core/supabase_client.dart';
import '../../data/edificio_repository.dart';
import '../../data/stats_repository.dart';
import '../../domain/models.dart';
import '../../domain/stats.dart';

/// StatsViewModel
class StatsController extends ChangeNotifier {
  StatsController() {
    _init();
  }

  final _stats = StatsRepository(AppSupabase.client);
  final _edificiRepo = EdificioRepository(AppSupabase.client);

  bool loading = true;
  List<StatsFlussoRow> rows = [];
  List<PianoRow> pianoRows = [];
  List<Edificio> edifici = [];
  List<StatoPiano> statoAttuale = [];
  int ultimoAggiornamento = 0;
  bool _disposed = false;

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Future<void> _init() async {
    // Edifici e mappa quota -> edificio (per i filtri)
    () async {
      edifici = await _edificiRepo.getEdifici();
      pianoRows = await _edificiRepo.getPianoRows();
      _notify();
    }();

    // Storico degli ultimi N giorni
    rows = await _stats.getStatsFlusso(StatsConfig.giorniStorico);
    loading = false;
    _notify();
  }

  Future<void> aggiornaStatoAttuale() async {
    final stato = await _stats.getStatoAttualePerPiano();
    statoAttuale = stato;
    ultimoAggiornamento = DateTime.now().millisecondsSinceEpoch;
    _notify();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}

/// ProfileStatsViewModel
class ProfileStatsController extends ChangeNotifier {
  final _stats = StatsRepository(AppSupabase.client);

  bool loading = true;
  bool errore = false;
  List<OccupazioneUtente> occupazioni = [];
  bool _disposed = false;

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Future<void> carica() async {
    // spinner solo al primo caricamento, poi aggiornamento silenzioso
    loading = occupazioni.isEmpty;
    errore = false;
    _notify();
    try {
      occupazioni = await _stats.getOccupazioniUtente(SessionManager.instance.userId);
      loading = false;
    } catch (e) {
      debugPrint('ProfileStats Errore caricamento: $e');
      loading = false;
      // se ho già dei dati li tengo, mostro l'errore solo a schermata vuota
      errore = occupazioni.isEmpty;
    }
    _notify();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
