package com.example.seatfinder.data.remote.dto

import kotlinx.serialization.Serializable
import com.example.seatfinder.domain.model.OccupazioneUtente
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

@Serializable
data class OccupazioneInsert(
    val id_utente: Int,
    val id_tavolo: Int,
    val data_occupazione: String,
    val lat_occupazione: Double? = null,
    val lng_occupazione: Double? = null
)

@Serializable
data class OccupazioneIdDto(
    val id: Int
)

@Serializable
data class OccupazioneTavoloDto(
    val id_tavolo: Int
)

@Serializable
data class OccupazioneUtenteDto(
    val id_tavolo: Int,
    val data_occupazione: String,
    val lat_occupazione: Double? = null,
    val lng_occupazione: Double? = null,
    val tavolo: TavoloInfoResponse
)

fun OccupazioneUtenteDto.toDomain() = OccupazioneUtente(
    tavoloId = id_tavolo,
    edificioNome = tavolo.piano.edificio.nome,
    quota = tavolo.quota,
    inizio = parseTimestamp(data_occupazione),
    lat = lat_occupazione,
    lng = lng_occupazione
)

private fun parseTimestamp(s: String): Long = try {
    OffsetDateTime.parse(s).toInstant().toEpochMilli()
} catch (e: DateTimeParseException) {
    // colonna senza fuso orario: la interpreto come ora locale del telefono
    LocalDateTime.parse(s.replace(' ', 'T'))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}