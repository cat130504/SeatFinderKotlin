import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:permission_handler/permission_handler.dart';
import 'dart:io';
import '../../auth/session_manager.dart';
import '../../core/supabase_client.dart';
import '../../data/tavolo_repository.dart';
import 'scanner_overlay.dart';

// Schermata per la scansione del QR code del tavolo
class QrScreen extends StatefulWidget {
  const QrScreen({super.key, required this.onSuccessOccupazione});
  final Future<void> Function(int, double?, double?) onSuccessOccupazione;

  @override
  State<QrScreen> createState() => _QrScreenState();
}

class _QrScreenState extends State<QrScreen> {
  //repository per interagire con il database dei tavoli
  final _repo = TavoloRepository(AppSupabase.client);
  //controller per gestire la fotocamera e la scansione dei codici a barre QR
  final _scanner = MobileScannerController(
    formats: const [BarcodeFormat.qrCode],
    facing: CameraFacing.back,
  );

  bool _isScanned = false; //flag per evitare scansioni multiple consecutive
  bool _torchOn = false; //stato della torcia (accesa/spenta)
  bool? _hasCamera; //null = in attesa di verifica permessi
  bool _hasLocation = false; //flag per i permessi di localizzazione

  @override
  void initState() {
    super.initState();
    // richiede i permessi necessari all'avvio della schermata
    _permessi();
  }

  //gunzione per gestire la richiesta dei permessi di fotocamera e posizione
  Future<void> _permessi() async {
    //su iOS la gestione del permesso della fotocamera è integrata nel plugin
    if (Platform.isIOS) {
      var loc = await Permission.location.status;
      if (!loc.isGranted) loc = await Permission.location.request();
      if (!mounted) return;
      setState(() {
        _hasCamera = true;
        _hasLocation = loc.isGranted;
      });
      return;
    }

    //su Android richiede esplicitamente i permessi di camera e posizione
    var cam = await Permission.camera.status;
    if (!cam.isGranted) cam = await Permission.camera.request();
    var loc = await Permission.location.status;
    if (!loc.isGranted) loc = await Permission.location.request();
    if (!mounted) return;
    setState(() {
      _hasCamera = cam.isGranted;
      _hasLocation = loc.isGranted;
    });
    if (!cam.isGranted) _toast('Permesso camera negato');
  }

  //mostra un messaggio a schermo (SnackBar)
  void _toast(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }

  //riabilita la scansione dopo un determinato intervallo di tempo
  Future<void> _riabilitaDopo(Duration d) async {
    await Future.delayed(d);
    if (mounted) _isScanned = false;
  }

  // Funzione eseguita quando viene rilevato un codice QR
  Future<void> _onDetect(BarcodeCapture capture) async {
    if (_isScanned) return;
    final value = capture.barcodes.isEmpty ? null : capture.barcodes.first.rawValue;
    if (value == null) return;
    _isScanned = true;

    //converte il testo del QR in un ID numerico del tavolo
    final id = int.tryParse(value.trim());
    if (id == null) {
      _toast('QR code non valido');
      _riabilitaDopo(const Duration(seconds: 2));
      return;
    }

    //controlla quanti posti liberi rimangono sul tavolo
    final postiLiberi = await _repo.getPostiLiberi(id);
    if (postiLiberi < 0) {
      _toast('Impossibile leggere il tavolo, riprova');
      _riabilitaDopo(const Duration(seconds: 2));
      return;
    }
    if (postiLiberi == 0) {
      _toast('Tavolo pieno, non occupabile');
      _riabilitaDopo(const Duration(seconds: 2));
      return;
    }

    double? lat;
    double? lng;
    //se il permesso di posizione è attivo, acquisisce le coordinate GPS attuali
    if (_hasLocation) {
      try {
        final pos = await Geolocator.getCurrentPosition(
          locationSettings: const LocationSettings(
            accuracy: LocationAccuracy.high,
            timeLimit: const Duration(seconds: 8),
          ),
        );
        lat = pos.latitude;
        lng = pos.longitude;
      } catch (e) {
        debugPrint('QR getCurrentPosition fallita ($e), provo getLastKnownPosition...');
        try {
          final lastPos = await Geolocator.getLastKnownPosition();
          if (lastPos != null) {
            lat = lastPos.latitude;
            lng = lastPos.longitude;
          }
        } catch (e2) {
          debugPrint('QR Impossibile ottenere alcuna posizione: $e2');
        }
      }
    }

    //tenta di occupare il tavolo inviando i dati al database
    final ok = await _repo.occupaTavolo(id, SessionManager.instance.userId,
        lat: lat, lng: lng);
    if (!ok) {
      _toast('Errore durante l\'occupazione del tavolo');
      _isScanned = false;
      return;
    }

    //mostra un messaggio di successo personalizzato in base ai posti rimanenti
    final rimasti = postiLiberi - 1;
    _toast(switch (postiLiberi) {
      1 => 'Tavolo occupato, tutti i posti sono occupati',
      2 => 'Tavolo occupato, $rimasti posto rimanente',
      _ => 'Tavolo occupato, $rimasti posti rimanenti',
    });
    // Esegue la callback di successo passata dal widget genitore
    await widget.onSuccessOccupazione(id, lat, lng);
  }

  //attiva o disattiva la torcia dello smartphone
  Future<void> _toggleTorch() async {
    try {
      await _scanner.toggleTorch();
      setState(() => _torchOn = !_torchOn);
    } catch (e) {
      debugPrint('QR Torcia non disponibile: $e');
    }
  }

  @override
  void dispose() {
    //rilascia le risorse del controller dello scanner alla chiusura
    _scanner.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    //mostra un caricamento se lo stato dei permessi della fotocamera non è ancora definito
    if (_hasCamera == null) {
      return const Center(child: CircularProgressIndicator());
    }
    //se il permesso della fotocamera è stato negato, mostra un avviso con il link alle impostazioni
    if (_hasCamera == false) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Serve il permesso per accedere alla fotocamera'),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: () async {
                await openAppSettings();
              },
              child: const Text('Apri impostazioni'),
            ),
          ],
        ),
      );
    }
    //struttura principale della schermata di scansione con fotocamera e overlay grafico
    return Stack(
      fit: StackFit.expand,
      children: [
        MobileScanner(controller: _scanner, onDetect: _onDetect, fit: BoxFit.cover),
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
                const SizedBox(height: 12), //spazio tra testo e icona
                IconButton(
                  onPressed: _toggleTorch,
                  tooltip: _torchOn ? 'Spegni torcia' : 'Accendi torcia',
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