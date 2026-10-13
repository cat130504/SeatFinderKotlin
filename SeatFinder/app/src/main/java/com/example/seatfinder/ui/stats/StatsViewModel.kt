package com.example.seatfinder.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.seatfinder.data.remote.dto.Edificio
import com.example.seatfinder.data.remote.dto.PianoRowDto
import com.example.seatfinder.data.remote.dto.StatsFlussoRow
import com.example.seatfinder.data.repository.EdificioRepository
import com.example.seatfinder.data.repository.StatsRepository
import com.example.seatfinder.domain.model.StatoPiano
import com.example.seatfinder.domain.stats.StatsConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsUiState(
    val loading: Boolean = true,
    val rows: List<StatsFlussoRow> = emptyList(),
    val pianoRows: List<PianoRowDto> = emptyList(),
    val edifici: List<Edificio> = emptyList(),
    val statoAttuale: List<StatoPiano> = emptyList(),
    val ultimoAggiornamento: Long = 0L
)

class StatsViewModel : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        // Edifici e mappa quota → edificio (per i filtri)
        viewModelScope.launch {
            val edifici = EdificioRepository.getEdifici()
            val pianoRows = EdificioRepository.getPianoRows()
            _state.update { it.copy(edifici = edifici, pianoRows = pianoRows) }
        }

        // Storico degli ultimi N giorni
        viewModelScope.launch {
            val rows = StatsRepository.getStatsFlusso(StatsConfig.GIORNI_STORICO)
            _state.update { it.copy(rows = rows, loading = false) }
        }
    }
    suspend fun aggiornaStatoAttuale() {
        val stato = StatsRepository.getStatoAttualePerPiano()
        _state.update {
            it.copy(statoAttuale = stato, ultimoAggiornamento = System.currentTimeMillis())
        }
    }
}
