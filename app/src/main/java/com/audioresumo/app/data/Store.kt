package com.audioresumo.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

object Store {

    private const val PREFS = "audio_resumo"
    private const val KEY_SUMMARIES = "summaries_v1"
    private const val KEY_USER = "user_v1"
    private const val KEY_SETTINGS = "settings_v1"

    lateinit var appContext: Context
        private set

    val summaries = mutableListOf<AudioSummary>()
    var user = UserAccount()
        private set
    var settings = AppSettings()
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val stored = prefs.getString(KEY_SUMMARIES, null)
        if (stored.isNullOrBlank()) {
            runCatching {
                val raw = appContext.assets.open("mock_library.json").bufferedReader().use { it.readText() }
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) summaries.add(summaryFrom(arr.getJSONObject(i)))
            }.onFailure {
                summaries.add(demoSummary())
            }
            persistSummaries()
        } else {
            runCatching {
                val arr = JSONArray(stored)
                for (i in 0 until arr.length()) summaries.add(summaryFrom(arr.getJSONObject(i)))
            }
        }

        runCatching {
            prefs.getString(KEY_USER, null)?.let { user = userFrom(JSONObject(it)) }
        }
        runCatching {
            prefs.getString(KEY_SETTINGS, null)?.let { settings = settingsFrom(JSONObject(it)) }
        }
        initialized = true
    }

    fun subscribe(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun notifyChanged() {
        listeners.toList().forEach { runCatching { it() } }
    }

    fun byId(id: String): AudioSummary? = summaries.firstOrNull { it.id == id }

    fun addSummary(summary: AudioSummary) {
        summaries.removeAll { it.id == summary.id }
        summaries.add(0, summary)
        persistSummaries()
        notifyChanged()
    }

    fun toggleFavorite(id: String) {
        val summary = byId(id) ?: return
        summary.isFavorite = !summary.isFavorite
        persistSummaries()
        notifyChanged()
    }

    fun updateProgress(id: String, percent: Int, seconds: Int) {
        val summary = byId(id) ?: return
        summary.progressPercent = percent.coerceIn(0, 100)
        summary.progressSeconds = seconds.coerceAtLeast(0)
        if (summary.progressPercent > 0) {
            user.history.removeAll { it.summaryId == id }
            user.history.add(0, HistoryEntry(id, summary.progressPercent))
            while (user.history.size > 30) user.history.removeAt(user.history.lastIndex)
            persistUser()
        }
        persistSummaries()
        notifyChanged()
    }

    fun saveQuizResult(id: String, correct: Int, total: Int) {
        val summary = byId(id) ?: return
        summary.quizCorrect = correct
        summary.quizTotal = total
        user.completedQuizzes += 1
        persistSummaries()
        persistUser()
        notifyChanged()
    }

    fun addListenedSeconds(seconds: Int) {
        if (seconds <= 0) return
        user.totalAudioMinutes += seconds / 60
        if (user.totalAudioMinutes > 0) user.streakDays = maxOf(user.streakDays, 1)
        persistUser()
        notifyChanged()
    }

    fun addBookmark(bookmark: Bookmark) {
        user.bookmarks.removeAll {
            it.summaryId == bookmark.summaryId && it.paragraphIndex == bookmark.paragraphIndex
        }
        user.bookmarks.add(0, bookmark)
        persistUser()
        notifyChanged()
    }

    fun removeBookmark(id: String) {
        user.bookmarks.removeAll { it.id == id }
        persistUser()
        notifyChanged()
    }

    fun updateUser(name: String, email: String) {
        if (name.isNotBlank()) user.name = name.trim()
        if (email.isNotBlank()) user.email = email.trim()
        persistUser()
        notifyChanged()
    }

    fun upgradeToPremium() {
        user.plan = "premium"
        persistUser()
        notifyChanged()
    }

    fun updateSettings(block: (AppSettings) -> Unit) {
        block(settings)
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SETTINGS, settingsToJson(settings).toString()).apply()
        notifyChanged()
    }

    fun clearHistoryAndCache() {
        user.history.clear()
        summaries.forEach {
            it.progressPercent = 0
            it.progressSeconds = 0
            it.quizCorrect = 0
            it.quizTotal = 0
        }
        persistUser()
        persistSummaries()
        notifyChanged()
    }

    fun allCategories(): List<String> = summaries.map { it.category }.distinct().sorted()

    fun continueSummary(): AudioSummary? {
        val last = user.history.firstOrNull()?.summaryId
        return last?.let(::byId) ?: summaries.firstOrNull()
    }

    fun featured(): AudioSummary? = summaries.maxByOrNull { it.listensCount }

    fun trending(limit: Int = 6): List<AudioSummary> =
        summaries.sortedByDescending { it.listensCount }.take(limit)

    fun quickListens(limit: Int = 6): List<AudioSummary> =
        summaries.filter { it.estimatedMinutes <= 5 }.take(limit)

    private fun persistSummaries() {
        val arr = JSONArray()
        summaries.forEach { arr.put(summaryToJson(it)) }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SUMMARIES, arr.toString()).apply()
    }

    private fun persistUser() {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_USER, userToJson(user).toString()).apply()
    }

    // ------------------------------------------------------------- parsing

    private fun stringList(o: JSONObject?, key: String): List<String> {
        val arr = o?.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    fun summaryFrom(o: JSONObject): AudioSummary {
        val sections = mutableListOf<SummarySection>()
        o.optJSONArray("sections")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                sections.add(
                    SummarySection(
                        id = s.optString("id", "s$i"),
                        title = s.optString("title", "Seção ${i + 1}"),
                        timestampSeconds = s.optInt("timestampSeconds", i * 60),
                        content = s.optString("content", ""),
                        practicalExample = s.optString("practicalExample").takeIf { it.isNotBlank() },
                        keyQuote = s.optString("keyQuote").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
        val learnings = mutableListOf<KeyLearning>()
        o.optJSONArray("keyLearnings")?.let { arr ->
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                learnings.add(KeyLearning(x.optString("title", ""), x.optString("description", "")))
            }
        }
        val concepts = mutableListOf<KeyConcept>()
        o.optJSONArray("keyConcepts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                concepts.add(
                    KeyConcept(
                        x.optString("term", ""),
                        x.optString("simpleExplanation", ""),
                        x.optString("analogy").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
        val examples = mutableListOf<PracticalExample>()
        o.optJSONArray("practicalExamples")?.let { arr ->
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                examples.add(PracticalExample(x.optString("scenario", ""), x.optString("actionableAdvice", "")))
            }
        }
        val branches = mutableListOf<MindMapBranch>()
        o.optJSONObject("mindMap")?.optJSONObject("root")?.optJSONArray("branches")?.let { arr ->
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                val subs = mutableListOf<MindMapSubBranch>()
                b.optJSONArray("subBranches")?.let { sa ->
                    for (j in 0 until sa.length()) {
                        val s = sa.getJSONObject(j)
                        subs.add(MindMapSubBranch(s.optString("id", "sb$j"), s.optString("label", ""), s.optString("detail").takeIf { it.isNotBlank() }))
                    }
                }
                branches.add(
                    MindMapBranch(
                        b.optString("id", "b$i"),
                        b.optString("label", ""),
                        b.optString("summary").takeIf { it.isNotBlank() },
                        subs
                    )
                )
            }
        }
        val rootLabel = o.optJSONObject("mindMap")?.optJSONObject("root")?.optString("label")
            ?: o.optString("title")
        val mindMap = MindMapData(MindMapRoot("root", rootLabel, branches))
        val flashcards = mutableListOf<Flashcard>()
        o.optJSONArray("flashcards")?.let { arr ->
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                flashcards.add(
                    Flashcard(
                        x.optString("id", "fc$i"),
                        x.optString("front", ""),
                        x.optString("back", ""),
                        x.optString("hint").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
        val quiz = mutableListOf<QuizQuestion>()
        o.optJSONArray("quiz")?.let { arr ->
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                quiz.add(
                    QuizQuestion(
                        x.optString("id", "q$i"),
                        x.optString("question", ""),
                        stringList(x, "options"),
                        x.optInt("correctIndex", 0),
                        x.optString("explanation", "")
                    )
                )
            }
        }
        val plan = o.optJSONObject("reviewPlan")
        val colors = stringList(o, "coverColors").ifEmpty {
            listOf("#8B5CF6", "#6366F1", "#0B132B")
        }
        return AudioSummary(
            id = o.optString("id", "summary-${System.currentTimeMillis()}"),
            title = o.optString("title", "Resumo"),
            subtitle = o.optString("subtitle", ""),
            author = o.optString("author", "Autor"),
            category = o.optString("category", "Educação"),
            format = o.optString("format", "essential"),
            estimatedMinutes = o.optInt("estimatedMinutes", 5).coerceAtLeast(1),
            coverColors = colors,
            executiveSummary = o.optString("executiveSummary", ""),
            sections = sections,
            keyLearnings = learnings,
            keyConcepts = concepts,
            practicalExamples = examples,
            conclusion = o.optString("conclusion", ""),
            mindMap = mindMap,
            flashcards = flashcards,
            quiz = quiz,
            reviewPlan = ReviewPlan(
                plan?.optString("day1", "") ?: "",
                plan?.optString("day3", "") ?: "",
                plan?.optString("day7", "") ?: "",
                plan?.optString("day14", "") ?: ""
            ),
            listensCount = o.optInt("listensCount", 0),
            rating = o.optDouble("rating", 4.8),
            isFavorite = o.optBoolean("isFavorite", false),
            progressPercent = o.optInt("progressPercent", o.optInt("userProgressPercent", 0)),
            progressSeconds = o.optInt("progressSeconds", o.optInt("userProgressSeconds", 0)),
            quizCorrect = o.optInt("quizCorrect", 0),
            quizTotal = o.optInt("quizTotal", 0),
            sourceType = o.optString("sourceType", "curated"),
            isUserGenerated = o.optBoolean("isUserGenerated", false),
            createdAt = o.optString("createdAt", isoNow())
        )
    }

    fun summaryToJson(s: AudioSummary): JSONObject = JSONObject().apply {
        put("id", s.id); put("title", s.title); put("subtitle", s.subtitle); put("author", s.author)
        put("category", s.category); put("format", s.format); put("estimatedMinutes", s.estimatedMinutes)
        put("coverColors", JSONArray(s.coverColors)); put("executiveSummary", s.executiveSummary)
        put("conclusion", s.conclusion); put("listensCount", s.listensCount); put("rating", s.rating)
        put("isFavorite", s.isFavorite); put("progressPercent", s.progressPercent)
        put("progressSeconds", s.progressSeconds); put("quizCorrect", s.quizCorrect)
        put("quizTotal", s.quizTotal); put("sourceType", s.sourceType)
        put("isUserGenerated", s.isUserGenerated); put("createdAt", s.createdAt)
        put("sections", JSONArray().apply {
            s.sections.forEach { x ->
                put(JSONObject().apply {
                    put("id", x.id); put("title", x.title); put("timestampSeconds", x.timestampSeconds)
                    put("content", x.content); put("practicalExample", x.practicalExample ?: "")
                    put("keyQuote", x.keyQuote ?: "")
                })
            }
        })
        put("keyLearnings", JSONArray().apply {
            s.keyLearnings.forEach { put(JSONObject().put("title", it.title).put("description", it.description)) }
        })
        put("keyConcepts", JSONArray().apply {
            s.keyConcepts.forEach { put(JSONObject().put("term", it.term).put("simpleExplanation", it.simpleExplanation).put("analogy", it.analogy ?: "")) }
        })
        put("practicalExamples", JSONArray().apply {
            s.practicalExamples.forEach { put(JSONObject().put("scenario", it.scenario).put("actionableAdvice", it.actionableAdvice)) }
        })
        put("mindMap", JSONObject().put("root", JSONObject().apply {
            put("id", s.mindMap.root.id); put("label", s.mindMap.root.label)
            put("branches", JSONArray().apply {
                s.mindMap.root.branches.forEach { b ->
                    put(JSONObject().apply {
                        put("id", b.id); put("label", b.label); put("summary", b.summary ?: "")
                        put("subBranches", JSONArray().apply {
                            b.subBranches.forEach { sub ->
                                put(JSONObject().put("id", sub.id).put("label", sub.label).put("detail", sub.detail ?: ""))
                            }
                        })
                    })
                }
            })
        }))
        put("flashcards", JSONArray().apply {
            s.flashcards.forEach { put(JSONObject().put("id", it.id).put("front", it.front).put("back", it.back).put("hint", it.hint ?: "")) }
        })
        put("quiz", JSONArray().apply {
            s.quiz.forEach { q ->
                put(JSONObject().apply {
                    put("id", q.id); put("question", q.question); put("options", JSONArray(q.options))
                    put("correctIndex", q.correctIndex); put("explanation", q.explanation)
                })
            }
        })
        put("reviewPlan", JSONObject().apply {
            put("day1", s.reviewPlan.day1); put("day3", s.reviewPlan.day3)
            put("day7", s.reviewPlan.day7); put("day14", s.reviewPlan.day14)
        })
    }

    private fun userFrom(o: JSONObject): UserAccount {
        val bookmarks = mutableListOf<Bookmark>()
        o.optJSONArray("bookmarks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                bookmarks.add(
                    Bookmark(
                        b.optString("id"), b.optString("summaryId"), b.optInt("paragraphIndex"),
                        b.optString("sectionTitle"), b.optString("note"), b.optString("createdAt")
                    )
                )
            }
        }
        val history = mutableListOf<HistoryEntry>()
        o.optJSONArray("history")?.let { arr ->
            for (i in 0 until arr.length()) {
                val h = arr.getJSONObject(i)
                history.add(HistoryEntry(h.optString("summaryId"), h.optInt("progressPercentage"), h.optString("listenedAt")))
            }
        }
        return UserAccount(
            name = o.optString("name", "Leitor"),
            email = o.optString("email", "leitor@audioresumo.app"),
            plan = o.optString("plan", "free"),
            streakDays = o.optInt("streakDays", 7),
            totalAudioMinutes = o.optInt("totalAudioMinutes", 42),
            completedQuizzes = o.optInt("completedQuizzes", 3),
            bookmarks = bookmarks,
            history = history
        )
    }

    private fun userToJson(u: UserAccount): JSONObject = JSONObject().apply {
        put("name", u.name); put("email", u.email); put("plan", u.plan)
        put("streakDays", u.streakDays); put("totalAudioMinutes", u.totalAudioMinutes)
        put("completedQuizzes", u.completedQuizzes)
        put("bookmarks", JSONArray().apply {
            u.bookmarks.forEach { b ->
                put(JSONObject().put("id", b.id).put("summaryId", b.summaryId)
                    .put("paragraphIndex", b.paragraphIndex).put("sectionTitle", b.sectionTitle)
                    .put("note", b.note).put("createdAt", b.createdAt))
            }
        })
        put("history", JSONArray().apply {
            u.history.forEach { h ->
                put(JSONObject().put("summaryId", h.summaryId).put("progressPercentage", h.progressPercentage)
                    .put("listenedAt", h.listenedAt))
            }
        })
    }

    private fun settingsToJson(s: AppSettings): JSONObject = JSONObject().apply {
        put("apiKey", s.apiKey); put("model", s.model); put("speechRate", s.speechRate.toDouble())
        put("preferredVoice", s.preferredVoice); put("dailyGoalMinutes", s.dailyGoalMinutes)
        put("onboarded", s.onboarded)
    }

    private fun settingsFrom(o: JSONObject): AppSettings = AppSettings(
        apiKey = o.optString("apiKey", ""),
        model = o.optString("model", "gemini-2.5-flash"),
        speechRate = o.optDouble("speechRate", 1.0).toFloat().coerceIn(0.5f, 2f),
        preferredVoice = o.optString("preferredVoice", ""),
        dailyGoalMinutes = o.optInt("dailyGoalMinutes", 15),
        onboarded = o.optBoolean("onboarded", false)
    )

    private fun demoSummary(): AudioSummary = AudioSummary(
        id = "demo",
        title = "Como aprender com áudio",
        subtitle = "Microaprendizagem que funciona",
        author = "Áudio de Resumo",
        category = "Educação",
        format = "quick",
        estimatedMinutes = 3,
        coverColors = listOf("#8B5CF6", "#6366F1", "#0B132B"),
        executiveSummary = "Ouvir resumos curtos, revisar em intervalos e aplicar uma ideia por vez aumenta a retenção.",
        sections = listOf(
            SummarySection("s1", "Ouça em blocos curtos", 0, "Blocos de 3 a 7 minutos reduzem a sobrecarga e facilitam a revisão."),
            SummarySection("s2", "Revisão espaçada", 120, "Revisite o conteúdo no dia 1, 3, 7 e 14 para consolidar a memória.")
        ),
        keyLearnings = listOf(KeyLearning("Menos é mais", "Uma ideia aplicada vale mais que dez ideias ouvidas.")),
        keyConcepts = listOf(KeyConcept("Repetição espaçada", "Revisões distribuídas no tempo fortalecem a memória.")),
        practicalExamples = listOf(PracticalExample("Rotina de estudo", "Reserve 10 minutos por dia para ouvir e anotar uma ação.")),
        conclusion = "Aprender é um sistema: ouça, revise e aplique.",
        mindMap = MindMapData(MindMapRoot("root", "Aprender com áudio", listOf(
            MindMapBranch("b1", "Microblocos", "Sessões curtas", listOf(MindMapSubBranch("sb1", "3 a 7 minutos"))),
            MindMapBranch("b2", "Revisão", "Espaçada", listOf(MindMapSubBranch("sb2", "Dias 1, 3, 7 e 14")))
        ))),
        flashcards = listOf(Flashcard("f1", "Qual a duração ideal de um bloco?", "3 a 7 minutos.")),
        quiz = listOf(QuizQuestion("q1", "Quando revisar?", listOf("Dia 1, 3, 7 e 14", "Nunca", "Só antes da prova", "Uma vez"), 0, "A revisão espaçada consolida a memória.")),
        reviewPlan = ReviewPlan("Ouça um resumo.", "Revise as anotações.", "Aplique uma ideia.", "Reveja flashcards.")
    )
}
