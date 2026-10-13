package com.example.seatfinder.ui.stats

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.seatfinder.auth.SessionManager
import com.example.seatfinder.data.repository.StatsRepository
import com.example.seatfinder.domain.model.OccupazioneUtente
import com.example.seatfinder.domain.stats.StatsConfig
import com.example.seatfinder.domain.stats.giorniDaOggi
import com.example.seatfinder.ui.stats.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ProfileStatsUiState(
    val loading: Boolean = true,
    val errore: Boolean = false,
    val occupazioni: List<OccupazioneUtente> = emptyList()
)

class ProfileStatsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ProfileStatsUiState())
    val state: StateFlow<ProfileStatsUiState> = _state.asStateFlow()

    fun carica() {
        viewModelScope.launch {
            // spinner solo al primo caricamento, poi aggiornamento silenzioso
            _state.update { it.copy(loading = it.occupazioni.isEmpty(), errore = false) }
            try {
                val lista = StatsRepository.getOccupazioniUtente(SessionManager.userId)
                _state.update { it.copy(occupazioni = lista, loading = false) }
            } catch (e: Exception) {
                Log.e("ProfileStats", "Errore caricamento", e)
                // se ho già dei dati li tengo, mostro l'errore solo a schermata vuota
                _state.update { it.copy(loading = false, errore = it.occupazioni.isEmpty()) }
            }
        }
    }
}