package com.kbul.spicycrab.network

import io.ktor.client.HttpClient
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

@Singleton
class OpenRouterClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(OkHttp) {
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
        imageBase64Jpeg: String,
        userComment: String,
    ): Result<NutritionEstimateDto> = runCatching {
        val systemMsg = ChatMessage(
            role = "system",
            content = JsonPrimitive(VisionPrompts.SYSTEM),
        )

        val userParts = buildJsonArray {
            if (userComment.isNotBlank()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", userComment)
                })
            } else {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", "Please estimate calories and macros for this food.")
                })
            }
            add(buildJsonObject {
                put("type", "image_url")
                put("image_url", buildJsonObject {
                    put("url", "data:image/jpeg;base64,$imageBase64Jpeg")
                })
            })
        }

        val request = ChatRequest(
            model = model,
            messages = listOf(systemMsg, ChatMessage(role = "user", content = userParts)),
            responseFormat = ResponseFormat("json_object"),
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
