package com.kbul.spicycrab.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
private const val APP_ATTRIBUTION_URL = "https://github.com/kirilan/SoloForge"
private const val APP_ATTRIBUTION_TITLE = "Solo Forge"

// Every eval number was measured at this value; tools/food_eval sends the same one.
private const val ANALYSIS_TEMPERATURE = 0.2

@Singleton
class OpenRouterClient internal constructor(engine: HttpClientEngine) {

    @Inject constructor() : this(OkHttp.create())

    // Models emit a stray comma before a closing brace often enough to matter, and more so
    // since the response gained a nested items[] array. Rejecting the whole meal over it is worse.
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(this@OpenRouterClient.json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }

    suspend fun analyzeFood(
        apiKey: String,
        model: String,
        imageBase64Jpeg: String?,
        userComment: String,
    ): Result<NutritionEstimateDto> = runCatching {
        val systemMsg = ChatMessage(
            role = "system",
            content = JsonPrimitive(VisionPrompts.SYSTEM),
        )

        val userParts = buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", userComment.ifBlank { "Please estimate calories and macros for this food." })
            })
            if (imageBase64Jpeg != null) {
                add(buildJsonObject {
                    put("type", "image_url")
                    put("image_url", buildJsonObject {
                        put("url", "data:image/jpeg;base64,$imageBase64Jpeg")
                    })
                })
            }
        }

        val request = ChatRequest(
            model = model,
            messages = listOf(systemMsg, ChatMessage(role = "user", content = userParts)),
            responseFormat = ResponseFormat("json_object"),
            temperature = ANALYSIS_TEMPERATURE,
        )

        val resp: HttpResponse = client.post(ENDPOINT) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $apiKey")
                append("HTTP-Referer", APP_ATTRIBUTION_URL)
                append("X-OpenRouter-Title", APP_ATTRIBUTION_TITLE)
                append("X-Title", APP_ATTRIBUTION_TITLE)
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val raw = resp.bodyAsText()
        if (!resp.status.isSuccess()) {
            error("OpenRouter ${resp.status.value}: ${raw.take(400)}")
        }

        val parsed = json.decodeFromString(ChatResponse.serializer(), raw)
        val content = parsed.choices.firstOrNull()?.message?.content
            ?: parsed.error?.message?.let { error("OpenRouter error: $it") }
            ?: error("Empty response from model")

        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        json.decodeFromString(NutritionEstimateDto.serializer(), cleaned)
    }
}
