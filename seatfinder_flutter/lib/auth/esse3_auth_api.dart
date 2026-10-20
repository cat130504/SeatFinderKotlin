import 'dart:convert';

import 'package:http/http.dart' as http;

// classe immutabile per memorizzare le informazioni essenziali dell'utente autenticato
class UserLogin {
  final int id;
  final String iniziali;
  const UserLogin({required this.id, required this.iniziali});
}

// classe sigillata (sealed) che funge da tipo base per rappresentare tutti i possibili esiti del login
sealed class LoginResult {}

// sottoclasse che rappresenta un login avvenuto con successo e restituisce i dati dell'utente
class LoginSuccess extends LoginResult {
  final UserLogin user;
  LoginSuccess(this.user);
}

// sottoclasse che indica un fallimento dovuto a credenziali (matricola o password) errate
class LoginInvalidCredentials extends LoginResult {}

// sottoclasse che rappresenta un errore generico o di rete durante la richiesta di autenticazione
class LoginError extends LoginResult {
  final Object? cause;
  LoginError([this.cause]);
}

// classe di servizio statica per gestire la comunicazione con le api di autenticazione esse3
class EsseTreAuthApi {
  static const _loginUrl = 'https://univpm.esse3.cineca.it/e3rest/api/login';
  static final http.Client _client = http.Client(); // unica istanza condivisa del client http per ottimizzare le connessioni

  // metodo asincrono per eseguire la richiesta di login passando matricola e password
  static Future<LoginResult> login(String matricola, String password) async {
    try {
      // codifica in base64 le credenziali formattate come stringa utente:password
      final credentials = base64.encode(utf8.encode('$matricola:$password'));
      // esegue la richiesta get impostando l'header di autorizzazione basic e applicando un timeout di venti secondi
      final res = await _client.get(
        Uri.parse(_loginUrl),
        headers: {'Authorization': 'Basic $credentials'},
      ).timeout(const Duration(seconds: 20));

      // restituisce credenziali non valide in caso di risposta con codice di stato 401 unauthorized
      if (res.statusCode == 401) return LoginInvalidCredentials();
      // restituisce un errore generico per qualsiasi altro codice di stato http non compreso tra 200 e 299
      if (res.statusCode < 200 || res.statusCode >= 300) return LoginError();

      // decodifica il corpo della risposta json per estrarre le informazioni relative all'utente
      final user = (jsonDecode(utf8.decode(res.bodyBytes))
      as Map<String, dynamic>)['user'] as Map<String, dynamic>;
      final first = user['firstName'].toString();
      final last = user['lastName'].toString();
      // restituisce il successo mappando l'id numerico e ricavando le iniziali dal nome e cognome
      return LoginSuccess(UserLogin(
        id: int.parse(user['id'].toString()),
        iniziali: '${first.isEmpty ? '' : first[0]}${last.isEmpty ? '' : last[0]}',
      ));
    } catch (e) {
      // cattura qualsiasi eccezione di rete o parsing e restituisce l'oggetto di errore con la causa associata
      return LoginError(e);
    }
  }
}