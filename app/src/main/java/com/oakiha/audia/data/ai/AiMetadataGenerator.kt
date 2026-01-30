package com.oakiha.audia.data.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import com.google.ai.client.generativeai.type.SerializationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import kotlin.Result

@Serializable
data class TrackMetadata(
    val title: String? = null,
    val Author: String? = null,
    val Book: String? = null,
    val Category: String? = null
)

class AiMetadataGenerator @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val json: Json
) {
    private fun cleanJson(jsonString: String): String {
        return jsonString.replace("```json", "").replace("```", "").trim()
    }

    suspend fun generate(
        Track: Track,
        fieldsToComplete: List<String>
    ): Result<TrackMetadata> {
        return try {
            val apiKey = userPreferencesRepository.geminiApiKey.first()
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API Key not configured."))
            }

            val selectedModel = userPreferencesRepository.geminiModel.first()
            val modelName = selectedModel.ifEmpty { "" }

            val generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey
            )

            val fieldsJson = fieldsToComplete.joinToString(separator = ", ") { "\"$it\"" }

            val systemPrompt = """
            You are a audiobook metadata expert. Your task is to find and complete missing metadata for a given audiobook.
            You will be given the book's title and Author, and a list of fields to complete.
            Your response MUST be a raw JSON object, without any markdown, backticks or other formatting.
            The JSON keys MUST be lowercase and match the requested fields (e.g., "title", "Author", "Book", "Category").
            For the Category, you must provide only one, the most accurate, single Category for the Track.
            If you cannot find a specific piece of information, you should return an empty string for that field.

            Example response for a request to complete "Book" and "Category":
            {
                "Book": "Some Book",
                "Category": "Indie Pop"
            }
            """.trimIndent()

            val BookInfo = if (Track.Book.isNotBlank()) "Book: \"${Track.Book}\"" else ""

            val fullPrompt = """
            $systemPrompt

            Book title: "${Track.title}"
            Track Author: "${Track.displayAuthor}"
            $BookInfo
            Fields to complete: [$fieldsJson]
            """.trimIndent()

            val response = generativeModel.generateContent(fullPrompt)
            val responseText = response.text
            if (responseText.isNullOrBlank()) {
                Timber.e("AI returned an empty or null response.")
                return Result.failure(Exception("AI returned an empty response."))
            }

            Timber.d("AI Response: $responseText")
            val cleanedJson = cleanJson(responseText)
            val metadata = json.decodeFromString<TrackMetadata>(cleanedJson)

            Result.success(metadata)
        } catch (e: SerializationException) {
            Timber.e(e, "Error deserializing AI response.")
            Result.failure(Exception("Failed to parse AI response: ${e.message}"))
        } catch (e: Exception) {
            Timber.e(e, "Generic error in AiMetadataGenerator.")
            Result.failure(Exception("AI Error: ${e.message}"))
        }
    }
}
