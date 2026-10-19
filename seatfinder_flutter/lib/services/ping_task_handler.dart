import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:geolocator/geolocator.dart';

import '../core/supabase_client.dart';
import '../data/tavolo_repository.dart';

/// Costanti (stesse del PingService Kotlin).
class PingConfig {
  static const channelId = 'ping_tavolo_channel';
  static const distanzaMassimaMetri = 50.0;
  static const intervalloControlloMs = 5000;
  static const timeoutRispostaAllontanamento = Duration(minutes: 10);
  static const durataProroga = Duration(minutes: 10);

  /// Sostituisce onTaskRemoved (SECONDI_GRAZIA = 15 s): la UI invia un
  /// "heartbeat" ogni 5 s; se non ne arrivano più per questo tempo l'app
  /// è considerata chiusa e il tavolo viene liberato.
  static const timeoutAppChiusa = Duration(seconds: 45);

  static const btnMantieni = 'mantieni';
  static const btnLibera = 'libera';
}

@pragma('vm:entry-point')
void startPingCallback() {
  FlutterForegroundTask.setTaskHandler(PingTaskHandler());
}

/// Gira in un isolate separato, dentro il foreground service Android.
class PingTaskHandler extends TaskHandler {
  TavoloRepository? _repo;
  int? _idTavolo;
  int _userId = -1;
  double? _lat;
  double? _lng;

  bool _notificaInCorso = false;
  bool _inPausa = false;
  DateTime? _scadenzaRisposta;
  DateTime? _finePausa;
  DateTime _ultimoHeartbeat = DateTime.now();

  bool _tickInCorso = false;
  bool _chiusura = false;

  final FlutterLocalNotificationsPlugin _notif = FlutterLocalNotificationsPlugin();

  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {
    final url = await FlutterForegroundTask.getData<String>(key: 'sb_url');
    final key = await FlutterForegroundTask.getData<String>(key: 'sb_key');
    _userId = await FlutterForegroundTask.getData<int>(key: 'userId') ?? -1;
    _idTavolo = await FlutterForegroundTask.getData<int>(key: 'idTavolo');
    _lat = await FlutterForegroundTask.getData<double>(key: 'lat');
    _lng = await FlutterForegroundTask.getData<double>(key: 'lng');

    if (url != null && key != null) {
      _repo = TavoloRepository(createDbClient(url, key));
    }
    _ultimoHeartbeat = DateTime.now();

    try {
      await _notif.initialize(
        settings: const InitializationSettings(
          android: AndroidInitializationSettings('@mipmap/ic_launcher'),
          iOS: DarwinInitializationSettings(
            requestAlertPermission: true,
            requestBadgePermission: true,
            requestSoundPermission: true,
          ),
        ),
      );
    } catch (e) {
      debugPrint('Init notifiche nel servizio fallita: $e');
    }
  }

  @override
  void onRepeatEvent(DateTime timestamp) {
    unawaited(_tick());
  }

  Future<void> _tick() async {
    if (_tickInCorso || _chiusura) return;
    _tickInCorso = true;
    try {
      final now = DateTime.now();

      // App chiusa (nessun heartbeat) -> libera
      if (now.difference(_ultimoHeartbeat) > PingConfig.timeoutAppChiusa) {
        await _libera(PingMotivo.appChiusa);
        return;
      }
      // Nessuna risposta alla notifica entro 10 min -> libera
      if (_scadenzaRisposta != null && now.isAfter(_scadenzaRisposta!)) {
        await _libera(PingMotivo.allontanamento);
        return;
      }
      // Fine della proroga
      if (_finePausa != null && now.isAfter(_finePausa!)) {
        _inPausa = false;
        _finePausa = null;
      }
      // Controlla la distanza solo se non c'è notifica attiva né proroga
      if (!_notificaInCorso && !_inPausa) {
        await _controllaDistanza();
      }
    } finally {
      _tickInCorso = false;
    }
  }

