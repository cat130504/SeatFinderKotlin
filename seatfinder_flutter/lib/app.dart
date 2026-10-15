import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'auth/session_manager.dart';
import 'core/config.dart';
import 'theme/app_theme.dart';
import 'ui/login_screen.dart';
import 'ui/main/main_controller.dart';
import 'ui/main/main_screen.dart';

class SeatFinderApp extends StatefulWidget {
  const SeatFinderApp({super.key});

  @override
  State<SeatFinderApp> createState() => _SeatFinderAppState();
}

class _SeatFinderAppState extends State<SeatFinderApp> {
  @override
  void initState() {
    super.initState();
    // Se la sessione è ancora valida salta il login (LoginActivity.onCreate)
    SessionManager.instance.restore();
  }

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<SessionManager>.value(
      value: SessionManager.instance,
      child: MaterialApp(
        title: 'SeatFinder',
        debugShowCheckedModeBanner: false,
        theme: buildTheme(),
        home: const _Root(),
      ),
    );
  }
}

class _Root extends StatelessWidget {
  const _Root();

  @override
  Widget build(BuildContext context) {
    if (!AppConfig.isConfigured) {
      return const Scaffold(
        body: Center(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Text(
              'Configurazione mancante.\nAvvia l\'app con\n'
              '--dart-define=SUPABASE_URL=... --dart-define=SUPABASE_KEY=...',
              textAlign: TextAlign.center,
            ),
          ),
        ),
      );
    }

    final session = context.watch<SessionManager>();
    if (!session.restored) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (!session.isLoggedIn) return const LoginScreen();

    return ChangeNotifierProvider(
      key: ValueKey(session.userId),
      create: (_) => MainController(),
      child: const MainScreen(),
    );
  }
}
