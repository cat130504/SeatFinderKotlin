import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../domain/models.dart';

/// Accesso al DB per tavoli/occupazioni.
/// A differenza di Kotlin (object singleton + SessionManager.userId) qui
/// il client e lo userId vengono passati: così funziona anche nell'isolate
/// del servizio in background.
// repository per la gestione delle operazioni di database relative ai tavoli e alle occupazioni degli utenti
class TavoloRepository {
  TavoloRepository(this._db);
  final SupabaseClient _db;

  // recupera la lista di tutti i tavoli registrati, filtrandoli opzionalmente per quota o piano specifico
  Future<List<Tavolo>> getTavoli([String? quota]) async {
    try {
      final q = _db.from('tavolo').select();
      final rows = quota != null ? await q.eq('quota', quota) : await q;
      return rows.map((r) => Tavolo.fromJson(r)).toList();
    } catch (e) {
      debugPrint('DB_ERROR Errore recupero tavoli: $e');
      return [];
    }
  }

  //Ritorna true se l'occupazione è stata registrata.
  //(In Kotlin l'errore veniva solo loggato e il chiamante mostrava comunque
  //"Tavolo occupato"; qui l'esito è esplicito.)
  // registra l'occupazione di un tavolo aggiornando lo stato, decrementando i posti liberi e salvando il record sul database
  Future<bool> occupaTavolo(int idTavolo, int idUtente,
      {double? lat, double? lng}) async {
    try {
      await _db.from('occupazione').insert({
        'id_utente': idUtente,
        'id_tavolo': idTavolo,
        // UTC con "Z": istante corretto indipendentemente dal fuso del telefono
        'data_occupazione': DateTime.now().toLocal().toIso8601String(),
        'lat_occupazione': lat,
        'lng_occupazione': lng,
      });
      await _db.from('tavolo').update({'is_occupato': true}).eq('id', idTavolo);
      await _db.rpc('decPostiTavolo', params: {'tavolo_id': idTavolo});

      return true;
    } catch (e) {
      debugPrint('DB_ERROR Errore occupazione tavolo: $e');
      return false;
    }
  }

  // libera un tavolo occupato registrando la data di chiusura dell'occupazione, incrementando i posti e resettando lo stato se vuoto
  Future<bool> liberaTavolo(int idTavolo, int idUtente) async {
    try {
      debugPrint('Provo a liberare tavolo id=$idTavolo per utente=$idUtente');

      final aperte = await _db
          .from('occupazione')
          .select('id')
          .eq('id_tavolo', idTavolo)
          .eq('id_utente', idUtente)
          .isFilter('data_liberazione', null);

      if (aperte.isEmpty) {
        debugPrint(
            'DB_ERROR Nessuna occupazione aperta per utente=$idUtente, tavolo=$idTavolo');
        return false;
      }
      final idOccupazione = (aperte.first['id'] as num).toInt();

      await _db.from('occupazione').update({
        'data_liberazione': DateTime.now().toUtc().toIso8601String(),
      }).eq('id', idOccupazione);

      await _db.rpc('incPostiTavolo', params: {'tavolo_id': idTavolo});

      final rows = await _db.from('tavolo').select().eq('id', idTavolo);
      if (rows.isNotEmpty) {
        final t = Tavolo.fromJson(rows.first);
        if (t.postiDisponibili >= t.posti) {
          await _db.from('tavolo').update({'is_occupato': false}).eq('id', idTavolo);
        }
      }
      return true;
    } catch (e) {
      debugPrint('DB_ERROR Errore liberazione tavolo: $e');
      return false;
    }
  }

  //id del tavolo attualmente occupato dall'utente, o null.
  // individua e restituisce l'identificativo del tavolo attualmente occupato da un utente, se esiste una sessione attiva
  Future<int?> getTavoloUtente(int idUtente) async {
    try {
      final rows = await _db
          .from('occupazione')
          .select('id_tavolo')
          .eq('id_utente', idUtente)
          .isFilter('data_liberazione', null);
      if (rows.isEmpty) return null;
      return (rows.first['id_tavolo'] as num).toInt();
    } catch (e) {
      debugPrint('DB_ERROR Errore recupero tavolo utente: $e');
      return null;
    }
  }

  // restituisce il numero di posti ancora disponibili per un determinato tavolo identificato dal suo id
  Future<int> getPostiLiberi(int id) async {
    try {
      final rows = await _db.from('tavolo').select().eq('id', id);
      if (rows.isEmpty) return -1;
      return Tavolo.fromJson(rows.first).postiDisponibili;
    } catch (e) {
      debugPrint('DB_ERROR Errore recupero posti liberi: $e');
      return -1;
    }
  }
}