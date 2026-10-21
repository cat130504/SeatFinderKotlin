package com.example.seatfinder.data.remote.dto

import kotlinx.serialization.Serializable
import com.example.seatfinder.domain.model.OccupazioneUtente
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

//DTO per l'INSERT di un'occupazione: rispecchia i campi della tabella "occupazione"
@Serializable
data class OccupazioneInsert(
    val id_utente: Int,
    val id_tavolo: Int,
    val data_occupazione: String,
    val lat_occupazione: Double? = null,
    val lng_occupazione: Double? = null
)

data class TavoloUtente(val id: Int, val lat: Double?, val lng: Double?)
//Solo l'id: usato per chiudere un'occupazione aperta
@Serializable
data class OccupazioneIdDto(
    val id: Int,
    val lat_occupazione: Double? = null,
    val lng_occupazione: Double? = null
)

//Lat/lng/id tavolo occupato
@Serializable
data class OccupazioneTavoloDto(
    val id_tavolo: Int,
    val lat_occupazione: Double? = null,
    val lng_occupazione: Double? = null
)

//Riga completa di occupazione con il tavolo annidato (piano + edificio). Usato per stats
@Serializable
data class OccupazioneUtenteDto(
    val id_tavolo: Int,
    val data_occupazione: String,
    val lat_occupazione: Double? = null,
    val lng_occupazione: Double? = null,
    val tavolo: TavoloInfoResponse
)

//DTO → domain: la UI lavora con OccupazioneUtente, non con il DTO.
fun OccupazioneUtenteDto.toDomain() = OccupazioneUtente(
    tavoloId = id_tavolo,
    edificioNome = tavolo.piano.edificio.nome,
    quota = tavolo.quota,
    inizio = parseTimestamp(data_occupazione),
    lat = lat_occupazione,
    lng = lng_occupazione
)

//Parsa la data di occupazione
private fun parseTimestamp(s: String): Long = try {
    OffsetDateTime.parse(s).toInstant().toEpochMilli()
} catch (e: DateTimeParseException) {
    LocalDateTime.parse(s.replace(' ', 'T'))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}