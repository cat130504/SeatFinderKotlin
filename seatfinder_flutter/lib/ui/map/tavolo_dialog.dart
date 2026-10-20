import 'package:flutter/material.dart';

import '../../domain/models.dart';

//dialog per mostrare le caratteristiche di un singolo tavolo, si apre al click su uno di essi
Future<void> showTavoloDialog(BuildContext context, Tavolo tavolo) {
  return showDialog<void>(
    context: context,
    builder: (_) => Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Tavolo ${tavolo.id}',
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            Text('Posti Totali: ${tavolo.posti}'),
            const SizedBox(height: 12),
            Text('Presa elettrica: ${tavolo.presa ? "Presente" : "Assente"}'),
            const SizedBox(height: 12),
            Text('Posti Disponibili: ${tavolo.postiDisponibili}'),
          ],
        ),
      ),
    ),
  );
}
