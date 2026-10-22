import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:geolocator/geolocator.dart';

import '../core/supabase_client.dart';
import '../data/tavolo_repository.dart';

//Costanti (stesse del PingService Kotlin).
// classe di configurazione statica contenente i parametri e le soglie per il servizio di monitoraggio in background
class GpsConfig {
  static const channelId = 'ping_tavolo_channel';
  static const distanzaMassimaMetri = 50.0;
  static const intervalloControlloMs = 5000;
  static const timeoutRispostaAllontanamento = Duration(minutes: 10);
  static const durataProroga = Duration(minutes: 10);

  //Sostituisce onTaskRemoved (SECONDI_GRAZIA = 15 s): la UI invia un
  //"heartbeat" ogni 5 s; se non ne arrivano più per questo tempo l'app
  //è considerata chiusa e il tavolo viene liberato.
  static const timeoutAppChiusa = Duration(seconds: 5);

  static const btnMantieni = 'mantieni';
  static const btnLibera = 'libera';
}

@pragma('vm:entry-point')
// funzione entry-point globale registrata per avviare il task handler del servizio in primo piano all'interno dell'isolate
void startPingCallback() {
  FlutterForegroundTask.setTaskHandler(PingTaskHandler());
}

//gira in un isolate separato, dentro il foreground service Android.
//gestore del task in background che esegue controlli periodici sulla posizione e gestisce i timeout di allontanamento
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
  // metodo eseguito all'avvio del task in background per inizializzare le configurazioni, i client e le notifiche locali
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
  // metodo richiamato ripetutamente a intervalli regolari per eseguire la logica di controllo periodica
  void onRepeatEvent(DateTime timestamp) {
    unawaited(_tick());
  }

  // gestisce il ciclo di controllo periodico verificando lo stato dell'app, i timeout e la distanza geografica
  Future<void> _tick() async {
    if (_tickInCorso || _chiusura) return;
    _tickInCorso = true;
    try {
      final now = DateTime.now();

      // App chiusa (nessun heartbeat) -> libera
      if (now.difference(_ultimoHeartbeat) > GpsConfig.timeoutAppChiusa) {
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

  // rileva la posizione GPS corrente e verifica se la distanza dal tavolo supera la soglia massima consentita
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

      if (distanza > GpsConfig.distanzaMassimaMetri) {
        await _avviaProceduraAllontanamento();
      }
    } catch (e) {
      debugPrint('Errore controllo posizione: $e');
    }
  }


  // avvia la procedura di allontanamento aggiornando la notifica in primo piano con i pulsanti interattivi di scelta
  Future<void> _avviaProceduraAllontanamento() async {
    if (_notificaInCorso || _chiusura) return;

    _notificaInCorso = true;
    _scadenzaRisposta =
        DateTime.now().add(GpsConfig.timeoutRispostaAllontanamento);

    await FlutterForegroundTask.updateService(
      notificationTitle: 'Ti sei allontanato dal tavolo!',
      notificationText:
      'Vuoi mantenere il posto? Verrà liberato tra 10 minuti se non rispondi.',
      notificationButtons: const [
        NotificationButton(
          id: GpsConfig.btnMantieni,
          text: 'Mantieni (+10 min)',
        ),
        NotificationButton(
          id: GpsConfig.btnLibera,
          text: 'Libera',
        ),
      ],
    );
  }

  // proroga la prenotazione del tavolo mettendo in pausa i controlli di distanza per il tempo stabilito
  void _mantieni() {
    debugPrint('DEBUG_LOC Mantieni: pausa controlli per 10 minuti');
    _scadenzaRisposta = null;
    _notificaInCorso = false;
    _inPausa = true;
    _finePausa = DateTime.now().add(GpsConfig.durataProroga);
    _ripristinaNotificaNormale();
  }

  // ripristina la notifica in primo piano standard rimuovendo i pulsanti di interazione
  void _ripristinaNotificaNormale() {
    FlutterForegroundTask.updateService(
      notificationTitle: 'Tavolo occupato',
      notificationText: 'Stai mantenendo la prenotazione del tavolo',
      notificationButtons: const [],
    );
  }

  // libera il tavolo sul database, invia il messaggio di aggiornamento all'app principale e arresta il servizio
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

  // mostra una notifica locale di sistema per informare l'utente che il tavolo è stato liberato a causa di un evento
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
  // riceve i dati inviati dal thread principale, aggiornando il timestamp dell'ultimo segnale di vita (heartbeat) ricevuto
  void onReceiveData(Object data) {
    if (data == 'heartbeat') {
      _ultimoHeartbeat = DateTime.now();
    }
  }

  @override
  // gestisce la pressione dei pulsanti interattivi presenti all'interno della notifica del servizio in primo piano
  void onNotificationButtonPressed(String id) {
    if (id == GpsConfig.btnMantieni) {
      _mantieni();
    } else if (id == GpsConfig.btnLibera) {
      debugPrint('DEBUG_LOC L\'utente ha scelto di liberare il tavolo');
      unawaited(_libera(PingMotivo.manuale));
    }
  }

  @override
  // metodo richiamato alla distruzione del task in background per il rilascio delle risorse
  Future<void> onDestroy(DateTime timestamp, bool isTimeout) async {}
}

// enumerativo che elenca i possibili motivi per cui un tavolo viene liberato dal servizio automatico
enum PingMotivo { allontanamento, appChiusa, manuale }