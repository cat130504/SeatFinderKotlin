import 'dart:io';

import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:permission_handler/permission_handler.dart';

import '../../auth/session_manager.dart';
import '../../core/supabase_client.dart';
import '../../data/tavolo_repository.dart';
import '../../services/gps_service.dart';
import 'scanner_overlay.dart';

// Schermata per la scansione del QR code del tavolo
class QrScreen extends StatefulWidget {
  const QrScreen({super.key, required this.onSuccessOccupazione});

  // (idTavolo, lat, lng)
  final Future<void> Function(int, double?, double?) onSuccessOccupazione;

  @override
  State<QrScreen> createState() => _QrScreenState();
}

class _QrScreenState extends State<QrScreen> with WidgetsBindingObserver {
  // Cooldown minimo tra una scansione e l'altra
  static const _scanCooldown = Duration(seconds: 3);

  // repository per interagire con il database dei tavoli
  final _repo = TavoloRepository(AppSupabase.client);

  // controller per gestire la fotocamera e la scansione dei QR
  final _scanner = MobileScannerController(
    formats: const [BarcodeFormat.qrCode],
    facing: CameraFacing.back,
  );

  //lock sincrono e timestamp per evitare doppie letture
  bool _isProcessingScan = false;
  DateTime? _lastScanTime;

  bool _torchOn = false;

  //stati dei permessi
  bool _loadingPermessi = true;
  bool _hasCamera = false;
  bool _hasFineLocation = false;
  bool _hasBackgroundLocation = false;

  @override
  void initState() {
    super.initState();
    // Osserva il ciclo di vita dell'app per aggiornare i permessi al ritorno dalle Impostazioni
    WidgetsBinding.instance.addObserver(this);
    _inizializzaPermessi();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _scanner.dispose();
    super.dispose();
  }

