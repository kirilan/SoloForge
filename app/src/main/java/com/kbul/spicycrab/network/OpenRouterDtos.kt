package com.kbul.spicycrab.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
    val temperature: Double? = 0.2,
)

@Serializable
data class ResponseFormat(val type: String)

@Serializable
data class ChatMessage(
    val role: String,
    val content: JsonElement,
)

@Serializable
data class ChatResponse(
    val choices: List<Choice> = emptyList(),
    val error: ChatError? = null,
)

@Serializable
data class Choice(val message: ResponseMessage? = null)

@Serializable
data class ResponseMessage(val content: String? = null)

@Serializable
data class ChatError(val message: String? = null, val code: String? = null)

@Serializable
data class NutritionEstimateDto(
    @SerialName("item_name") val itemName: String,
    @SerialName("estimated_grams") val estimatedGrams: Double,
    val calories: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    @SerialName("fat_g") val fatG: Double,
    @SerialName("fiber_g") val fiberG: Double = 0.0,
    val confidence: String = "medium",
    val notes: String = "",
    val items: List<FoodItemDto> = emptyList(),
    @SerialName("uncertainty_reasons") val uncertaintyReasons: List<String> = emptyList(),
    @SerialName("recommended_action") val recommendedAction: String = "accept",
)

@Serializable
data class FoodItemDto(
    val name: String,
    @SerialName("estimated_grams") val estimatedGrams: Double,
    val calories: Double,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
    @SerialName("fiber_g") val fiberG: Double = 0.0,
)
