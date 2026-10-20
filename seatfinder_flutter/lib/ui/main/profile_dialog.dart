import 'package:flutter/material.dart';

//funzione di utilità asincrona per mostrare un dialogo di conferma per il profilo/logout
Future<void> showProfileDialog(
  BuildContext context, {
  required String nome,
  required VoidCallback onConfirmLogout,
}) {

  //contenuto del dialog con nome, campi di testo e bottoni
  return showDialog<void>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text('Ciao $nome!', style: Theme.of(ctx).textTheme.titleLarge),
      content: Text(
        'Vuoi effettuare il logout dall\'applicazione?',
        style: Theme.of(ctx).textTheme.bodyMedium,
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(ctx).pop(),
          child: const Text('Annulla'),
        ),
        FilledButton(
          style: FilledButton.styleFrom(
              backgroundColor: Theme.of(ctx).colorScheme.error),
          onPressed: () {
            Navigator.of(ctx).pop();
            onConfirmLogout();
          },
          child: const Text('Logout'),
        ),
      ],
    ),
  );
}
