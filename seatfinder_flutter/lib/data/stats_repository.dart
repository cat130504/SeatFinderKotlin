import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../domain/models.dart';
import 'edificio_repository.dart';
import 'tavolo_repository.dart';

// repository principale per il recupero e l'aggregazione dei dati statistici e di flusso dal database supabase
class StatsRepository {
  StatsRepository(this._db)
      : _tavoli = TavoloRepository(_db),
        _edifici = EdificioRepository(_db);

  final SupabaseClient _db;
  final TavoloRepository _tavoli;
  final EdificioRepository _edifici;

  // richiama una funzione remota (rpc) su supabase per ottenere i dati storici aggregati dei flussi di occupazione
  Future<List<StatsFlussoRow>> getStatsFlusso(int giorni) async {
    try {
      final rows = await _db.rpc('stats_flusso', params: {'p_giorni': giorni});
      return (rows as List)
          .map((r) => StatsFlussoRow.fromJson(r as Map<String, dynamic>))
          .toList();
    } catch (e) {
      debugPrint('DB_ERROR Errore stats_flusso: $e');
      return [];
    }
  }

  //lancia in caso di errore (la UI mostra "Riprova"), come in Kotlin.
  //recupera lo storico completo di tutte le occupazioni effettuate da un determinato utente ordinandole per data discendente
  Future<List<OccupazioneUtente>> getOccupazioniUtente(int userId) async {
    final rows = await _db
        .from('occupazione')
        .select('id_tavolo, data_occupazione, lat_occupazione, lng_occupazione, '
        'tavolo:id_tavolo(quota, piano(edificio(nome)))')
        .eq('id_utente', userId)
        .order('data_occupazione', ascending: false);
    return rows.map((r) => OccupazioneUtente.fromJson(r)).toList();
  }

  // calcola lo stato attuale in tempo reale di occupazione dei tavoli aggregando i dati per ciascun piano ed edificio
  Future<List<StatoPiano>> getStatoAttualePerPiano() async {
    try {
      final tavoli = await _tavoli.getTavoli();
      final pianoRows = await _edifici.getPianoRows();
      final edifici = await _edifici.getEdifici();

      final quotaToEdificioId = {for (final p in pianoRows) p.quota: p.edificio};
      final idToNome = {for (final e in edifici) e.id: e.nome};

      final perQuota = <String, List<Tavolo>>{};
      for (final t in tavoli) {
        perQuota.putIfAbsent(t.quota, () => []).add(t);
      }

      final out = perQuota.entries.map((e) {
        final edificioId = quotaToEdificioId[e.key] ?? -1;
        return StatoPiano(
          quota: e.key,
          edificioId: edificioId,
          edificioNome: idToNome[edificioId] ?? '—',
          tavoliOccupati: e.value.where((t) => t.isOccupato).length,
          tavoliTotali: e.value.length,
          postiLiberi: e.value.fold(0, (a, t) => a + t.postiDisponibili),
          postiTotali: e.value.fold(0, (a, t) => a + t.posti),
        );
      }).toList()
        ..sort((a, b) {
          final c = a.edificioId.compareTo(b.edificioId);
          return c != 0 ? c : a.quota.compareTo(b.quota);
        });
      return out;
    } catch (e) {
      debugPrint('DB_ERROR Errore stato attuale: $e');
      return [];
    }
  }
}