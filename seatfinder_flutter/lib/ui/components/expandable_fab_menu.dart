import 'package:flutter/material.dart';

//modello che definisce le caratteristiche di ogni voce nel menu contenente:
//l'icona da mostrare, l'etichetta testuale descrittiva e la funzione di callback eseguita al click.
class FabOption {
  final IconData icon;
  final String label;
  final VoidCallback onClick;
  const FabOption({required this.icon, required this.label, required this.onClick});
}

//widget principale con StatefulWidget per memorizzare lo stato di apertura/chiusura del menu. Accetta una lista di opzioni (options), un flag di
//allineamento e un eventuale heroTag per evitare conflitti di animazione Hero tra più floatingButtons
class ExpandableFabMenu extends StatefulWidget {
  const ExpandableFabMenu({
    super.key,
    required this.options,
    this.alignEnd = true,
    this.heroTag,
  });

  final List<FabOption> options; //opzioni fab
  final bool alignEnd; // true = menu a destra (label prima del bottone)
  final Object? heroTag;

  @override
  State<ExpandableFabMenu> createState() => _ExpandableFabMenuState();
}

class _ExpandableFabMenuState extends State<ExpandableFabMenu> {
  bool _expanded = false;


  //la classe di stato gestisce la variabile booleana _expanded.
  //nel metodo build, ricava lo schema colori del tema corrente e imposta l'allineamento (cross) per disporre gli elementi figli in verticale
  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    //determina l'allineamento orizzontale dei componenti in base al parametro alignEnd
    final cross = widget.alignEnd ? CrossAxisAlignment.end : CrossAxisAlignment.start;

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: cross,
      children: [
        AnimatedSize(
          duration: const Duration(milliseconds: 200),
          alignment: Alignment.bottomCenter,
          child: _expanded
              ? Column(
                  crossAxisAlignment: cross,
                  children: [
                    //iterazione sulle opzioni passate per creare dinamicamente i pulsanti secondari
                    for (final o in widget.options)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            //se allineato a destra, mostra prima la label testuale e poi il pulsante
                            if (widget.alignEnd) ...[
                              _OptionLabel(o.label),
                              const SizedBox(width: 8),
                            ],
                            FloatingActionButton.small(
                              heroTag: null,
                              backgroundColor: cs.secondaryContainer,
                              foregroundColor: cs.onSecondaryContainer,
                              onPressed: () {
                                //esegue l'azione associata
                                o.onClick();
                                //chiude il menu
                                setState(() => _expanded = false);
                              },
                              tooltip: o.label,
                              child: Icon(o.icon),
                            ),
                            //se allineato a sinistra, mostra prima il pulsante e poi la label testuale
                            if (!widget.alignEnd) ...[
                              const SizedBox(width: 8),
                              _OptionLabel(o.label),
                            ],
                          ],
                        ),
                      ),
                  ],
                )
              : const SizedBox(width: 0, height: 0),//quando chiuso, non occupa spazio
        ),
        FloatingActionButton(
          heroTag: widget.heroTag,
          onPressed: () => setState(() => _expanded = !_expanded),
          child: Icon(_expanded ? Icons.close : Icons.add),
        ),
      ],
    );
  }
}

//widget privato e riutilizzabile che disegna il riquadro dell'etichetta testuale accanto a ciascun pulsante secondario.
//sfrutta un widget material per applicare un'elevazione (ombra) e bordi arrotondati coerenti con il design dell'app
class _OptionLabel extends StatelessWidget {
  const _OptionLabel(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Material(
        color: Theme.of(context).colorScheme.surface,
        elevation: 2,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          child: Text(text, style: Theme.of(context).textTheme.labelMedium),
        ),
      );
}
