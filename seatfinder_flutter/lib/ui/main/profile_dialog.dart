import 'package:flutter/material.dart';

Future<void> showProfileDialog(
  BuildContext context, {
  required String nome,
  required VoidCallback onConfirmLogout,
}) {
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
