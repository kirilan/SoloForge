package com.kbul.spicycrab.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterClientTest {

    private fun clientReturning(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        OpenRouterClient(
            MockEngine {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        )

    private fun chatResponseWith(content: String): String {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"choices":[{"message":{"content":"$escaped"}}]}"""
    }

    private val validEstimate = """
        {"item_name":"Grilled salmon","estimated_grams":180.0,"calories":367.0,
         "protein_g":36.0,"carbs_g":0.0,"fat_g":24.0,"fiber_g":0.0,
         "confidence":"high","notes":"plain fillet"}
    """.trimIndent()

    @Test
    fun parsesWellFormedResponse() = runBlocking {
        val result = clientReturning(chatResponseWith(validEstimate))
            .analyzeFood("key", "model", null, "salmon")
        val dto = result.getOrThrow()
        assertEquals("Grilled salmon", dto.itemName)
        assertEquals(367.0, dto.calories, 0.0)
        assertEquals("high", dto.confidence)
    }

    @Test
    fun stripsMarkdownCodeFences() = runBlocking {
        val fenced = "```json\n$validEstimate\n```"
        val result = clientReturning(chatResponseWith(fenced))
            .analyzeFood("key", "model", null, "salmon")
        assertEquals("Grilled salmon", result.getOrThrow().itemName)
    }

    @Test
    fun missingOptionalFieldsFallBackToDefaults() = runBlocking {
        val minimal = """{"item_name":"Apple","estimated_grams":150.0,"calories":80.0,
            "protein_g":0.4,"carbs_g":21.0,"fat_g":0.2}"""
        val dto = clientReturning(chatResponseWith(minimal))
            .analyzeFood("key", "model", null, "apple").getOrThrow()
        assertEquals(0.0, dto.fiberG, 0.0)
        assertEquals("medium", dto.confidence)
    }

    @Test
    fun httpErrorBecomesFailureWithStatus() = runBlocking {
        val result = clientReturning("""{"error":{"message":"invalid key"}}""", HttpStatusCode.Unauthorized)
            .analyzeFood("bad", "model", null, "x")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("401"))
    }

    @Test
    fun apiErrorPayloadBecomesFailure() = runBlocking {
        val result = clientReturning("""{"choices":[],"error":{"message":"model overloaded"}}""")
            .analyzeFood("key", "model", null, "x")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("model overloaded"))
    }

    @Test
    fun nonJsonModelOutputBecomesFailureNotCrash() = runBlocking {
        val result = clientReturning(chatResponseWith("Sorry, I cannot identify this food."))
            .analyzeFood("key", "model", null, "x")
        assertTrue(result.isFailure)
    }
}
