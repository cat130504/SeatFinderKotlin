import 'dart:async';

import 'package:flutter/material.dart';

import '../../core/supabase_client.dart';
import '../../data/tavolo_repository.dart';
import '../../domain/models.dart';
import 'interactive_map.dart';
import 'tavolo_dialog.dart';

/// Schermata della mappa: carica i tavoli e li rinfresca ogni 30 s,
/// oppure subito quando cambia quota o refreshTick.
class MapScreen extends StatefulWidget {
  const MapScreen({
    super.key,
    required this.assetPath,
    required this.quota,
    required this.refreshTick,
  });

  final String assetPath;
  final String quota;
  final int refreshTick;

  @override
  State<MapScreen> createState() => _MapScreenState();
}

class _MapScreenState extends State<MapScreen> {
  final _repo = TavoloRepository(AppSupabase.client);
  List<Tavolo> _tavoli = [];
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _carica();
    _timer = Timer.periodic(const Duration(seconds: 30), (_) => _carica());
  }

  @override
  void didUpdateWidget(MapScreen old) {
    super.didUpdateWidget(old);
    if (old.quota != widget.quota || old.refreshTick != widget.refreshTick) {
      _carica();
    }
  }

  Future<void> _carica() async {
    final quota = widget.quota;
    final result = await _repo.getTavoli(quota);
    if (!mounted || quota != widget.quota) return; // risposta di una quota vecchia
    setState(() => _tavoli = result);
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return InteractiveMap(
      assetPath: widget.assetPath,
      tavoli: _tavoli,
      onTableClick: (t) => showTavoloDialog(context, t),
    );
  }
}
