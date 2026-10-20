import 'package:flutter/material.dart';

//definisce un widget stateless (PlanBadge) che riceve una stringa obbligatoria planName (il nome della quota).
//nel metodo build, estrae il colorScheme dal contesto per utilizzare colori dinamici coerenti con l'applicazione.
class PlanBadge extends StatelessWidget {
  const PlanBadge({super.key, required this.planName});
  final String planName;


  //ritorna un widget material con colore di sfondo, ombra, bordi arrotondati e padding interno
  @override
  Widget build(BuildContext context) {
    //recupera lo schema dei colori del tema corrente per mantenere coerenza visiva
    final cs = Theme.of(context).colorScheme;
    return Material(
      //colore di sfondo basato sul tema primario container
      color: cs.primaryContainer,
      //effetto di ombra
      elevation: 6,
      borderRadius: BorderRadius.circular(16),

      //padding interno
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),

        //dispone gli elementi in una colonna con dimensione piu piccola possibile
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.location_on, size: 18, color: cs.onPrimaryContainer),
            const SizedBox(width: 6),

            //avvolge il testo planName in un widget Flexible per evitare problemi di overflow.
            //il testo adotta lo stile tipografico labelLarge
            Flexible(
              child: Text(
                planName,
                style: Theme.of(context)
                    .textTheme
                    .labelLarge
                    ?.copyWith(color: cs.onPrimaryContainer),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
