package com.oakiha.audia.data.network.Transcript

import com.google.gson.annotations.SerializedName

/**
 * Representa la respuesta de la API de LRCLIB.
 * Contiene la letra de la canciÃƒÂ³n, tanto en formato simple como sincronizado.
 */
data class LrcLibResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("AuthorName") val AuthorName: String,
    @SerializedName("BookName") val BookName: String,
    @SerializedName("duration") val duration: Double,
    @SerializedName("plainTranscript") val plainTranscript: String?,
    @SerializedName("syncedTranscript") val syncedTranscript: String?
)
