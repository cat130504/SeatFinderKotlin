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


object TavoloRepository {

    // ==================== STATO IN MEMORIA ====================

    // ---------- Tavolo attualmente occupato ----------
    private val _tavoloAttuale = MutableStateFlow<Int?>(null)
    val tavoloAttuale: StateFlow<Int?> = _tavoloAttuale.asStateFlow()

    // ---------- Tick di refresh per la mappa ----------
    // Incrementato ogni volta che qualcosa cambia (occupazione/liberazione),
    // così MapScreen può ricaricare i tavoli dal DB anche se la quota non cambia.
    private val _refreshTick = MutableStateFlow(0)
    val refreshTick: StateFlow<Int> = _refreshTick.asStateFlow()

    fun setTavolo(id: Int?) {
        _tavoloAttuale.value = id
        // Qualsiasi cambio di stato del tavolo invalida la mappa
        _refreshTick.value++
    }


    fun richiediRefresh() {
        _refreshTick.value++
    }

    // ==================== ACCESSO AL DB ====================

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

    @OptIn(ExperimentalTime::class)
    suspend fun occupaTavolo(idTavolo: Int, idUtente: Int, lat: Double? = null, lng: Double? = null) {
        try {
            withContext(Dispatchers.IO) {

                // 1. Aggiorna stato tavolo
                SupabaseClient.client.from("tavolo").update(
                    TavoloUpdate(is_occupato = true)
                ) {
                    filter { eq("id", idTavolo) }
                }

                SupabaseClient.client.postgrest.rpc(
                    "decPostiTavolo",
                    mapOf("tavolo_id" to idTavolo)
                )

                // 2. Inserisci nuova occupazione
                SupabaseClient.client.from("occupazione").insert(
                    OccupazioneInsert(
                        id_utente = idUtente,
                        id_tavolo = idTavolo,
                        data_occupazione = Clock.System.now().toString(),
                        lat_occupazione = lat,
                        lng_occupazione = lng
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("DB_ERROR", "Errore occupazione tavolo: ${e.message}")
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun liberaTavolo(idTavolo: Int): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("DEBUG", "Provo a liberare tavolo con id=$idTavolo per utente=${SessionManager.userId}")

                // 1. Trova la riga di occupazione di QUESTO utente per QUESTO tavolo
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

                // 2. Chiudi SOLO la riga di questo utente
                SupabaseClient.client.from("occupazione").update({
                    set("data_liberazione", Clock.System.now().toString())
                }) {
                    filter { eq("id", idOccupazione) }
                }

                // 3. Incrementa i posti disponibili
                SupabaseClient.client.postgrest.rpc(
                    "incPostiTavolo",
                    mapOf("tavolo_id" to idTavolo)
                )

                // 4. Leggi lo stato aggiornato del tavolo per vedere se è tornato tutto libero
                val tavoloAggiornato = SupabaseClient.client.from("tavolo")
                    .select {
                        filter { eq("id", idTavolo) }
                    }
                    .decodeList<TavoloDto>()
                    .firstOrNull()

                if (tavoloAggiornato != null && tavoloAggiornato.postiDisponibili >= tavoloAggiornato.posti) {
                    // Tutti i posti sono liberi: il tavolo è completamente libero
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

    /** Id del tavolo attualmente occupato dall'utente, o null se non ne occupa nessuno. */
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
