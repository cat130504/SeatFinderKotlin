package com.example.seatfinder.data.repository

import android.util.Log
import com.example.seatfinder.data.remote.SupabaseClient
import com.example.seatfinder.data.remote.dto.Edificio
import com.example.seatfinder.data.remote.dto.PianoQuota
import com.example.seatfinder.data.remote.dto.PianoRowDto
import com.example.seatfinder.data.remote.dto.TavoloInfoResponse
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

//Espone le letture su "edificio" e "piano", query annidata per ricavare (edificio, quota) da un tavolo

object EdificioRepository {

    //Elenco di tutti gli edifici. Usato dal FAB e da StatsScreen.
    suspend fun getEdifici(): List<Edificio> {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClient.client
                    .from("edificio")
                    .select(Columns.raw("id, nome"))
                    .decodeList<Edificio>()
            }
        } catch (e: Exception) {
            Log.e("DB_ERROR", "Errore recupero edifici: ${e.message}")
            emptyList()
        }
    }

    //Piani di un edificio specifico. Usato quando si cambia edificio dal FAB.
    suspend fun getPiani(idEdificio: Int): List<PianoQuota> {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClient.client
                    .from("piano")
                    .select(Columns.raw("quota")) {
                        filter { eq("edificio", idEdificio) }
                    }
                    .decodeList<PianoQuota>()
            }
        } catch (e: Exception) {
            Log.e("DB_ERROR", "Errore recupero piani: ${e.message}")
            emptyList()
        }
    }

    //Tutte le coppie (quota, edificio). Serve a StatsRepository
    suspend fun getPianoRows(): List<PianoRowDto> {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClient.client
                    .from("piano")
                    .select(Columns.raw("quota, edificio"))
                    .decodeList<PianoRowDto>()
            }
        } catch (e: Exception) {
            Log.e("DB_ERROR", "Errore recupero piano rows: ${e.message}")
            emptyList()
        }
    }

    //(nome edificio, quota) del tavolo indicato, ricavati con una query annidata
    suspend fun getInfoTavolo(idTavolo: Int): Pair<String, String>? {
        return try {
            withContext(Dispatchers.IO) {
                val response = SupabaseClient.client
                    .from("tavolo")
                    .select(Columns.raw("quota, piano(edificio(nome))")) {
                        filter { eq("id", idTavolo) }
                    }
                    .decodeSingleOrNull<TavoloInfoResponse>()

                response?.let { Pair(it.piano.edificio.nome, it.quota) }
            }
        } catch (e: Exception) {
            Log.e("DB_ERROR", "Errore recupero info tavolo: ${e.message}")
            null
        }
    }
}