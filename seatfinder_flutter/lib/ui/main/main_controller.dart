import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';

import '../../auth/session_manager.dart';
import '../../core/supabase_client.dart';
import '../../data/edificio_repository.dart';
import '../../data/tavolo_repository.dart';
import '../../domain/models.dart';
import '../../services/gps_service.dart';

//classe map selezione per rappresentare lo stato attuale della mappa con caratteristica edificio e quota
class MapSelezione {
  final Edificio? edificio;
  final List<PianoQuota> piani;
  final String? quota;
  const MapSelezione({this.edificio, this.piani = const [], this.quota});
}

class MainController extends ChangeNotifier {

  //main controller ascolta i dati provenienti dal task in background (FlutterForegroundTask) ed
  //avvia l'inizializzazione.
  MainController() {
    FlutterForegroundTask.addTaskDataCallback(_onTaskData);
    _init();
  }

  //ottiene tutti i tavoli e gli edifici
  final _tavoli = TavoloRepository(AppSupabase.client);
  final _edificiRepo = EdificioRepository(AppSupabase.client);

  //dichiarazione dello stato reattivo
  int? tavoloAttuale;
  ({String edificio, String quota})? infoTavolo;
  List<Edificio> edifici = [];
  MapSelezione selezione = const MapSelezione();
  bool ready = false;
  int refreshTick = 0;
  double? tavoloLat;
  double? tavoloLng;
  Timer? _heartbeat;
  bool _disposed = false;

  //notify funzionane solo se non é stato fatto il dispose
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  //verifica se l'utente ha gia un tavolo occupato e carica i dati dal database
  Future<void> _init() async {
    try {
      final occ = await _tavoli.getOccupazioneAperta(SessionManager.instance.userId);
      tavoloLat = occ?.lat;
      tavoloLng = occ?.lng;
      await _setTavolo(occ?.id);
      await _caricaEdifici();
    } finally {
      ready = true; // qualunque cosa accada sblocchiamo la UI
      _notify();
    }
  }

  //caricamento degli edifici
  Future<void> _caricaEdifici() async {
    try {
      edifici = await _edificiRepo.getEdifici();
      _notify();
      if (edifici.isNotEmpty) await _caricaPiani(edifici.first);
    } catch (e) {
      debugPrint('SUPABASE_INIT Errore: $e');
    }
  }

  //caricamento dei piani
  Future<void> _caricaPiani(Edificio edificio) async {
    final piani = await _edificiRepo.getPiani(edificio.id);
    selezione = MapSelezione(
      edificio: edificio,
      piani: piani,
      quota: piani.isNotEmpty ? piani.first.quota : selezione.quota,
    );
    _notify();
  }

  //selezione asincrona di edificio carica i piani
  void selezionaEdificio(Edificio e) => _caricaPiani(e);

  //alla selezione del piano si crea un nuovo stato di MapSelezione per aggiornare la vista
  void selezionaPiano(String quota) {
    selezione = MapSelezione(
        edificio: selezione.edificio, piani: selezione.piani, quota: quota);
    _notify();
  }

  //imposta i valori dopo che l'utente occupa untavolo sia testuali che il tavolo attuale
  Future<void> _setTavolo(int? id) async {
    tavoloAttuale = id;
    refreshTick++;
    infoTavolo = id == null ? null : await _edificiRepo.getInfoTavolo(id);
    _notify();
    await _sincronizzaServizio();
  }

  //avvio o interruzione del servizio pingservice per verificare la posizione e l'occupazione
  Future<void> _sincronizzaServizio() async {
    final id = tavoloAttuale;
    if (id != null) {
      await GpsService.start(
        idTavolo: id,
        userId: SessionManager.instance.userId,
        lat: tavoloLat,
        lng: tavoloLng,
        onLiberato: _onLiberatoDalServizio,
      );
    } else {
      tavoloLat = null;
      tavoloLng = null;
      await GpsService.stop();
    }
  }
  void _onLiberatoDalServizio() {
    tavoloAttuale = null;
    infoTavolo = null;
    tavoloLat = null;
    tavoloLng = null;
    refreshTick++;
    _notify();
  }


  Future<void> onTavoloOccupato(int idTavolo, double? lat, double? lng) async {
    tavoloLat = lat;
    tavoloLng = lng;
    await _setTavolo(idTavolo);
  }

  Future<void> liberaTavolo() async {
    final id = tavoloAttuale;
    if (id == null) return;
    final ok = await _tavoli.liberaTavolo(id, SessionManager.instance.userId);
    if (ok) await _setTavolo(null);
  }
  //_onTaskData intercetta i messaggi inviati dal task in background
  //(ad es. se il sistema libera automaticamente il tavolo per allontanamento) resettando lo stato locale.
  //Il metodo logout libera il tavolo attivo prima di scollegare l'utente.
  //Infine, dispose pulisce il timer attivo, rimuove il listener del task in background e rilascia le risorse del controller.
  void _onTaskData(Object data) {
    if (data is Map && data['type'] == 'liberato') {
      tavoloAttuale = null;
      infoTavolo = null;
      refreshTick++;
      _heartbeat?.cancel();
      _heartbeat = null;
      _notify();
    }
  }

  Future<void> logout() async {
    final id = tavoloAttuale;
    if (id != null) {
      await _tavoli.liberaTavolo(id, SessionManager.instance.userId);
    }
    await _setTavolo(null);
    await SessionManager.instance.logout();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
