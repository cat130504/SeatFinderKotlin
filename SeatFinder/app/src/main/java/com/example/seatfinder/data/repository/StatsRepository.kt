package com.example.seatfinder.data.repository

import android.util.Log
import com.example.seatfinder.data.remote.SupabaseClient
import com.example.seatfinder.data.remote.dto.OccupazioneUtenteDto
import com.example.seatfinder.data.remote.dto.StatsFlussoRow
import com.example.seatfinder.domain.model.OccupazioneUtente
import com.example.seatfinder.domain.model.StatoPiano
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.seatfinder.data.remote.dto.toDomain
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

//"Cameriere" delle statistiche: solo letture dal DB, nessuna scrittura.
object StatsRepository {

    //Storico di affluenza: righe (quota, giorno, ora, occupazioni, posti totali)
    // ottenute dalla funzione RPC "stats_flusso" su Supabase.

    suspend fun getStatsFlusso(giorni: Int): List<StatsFlussoRow> {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClient.client.postgrest
                    .rpc(
                        function = "stats_flusso",
                        parameters = mapOf("p_giorni" to giorni)
                    )
                    .decodeList<StatsFlussoRow>()
            }
        } catch (e: Exception) {
            Log.e("DB_ERROR", "Errore stats_flusso: ${e.message}", e)
            emptyList()
        }
    }

    //Occupazioni dell'utente, dalla più recente
    suspend fun getOccupazioniUtente(userId: Int): List<OccupazioneUtente> =
        withContext(Dispatchers.IO) {
            SupabaseClient.client.postgrest
                .from("occupazione")   // ← verifica il nome reale della tabella
                .select(
                    Columns.raw(
                        "id_tavolo, data_occupazione, lat_occupazione, lng_occupazione, " +
                                "tavolo:id_tavolo(quota, piano(edificio(nome)))"
                    )
                ) {
                    filter { eq("id_utente", userId) }
                    order("data_occupazione", Order.DESCENDING)
                }
                .decodeList<OccupazioneUtenteDto>()
                .map { it.toDomain() }
        }

    //Stato attuale per piano: aggrega i tavoli di ogni quota
    suspend fun getStatoAttualePerPiano(): List<StatoPiano> {
        return try {
            withContext(Dispatchers.IO) {
                val tavoli = TavoloRepository.getTavoli()
                val pianoRows = EdificioRepository.getPianoRows()
                val edifici = EdificioRepository.getEdifici()

                //quota → idEdificio: per associare ogni tavolo al suo edificio
                val quotaToEdificioId = pianoRows.associate { it.quota to it.edificio }
                //idEdificio → nome: per la label nella card
                val idToNomeEdificio = edifici.associate { it.id to it.nome }

                // Aggrega per quota
                tavoli.groupBy { it.quota }
                    .map { (quota, lista) ->
                        //Fallback "-1"/"—" se la quota non è mappata (dati incoerenti)
                        val edificioId = quotaToEdificioId[quota] ?: -1
                        val edificioNome = idToNomeEdificio[edificioId] ?: "—"

                        StatoPiano(
                            quota = quota,
                            edificioId = edificioId,
                            edificioNome = edificioNome,
                            tavoliOccupati = lista.count { it.is_occupato },
                            tavoliTotali = lista.size,
                            postiLiberi = lista.sumOf { it.postiDisponibili },
                            postiTotali = lista.sumOf { it.posti }
                        )
                    }
                    .sortedWith(compareBy({ it.edificioId }, { it.quota }))
            }
        } catch (e: Exception) {
            Log.e("DB_ERROR", "Errore stato attuale: ${e.message}", e)
            emptyList()
        }
    }
}