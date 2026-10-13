package com.example.seatfinder.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.seatfinder.auth.SessionManager
import com.example.seatfinder.data.remote.dto.Edificio
import com.example.seatfinder.data.remote.dto.PianoQuota
import com.example.seatfinder.data.repository.EdificioRepository
import com.example.seatfinder.data.repository.TavoloRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapSelezione(
    val edificio: Edificio? = null,
    val piani: List<PianoQuota> = emptyList(),
    val quota: String? = null
)

class MainViewModel : ViewModel() {

    val tavoloAttuale: StateFlow<Int?> = TavoloRepository.tavoloAttuale

    private val _edifici = MutableStateFlow<List<Edificio>>(emptyList())
    val edifici: StateFlow<List<Edificio>> = _edifici.asStateFlow()

    private val _selezione = MutableStateFlow(MapSelezione())
    val selezione: StateFlow<MapSelezione> = _selezione.asStateFlow()

    private val _infoTavolo = MutableStateFlow<Pair<String, String>?>(null)
    val infoTavolo: StateFlow<Pair<String, String>?> = _infoTavolo.asStateFlow()

    // Coordinate del tavolo, passate al PingService per il controllo di allontanamento
    var tavoloLat: Double? = null
        private set
    var tavoloLng: Double? = null
        private set

    init {
        // Aggiorna (edificio, quota) ogni volta che cambia il tavolo occupato
        viewModelScope.launch {
            tavoloAttuale.collectLatest { id ->
                _infoTavolo.value = id?.let { EdificioRepository.getInfoTavolo(it) }
            }
        }

        // Stato iniziale: tavolo già occupato + edifici/piani
        viewModelScope.launch {
            TavoloRepository.setTavolo(TavoloRepository.getTavoloUtente(SessionManager.userId))
            caricaEdifici()
        }
    }

    private suspend fun caricaEdifici() {
        try {
            val lista = EdificioRepository.getEdifici()
            _edifici.value = lista
            lista.firstOrNull()?.let { caricaPiani(it) }
        } catch (e: Exception) {
            Log.e("SUPABASE_INIT", "Errore: ${e.message}")
        }
    }

    private suspend fun caricaPiani(edificio: Edificio) {
        val piani = EdificioRepository.getPiani(edificio.id)
        _selezione.update { attuale ->
            MapSelezione(
                edificio = edificio,
                piani = piani,
                // Se l'edificio non ha piani resta la quota precedente (come prima)
                quota = piani.firstOrNull()?.quota ?: attuale.quota
            )
        }
    }

    fun selezionaEdificio(edificio: Edificio) {
        viewModelScope.launch { caricaPiani(edificio) }
    }

    fun selezionaPiano(quota: String) {
        _selezione.update { it.copy(quota = quota) }
    }

    fun onTavoloOccupato(idTavolo: Int, lat: Double?, lng: Double?) {
        // Prima le coordinate, poi il tavolo: chi osserva tavoloAttuale le trova già pronte
        tavoloLat = lat
        tavoloLng = lng
        TavoloRepository.setTavolo(idTavolo)
    }

    fun liberaTavolo() {
        val id = tavoloAttuale.value ?: return
        viewModelScope.launch {
            if (TavoloRepository.liberaTavolo(id)) {
                TavoloRepository.setTavolo(null)
            }
        }
    }
}
