import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../auth/session_manager.dart';
import '../../services/gps_service.dart';
import '../components/expandable_fab_menu.dart';
import '../components/plan_badge.dart';
import '../map/map_assets.dart';
import '../map/map_screen.dart';
import '../qr/qr_screen.dart';
import '../stats/profile_stats_screen.dart';
import '../stats/stats_screen.dart';
import 'app_top_bar.dart';
import 'main_controller.dart';
import 'profile_dialog.dart';

//enum che definisce le 4 destinazioni della barra di navigazione inferiore
enum Destination {
  map('Mappa', Icons.map),
  stats('Statistiche', Icons.query_stats),
  qr('Scansione', Icons.qr_code_scanner),
  profile('Profilo', Icons.person);

  const Destination(this.label, this.icon);
  final String label;
  final IconData icon;
}
//schermata principale dell'applicazione che gestisce la navigazione tra le tab tramite BottomNavigationBar
class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

//Traccia la tab attualmente selezionata (inizializzata sulla Mappa)
class _MainScreenState extends State<MainScreen> {
  Destination _current = Destination.map;

  @override
  void initState() {
    super.initState();

    //richiede i permessi necessari per il funzionamento dell'applicazione
    GpsService.richiediPermessi();
  }

  @override
  Widget build(BuildContext context) {
    // Ascolta i cambiamenti nel MainController tramite Provider
    final c = context.watch<MainController>();
    final session = SessionManager.instance;

    // ---- GATE GLOBALE: aspetta il caricamento iniziale ----
    if (!c.ready) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    //abilita il qr in base allo stato dell'occupazione di un tavolo
    final scanEnabled = c.tavoloAttuale == null;
    // Se un tavolo diventa occupato mentre siamo sulla tab QR, torna alla mappa
    final current = (_current == Destination.qr && !scanEnabled) ? Destination.map : _current;

    //barra superiore con informaizoni su utente e tavolo
    return Scaffold(
      appBar: AppTopBar(
        iniziali: session.iniziali,
        infoTavolo: c.infoTavolo,
        tavoloAttuale: c.tavoloAttuale,
        onProfileClick: () => showProfileDialog(
          context,
          nome: session.nome ?? session.iniziali ?? 'Utente',
          onConfirmLogout: c.logout,
        ),
        onLiberaClick: c.liberaTavolo,
      ),
      //barra di navigazione inferiore (bottomNavigationBar) con separatore grafico
      bottomNavigationBar: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Divider(
            height: 1,
            thickness: 1,
            color: Theme.of(context).colorScheme.outline.withValues(alpha: 0.2),
          ),
          NavigationBar(
            backgroundColor: Theme.of(context).colorScheme.surface,
            surfaceTintColor: Colors.transparent,
            elevation: 0,
            selectedIndex: current.index,
            onDestinationSelected: (i) =>
                setState(() => _current = Destination.values[i]),
            destinations: [
              for (final d in Destination.values)
                NavigationDestination(
                  icon: Icon(d.icon),
                  label: d.label,
                  tooltip: d.label,
                  //disabilita la tab QR se l'utente ha già un tavolo occupato
                  enabled: d == Destination.qr ? scanEnabled : true,
                ),
            ],
          ),
        ],
      ),
      //corpo dinamico della schermata basato sulla tab attualmente selezionata
      body: switch (current) {
        Destination.map => _MapTab(controller: c),
        Destination.stats => const StatsScreen(),
        Destination.profile => const ProfileStatsScreen(),
        Destination.qr => QrScreen(
            onSuccessOccupazione: (id, lat, lng) async {
              await c.onTavoloOccupato(id, lat, lng);
              if (mounted) setState(() => _current = Destination.map);
            },
          ),
      },
    );
  }
}

//widget privato che gestisce la visualizzazione della tab della Mappa e dei menu fluttuanti associati
class _MapTab extends StatelessWidget {
  const _MapTab({required this.controller});
  final MainController controller;

  @override
  Widget build(BuildContext context) {
    final sel = controller.selezione;
    final edificio = sel.edificio;
    final quota = sel.quota;

    // ---- GATE LOCALE: mappa solo con edificio+quota noti ----
    if (edificio == null || quota == null) {
      return const Center(child: CircularProgressIndicator());
    }

    return Stack(
      children: [
        Positioned.fill(
          child: FutureBuilder<String>(
            future: MapAssets.resolve(edificio.nome, quota),
            builder: (context, snap) {
              if (!snap.hasData) {
                return const Center(child: CircularProgressIndicator());
              }
              return MapScreen(
                assetPath: snap.data!,
                quota: quota,
                refreshTick: controller.refreshTick,
              );
            },
          ),
        ),
        //testo superiore che mostra il nome dell'edificio e il piano corrente
        Align(
          alignment: Alignment.topCenter,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: PlanBadge(planName: 'Edificio: ${edificio.nome}   Piano: $quota'),
          ),
        ),
        //menu a comparsa in basso a destra e sinistra per la selezione della quota e dell'edificio
        if (sel.piani.isNotEmpty)
          Align(
            alignment: Alignment.bottomRight,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: ExpandableFabMenu(
                heroTag: 'fab_piani',
                options: [
                  for (final p in sel.piani)
                    FabOption(
                      icon: Icons.description,
                      label: p.quota,
                      onClick: () => controller.selezionaPiano(p.quota),
                    ),
                ],
              ),
            ),
          ),
        if (controller.edifici.isNotEmpty)
          Align(
            alignment: Alignment.bottomLeft,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: ExpandableFabMenu(
                heroTag: 'fab_edifici',
                alignEnd: false,
                options: [
                  for (final e in controller.edifici)
                    FabOption(
                      icon: Icons.business,
                      label: e.nome,
                      onClick: () => controller.selezionaEdificio(e),
                    ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}
