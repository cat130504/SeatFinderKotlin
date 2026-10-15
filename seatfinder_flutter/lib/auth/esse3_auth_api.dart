import 'dart:convert';

import 'package:http/http.dart' as http;

class UserLogin {
  final int id;
  final String iniziali;
  const UserLogin({required this.id, required this.iniziali});
}

sealed class LoginResult {}

class LoginSuccess extends LoginResult {
  final UserLogin user;
  LoginSuccess(this.user);
}

class LoginInvalidCredentials extends LoginResult {}

class LoginError extends LoginResult {
  final Object? cause;
  LoginError([this.cause]);
}

class EsseTreAuthApi {
  static const _loginUrl = 'https://univpm.esse3.cineca.it/e3rest/api/login';
  static final http.Client _client = http.Client(); // una sola istanza

  static Future<LoginResult> login(String matricola, String password) async {
    try {
      final credentials = base64.encode(utf8.encode('$matricola:$password'));
      final res = await _client.get(
        Uri.parse(_loginUrl),
        headers: {'Authorization': 'Basic $credentials'},
      ).timeout(const Duration(seconds: 20));

      if (res.statusCode == 401) return LoginInvalidCredentials();
      if (res.statusCode < 200 || res.statusCode >= 300) return LoginError();

      final user = (jsonDecode(utf8.decode(res.bodyBytes))
          as Map<String, dynamic>)['user'] as Map<String, dynamic>;
      final first = user['firstName'].toString();
      final last = user['lastName'].toString();
      return LoginSuccess(UserLogin(
        id: int.parse(user['id'].toString()),
        iniziali: '${first.isEmpty ? '' : first[0]}${last.isEmpty ? '' : last[0]}',
      ));
    } catch (e) {
      return LoginError(e);
    }
  }
}
