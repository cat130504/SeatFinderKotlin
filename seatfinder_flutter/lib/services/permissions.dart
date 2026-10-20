import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:geolocator/geolocator.dart';

Future<void>? _inFlight;

Future<void> richiediPermessiIniziali() {
  return _inFlight ??= _richiedi().whenComplete(() => _inFlight = null);
}

Future<void> _richiedi() async {
  // 1. Permesso Notifiche (Android 13+ e iOS)
  try {
    final notifPlugin = FlutterLocalNotificationsPlugin();
    if (Platform.isAndroid) {
      await notifPlugin
          .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin>()
          ?.requestNotificationsPermission();
    } else if (Platform.isIOS) {
      await notifPlugin
          .resolvePlatformSpecificImplementation<
          IOSFlutterLocalNotificationsPlugin>()
          ?.requestPermissions(alert: true, badge: true, sound: true);
    }
  } catch (e) {
    debugPrint('Errore richiesta permesso notifiche: $e');
  }

  // 2. Permesso Posizione tramite Geolocator
  try {
    final serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      debugPrint('Servizi di localizzazione disattivati sul dispositivo.');
      return;
    }

    var perm = await Geolocator.checkPermission();
    if (perm == LocationPermission.denied) {
      perm = await Geolocator.requestPermission();
    }
  } catch (e) {
    debugPrint('Errore richiesta permesso posizione: $e');
  }
}

Future<bool> haPermessoPosizione() async {
  final perm = await Geolocator.checkPermission();
  return perm == LocationPermission.whileInUse ||
      perm == LocationPermission.always;
}