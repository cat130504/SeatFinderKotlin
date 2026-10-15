import 'package:flutter/material.dart';

class AppTopBar extends StatelessWidget implements PreferredSizeWidget {
  const AppTopBar({
    super.key,
    required this.iniziali,
    required this.infoTavolo,
    required this.tavoloAttuale,
    required this.onProfileClick,
    required this.onLiberaClick,
  });

  final String? iniziali;
  final ({String edificio, String quota})? infoTavolo;
  final int? tavoloAttuale;
  final VoidCallback onProfileClick;
  final VoidCallback onLiberaClick;

  @override
  Size get preferredSize => const Size.fromHeight(72);

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final tt = Theme.of(context).textTheme;

    return AppBar(
      toolbarHeight: 72,
      backgroundColor: cs.surface,
      scrolledUnderElevation: 0,
      titleSpacing: 16,
      title: Row(
        children: [
          Material(
            color: cs.primaryContainer,
            shape: const CircleBorder(),
            child: InkWell(
              customBorder: const CircleBorder(),
              onTap: onProfileClick,
              child: SizedBox(
                width: 40,
                height: 40,
                child: Center(
                  child: Text(
                    iniziali ?? '?',
                    style: TextStyle(
                      color: cs.onPrimaryContainer,
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                    ),
                  ),
                ),
              ),
            ),
          ),
          if (infoTavolo != null) ...[
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(infoTavolo!.edificio,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: tt.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                  Text('Piano: ${infoTavolo!.quota}',
                      style: tt.bodySmall?.copyWith(color: cs.onSurfaceVariant)),
                  Text('Tavolo: $tavoloAttuale',
                      style: tt.bodySmall?.copyWith(color: cs.onSurfaceVariant)),
                ],
              ),
            ),
          ],
        ],
      ),
      actions: [
        if (tavoloAttuale != null)
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: FilledButton(
              onPressed: onLiberaClick,
              style: FilledButton.styleFrom(backgroundColor: cs.error),
              child: const Text('Libera Tavolo'),
            ),
          ),
      ],
    );
  }
}
