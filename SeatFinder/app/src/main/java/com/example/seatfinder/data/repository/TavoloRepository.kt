package com.example.seatfinder.data.repository

import android.util.Log
import com.example.seatfinder.auth.SessionManager
import com.example.seatfinder.data.remote.SupabaseClient
import com.example.seatfinder.data.remote.dto.OccupazioneIdDto
import com.example.seatfinder.data.remote.dto.OccupazioneInsert
import com.example.seatfinder.data.remote.dto.OccupazioneTavoloDto
import com.example.seatfinder.data.remote.dto.TavoloDto
import com.example.seatfinder.data.remote.dto.TavoloUpdate
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import java.time.OffsetDateTime
import java.time.ZoneId

//Espone le operazioni su tavoli e occupazioni
object TavoloRepository {

    //Tavolo attualmente occupato dall'utente (null = nessuno). Condiviso fra UI e service
    private val _tavoloAttuale = MutableStateFlow<Int?>(null)
    val tavoloAttuale: StateFlow<Int?> = _tavoloAttuale.asStateFlow()

    //Contatore "forza refresh": MapScreen lo osserva e ricarica quando cambia
    private val _refreshTick = MutableStateFlow(0)
    val refreshTick: StateFlow<Int> = _refreshTick.asStateFlow()

    //Imposta il tavolo attuale e notifica la mappa (incrementa refreshTick)
    fun setTavolo(id: Int?) {
        _tavoloAttuale.value = id
        _refreshTick.value++
    }

    //Chiede alla mappa di ricaricare i tavoli senza cambiare il tavolo attuale
    fun richiediRefresh() {
        _refreshTick.value++
    }

    // ==================== ACCESSO AL DB ====================

    //Legge i tavoli dal DB, filtrati per quota
    //In caso di errore restituisce lista vuota
    suspend fun getTavoli(quota: String? = null): List<TavoloDto> {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClient.client
                    .from("tavolo")
                    .select() {
                        if (quota != null) {
                            filter { eq("quota", quota) }
                        }
                    }
                    .decodeList<TavoloDto>()
            }
        } catch (e: Exception) {
            Log.e("DB_ERROR", "Errore recupero tavoli: ${e.message}")
            emptyList()
        }
    }

    //Occupa un tavolo: tre operazioni separate sul DB
    //1. tavolo.is_occupato = true
    //2. RPC decPostiTavolo (decrementa postiDisponibili di 1)
    //3. Inserisce una riga in "occupazione" con timestamp e coordinate
    @OptIn(ExperimentalTime::class)
    suspend fun occupaTavolo(idTavolo: Int, idUtente: Int, lat: Double? = null, lng: Double? = null) {
        try {
            withContext(Dispatchers.IO) {

                SupabaseClient.client.from("tavolo").update(
                    TavoloUpdate(is_occupato = true)
                ) {
                    filter { eq("id", idTavolo) }
                }

                SupabaseClient.client.postgrest.rpc(
                    "decPostiTavolo",
                    mapOf("tavolo_id" to idTavolo)
                )

                SupabaseClient.client.from("occupazione").insert(
                    OccupazioneInsert(
                        id_utente = idUtente,
                        id_tavolo = idTavolo,
                        data_occupazione = OffsetDateTime.now(ZoneId.systemDefault()).toString(),
                        lat_occupazione = lat,
                        lng_occupazione = lng
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("DB_ERROR", "Errore occupazione tavolo: ${e.message}")
        }
    }

    //Libera un tavolo: quattro passi
    //1. Trova l'occupazione aperta (data_liberazione IS NULL) dell'utente per quel tavolo
    //2. La chiude scrivendo data_liberazione
    //3. RPC incPostiTavolo (incrementa postiDisponibili di 1)
    //4. Se il tavolo è di nuovo tutto libero, is_occupato = false
    //Ritorna true se l'operazione è andata a buon fine, false altrimenti
    @OptIn(ExperimentalTime::class)
    suspend fun liberaTavolo(idTavolo: Int): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("DEBUG", "Provo a liberare tavolo con id=$idTavolo per utente=${SessionManager.userId}")

                //1. Cerca l'occupazione aperta dell'utente per questo tavolo
                val occupazioneAperta = SupabaseClient.client.from("occupazione")
                    .select {
                        filter {
                            eq("id_tavolo", idTavolo)
                            eq("id_utente", SessionManager.userId)
                            exact("data_liberazione", null)
                        }
                    }
                    .decodeList<OccupazioneIdDto>()

                if (occupazioneAperta.isEmpty()) {
                    Log.e("DB_ERROR", "Nessuna occupazione aperta per utente=${SessionManager.userId}, tavolo=$idTavolo")
                    return@withContext false
                }

                val idOccupazione = occupazioneAperta.first().id

                //2. Chiude l'occupazione scrivendo la data di liberazione
                SupabaseClient.client.from("occupazione").update({
                    set("data_liberazione", OffsetDateTime.now(ZoneId.systemDefault()).toString())                }) {
                    filter { eq("id", idOccupazione) }
                }

                //3. Incrementa postiDisponibili di 1
                SupabaseClient.client.postgrest.rpc(
                    "incPostiTavolo",
                    mapOf("tavolo_id" to idTavolo)
                )

                //4. Se il tavolo è tornato completamente libero, is_occupato = false
                val tavoloAggiornato = SupabaseClient.client.from("tavolo")
                    .select {
                        filter { eq("id", idTavolo) }
                    }
                    .decodeList<TavoloDto>()
                    .firstOrNull()

                if (tavoloAggiornato != null && tavoloAggiornato.postiDisponibili >= tavoloAggiornato.posti) {
                    SupabaseClient.client.from("tavolo").update({
                        set("is_occupato", false)
                    }) {
                        filter { eq("id", idTavolo) }
                    }
                }

                true
            }
        } catch (e: Exception) {
            Log.e("DB_ERROR", "Errore liberazione tavolo", e)
            false
        }
    }

    //Id del tavolo attualmente occupato dall'utente, o null se non ne occupa nessuno
    suspend fun getTavoloUtente(idUtente: Int): Int? {
        return try {
            val result = withContext(Dispatchers.IO) {
                SupabaseClient.client
                    .from("occupazione")
                    .select {
                        filter {
                            eq("id_utente", idUtente)
                            exact("data_liberazione", null)   // IS NULL
                        }
                    }
                    .decodeList<OccupazioneTavoloDto>()
            }
            result.firstOrNull()?.id_tavolo
        } catch (e: Exception) {
            Log.e("DB_ERROR", "Errore recupero tavolo utente: ${e.message}")
            null
        }
    }

    //Posti liberi di un tavolo
    suspend fun getPostiLiberi(id: Int): Int {
        return try {
            val result = withContext(Dispatchers.IO) {
                SupabaseClient.client
                    .from("tavolo")
                    .select {
                        filter { eq("id", id) }
                    }
                    .decodeList<TavoloDto>()
            }

            if (result.isNotEmpty()) result[0].postiDisponibili else -1
        } catch (e: Exception) {
            Log.e("DB_ERROR", "Errore recupero posti liberi: ${e.message}")
            -1
        }
    }
}