import 'dart:io';

import 'package:permission_handler/permission_handler.dart';

Future<void>? _inFlight;

/// Equivalente di MainActivity.richiediPermessi():
/// notifiche (Android 13+), posizione precisa, poi posizione in background.
///
/// Eseguita una sola volta e condivisa: PingService.start() ne attende il
/// completamento, così il foreground service di tipo "location" non parte
/// prima che il permesso sia stato concesso (su Android 14+ sarebbe un crash).
Future<void> richiediPermessiIniziali() => _inFlight ??= _richiedi();

Future<void> _richiedi() async {
  if (Platform.isAndroid) {
    final notif = await Permission.notification.status;
    if (!notif.isGranted) await Permission.notification.request();
  }

  var loc = await Permission.location.status;
  if (!loc.isGranted) loc = await Permission.location.request();

  if (loc.isGranted) {
    final always = await Permission.locationAlways.status;
    if (!always.isGranted) await Permission.locationAlways.request();
  }
}
