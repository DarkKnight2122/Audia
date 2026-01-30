package com.oakiha.audia.data.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.oakiha.audia.data.DailyMixManager
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.Result
import kotlin.math.max

class AiBooklistGenerator @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dailyMixManager: DailyMixManager,
    private val json: Json
) {
    private val promptCache: MutableMap<String, List<String>> = mutableMapOf()

    suspend fun generate(
        userPrompt: String,
        allTracks: List<Track>,
        minLength: Int,
        maxLength: Int,
        candidateTracks: List<Track>? = null
    ): Result<List<Track>> {
        return try {
            val apiKey = userPreferencesRepository.geminiApiKey.first()
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API Key not configured."))
            }

            val normalizedPrompt = userPrompt.trim().lowercase()
            promptCache[normalizedPrompt]?.let { cachedIds ->
                val TrackMap = allTracks.associateBy { it.id }
                val cachedTracks = cachedIds.mapNotNull { TrackMap[it] }
                if (cachedTracks.isNotEmpty()) {
                    return Result.success(cachedTracks)
                }
            }

            val selectedModel = userPreferencesRepository.geminiModel.first()
            val modelName = selectedModel.ifEmpty { "" }

            val generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey
            )

            val samplingPool = when {
                candidateTracks.isNullOrEmpty().not() -> candidateTracks ?: allTracks
                else -> {
                    // Prefer a cost-aware ranked list before falling back to the whole library
                    val rankedForPrompt = dailyMixManager.generateDailyMix(
                        allTracks = allTracks,
                        favoriteTrackIds = emptySet(),
                        limit = 200
                    )
                    if (rankedForPrompt.isNotEmpty()) rankedForPrompt else allTracks
                }
            }

            // To optimize cost, cap the context size and shuffle it a bit for diversity
            val sampleSize = max(minLength, 80).coerceAtMost(200)
            val Tracksample = samplingPool.shuffled().take(sampleSize)

            val availableTracksJson = Tracksample.joinToString(separator = ",\n") { Track ->
                // Calculate score for each Track. This might be slow if it's a real-time calculation.
                val score = dailyMixManager.getScore(Track.id)
                """
                {
                    "id": "${Track.id}",
                    "title": "${Track.title.replace("\"", "'")}",
                    "Author": "${Track.displayAuthor.replace("\"", "'")}",
                    "Category": "${Track.Category?.replace("\"", "'") ?: "unknown"}",
                    "relevance_score": $score
                }
                """.trimIndent()
            }

            // Get the custom system prompt from user preferences
            val customSystemPrompt = userPreferencesRepository.geminiSystemPrompt.first()

            // Build the task-specific instructions
            val taskInstructions = """
            Your task is to create a Booklist for a user based on their prompt.
            You will be given a user's request, a desired Booklist length range, and a list of available books with their metadata.

            Instructions:
            1. Analyze the user's prompt to understand the desired mood, Category, or theme. This is the MOST IMPORTANT factor.
            2. Select books from the provided list that best match the user's request.
            3. The `relevance_score` is a secondary factor. Use it to break ties or to choose between books that equally match the prompt. Do NOT prioritize it over the prompt match.
            4. The final Booklist should have a number of Tracks between `min_length` and `max_length`. It does not have to be the maximum.
            5. Your response MUST be ONLY a valid JSON array of Track IDs. Do not include any other text, explanations, or markdown formatting.

            Example response for a Booklist of 3 Tracks:
            ["Track_id_1", "Track_id_2", "Track_id_3"]
            """.trimIndent()

            val fullPrompt = """
            
            $taskInstructions
            
            $customSystemPrompt
            
            User's request: "$userPrompt"
            Minimum Booklist length: $minLength
            Maximum Booklist length: $maxLength
            available books:
            [
            $availableTracksJson
            ]
            """.trimIndent()

            val response = generativeModel.generateContent(fullPrompt)
            val responseText = response.text ?: return Result.failure(Exception("AI returned an empty response."))

            val TrackIds = extractBooklistTrackIds(responseText)

            // Map the returned IDs to the actual Track objects
            val TrackMap = allTracks.associateBy { it.id }
            val generatedBooklist = TrackIds.mapNotNull { TrackMap[it] }

            if (generatedBooklist.isNotEmpty()) {
                promptCache[normalizedPrompt] = generatedBooklist.map { it.id }
            }

            Result.success(generatedBooklist)

        } catch (e: IllegalArgumentException) {
            Result.failure(Exception(e.message ?: "AI response did not contain a valid Booklist."))
        } catch (e: Exception) {
            Result.failure(Exception("AI Error: ${e.message}"))
        }
    }

    private fun extractBooklistTrackIds(rawResponse: String): List<String> {
        val sanitized = rawResponse
            .replace("```json", "")
            .replace("```", "")
            .trim()

        for (startIndex in sanitized.indices) {
            if (sanitized[startIndex] != '[') continue

            var depth = 0
            var inString = false
            var isEscaped = false

            for (index in startIndex until sanitized.length) {
                val character = sanitized[index]

                if (inString) {
                    if (isEscaped) {
                        isEscaped = false
                        continue
                    }

                    when (character) {
                        '\\' -> isEscaped = true
                        '"' -> inString = false
                    }
                    continue
                }

                when (character) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) {
                            val candidate = sanitized.substring(startIndex, index + 1)
                            val decoded = runCatching { json.decodeFromString<List<String>>(candidate) }
                            if (decoded.isSuccess) {
                                return decoded.getOrThrow()
                            }
                            break
                        }
                    }
                }
            }
        }

        throw IllegalArgumentException("AI response did not contain a valid Booklist.")
    }
}
