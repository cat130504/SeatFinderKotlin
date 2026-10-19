import 'package:flutter_foreground_task/flutter_foreground_task.dart';

import '../core/config.dart';
import 'permissions.dart';
import 'ping_task_handler.dart';

/// Equivalente di PingServiceLauncher + configurazione dei canali.
class PingService {
  static void init() {
    FlutterForegroundTask.init(

      androidNotificationOptions: AndroidNotificationOptions(
        channelId: PingConfig.channelId,
        channelName: 'Stato Tavolo',
        channelDescription:
        'Avvisi di occupazione e allontanamento dal tavolo',
        channelImportance: NotificationChannelImportance.HIGH,
        priority: NotificationPriority.HIGH,
        onlyAlertOnce: false,
      ),
      iosNotificationOptions: const IOSNotificationOptions(
        showNotification: true,
        playSound: false,
      ),
      foregroundTaskOptions: ForegroundTaskOptions(
        eventAction: ForegroundTaskEventAction.repeat(PingConfig.intervalloControlloMs),
        autoRunOnBoot: false,
        autoRunOnMyPackageReplaced: false,
        allowWakeLock: true,
        allowWifiLock: true,
      ),
    );
  }

  static Future<void> start({
    required int idTavolo,
    required int userId,
    double? lat,
    double? lng,
  }) async {
    // 1. Richiede i permessi iniziali (Posizione + Notifiche)
    await richiediPermessiIniziali();

    // 2. Verifica tramite Geolocator che il permesso posizione sia concesso
    if (!await haPermessoPosizione()) return;

    // Dati letti dall'isolate del servizio in onStart()
    await FlutterForegroundTask.saveData(key: 'sb_url', value: AppConfig.supabaseUrl);
    await FlutterForegroundTask.saveData(key: 'sb_key', value: AppConfig.supabaseKey);
    await FlutterForegroundTask.saveData(key: 'userId', value: userId);
    await FlutterForegroundTask.saveData(key: 'idTavolo', value: idTavolo);
    if (lat != null && lng != null) {
      await FlutterForegroundTask.saveData(key: 'lat', value: lat);
      await FlutterForegroundTask.saveData(key: 'lng', value: lng);
    } else {
      await FlutterForegroundTask.removeData(key: 'lat');
      await FlutterForegroundTask.removeData(key: 'lng');
    }

    if (await FlutterForegroundTask.isRunningService) {
      await FlutterForegroundTask.restartService();
    } else {
      await FlutterForegroundTask.startService(
        serviceId: 256,
        notificationTitle: 'Tavolo occupato',
        notificationText: 'Stai mantenendo la prenotazione del tavolo',
        callback: startPingCallback,
      );
    }
  }

  static Future<void> stop() async {
    if (await FlutterForegroundTask.isRunningService) {
      await FlutterForegroundTask.stopService();
    }
  }

  /// Da chiamare periodicamente dalla UI finché c'è un tavolo occupato.
  static void sendHeartbeat() => FlutterForegroundTask.sendDataToTask('heartbeat');
}