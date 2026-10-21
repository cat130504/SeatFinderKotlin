package com.example.seatfinder.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.seatfinder.auth.SessionManager
import com.example.seatfinder.data.remote.dto.Edificio
import com.example.seatfinder.data.remote.dto.PianoQuota
import com.example.seatfinder.data.repository.EdificioRepository
import com.example.seatfinder.data.repository.TavoloRepository
import com.example.seatfinder.service.PingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

//Custodisce lo stato che serve a MainAppScreen per disegnare

data class MapSelezione(
    val edificio: Edificio? = null,
    val piani: List<PianoQuota> = emptyList(),
    val quota: String? = null
)

class MainViewModel : ViewModel() {

    //Quale tavolo sto occupando. Delegato a TavoloRepository così anche PingService lo vede
    val tavoloAttuale: StateFlow<Int?> = TavoloRepository.tavoloAttuale

    //Elenco edifici per il FAB "edificio"
    private val _edifici = MutableStateFlow<List<Edificio>>(emptyList())
    val edifici: StateFlow<List<Edificio>> = _edifici.asStateFlow()

    //Cosa è selezionato nella mappa
    private val _selezione = MutableStateFlow(MapSelezione())
    val selezione: StateFlow<MapSelezione> = _selezione.asStateFlow()

    //Info del tavolo attualmente occupato, mostrati nella AppTopBar
    private val _infoTavolo = MutableStateFlow<Pair<String, String>?>(null)
    val infoTavolo: StateFlow<Pair<String, String>?> = _infoTavolo.asStateFlow()

    //Spinner finchè la mappa non è pronta
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    //Coordinate del tavolo occupato. Servono a PingService per il controllo distanza
    var tavoloLat: Double? = null
        private set
    var tavoloLng: Double? = null
        private set

    init {
        //Ogni volta che cambia il tavolo attuale, ricarica (edificio, quota) per la top bar
        viewModelScope.launch {
            tavoloAttuale.collectLatest { id ->
                _infoTavolo.value = id?.let { EdificioRepository.getInfoTavolo(it) }
            }
        }

        //Caricamento iniziale: recupera il tavolo già occupato (se c'è) e gli edifici
        viewModelScope.launch {
            try {
                val tavolo = TavoloRepository.getTavoloUtente(SessionManager.userId)
                tavoloLat = tavolo?.lat
                tavoloLng = tavolo?.lng
                TavoloRepository.setTavolo(tavolo?.id) //inizializza anche il controllo di posizione
                caricaEdifici()
            } finally {
                _ready.value = true
            }
        }
    }

    //Carica edifici e apre i piani del primo
    private suspend fun caricaEdifici() {
        try {
            val lista = EdificioRepository.getEdifici()
            _edifici.value = lista
            lista.firstOrNull()?.let { caricaPiani(it) }
        } catch (e: Exception) {
            Log.e("SUPABASE_INIT", "Errore: ${e.message}")
        }
    }

    //Logout: prima libera l'eventuale tavolo occupato, poi azzera lo stato
    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            tavoloAttuale.value?.let { id -> TavoloRepository.liberaTavolo(id) }
            TavoloRepository.setTavolo(null)
            onDone()
        }
    }

    //Carica i piani dell'edificio scelto e seleziona la prima quota come default
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

    //Cambio edificio dal FAB: ricarica i piani e aggiorna la selezione
    fun selezionaEdificio(edificio: Edificio) {
        viewModelScope.launch { caricaPiani(edificio) }
    }

    //Cambio piano dal FAB: aggiorna solo la quota
    fun selezionaPiano(quota: String) {
        _selezione.update { it.copy(quota = quota) }
    }

    //Chiamato dopo una scansione QR riuscita: salva le coordinate e notifica il repository
    fun onTavoloOccupato(idTavolo: Int, lat: Double?, lng: Double?) {
        tavoloLat = lat
        tavoloLng = lng
        TavoloRepository.setTavolo(idTavolo)
    }

    //"Libera Tavolo" dalla top bar: libera sul DB, poi azzera lo stato locale
    fun liberaTavolo() {
        val id = tavoloAttuale.value ?: return
        viewModelScope.launch {
            if (TavoloRepository.liberaTavolo(id)) {
                TavoloRepository.setTavolo(null)
            }
        }
    }
}