  Future<void> _controllaDistanza() async {
    final latAncora = _lat;
    final lngAncora = _lng;
    if (latAncora == null || lngAncora == null) return;

    try {
      final pos = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 20),
        ),
      );
      final distanza = Geolocator.distanceBetween(
          latAncora, lngAncora, pos.latitude, pos.longitude);
      debugPrint('DEBUG_LOC Distanza dal tavolo: $distanza metri');

      if (distanza > PingConfig.distanzaMassimaMetri) {
        await _avviaProceduraAllontanamento();
      }
    } catch (e) {
      debugPrint('Errore controllo posizione: $e');
    }
  }


  Future<void> _avviaProceduraAllontanamento() async {
    if (_notificaInCorso || _chiusura) return;

    _notificaInCorso = true;
    _scadenzaRisposta =
        DateTime.now().add(PingConfig.timeoutRispostaAllontanamento);

    await FlutterForegroundTask.updateService(
      notificationTitle: 'Ti sei allontanato dal tavolo!',
      notificationText:
      'Vuoi mantenere il posto? Verrà liberato tra 10 minuti se non rispondi.',
      notificationButtons: const [
        NotificationButton(
          id: PingConfig.btnMantieni,
          text: 'Mantieni (+10 min)',
        ),
        NotificationButton(
          id: PingConfig.btnLibera,
          text: 'Libera',
        ),
      ],
    );
  }

  void _mantieni() {
    debugPrint('DEBUG_LOC Mantieni: pausa controlli per 10 minuti');
    _scadenzaRisposta = null;
    _notificaInCorso = false;
    _inPausa = true;
    _finePausa = DateTime.now().add(PingConfig.durataProroga);
    _ripristinaNotificaNormale();
  }

  void _ripristinaNotificaNormale() {
    FlutterForegroundTask.updateService(
      notificationTitle: 'Tavolo occupato',
      notificationText: 'Stai mantenendo la prenotazione del tavolo',
      notificationButtons: const [],
    );
  }

  Future<void> _libera(PingMotivo motivo) async {
    if (_chiusura) return;
    _chiusura = true;

    final id = _idTavolo;
    if (id != null && _repo != null) {
      await _repo!.liberaTavolo(id, _userId);
    }
    FlutterForegroundTask.sendDataToMain({'type': 'liberato'});
    await _mostraNotificaLiberato(motivo);
    await FlutterForegroundTask.stopService();
  }

  Future<void> _mostraNotificaLiberato(PingMotivo motivo) async {
    final msg = motivo == PingMotivo.allontanamento
        ? 'Ti sei allontanato, il tavolo è stato liberato.'
        : motivo == PingMotivo.appChiusa
        ? 'App chiusa, il tavolo è stato liberato.'
        : 'Il tavolo è stato liberato.';
    try {
      await _notif.show(
        id: 2,
        title: 'Tavolo liberato',
        body: msg,
        notificationDetails: const NotificationDetails(
          android: AndroidNotificationDetails(
            'tavolo_liberato_channel',
            'Tavolo liberato',
            importance: Importance.max,
            priority: Priority.high,
          ),
          iOS: DarwinNotificationDetails(
            presentAlert: true,
            presentBanner: true,
            presentSound: true,
          ),
        ),
      );
    } catch (e) {
      debugPrint('Notifica "liberato" non mostrata: $e');
    }
  }

  @override
  void onReceiveData(Object data) {
    if (data == 'heartbeat') {
      _ultimoHeartbeat = DateTime.now();
    }
  }

  @override
  void onNotificationButtonPressed(String id) {
    if (id == PingConfig.btnMantieni) {
      _mantieni();
    } else if (id == PingConfig.btnLibera) {
      debugPrint('DEBUG_LOC L\'utente ha scelto di liberare il tavolo');
      unawaited(_libera(PingMotivo.manuale));
    }
  }

  @override
  Future<void> onDestroy(DateTime timestamp, bool isTimeout) async {}
}

enum PingMotivo { allontanamento, appChiusa, manuale }