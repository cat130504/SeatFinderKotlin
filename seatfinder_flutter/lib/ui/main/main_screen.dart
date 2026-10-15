import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../auth/session_manager.dart';
import '../../services/permissions.dart';
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

/// Le 4 destinazioni della bottom bar (Destination.kt).
enum Destination {
  map('Mappa', Icons.map),
  stats('Statistiche', Icons.query_stats),
  qr('Scansione', Icons.qr_code_scanner),
  profile('Profilo', Icons.person);

  const Destination(this.label, this.icon);
  final String label;
  final IconData icon;
}

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  Destination _current = Destination.map;

  @override
  void initState() {
    super.initState();
    richiediPermessiIniziali();
  }

  @override
  Widget build(BuildContext context) {
    final c = context.watch<MainController>();
    final session = SessionManager.instance;

    // ---- GATE GLOBALE: aspetta il caricamento iniziale ----
    if (!c.ready) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    final scanEnabled = c.tavoloAttuale == null;
    // Se un tavolo diventa occupato mentre siamo sulla tab QR, torna alla mappa
    final current = (_current == Destination.qr && !scanEnabled) ? Destination.map : _current;

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
                  enabled: d == Destination.qr ? scanEnabled : true,
                ),
            ],
          ),
        ],
      ),
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
        Align(
          alignment: Alignment.topCenter,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: PlanBadge(planName: 'Edificio: ${edificio.nome}   Piano: $quota'),
          ),
        ),
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