  // Quando l'utente torna nell'app dalle Impostazioni di sistema, ricontrolla i permessi
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _aggiornaStatoPermessi();
    }
  }

  // Legge lo stato attuale dei permessi senza lanciare popup
  Future<void> _aggiornaStatoPermessi() async {
    final cam = Platform.isIOS
        ? true
        : await Permission.camera.isGranted;
    final fine = await Permission.locationWhenInUse.isGranted;
    final background = await Permission.locationAlways.isGranted;

    if (!mounted) return;
    setState(() {
      _hasCamera = cam;
      _hasFineLocation = fine;
      _hasBackgroundLocation = background;
      _loadingPermessi = false;
    });
  }

  // All'apertura controlla lo stato e, se manca la fotocamera, la richiede subito
  Future<void> _inizializzaPermessi() async {
    if (!Platform.isIOS) {
      final camStatus = await Permission.camera.status;
      if (!camStatus.isGranted && !camStatus.isPermanentlyDenied) {
        await Permission.camera.request();
      }
    }
    await _aggiornaStatoPermessi();
  }

  // Gestisce il click sul bottone di sblocco permessi (con fallback alle Impostazioni se bloccato)
  Future<void> _richiediPermessoMancante() async {
    if (!_hasCamera) {
      final status = await Permission.camera.request();
      if (!status.isGranted) {
        _toast('Abilita il permesso Fotocamera dalle Impostazioni');
        await openAppSettings();
      }
    } else if (!_hasFineLocation) {
      final status = await Permission.locationWhenInUse.request();
      if (status.isGranted) {
        // Appena concessa in foreground, prova subito a chiedere il background
        final bgStatus = await Permission.locationAlways.request();
        if (!bgStatus.isGranted) {
          await openAppSettings();
        }
      } else {
        _toast('Abilita la posizione ("Consenti sempre") dalle Impostazioni');
        await openAppSettings();
      }
    } else if (!_hasBackgroundLocation) {
      final bgStatus = await Permission.locationAlways.request();
      // Se il sistema non mostra il popup o l'utente non ha selezionato "Consenti sempre", apre le Impostazioni
      if (!bgStatus.isGranted) {
        await openAppSettings();
      }
    }

    await _aggiornaStatoPermessi();
  }

  // mostra un messaggio a schermo (SnackBar)
  void _toast(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }

  // eseguita quando viene rilevato un QR
  Future<void> _onDetect(BarcodeCapture capture) async {
    // 1. Lock sincrono: se stiamo già elaborando uno scan, ignora subito
    if (_isProcessingScan) return;

    // 2. Controllo cooldown: ignora se sono passati meno di 3 secondi dall'ultimo tentativo
    final now = DateTime.now();
    if (_lastScanTime != null && now.difference(_lastScanTime!) < _scanCooldown) {
      return;
    }

    final value = capture.barcodes.isEmpty ? null : capture.barcodes.first.rawValue;
    if (value == null) return;

    // Blocca immediatamente prima di qualsiasi await
    _isProcessingScan = true;
    _lastScanTime = now;

    try {
      // Controllo di sicurezza in tempo reale sul permesso "Consenti sempre"
      final hasAlways = await Permission.locationAlways.isGranted;
      if (!hasAlways) {
        _toast('Permesso posizione "Consenti sempre" mancante!');
        await _aggiornaStatoPermessi();
        _isProcessingScan = false;
        return;
      }

      // converte il testo del QR in id numerico del tavolo
      final id = int.tryParse(value.trim());
      if (id == null) {
        _toast('QR code non valido');
        _isProcessingScan = false;
        return;
      }

      // controlla quanti posti liberi rimangono
      final postiLiberi = await _repo.getPostiLiberi(id);
      if (postiLiberi < 0) {
        _toast('Impossibile leggere il tavolo, riprova');
        _isProcessingScan = false;
        return;
      }
      if (postiLiberi == 0) {
        _toast('Tavolo pieno, non occupabile');
        _isProcessingScan = false;
        return;
      }

      // posizione
      final pos = await GpsService.ottieniPosizione();
      final double? lat = pos?.lat;
      final double? lng = pos?.lng;

      final ok = await _repo.occupaTavolo(
        id,
        SessionManager.instance.userId,
        lat: lat,
        lng: lng,
      );

      if (!ok) {
        _toast('Errore durante l\'occupazione del tavolo');
        _isProcessingScan = false;
        return;
      }

      //ferma lo scanner dopo l'occupazione per evitare qualsiasi lettura successiva
      await _scanner.stop();

      final rimasti = postiLiberi - 1;
      _toast(switch (postiLiberi) {
        1 => 'Tavolo occupato, tutti i posti sono occupati',
        2 => 'Tavolo occupato, $rimasti posto rimanente',
        _ => 'Tavolo occupato, $rimasti posti rimanenti',
      });

      await widget.onSuccessOccupazione(id, lat, lng);
      // Nota: in caso di successo _isProcessingScan resta true
    } catch (e) {
      debugPrint('Errore durante la scansione/occupazione: $e');
      _toast('Errore durante l\'occupazione del tavolo');
      _isProcessingScan = false;
    }
  }

  // attiva o disattiva la torcia
  Future<void> _toggleTorch() async {
    try {
      await _scanner.toggleTorch();
      setState(() => _torchOn = !_torchOn);
    } catch (e) {
      debugPrint('QR Torcia non disponibile: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loadingPermessi) {
      return const Center(child: CircularProgressIndicator());
    }

    //blocca lo scanner finché non sono attivi Fotocamera + Posizione + "Consenti sempre"
    if (!_hasCamera || !_hasFineLocation || !_hasBackgroundLocation) {
      final String messaggio;
      final String testoBottone;

      if (!_hasCamera) {
        messaggio = 'Serve il permesso per accedere alla fotocamera per poter leggere il QR code del tavolo.';
        testoBottone = 'Concedi Fotocamera';
      } else if (!_hasFineLocation) {
        messaggio = 'Per occupare un tavolo è necessario concedere il permesso di posizione precisa e impostarlo su "Consenti sempre".';
        testoBottone = 'Concedi Posizione';
      } else {
        messaggio = 'Hai concesso la posizione solo mentre l\'app è in uso.\n\n'
            'Per occupare un tavolo devi impostare il permesso di posizione su "Consenti sempre", '
            'così l\'app può liberare automaticamente il posto quando ti allontani.';
        testoBottone = 'Apri Impostazioni ("Consenti sempre")';
      }

      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                messaggio,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyLarge,
              ),
              const SizedBox(height: 20),
              FilledButton(
                onPressed: _richiediPermessoMancante,
                child: Text(testoBottone),
              ),
            ],
          ),
        ),
      );
    }

    return Stack(
      fit: StackFit.expand,
      children: [
        MobileScanner(
          controller: _scanner,
          onDetect: _onDetect,
          fit: BoxFit.cover,
        ),
        const ScannerOverlay(),
        Center(
          child: Transform.translate(
            offset: const Offset(0, 130 + 48),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text(
                  'Inquadra il QR code del tavolo',
                  style: TextStyle(color: Colors.white),
                ),
                const SizedBox(height: 12),
                IconButton(
                  onPressed: _toggleTorch,
                  tooltip: _torchOn ? 'Spegni Torcia' : 'Accendi Torcia',
                  icon: Icon(
                    _torchOn ? Icons.flash_on : Icons.flash_off,
                    color: Colors.white,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

extension on int {
  double get dpOrPx => toDouble();
}