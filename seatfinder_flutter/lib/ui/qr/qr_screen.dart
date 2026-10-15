import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:permission_handler/permission_handler.dart';
import 'dart:io';
import '../../auth/session_manager.dart';
import '../../core/supabase_client.dart';
import '../../data/tavolo_repository.dart';
import 'scanner_overlay.dart';

class QrScreen extends StatefulWidget {
  const QrScreen({super.key, required this.onSuccessOccupazione});

  /// (idTavolo, lat, lng)
  final Future<void> Function(int, double?, double?) onSuccessOccupazione;

  @override
  State<QrScreen> createState() => _QrScreenState();
}

class _QrScreenState extends State<QrScreen> {
  final _repo = TavoloRepository(AppSupabase.client);
  final _scanner = MobileScannerController(
    formats: const [BarcodeFormat.qrCode],
    facing: CameraFacing.back,
  );

  bool _isScanned = false;
  bool _torchOn = false;
  bool? _hasCamera; // null = in attesa
  bool _hasLocation = false;

  @override
  void initState() {
    super.initState();
    _permessi();
  }

  Future<void> _permessi() async {
    // iOS: mobile_scanner mostra da solo la richiesta della fotocamera
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

  void _toast(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }

  Future<void> _riabilitaDopo(Duration d) async {
    await Future.delayed(d);
    if (mounted) _isScanned = false;
  }

  Future<void> _onDetect(BarcodeCapture capture) async {
    if (_isScanned) return;
    final value = capture.barcodes.isEmpty ? null : capture.barcodes.first.rawValue;
    if (value == null) return;
    _isScanned = true;

    final id = int.tryParse(value.trim());
    if (id == null) {
      _toast('QR code non valido');
      _riabilitaDopo(const Duration(seconds: 2));
      return;
    }

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
    if (_hasLocation) {
      try {
        final pos = await Geolocator.getLastKnownPosition() ??
            await Geolocator.getCurrentPosition(
              locationSettings: const LocationSettings(
                accuracy: LocationAccuracy.high,
                timeLimit: Duration(seconds: 10),
              ),
            );
        lat = pos.latitude;
        lng = pos.longitude;
      } catch (e) {
        debugPrint('QR Impossibile ottenere la posizione: $e');
      }
    }

    final ok = await _repo.occupaTavolo(id, SessionManager.instance.userId,
        lat: lat, lng: lng);
    if (!ok) {
      _toast('Errore durante l\'occupazione del tavolo');
      _isScanned = false;
      return;
    }

    final rimasti = postiLiberi - 1;
    _toast(switch (postiLiberi) {
      1 => 'Tavolo occupato, tutti i posti sono occupati',
      2 => 'Tavolo occupato, $rimasti posto rimanente',
      _ => 'Tavolo occupato, $rimasti posti rimanenti',
    });
    await widget.onSuccessOccupazione(id, lat, lng);
  }

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
        // Testo sotto il riquadro (boxSize 260 -> bordo inferiore a centro + 130)
        Center(
          child: Transform.translate(
            offset: const Offset(0, 130 + 28),
            child: const Text(
              'Inquadra il QR code del tavolo',
              style: TextStyle(color: Colors.white),
            ),
          ),
        ),
        Align(
          alignment: Alignment.bottomCenter,
          child: Padding(
            padding: const EdgeInsets.only(bottom: 40),
            child: IconButton(
              onPressed: _toggleTorch,
              tooltip: _torchOn ? 'Spegni torcia' : 'Accendi torcia',
              icon: Icon(_torchOn ? Icons.flash_on : Icons.flash_off, color: Colors.white),
            ),
          ),
        ),
      ],
    );
  }
}
