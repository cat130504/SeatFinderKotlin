import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';

import '../../auth/session_manager.dart';
import '../../core/supabase_client.dart';
import '../../data/edificio_repository.dart';
import '../../data/tavolo_repository.dart';
import '../../domain/models.dart';
import '../../services/ping_service.dart';

class MapSelezione {
  final Edificio? edificio;
  final List<PianoQuota> piani;
  final String? quota;
  const MapSelezione({this.edificio, this.piani = const [], this.quota});
}

/// Equivalente di MainViewModel + dello stato condiviso di TavoloRepository
/// (tavoloAttuale / refreshTick) + LaunchedEffect che avvia/ferma il servizio.
class MainController extends ChangeNotifier {
  MainController() {
    FlutterForegroundTask.addTaskDataCallback(_onTaskData);
    _init();
  }

  final _tavoli = TavoloRepository(AppSupabase.client);
  final _edificiRepo = EdificioRepository(AppSupabase.client);

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

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Future<void> _init() async {
    try {
      final id = await _tavoli.getTavoloUtente(SessionManager.instance.userId);
      await _setTavolo(id);
      await _caricaEdifici();
    } finally {
      ready = true; // qualunque cosa accada sblocchiamo la UI
      _notify();
    }
  }

  Future<void> _caricaEdifici() async {
    try {
      edifici = await _edificiRepo.getEdifici();
      _notify();
      if (edifici.isNotEmpty) await _caricaPiani(edifici.first);
    } catch (e) {
      debugPrint('SUPABASE_INIT Errore: $e');
    }
  }

  Future<void> _caricaPiani(Edificio edificio) async {
    final piani = await _edificiRepo.getPiani(edificio.id);
    selezione = MapSelezione(
      edificio: edificio,
      piani: piani,
      quota: piani.isNotEmpty ? piani.first.quota : selezione.quota,
    );
    _notify();
  }

  void selezionaEdificio(Edificio e) => _caricaPiani(e);

  void selezionaPiano(String quota) {
    selezione = MapSelezione(
        edificio: selezione.edificio, piani: selezione.piani, quota: quota);
    _notify();
  }

  // ---------- tavolo occupato ----------

  Future<void> _setTavolo(int? id) async {
    tavoloAttuale = id;
    refreshTick++;
    infoTavolo = id == null ? null : await _edificiRepo.getInfoTavolo(id);
    _notify();
    await _sincronizzaServizio();
  }

  Future<void> _sincronizzaServizio() async {
    final id = tavoloAttuale;
    _heartbeat?.cancel();
    _heartbeat = null;

    if (id != null) {
      await PingService.start(
        idTavolo: id,
        userId: SessionManager.instance.userId,
        lat: tavoloLat,
        lng: tavoloLng,
      );
      _heartbeat = Timer.periodic(
          const Duration(seconds: 5), (_) => PingService.sendHeartbeat());
    } else {
      tavoloLat = null;
      tavoloLng = null;
      await PingService.stop();
    }
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

  /// Il servizio in background ha liberato il tavolo (allontanamento / app chiusa).
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
    _heartbeat?.cancel();
    FlutterForegroundTask.removeTaskDataCallback(_onTaskData);
    super.dispose();
  }
}
