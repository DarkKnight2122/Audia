package com.oakiha.audia.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.oakiha.audia.data.database.EngagementDao
import com.oakiha.audia.data.database.TrackEngagementEntity
import com.oakiha.audia.data.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Calendar
import java.util.LinkedHashSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyMixManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engagementDao: EngagementDao
) {

    private val gson = Gson()
    private val legacyScoresFile = File(context.filesDir, "track_scores.json")
    private val fileLock = Any()
    private val statsType = object : TypeToken<MutableMap<String, TrackEngagementStats>>() {}.type

    // Flag to track if we've migrated legacy data
    private var legacyMigrationComplete = false

    data class TrackEngagementStats(
        val playCount: Int = 0,
        val totalPlayDurationMs: Long = 0L,
        val lastPlayedTimestamp: Long = 0L
    )

    init {
        // Migrate legacy JSON data to Room on first access
        migrateLegacyDataIfNeeded()
    }

    /**
     * Migrates engagements from legacy JSON file to Room database.
     * This runs once on startup if the legacy file exists.
     */
    private fun migrateLegacyDataIfNeeded() {
        if (legacyMigrationComplete || !legacyScoresFile.exists()) {
            legacyMigrationComplete = true
            return
        }

        synchronized(fileLock) {
            if (legacyMigrationComplete) return

            try {
                val legacyData = readLegacyEngagementsLocked()
                if (legacyData.isNotEmpty()) {
                    val entities = legacyData.map { (trackId, stats) ->
                        TrackEngagementEntity(
                            trackId = trackId,
                            playCount = stats.playCount.coerceAtLeast(0),
                            totalPlayDurationMs = stats.totalPlayDurationMs.coerceAtLeast(0L),
                            lastPlayedTimestamp = stats.lastPlayedTimestamp.coerceAtLeast(0L)
                        )
                    }
                    // Insert into Room - blocking is acceptable during init
                    runBlocking {
                        engagementDao.upsertEngagements(entities)
                    }
                    Log.i(TAG, "Migrated ${entities.size} engagement records from JSON to Room")
                    
                    // Rename legacy file as backup instead of deleting
                    val backupFile = File(context.filesDir, "track_scores.json.bak")
                    legacyScoresFile.renameTo(backupFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate legacy engagement data", e)
            }

            legacyMigrationComplete = true
        }
    }

    /**
     * Reads engagements from Room database (blocking version for compatibility).
     */
    private fun readEngagements(): Map<String, TrackEngagementStats> {
        return runBlocking {
            engagementDao.getAllEngagements().associate { entity ->
                entity.trackId to TrackEngagementStats(
                    playCount = entity.playCount,
                    totalPlayDurationMs = entity.totalPlayDurationMs,
                    lastPlayedTimestamp = entity.lastPlayedTimestamp
                )
            }
        }
    }

    /**
     * Legacy method to read from JSON file during migration.
     */
    private fun readLegacyEngagementsLocked(): MutableMap<String, TrackEngagementStats> {
        if (!legacyScoresFile.exists()) {
            return mutableMapOf()
        }

        val raw = runCatching { legacyScoresFile.readText() }
            .onFailure { Log.e(TAG, "Failed to read legacy track scores file", it) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return mutableMapOf()

        return runCatching {
            val element = gson.fromJson(raw, JsonElement::class.java)
            parseEngagementElement(element)
        }.getOrElse { throwable ->
            Log.e(TAG, "Failed to parse legacy track scores file", throwable)
            mutableMapOf()
        }
    }

    private fun parseEngagementElement(element: JsonElement?): MutableMap<String, TrackEngagementStats> {
        if (element == null || element.isJsonNull) {
            return mutableMapOf()
        }

        if (element.isJsonObject) {
            return parseEngagementObject(element.asJsonObject)
        }

        return runCatching {
            val parsed: MutableMap<String, TrackEngagementStats> = gson.fromJson(element, statsType)
            parsed.mapValuesTo(mutableMapOf()) { (_, stats) -> sanitizeStats(stats) }
        }.getOrElse {
            Log.w(TAG, "Unsupported track engagement format, ignoring it")
            mutableMapOf()
        }
    }

    private fun parseEngagementObject(obj: JsonObject): MutableMap<String, TrackEngagementStats> {
        val result = mutableMapOf<String, TrackEngagementStats>()
        for ((key, value) in obj.entrySet()) {
            val stats = parseStatsValue(key, value)
            if (stats != null) {
                result[key] = stats
            } else {
                Log.w(TAG, "Skipping track engagement entry for \"$key\" because it could not be parsed: $value")
            }
        }
        return result
    }

    private fun parseStatsValue(key: String, value: JsonElement): TrackEngagementStats? {
        if (value.isJsonObject) {
            val parsedStats = runCatching {
                gson.fromJson(value, TrackEngagementStats::class.java)
            }.getOrNull()

            if (parsedStats != null) {
                return sanitizeStats(parsedStats)
            }

            val extracted = extractScore(value)
            if (extracted != null) {
                return TrackEngagementStats(playCount = extracted)
            }
        } else {
            val extracted = extractScore(value)
            if (extracted != null) {
                return TrackEngagementStats(playCount = extracted)
            }
        }

        Log.w(TAG, "Encountered unsupported engagement value for \"$key\": $value")
        return null
    }

    private fun extractScore(value: JsonElement): Int? {
        if (value.isJsonPrimitive) {
            val primitive = value.asJsonPrimitive
            return when {
                primitive.isNumber -> primitive.asNumber.toInt()
                primitive.isString -> primitive.asString.toIntOrNull()
                else -> null
            }
        }

        if (value.isJsonObject) {
            val obj = value.asJsonObject
            for (key in SCORE_KEY_CANDIDATES) {
                val candidate = obj.get(key)
                if (candidate != null && candidate.isJsonPrimitive) {
                    val primitive = candidate.asJsonPrimitive
                    val parsed = when {
                        primitive.isNumber -> primitive.asNumber.toInt()
                        primitive.isString -> primitive.asString.toIntOrNull()
                        else -> null
                    }
                    if (parsed != null) {
                        return parsed
                    }
                }
            }
        }

        return null
    }

    private fun sanitizeStats(stats: TrackEngagementStats): TrackEngagementStats {
        return stats.copy(
            playCount = stats.playCount.coerceAtLeast(0),
            totalPlayDurationMs = stats.totalPlayDurationMs.coerceAtLeast(0L),
            lastPlayedTimestamp = stats.lastPlayedTimestamp.coerceAtLeast(0L)
        )
    }

    /**
     * Records a track play using Room's atomic upsert operation.
     * More efficient than JSON read-modify-write.
     */
    fun recordPlay(
        trackId: String,
        trackDurationMs: Long = 0L,
        timestamp: Long = System.currentTimeMillis()
    ) {
        runBlocking {
            engagementDao.recordPlay(
                trackId = trackId,
                durationMs = trackDurationMs.coerceAtLeast(0L),
                timestamp = timestamp.coerceAtLeast(0L)
            )
        }
    }

    fun incrementScore(trackId: String) {
        recordPlay(trackId)
    }

    fun getScore(trackId: String): Int {
        return runBlocking {
            engagementDao.getPlayCount(trackId) ?: 0
        }
    }

    fun getEngagementStats(trackId: String): TrackEngagementStats? {
        return runBlocking {
            engagementDao.getEngagement(trackId)?.let { entity ->
                TrackEngagementStats(
                    playCount = entity.playCount,
                    totalPlayDurationMs = entity.totalPlayDurationMs,
                    lastPlayedTimestamp = entity.lastPlayedTimestamp
                )
            }
        }
    }

    fun getAllEngagementStats(): Map<String, TrackEngagementStats> {
        return readEngagements()
    }

    private fun computeRankedTracks(
        allTracks: List<Track>,
        favoriteTrackIds: Set<String>,
        random: java.util.Random
    ): List<RankedTrack> {
        if (allTracks.isEmpty()) return emptyList()

        val engagements = readEngagements()
        val trackById = allTracks.associateBy { it.id }
        val now = System.currentTimeMillis()

        val artistAffinity = mutableMapOf<Long, Double>()
        val genreAffinity = mutableMapOf<String, Double>()

        engagements.forEach { (trackId, stats) ->
            val track = trackById[trackId] ?: return@forEach
            val weight = stats.playCount.toDouble() + (stats.totalPlayDurationMs / 60000.0)
            if (weight <= 0) return@forEach
            artistAffinity.merge(track.authorId, weight, Double::plus)
            track.genre?.lowercase()?.let { genreAffinity.merge(it, weight, Double::plus) }
        }

        val favoriteArtistWeights = mutableMapOf<Long, Int>()
        favoriteTrackIds.forEach { id ->
            val track = trackById[id] ?: return@forEach
            favoriteArtistWeights.merge(track.authorId, 1, Int::plus)
        }

        val maxPlayCount = engagements.values.maxOfOrNull { it.playCount }?.takeIf { it > 0 } ?: 1
        val maxDuration = engagements.values.maxOfOrNull { it.totalPlayDurationMs }?.takeIf { it > 0L } ?: 1L
        val maxArtistAffinity = artistAffinity.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val maxGenreAffinity = genreAffinity.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val maxFavoriteArtist = favoriteArtistWeights.values.maxOrNull()?.takeIf { it > 0 } ?: 1

        return allTracks.map { track ->
            val stats = engagements[track.id]
            val playCountScore = (stats?.playCount?.toDouble() ?: 0.0) / maxPlayCount
            val durationScore = (stats?.totalPlayDurationMs?.toDouble() ?: 0.0) / maxDuration
            val affinityScore = (playCountScore * 0.7 + durationScore * 0.3).coerceIn(0.0, 1.0)

            val genreKey = track.genre?.lowercase()
            val artistPreference = artistAffinity[track.authorId]?.div(maxArtistAffinity) ?: 0.0
            val genrePreference = genreKey?.let { (genreAffinity[it] ?: 0.0) / maxGenreAffinity } ?: 0.0
            val favoriteArtistPreference = favoriteArtistWeights[track.authorId]?.toDouble()?.div(maxFavoriteArtist) ?: 0.0
            val preferenceScore = listOf(artistPreference, genrePreference, favoriteArtistPreference).maxOrNull() ?: 0.0

            val recencyScore = computeRecencyScore(stats?.lastPlayedTimestamp, now)
            val noveltyScore = computeNoveltyScore(track.dateAdded, now)
            val favoriteScore = if (favoriteTrackIds.contains(track.id)) 1.0 else 0.0
            val baselineScore = if (stats == null) 0.1 else 0.0
            val noise = random.nextDouble() * 0.03

            val finalScore = (affinityScore * 0.4) +
                (preferenceScore * 0.2) +
                (recencyScore * 0.2) +
                (favoriteScore * 0.15) +
                (noveltyScore * 0.05) +
                baselineScore +
                noise

            val discoveryScore = ((1.0 - affinityScore).coerceIn(0.0, 1.0) * 0.6) +
                (noveltyScore * 0.25) +
                (preferenceScore * 0.15)

            RankedTrack(
                track = track,
                finalScore = finalScore,
                discoveryScore = discoveryScore,
                affinityScore = affinityScore,
                recencyScore = recencyScore,
                noveltyScore = noveltyScore,
                favoriteScore = favoriteScore
            )
        }
            .sortedWith(compareByDescending<RankedTrack> { it.finalScore }.thenBy { it.track.id })
    }

    fun generateDailyMix(
        allTracks: List<Track>,
        favoriteTrackIds: Set<String> = emptySet(),
        limit: Int = 30
    ): List<Track> {
        if (allTracks.isEmpty()) {
            return emptyList()
        }

        val calendar = Calendar.getInstance()
        val seed = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        val random = java.util.Random(seed.toLong())

        val rankedTracks = computeRankedTracks(allTracks, favoriteTrackIds, random)
        if (rankedTracks.isEmpty()) {
            return allTracks.shuffled(random).take(limit.coerceAtMost(allTracks.size))
        }

        val selected = pickWithDiversity(rankedTracks, favoriteTrackIds, limit)
        if (selected.size >= limit || selected.size == rankedTracks.size) {
            return selected
        }

        val remaining = allTracks
            .filterNot { track -> selected.any { it.id == track.id } }
            .shuffled(random)

        val combined = (selected + remaining).distinctBy { it.id }
        return combined.take(limit.coerceAtMost(combined.size))
    }

    fun generateYourMix(
        allTracks: List<Track>,
        favoriteTrackIds: Set<String> = emptySet(),
        limit: Int = 60
    ): List<Track> {
        if (allTracks.isEmpty()) {
            return emptyList()
        }

        val calendar = Calendar.getInstance()
        val seed = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR) + 17
        val random = java.util.Random(seed.toLong())
        val rankedTracks = computeRankedTracks(allTracks, favoriteTrackIds, random)

        if (rankedTracks.isEmpty()) {
            return allTracks.shuffled(random).take(limit.coerceAtMost(allTracks.size))
        }

        val favoriteSectionSize = (limit * 0.3).toInt().coerceAtLeast(5).coerceAtMost(limit)
        val coreSectionSize = (limit * 0.45).toInt().coerceAtLeast(10).coerceAtMost(limit)
        val discoverySectionSize = (limit - favoriteSectionSize - coreSectionSize).coerceAtLeast(0)

        val favoriteSection = pickWithDiversity(
            rankedTracks.filter { favoriteTrackIds.contains(it.track.id) },
            favoriteTrackIds,
            favoriteSectionSize
        )

        val alreadySelectedIds = favoriteSection.map { it.id }.toMutableSet()

        val coreSection = pickWithDiversity(
            rankedTracks.filterNot { alreadySelectedIds.contains(it.track.id) },
            favoriteTrackIds,
            coreSectionSize
        )

        alreadySelectedIds.addAll(coreSection.map { it.id })

        val discoveryCandidates = rankedTracks
            .filterNot { alreadySelectedIds.contains(it.track.id) }
            .sortedWith(compareByDescending<RankedTrack> { it.discoveryScore }.thenBy { it.track.id })

        val discoverySection = pickWithDiversity(discoveryCandidates, favoriteTrackIds, discoverySectionSize)

        val orderedResult = LinkedHashSet<Track>()
        orderedResult.addAll(favoriteSection)
        orderedResult.addAll(coreSection)
        orderedResult.addAll(discoverySection)

        if (orderedResult.size < limit) {
            val filler = allTracks
                .filterNot { orderedResult.any { selected -> selected.id == it.id } }
                .shuffled(random)
            for (track in filler) {
                orderedResult.add(track)
                if (orderedResult.size >= limit) break
            }
        }

        return orderedResult.toList().take(limit.coerceAtMost(orderedResult.size))
    }

    private fun pickWithDiversity(
        rankedTracks: List<RankedTrack>,
        favoriteTrackIds: Set<String>,
        limit: Int
    ): List<Track> {
        if (limit <= 0 || rankedTracks.isEmpty()) return emptyList()

        val selected = mutableListOf<Track>()
        val artistCounts = mutableMapOf<Long, Int>()

        for (candidate in rankedTracks) {
            if (selected.size >= limit) break
            val authorId = candidate.track.authorId
            val maxPerArtist = if (favoriteTrackIds.contains(candidate.track.id)) 2 else 1
            val currentCount = artistCounts.getOrDefault(authorId, 0)
            if (currentCount >= maxPerArtist) continue

            selected += candidate.track
            artistCounts[authorId] = currentCount + 1
        }

        if (selected.size < limit) {
            for (candidate in rankedTracks) {
                if (selected.size >= limit) break
                if (selected.any { it.id == candidate.track.id }) continue
                selected += candidate.track
            }
        }

        return selected.take(limit)
    }

    private fun computeRecencyScore(lastPlayedTimestamp: Long?, now: Long): Double {
        if (lastPlayedTimestamp == null || lastPlayedTimestamp <= 0L) return 0.6
        val daysSinceLastPlay = ((now - lastPlayedTimestamp).coerceAtLeast(0L) / TimeUnit.DAYS.toMillis(1)).toDouble()
        return when {
            daysSinceLastPlay < 1 -> 0.2
            daysSinceLastPlay < 3 -> 0.5
            daysSinceLastPlay < 7 -> 0.7
            daysSinceLastPlay < 14 -> 0.85
            else -> 1.0
        }
    }

    private fun computeNoveltyScore(dateAdded: Long, now: Long): Double {
        if (dateAdded <= 0L) return 0.0
        val dateAddedMillis = if (dateAdded < 10_000_000_000L) {
            TimeUnit.SECONDS.toMillis(dateAdded)
        } else {
            dateAdded
        }
        val daysSinceAdded = ((now - dateAddedMillis).coerceAtLeast(0L) / TimeUnit.DAYS.toMillis(1)).toDouble()
        return (1.0 - (daysSinceAdded / 60.0)).coerceIn(0.0, 1.0)
    }

    private data class RankedTrack(
        val track: Track,
        val finalScore: Double,
        val discoveryScore: Double,
        val affinityScore: Double,
        val recencyScore: Double,
        val noveltyScore: Double,
        val favoriteScore: Double
    )

    companion object {
        private const val TAG = "DailyMixManager"
        private val SCORE_KEY_CANDIDATES = listOf("score", "count", "value")
    }
}

