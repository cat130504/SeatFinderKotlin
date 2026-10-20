import 'package:flutter/foundation.dart';

import '../../auth/session_manager.dart';
import '../../core/supabase_client.dart';
import '../../data/edificio_repository.dart';
import '../../data/stats_repository.dart';
import '../../domain/models.dart';
import '../../domain/stats.dart';

// StatsViewModel
// controller principale per la gestione e il recupero asincrono delle statistiche generali e dei flussi
class StatsController extends ChangeNotifier {
  StatsController() {
    _init();
  }

  // repository dedicati al recupero dei dati statistici e delle informazioni sugli edifici tramite supabase
  final _stats = StatsRepository(AppSupabase.client);
  final _edificiRepo = EdificioRepository(AppSupabase.client);

  bool loading = true;
  List<StatsFlussoRow> rows = [];
  List<PianoRow> pianoRows = [];
  List<Edificio> edifici = [];
  List<StatoPiano> statoAttuale = [];
  int ultimoAggiornamento = 0;
  bool _disposed = false;

  // metodo interno di utilità per notificare i listener evitando chiamate successive alla chiusura
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  // funzione di inizializzazione asincrona per caricare in parallelo edifici, piani e storico delle statistiche
  Future<void> _init() async {
    // recupera in background la lista degli edifici e dei piani per popolare i filtri dell'interfaccia
    () async {
      edifici = await _edificiRepo.getEdifici();
      pianoRows = await _edificiRepo.getPianoRows();
      _notify();
    }();

    // recupera lo storico dei flussi calcolato sugli ultimi n giorni configurati
    rows = await _stats.getStatsFlusso(StatsConfig.giorniStorico);
    loading = false;
    _notify();
  }

  // metodo pubblico per aggiornare a richiesta lo stato attuale di occupazione dei tavoli per ciascun piano
  Future<void> aggiornaStatoAttuale() async {
    final stato = await _stats.getStatoAttualePerPiano();
    statoAttuale = stato;
    ultimoAggiornamento = DateTime.now().millisecondsSinceEpoch;
    _notify();
  }

  @override
  void dispose() {
    // imposta il flag a true per bloccare future notifiche ed evitare memory leak al termine del ciclo di vita
    _disposed = true;
    super.dispose();
  }
}

/// ProfileStatsViewModel
// controller specifico per la gestione del recupero delle statistiche personali e dello storico utente
class ProfileStatsController extends ChangeNotifier {
  final _stats = StatsRepository(AppSupabase.client);

  bool loading = true;
  bool errore = false;
  List<OccupazioneUtente> occupazioni = [];
  bool _disposed = false;

  // metodo privato per inviare la notifica di aggiornamento alla ui in sicurezza
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  // metodo asincreno per caricare o aggiornare lo storico delle occupazioni personali dell'utente loggato
  Future<void> carica() async {
    // mostra lo spinner di caricamento solo durante la prima chiamata se la lista è vuota, altrimenti aggiorna silenziosamente
    loading = occupazioni.isEmpty;
    errore = false;
    _notify();
    try {
      occupazioni = await _stats.getOccupazioniUtente(SessionManager.instance.userId);
      loading = false;
    } catch (e) {
      debugPrint('ProfileStats Errore caricamento: $e');
      loading = false;
      // se l'utente possiede già dei dati in cache li mantiene, mostrando l'errore solo se la schermata è completamente vuota
      errore = occupazioni.isEmpty;
    }
    _notify();
  }

  @override
  void dispose() {
    // contrassegna il controller come dismesso per prevenire chiamate a notifyListeners dopo la chiusura
    _disposed = true;
    super.dispose();
  }
}