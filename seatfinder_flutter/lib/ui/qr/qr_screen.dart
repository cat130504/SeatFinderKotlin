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

class _QrScreenState extends State<QrScreen> {
  // repository per interagire con il database dei tavoli
  final _repo = TavoloRepository(AppSupabase.client);
  // controller per gestire la fotocamera e la scansione dei QR
  final _scanner = MobileScannerController(
    formats: const [BarcodeFormat.qrCode],
    facing: CameraFacing.back,
  );

  bool _isScanned = false; // evita scansioni multiple consecutive
  bool _torchOn = false; // stato della torcia
  bool? _hasCamera; // null = in attesa di verifica permessi

  @override
  void initState() {
    super.initState();
    _permessi();
  }

  // Permesso fotocamera. La posizione è gestita da GpsService.richiediPermessi().
  Future<void> _permessi() async {
    // su iOS la richiesta della fotocamera la mostra mobile_scanner
    if (Platform.isIOS) {
      if (!mounted) return;
      setState(() => _hasCamera = true);
      return;
    }

    var cam = await Permission.camera.status;
    if (!cam.isGranted) cam = await Permission.camera.request();
    if (!mounted) return;
    setState(() => _hasCamera = cam.isGranted);
    if (!cam.isGranted) _toast('Permesso camera negato');
  }

  // mostra un messaggio a schermo (SnackBar)
  void _toast(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }

  // riabilita la scansione dopo un intervallo
  Future<void> _riabilitaDopo(Duration d) async {
    await Future.delayed(d);
    if (mounted) _isScanned = false;
  }

  // eseguita quando viene rilevato un QR
  Future<void> _onDetect(BarcodeCapture capture) async {
    if (_isScanned) return;
    final value = capture.barcodes.isEmpty ? null : capture.barcodes.first.rawValue;
    if (value == null) return;
    _isScanned = true;

    // converte il testo del QR in id numerico del tavolo
    final id = int.tryParse(value.trim());
    if (id == null) {
      _toast('QR code non valido');
      _riabilitaDopo(const Duration(seconds: 2));
      return;
    }

    // controlla quanti posti liberi rimangono
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

    // posizione: null se permesso negato o GPS non disponibile
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
      _isScanned = false;
      return;
    }
    else{
      final rimasti = postiLiberi - 1;
      _toast(switch (postiLiberi) {
        1 => 'Tavolo occupato, tutti i posti sono occupati',
        2 => 'Tavolo occupato, $rimasti posto rimanente',
        _ => 'Tavolo occupato, $rimasti posti rimanenti',
      });
      await widget.onSuccessOccupazione(id, lat, lng);
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
  void dispose() {
    _scanner.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_hasCamera == null) {
      return const Center(child: CircularProgressIndicator());
    }
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
                const SizedBox(height: 12), // spazio tra testo e icona
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