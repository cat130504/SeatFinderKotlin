import 'package:flutter/material.dart';

import '../auth/esse3_auth_api.dart';
import '../auth/session_manager.dart';

//schermata di login strutturata come StatefulWidget poiche lo stato cambia nel tempo
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  //controller per leggere e manipolare il testo inserito nei campi di input
  final _matricola = TextEditingController();
  final _password = TextEditingController();
  //flag per indicare se è in corso una richiesta di rete con esse3api
  bool _loading = false;

  //getter che restituisce true solo se entrambi i campi non sono vuoti e non siamo in fase di caricamento
  bool get _canLogin =>
      _matricola.text.trim().isNotEmpty && _password.text.trim().isNotEmpty && !_loading;


  //aggiunge un listener a entrambi i campi per aggiornare lo stato quando vengono modificati i campi di testo, utile per il bottone
  @override
  void initState() {
    super.initState();
    _matricola.addListener(() => setState(() {}));
    _password.addListener(() => setState(() {}));
  }

  //rilascia le risorse dei controller quando la schermata viene distrutta
  @override
  void dispose() {
    _matricola.dispose();
    _password.dispose();
    super.dispose();
  }

  //snackbar da mostrare all'utente
  void _toast(String msg) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }

  //gestione dell'autenticazione, attiva lo state _loading, esegue la chiamata a esse3api, se positiva rimuove lo stato loading e
  //salva l'utente in sessione altrimenti stampa un messaggio di errore in base al problema riscontrato.
  Future<void> _login() async {
    setState(() => _loading = true);
    final result =
        await EsseTreAuthApi.login(_matricola.text.trim(), _password.text.trim());

    if (!mounted) return;
    setState(() => _loading = false);

    switch (result) {
      case LoginSuccess(:final user):
        // _Root reagisce al cambio di sessione e mostra MainScreen
        await SessionManager.instance.saveSession(user);
      case LoginInvalidCredentials():
        _toast('Matricola o password errata');
      case LoginError():
        _toast('Impossibile accedere, controlla la connessione');
    }
  }

  @override
  Widget build(BuildContext context) {
    final screenSize = MediaQuery.sizeOf(context);
    final screenHeight = screenSize.height;
    // Altezza della tastiera (0 quando è chiusa, >0 quando è aperta)
    final keyboardHeight = MediaQuery.viewInsetsOf(context).bottom;

    OutlineInputBorder border = const OutlineInputBorder();
    final fieldDecoration = InputDecoration(
      filled: true,
      fillColor: Colors.white,
      border: border,
    );

    return Scaffold(
      resizeToAvoidBottomInset: false,
      body: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeOut,
        transform: Matrix4.translationValues(0, -keyboardHeight * 0.75, 0),
        width: screenSize.width,
        height: screenHeight,
        child: Stack(
          fit: StackFit.expand,
          children: [
            Image.asset(
              'assets/images/login.png',
              fit: BoxFit.cover,
              errorBuilder: (_, __, ___) =>
                  ColoredBox(color: Theme.of(context).colorScheme.primary),
            ),

            SafeArea(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 40),
                child: Column(
                  children: [
                    SizedBox(height: screenHeight * 0.46),

                    TextField(
                      controller: _matricola,
                      enabled: !_loading,
                      textInputAction: TextInputAction.next,
                      decoration: fieldDecoration.copyWith(labelText: 'Matricola'),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: _password,
                      enabled: !_loading,
                      obscureText: true,
                      keyboardType: TextInputType.visiblePassword,
                      textInputAction: TextInputAction.done,
                      onSubmitted: (_) {
                        if (_canLogin) _login();
                      },
                      decoration: fieldDecoration.copyWith(labelText: 'Password'),
                    ),
                    const SizedBox(height: 24),
                    SizedBox(
                      width: double.infinity,
                      height: 50,
                      child: FilledButton(
                        onPressed: _canLogin ? _login : null,
                        style: FilledButton.styleFrom(
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(8),
                          ),
                        ),
                        child: const Text('Login'),
                      ),
                    ),
                    if (_loading) ...[
                      const SizedBox(height: 16),
                      const CircularProgressIndicator(),
                    ],
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
