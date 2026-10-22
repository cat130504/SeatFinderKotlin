import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:geolocator/geolocator.dart';

import '../core/supabase_client.dart';
import '../data/tavolo_repository.dart';

class GpsConfig {
  static const distanzaMassimaMetri = 50.0;
  static const accuratezzaMaxMetri = 50.0;
  static const intervalloPing = Duration(minutes: 1);
  static const timeoutRisposta = Duration(minutes: 10);
  static const durataProroga = Duration(minutes: 10);
}

/// Ping + controllo distanza, nell'isolate principale.
/// Lo stream di posizione tiene viva l'app in background su Android (foreground
/// service di geolocator) e su iOS (background location).
class GpsService {
  GpsService._();

  static final _notif = FlutterLocalNotificationsPlugin();
  static final _repo = TavoloRepository(AppSupabase.client);
  static bool _notifPronte = false;

  static int? _idTavolo;
  static int _userId = -1;
  static double? _lat;
  static double? _lng;
  static void Function()? _onLiberato;

  static StreamSubscription<Position>? _sub;
  static Timer? _pingTimer;
  static Timer? _scadenzaTimer;
  static Timer? _prorogaTimer;
  static bool _allontanato = false;
  static bool _inProroga = false;

  // ---------- permessi e posizione puntuale ----------

  /// Da chiamare all'avvio (MainScreen.initState).
  static Future<void> richiediPermessi() async {
    await _initNotifiche();
    await _notif
        .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
        ?.requestNotificationsPermission();
    await _notif
        .resolvePlatformSpecificImplementation<IOSFlutterLocalNotificationsPlugin>()
        ?.requestPermissions(alert: true, badge: false, sound: true);

    if (!await Geolocator.isLocationServiceEnabled()) return;
    final p = await Geolocator.checkPermission();
    if (p == LocationPermission.denied) await Geolocator.requestPermission();
  }

  static Future<bool> _permessoOk() async {
    if (!await Geolocator.isLocationServiceEnabled()) return false;
    final p = await Geolocator.checkPermission();
    return p == LocationPermission.whileInUse || p == LocationPermission.always;
  }

