package com.oakiha.audia.data.model

/**
 * Preference for Transcript source priority order.
 * Determines which source to try first when fetching Transcript.
 */
enum class TranscriptSourcePreference(val displayName: String) {
    /**
     * Try online API first, then embedded Transcript, then local .lrc files
     */
    API_FIRST("Online First"),
    
    /**
     * Try embedded Transcript in metadata first, then API, then local .lrc files
     */
    EMBEDDED_FIRST("Embedded First"),
    
    /**
     * Try local .lrc files first, then embedded Transcript, then API
     */
    LOCAL_FIRST("Local First");
    
    companion object {
        fun fromOrdinal(ordinal: Int): TranscriptSourcePreference {
            return values().getOrElse(ordinal) { EMBEDDED_FIRST }
        }
        
        fun fromName(name: String?): TranscriptSourcePreference {
            return values().find { it.name == name } ?: EMBEDDED_FIRST
        }
    }
}
