import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../domain/models.dart';

// repository responsabile della gestione e del recupero dei dati relativi agli edifici e ai piani dal database supabase
class EdificioRepository {
  EdificioRepository(this._db);
  final SupabaseClient _db;

  // recupera in modo asincrono l'elenco completo di tutti gli edifici registrati
  Future<List<Edificio>> getEdifici() async {
    try {
      final rows = await _db.from('edificio').select('id, nome');
      return rows.map((r) => Edificio.fromJson(r)).toList();
    } catch (e) {
      debugPrint('DB_ERROR Errore recupero edifici: $e');
      return [];
    }
  }

  // recupera le quote dei piani associati a uno specifico edificio identificato dal suo id
  Future<List<PianoQuota>> getPiani(int idEdificio) async {
    try {
      final rows =
      await _db.from('piano').select('quota').eq('edificio', idEdificio);
      return rows.map((r) => PianoQuota.fromJson(r)).toList();
    } catch (e) {
      debugPrint('DB_ERROR Errore recupero piani: $e');
      return [];
    }
  }

  // restituisce una lista completa di righe di raccordo tra piani ed edifici per scopi di filtraggio
  Future<List<PianoRow>> getPianoRows() async {
    try {
      final rows = await _db.from('piano').select('quota, edificio');
      return rows.map((r) => PianoRow.fromJson(r)).toList();
    } catch (e) {
      debugPrint('DB_ERROR Errore recupero piano rows: $e');
      return [];
    }
  }

  // (nomeEdificio, quota) del tavolo, o null.
  // interroga il database per ottenere le informazioni strutturali dettagliate di un tavolo specifico tramite relazioni annidate
  Future<({String edificio, String quota})?> getInfoTavolo(int idTavolo) async {
    try {
      final r = await _db
          .from('tavolo')
          .select('quota, piano(edificio(nome))')
          .eq('id', idTavolo)
          .maybeSingle();
      if (r == null) return null;
      final piano = r['piano'] as Map<String, dynamic>;
      final edificio = piano['edificio'] as Map<String, dynamic>;
      return (edificio: edificio['nome'].toString(), quota: r['quota'].toString());
    } catch (e) {
      debugPrint('DB_ERROR Errore recupero info tavolo: $e');
      return null;
    }
  }
}