  /// Posizione al momento della scansione (con fallback sull'ultima nota).
  static Future<({double lat, double lng})?> ottieniPosizione() async {
    if (!await _permessoOk()) return null;
    try {
      final p = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 10),
        ),
      );
      return (lat: p.latitude, lng: p.longitude);
    } catch (_) {
      final l = await Geolocator.getLastKnownPosition();
      return l == null ? null : (lat: l.latitude, lng: l.longitude);
    }
  }

  // ---------- avvio / stop ----------

  static Future<void> start({
    required int idTavolo,
    required int userId,
    double? lat,
    double? lng,
    required void Function() onLiberato,
  }) async {
    await stop();
    _idTavolo = idTavolo;
    _userId = userId;
    _lat = lat;
    _lng = lng;
    _onLiberato = onLiberato;

    await _initNotifiche();

    _ping();
    _pingTimer = Timer.periodic(GpsConfig.intervalloPing, (_) => _ping());

    // Lo stream parte SEMPRE (anche senza coordinate di riferimento):
    // è lui che tiene viva l'app in background e quindi i ping.
    if (await _permessoOk()) {
      _sub = Geolocator.getPositionStream(locationSettings: _settings()).listen(
        _onPosizione,
        onError: (e) => debugPrint('GPS errore stream: $e'),
      );
    }
  }

  static Future<void> stop() async {
    _sub?.cancel();
    _pingTimer?.cancel();
    _scadenzaTimer?.cancel();
    _prorogaTimer?.cancel();
    _sub = null;
    _pingTimer = null;
    _scadenzaTimer = null;
    _prorogaTimer = null;
    _allontanato = false;
    _inProroga = false;
    _idTavolo = null;
    _onLiberato = null;
    try {
      await _notif.cancel(id: 3);
    } catch (_) {}
  }

  static LocationSettings _settings() {
    if (Platform.isAndroid) {
      return AndroidSettings(
        accuracy: LocationAccuracy.high,
        distanceFilter: 5,
        intervalDuration: const Duration(seconds: 10),
        foregroundNotificationConfig: const ForegroundNotificationConfig(
          notificationTitle: 'Tavolo occupato',
          notificationText: 'Stai mantenendo la prenotazione del tavolo',
          enableWakeLock: true,
          setOngoing: true,
        ),
      );
    }
    if (Platform.isIOS) {
      return AppleSettings(
        accuracy: LocationAccuracy.high,
        activityType: ActivityType.other,
        distanceFilter: 5,
        pauseLocationUpdatesAutomatically: false,
        showBackgroundLocationIndicator: true,
        allowBackgroundLocationUpdates: true,
      );
    }
    return const LocationSettings(accuracy: LocationAccuracy.high, distanceFilter: 5);
  }

  // ---------- ping ----------

  static Future<void> _ping() async {
    final id = _idTavolo;
    if (id == null) return;
    final ok = await _repo.pingOccupazione(id, _userId);
    if (ok == false && _idTavolo == id) {
      // il server ha già liberato l'occupazione
      final cb = _onLiberato;
      await stop();
      cb?.call();
    }
  }

  // ---------- distanza ----------

  static void _onPosizione(Position p) {
    final lat = _lat, lng = _lng;
    if (lat == null || lng == null) return;
    if (_allontanato || _inProroga) return;
    if (p.accuracy > GpsConfig.accuratezzaMaxMetri) return;

    final d = Geolocator.distanceBetween(lat, lng, p.latitude, p.longitude);
    if (d > GpsConfig.distanzaMassimaMetri) _avviaAllontanamento();
  }

  static void _avviaAllontanamento() {
    _allontanato = true;
    _scadenzaTimer?.cancel();
    _scadenzaTimer =
        Timer(GpsConfig.timeoutRisposta, () => _libera(motivo: 'allontanamento'));
    _mostraAvviso();
  }

  static void _mantieni() {
    _scadenzaTimer?.cancel();
    _notif.cancel(id: 3);
    _allontanato = false;
    _inProroga = true;
    _prorogaTimer?.cancel();
    _prorogaTimer = Timer(GpsConfig.durataProroga, () => _inProroga = false);
  }

  static Future<void> _libera({String motivo = 'manuale'}) async {
    final id = _idTavolo;
    if (id == null) return;
    final cb = _onLiberato;
    await _repo.liberaTavolo(id, _userId);
    await stop();
    try {
      await _notif.show(
        id: 2,
        title: 'Tavolo liberato',
        body: motivo == 'allontanamento'
            ? 'Ti sei allontanato, il tavolo è stato liberato.'
            : 'Il tavolo è stato liberato.',
        notificationDetails: const NotificationDetails(
          android: AndroidNotificationDetails('tavolo_liberato', 'Tavolo liberato'),
          iOS: DarwinNotificationDetails(),
        ),
      );
    } catch (_) {}
    cb?.call();
  }

  // ---------- notifiche ----------

  static Future<void> _initNotifiche() async {
    if (_notifPronte) return;
    await _notif.initialize(
      settings: InitializationSettings(
        android: const AndroidInitializationSettings('@mipmap/ic_launcher'),
        iOS: DarwinInitializationSettings(
          requestAlertPermission: false,
          requestBadgePermission: false,
          requestSoundPermission: false,
          notificationCategories: [
            DarwinNotificationCategory(
              'allontanato',
              actions: [
                DarwinNotificationAction.plain('mantieni', 'Mantieni (+10 min)',
                    options: {DarwinNotificationActionOption.foreground}),
                DarwinNotificationAction.plain('libera', 'Libera', options: {
                  DarwinNotificationActionOption.foreground,
                  DarwinNotificationActionOption.destructive,
                }),
              ],
            ),
          ],
        ),
      ),
      onDidReceiveNotificationResponse: (r) {
        if (r.actionId == 'mantieni') _mantieni();
        if (r.actionId == 'libera') _libera();
      },
    );
    _notifPronte = true;
  }

  static Future<void> _mostraAvviso() async {
    try {
      await _notif.show(
        id: 3,
        title: 'Ti sei allontanato dal tavolo!',
        body: 'Vuoi mantenere il posto? Verrà liberato tra 10 minuti se non rispondi.',
        notificationDetails: NotificationDetails(
          android: AndroidNotificationDetails(
            'avvisi_tavolo',
            'Avvisi Tavolo',
            importance: Importance.high,
            priority: Priority.high,
            ongoing: true,
            actions: const [
              AndroidNotificationAction('mantieni', 'Mantieni (+10 min)',
                  showsUserInterface: true, cancelNotification: true),
              AndroidNotificationAction('libera', 'Libera', showsUserInterface: true),
            ],
          ),
          iOS: const DarwinNotificationDetails(
            categoryIdentifier: 'allontanato',
            presentAlert: true,
            presentSound: true,
          ),
        ),
      );
    } catch (e) {
      debugPrint('Notifica avviso non mostrata: $e');
    }
  }
}