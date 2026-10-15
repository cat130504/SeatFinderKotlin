import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'esse3_auth_api.dart';

/// Unisce SessionManager + AuthManager di Kotlin.
/// Sessione persistita 7 giorni in SharedPreferences ("LoginPrefs").
class SessionManager extends ChangeNotifier {
  SessionManager._();
  static final SessionManager instance = SessionManager._();

  static const _kUserId = 'userId';
  static const _kIniziali = 'iniziali';
  static const _kExpire = 'expireTime';
  static const _sessionDurationMs = 7 * 24 * 60 * 60 * 1000;

  int userId = -1;
  String? nome;
  String? iniziali;

  /// false finché non è finito il tentativo di ripristino della sessione.
  bool restored = false;

  bool get isLoggedIn => userId != -1;

  Future<void> restore() async {
    final p = await SharedPreferences.getInstance();
    final id = p.getInt(_kUserId) ?? -1;
    final ini = p.getString(_kIniziali);
    final expire = p.getInt(_kExpire) ?? 0;

    if (id != -1 && ini != null && DateTime.now().millisecondsSinceEpoch < expire) {
      userId = id;
      iniziali = ini;
    }
    restored = true;
    notifyListeners();
  }

  Future<void> saveSession(UserLogin user) async {
    final p = await SharedPreferences.getInstance();
    await p.setInt(_kUserId, user.id);
    await p.setString(_kIniziali, user.iniziali);
    await p.setInt(
        _kExpire, DateTime.now().millisecondsSinceEpoch + _sessionDurationMs);
    userId = user.id;
    iniziali = user.iniziali;
    notifyListeners();
  }

  /// Pulisce sessione e prefs. Lo stop del servizio in background
  /// è gestito da MainController.logout().
  Future<void> logout() async {
    final p = await SharedPreferences.getInstance();
    await p.clear();
    userId = -1;
    nome = null;
    iniziali = null;
    notifyListeners();
  }
}
