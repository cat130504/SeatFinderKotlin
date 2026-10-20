import 'package:flutter/material.dart';


//la classe estende StatelessWidget e implementa PreferredSizeWidget
//(necessario per poterla inserire nella proprietà appBar di uno Scaffold).
//definisce i parametri in ingresso e fissa l'altezza complessiva della barra a 72 pixel con preferredSize
class AppTopBar extends StatelessWidget implements PreferredSizeWidget {
  const AppTopBar({
    super.key,
    required this.iniziali,
    required this.infoTavolo,
    required this.tavoloAttuale,
    required this.onProfileClick,
    required this.onLiberaClick,
  });

  //parametri in ingresso
  final String? iniziali;
  final ({String edificio, String quota})? infoTavolo;
  final int? tavoloAttuale;
  final VoidCallback onProfileClick;
  final VoidCallback onLiberaClick;

  //size di 72px
  @override
  Size get preferredSize => const Size.fromHeight(72);

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final tt = Theme.of(context).textTheme;

    //restituisce appbar con altezza 72px e spaziatore laterale di 16px oltre ad altri parametri di stile
    return AppBar(
      toolbarHeight: 72,
      backgroundColor: cs.surface,
      scrolledUnderElevation: 0,
      titleSpacing: 16,
      title: Row(
        children: [
          //avatar circolare cliccabile (con effetto di click) contentente le iniziali (nome-cognome) dell'utente
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

          //se l'utente sta occupando un tavolo, viene stampato in una colonna flessibile il piano,
          //l'edificio ed il numero del tavolo.
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

      //se l'utente sta occupando un tavolo, viene stampato un bottone con funzione onliberaclick per la liberazione
      //del tavolo
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
