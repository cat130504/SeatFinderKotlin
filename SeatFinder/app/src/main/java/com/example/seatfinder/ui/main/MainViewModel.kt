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

    // true quando il caricamento iniziale (tavolo utente + edifici + piani) è concluso
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    var tavoloLat: Double? = null
        private set
    var tavoloLng: Double? = null
        private set

    init {
        viewModelScope.launch {
            tavoloAttuale.collectLatest { id ->
                _infoTavolo.value = id?.let { EdificioRepository.getInfoTavolo(it) }
            }
        }

        viewModelScope.launch {
            try {
                TavoloRepository.setTavolo(
                    TavoloRepository.getTavoloUtente(SessionManager.userId)
                )
                caricaEdifici()
            } finally {
                // qualunque cosa accada, sblocchiamo l'UI
                _ready.value = true
            }
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

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            tavoloAttuale.value?.let { id -> TavoloRepository.liberaTavolo(id) }
            TavoloRepository.setTavolo(null)
            onDone()
        }
    }

    private suspend fun caricaPiani(edificio: Edificio) {
        val piani = EdificioRepository.getPiani(edificio.id)
        _selezione.update { attuale ->
            MapSelezione(
                edificio = edificio,
                piani = piani,
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