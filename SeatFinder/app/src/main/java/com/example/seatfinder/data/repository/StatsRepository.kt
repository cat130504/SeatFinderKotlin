package com.example.seatfinder.data.repository

import android.util.Log
import com.example.seatfinder.data.remote.SupabaseClient
import com.example.seatfinder.data.remote.dto.StatsFlussoRow
import com.example.seatfinder.domain.model.StatoPiano
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StatsRepository {

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

    suspend fun getStatoAttualePerPiano(): List<StatoPiano> {
        return try {
            withContext(Dispatchers.IO) {
                val tavoli = TavoloRepository.getTavoli()
                val pianoRows = EdificioRepository.getPianoRows()
                val edifici = EdificioRepository.getEdifici()

                val quotaToEdificioId = pianoRows.associate { it.quota to it.edificio }
                val idToNomeEdificio = edifici.associate { it.id to it.nome }

                // Aggrega per quota
                tavoli.groupBy { it.quota }
                    .map { (quota, lista) ->
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
