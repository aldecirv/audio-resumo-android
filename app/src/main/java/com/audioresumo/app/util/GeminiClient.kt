package com.audioresumo.app.util

import com.audioresumo.app.data.AudioSummary
import com.audioresumo.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object GeminiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generate(
        text: String,
        fileBytes: ByteArray? = null,
        mimeType: String? = null,
        format: String = "essential",
        sourceType: String = "text",
        titleHint: String = ""
    ): Result<AudioSummary> = withContext(Dispatchers.IO) {
        val key = Store.settings.apiKey.trim()
        if (key.isBlank()) return@withContext Result.failure(IllegalStateException("Chave Gemini não configurada."))
        if (text.length < 80 && fileBytes == null) {
            return@withContext Result.failure(IllegalArgumentException("Conteúdo muito curto para gerar um resumo."))
        }
        val minutes = when (format) {
            "quick" -> 2
            "complete" -> 10
            "deep" -> 20
            else -> 5
        }
        val prompt = buildPrompt(format, minutes, sourceType, titleHint, text)
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        if (fileBytes != null && mimeType != null) {
            if (fileBytes.size > 15 * 1024 * 1024) {
                return@withContext Result.failure(IllegalArgumentException("Arquivo maior que 15 MB para envio à IA."))
            }
            val base64 = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP)
            parts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject().put("mimeType", mimeType).put("data", base64)
                )
            )
        }
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            put(
                "generationConfig",
                JSONObject().put("temperature", 0.6).put("responseMimeType", "application/json")
            )
        }
        val model = Store.settings.model.ifBlank { "gemini-2.5-flash" }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }
                        .getOrNull().orEmpty()
                    throw IllegalStateException(
                        "Gemini ${response.code}: ${message.ifBlank { "falha na requisição" }}"
                    )
                }
                val root = JSONObject(raw)
                val content = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    .orEmpty()
                if (content.isBlank()) throw IllegalStateException("A IA não retornou conteúdo.")
                val json = extractJson(content)
                val summary = Store.summaryFrom(json)
                if (summary.sections.isEmpty()) throw IllegalStateException("Resumo sem seções.")
                summary.copy(
                    id = "ai-${UUID.randomUUID()}",
                    sourceType = sourceType,
                    isUserGenerated = true,
                    author = summary.author.ifBlank { "Conteúdo enviado" },
                    createdAt = java.time.Instant.now().toString()
                )
            }
        }
    }

    private fun extractJson(content: String): JSONObject {
        val cleaned = content.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalStateException("Resposta da IA não é JSON.")
        return JSONObject(cleaned.substring(start, end + 1))
    }

    private fun buildPrompt(
        format: String,
        minutes: Int,
        sourceType: String,
        titleHint: String,
        text: String
    ): String = """
        Você é um especialista brasileiro em microaprendizagem e resumos de livros.
        Crie um resumo em português do Brasil, no formato "$format" (cerca de $minutes minutos de áudio), a partir do conteúdo abaixo.
        ${if (titleHint.isNotBlank()) "Título provável do material: $titleHint." else ""}
        O resumo precisa ser fiel ao conteúdo, prático e claro. Não invente fatos.

        Responda APENAS com um objeto JSON válido, sem markdown, neste schema:
        {
          "title": "título curto",
          "subtitle": "subtítulo",
          "author": "autor ou 'Conteúdo local'",
          "category": "uma de: Desenvolvimento pessoal, Inteligência emocional, Psicologia, Negócios, Empreendedorismo, Finanças pessoais, Tecnologia, História, Filosofia, Saúde e bem-estar, Produtividade, Educação",
          "format": "$format",
          "estimatedMinutes": $minutes,
          "coverColors": ["#8B5CF6", "#6366F1", "#0B132B"],
          "executiveSummary": "síntese de 3 a 5 frases",
          "sections": [{"id":"s1","title":"...","timestampSeconds":0,"content":"2 a 4 parágrafos","practicalExample":"...","keyQuote":"..."}],
          "keyLearnings": [{"title":"...","description":"..."}],
          "keyConcepts": [{"term":"...","simpleExplanation":"...","analogy":"..."}],
          "practicalExamples": [{"scenario":"...","actionableAdvice":"..."}],
          "conclusion": "...",
          "mindMap": {"root":{"id":"root","label":"...","branches":[{"id":"b1","label":"...","summary":"...","subBranches":[{"id":"sb1","label":"...","detail":"..."}]}]}},
          "flashcards": [{"id":"f1","front":"pergunta","back":"resposta","hint":"..."}],
          "quiz": [{"id":"q1","question":"...","options":["a","b","c","d"],"correctIndex":0,"explanation":"..."}],
          "reviewPlan": {"day1":"...","day3":"...","day7":"...","day14":"..."}
        }
        Gere de 3 a 5 seções, 3 a 5 keyLearnings, 3 keyConcepts, 4 flashcards e 3 a 5 perguntas de quiz.

        CONTEÚDO (${sourceType.uppercase()}):
        ${text.take(60000)}
    """.trimIndent()
}
