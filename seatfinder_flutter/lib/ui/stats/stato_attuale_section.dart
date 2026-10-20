import 'package:flutter/material.dart';

import '../../domain/models.dart';
import 'stats_colors.dart';

// sezione dedicata alla visualizzazione in tempo reale dello stato di occupazione di edifici e piani
class SezioneStatoAttuale extends StatelessWidget {
  const SezioneStatoAttuale({
    super.key,
    required this.stato,
    required this.edifici,
    required this.edificioSel,
    required this.onEdificioChange,
  });

  final List<StatoPiano> stato;
  final List<Edificio> edifici;
  final int? edificioSel;
  final ValueChanged<int?> onEdificioChange;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Stato attuale', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            // riga di chip filtrabili per selezionare un edificio specifico o visualizzarli tutti
            RigaChip(children: [
              FilterChip(
                selected: edificioSel == null,
                onSelected: (_) => onEdificioChange(null),
                label: const Text('Tutti'),
              ),
              for (final ed in edifici)
                FilterChip(
                  selected: edificioSel == ed.id,
                  onSelected: (_) => onEdificioChange(ed.id),
                  label: Text(ed.nome),
                ),
            ]),
            const SizedBox(height: 12),
            // mostra un indicatore circolare di caricamento se la lista dello stato è ancora vuota
            if (stato.isEmpty)
              const SizedBox(
                height: 60,
                child: Center(
                  child: SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                ),
              )
            else
            // genera una riga dettagliata per ogni piano appartenente allo stato filtrato
              for (final s in stato)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: _RigaStatoPiano(s),
                ),
          ],
        ),
      ),
    );
  }
}

// componente che rappresenta la singola riga di stato per un determinato piano di un edificio
class _RigaStatoPiano extends StatelessWidget {
  const _RigaStatoPiano(this.s);
  final StatoPiano s;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('${s.edificioNome} · ${s.quota}', style: tt.labelLarge),
            Flexible(
              child: Text(
                '${s.tavoliLiberi}/${s.tavoliTotali} tavoli liberi · '
                    '${s.postiLiberi}/${s.postiTotali} posti',
                textAlign: TextAlign.end,
                style: tt.bodySmall?.copyWith(color: cs.onSurfaceVariant),
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        // barra di progresso dinamica che visualizza la percentuale di occupazione corrente
        BarraProgresso(
          valore: s.disponibilita,
          colore: coloreOccupazione(s.percentualeOccupazione),
        ),
      ],
    );
  }
}