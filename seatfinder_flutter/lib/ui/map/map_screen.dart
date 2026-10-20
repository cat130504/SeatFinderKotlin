import 'dart:async';

import 'package:flutter/material.dart';

import '../../core/supabase_client.dart';
import '../../data/tavolo_repository.dart';
import '../../domain/models.dart';
import 'interactive_map.dart';
import 'tavolo_dialog.dart';

//schermata della mappa: carica i tavoli e li ricarica ogni 30 s, oppure quando cambia quota o refreshTick.
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
  //repository per effettuare le chiamate a Supabase per recuperare i dati dei tavoli
  final _repo = TavoloRepository(AppSupabase.client);
  //lista locale dei tavoli associati al piano corrente
  List<Tavolo> _tavoli = [];
  //timer per il refresh periodico automatico dei dati
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    //carica i tavoli all'avvio della schermata
    _carica();
    //avvia un timer periodico che ricarica i dati dei tavoli ogni 30 secondi
    _timer = Timer.periodic(const Duration(seconds: 30), (_) => _carica());
  }

  @override
  void didUpdateWidget(MapScreen old) {
    super.didUpdateWidget(old);
    //ricarica immediatamente i tavoli se cambia il piano (quota) o se viene forzato un aggiornamento (refreshTick)
    if (old.quota != widget.quota || old.refreshTick != widget.refreshTick) {
      _carica();
    }
  }

  //metodo asincrono per recuperare i tavoli dal database tramite il repository
  Future<void> _carica() async {
    final quota = widget.quota;
    final result = await _repo.getTavoli(quota);
    // Controllo di sicurezza: se il widget è stato smontato o la quota è cambiata nel frattempo, ignora il risultato obsoleto
    if (!mounted || quota != widget.quota) return;
    setState(() => _tavoli = result);
  }

  @override
  void dispose() {
    //cancella il timer periodico quando la schermata viene distrutta per evitare memory leak
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    //restituisce il widget InteractiveMap passando l'asset della planimetria, la lista dei tavoli aggiornata
    //e la funzione da eseguire al click su un tavolo (che apre il rispettivo dialogo di dettaglio/gestione)
    return InteractiveMap(
      assetPath: widget.assetPath,
      tavoli: _tavoli,
      onTableClick: (t) => showTavoloDialog(context, t),
    );
  }
}