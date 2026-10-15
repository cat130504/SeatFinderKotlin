import 'package:flutter/material.dart';

class FabOption {
  final IconData icon;
  final String label;
  final VoidCallback onClick;
  const FabOption({required this.icon, required this.label, required this.onClick});
}

class ExpandableFabMenu extends StatefulWidget {
  const ExpandableFabMenu({
    super.key,
    required this.options,
    this.alignEnd = true,
    this.heroTag,
  });

  final List<FabOption> options;
  final bool alignEnd; // true = menu a destra (label prima del bottone)
  final Object? heroTag;

  @override
  State<ExpandableFabMenu> createState() => _ExpandableFabMenuState();
}

class _ExpandableFabMenuState extends State<ExpandableFabMenu> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
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
                    for (final o in widget.options)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            if (widget.alignEnd) ...[
                              _OptionLabel(o.label),
                              const SizedBox(width: 8),
                            ],
                            FloatingActionButton.small(
                              heroTag: null,
                              backgroundColor: cs.secondaryContainer,
                              foregroundColor: cs.onSecondaryContainer,
                              onPressed: () {
                                o.onClick();
                                setState(() => _expanded = false);
                              },
                              tooltip: o.label,
                              child: Icon(o.icon),
                            ),
                            if (!widget.alignEnd) ...[
                              const SizedBox(width: 8),
                              _OptionLabel(o.label),
                            ],
                          ],
                        ),
                      ),
                  ],
                )
              : const SizedBox(width: 0, height: 0),
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
