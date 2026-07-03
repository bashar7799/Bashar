package com.example.ui.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GeminiRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.api.StructuredResponse
import com.example.data.db.BasharDatabase
import com.example.data.db.ChatMessage
import com.example.data.db.SavedItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class BasharViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val db = Room.databaseBuilder(
        application,
        BasharDatabase::class.java,
        "bashar_database"
    ).fallbackToDestructiveMigration().build()

    private val chatDao = db.chatDao()
    private val savedItemDao = db.savedItemDao()

    // Moshi parser for JSON responses
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val responseAdapter = moshi.adapter(StructuredResponse::class.java)

    // Observables from Database
    val messages: StateFlow<List<ChatMessage>> = chatDao.getAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedItems: StateFlow<List<SavedItem>> = savedItemDao.getAllSavedItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Local state
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _activeQuickReplies = MutableStateFlow<List<String>>(
        listOf("أهلاً بك يا بشار! 👋", "اكتب لي قصيدة 📝", "أخبرني نكتة 💬")
    )
    val activeQuickReplies: StateFlow<List<String>> = _activeQuickReplies.asStateFlow()

    // Summarizer State
    private val _summaryResult = MutableStateFlow<String?>(null)
    val summaryResult: StateFlow<String?> = _summaryResult.asStateFlow()

    // Study Helper State
    private val _studyResult = MutableStateFlow<String?>(null)
    val studyResult: StateFlow<String?> = _studyResult.asStateFlow()

    // TextToSpeech Engine
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(application, this)
        seedWelcomeMessage()
    }

    private fun seedWelcomeMessage() {
        viewModelScope.launch {
            val list = chatDao.getAllMessages().first()
            if (list.isEmpty()) {
                chatDao.insertMessage(
                    ChatMessage(
                        sender = "bashar",
                        text = "مرحبًا! أنا بشار، صديقك ومساعدك الذكي والمرح. كيف يمكنني مساعدتك اليوم؟ 🤖✨",
                        englishTranslation = "Hello! I am Bashar, your smart, friendly, and playful helper. How can I help you today?",
                        emojiSuggestions = "🤖👋✨🌸",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ar"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Arabic is not supported or missing data on this device.")
                _isTtsReady.value = false
            } else {
                _isTtsReady.value = true
            }
        } else {
            Log.e("TTS", "Initialization of TextToSpeech failed.")
            _isTtsReady.value = false
        }
    }

    fun speak(text: String) {
        if (_isTtsReady.value) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "BasharTTS")
            _isSpeaking.value = true
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    // Chat function
    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            // Save user message to DB
            chatDao.insertMessage(
                ChatMessage(
                    sender = "user",
                    text = userText,
                    timestamp = System.currentTimeMillis()
                )
            )

            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                // Return a friendly simulated offline message if key is missing
                simulateOfflineResponse(userText)
                _isGenerating.value = false
                return@launch
            }

            try {
                // Fetch previous messages for context
                val history = chatDao.getAllMessages().first().takeLast(10)
                val chatContents = history.map { msg ->
                    Content(
                        role = if (msg.sender == "user") "user" else "model",
                        parts = listOf(Part(text = msg.text))
                    )
                }

                val systemInstruction = Content(
                    parts = listOf(Part(text = SYSTEM_PROMPT))
                )

                val request = GeminiRequest(
                    contents = chatContents,
                    systemInstruction = systemInstruction,
                    generationConfig = GenerationConfig(temperature = 0.7)
                )

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiKey, request)
                }

                val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (rawText != null) {
                    parseAndSaveStructuredResponse(rawText)
                } else {
                    saveFallbackMessage("معذرةً، لم أستطع فهم ذلك بالكامل. دعنا نتحقق من ذلك لاحقًا.")
                }
            } catch (e: Exception) {
                Log.e("API", "Error calling Gemini", e)
                saveFallbackMessage("دعني أتحقق من ذلك لاحقًا. (حدث خطأ في الاتصال بالسيرفر)")
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private suspend fun parseAndSaveStructuredResponse(rawText: String) {
        // We clean rawText if it has markdown block ticks (like ```json ... ```)
        var cleaned = rawText.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json")
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```")
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```")
        }
        cleaned = cleaned.trim()

        try {
            val structured = responseAdapter.fromJson(cleaned)
            if (structured != null) {
                chatDao.insertMessage(
                    ChatMessage(
                        sender = "bashar",
                        text = structured.text,
                        englishTranslation = structured.englishTranslation,
                        emojiSuggestions = structured.emojiSuggestions,
                        timestamp = System.currentTimeMillis()
                    )
                )
                _activeQuickReplies.value = structured.quickReplies
            } else {
                saveFallbackMessage(rawText)
            }
        } catch (e: Exception) {
            Log.e("Moshi", "Error parsing response: $rawText", e)
            // Fallback: save raw output directly
            saveFallbackMessage(rawText)
        }
    }

    private suspend fun saveFallbackMessage(text: String) {
        chatDao.insertMessage(
            ChatMessage(
                sender = "bashar",
                text = text,
                englishTranslation = "Let me look into that later.",
                emojiSuggestions = "🤔✨🌸",
                timestamp = System.currentTimeMillis()
            )
        )
        _activeQuickReplies.value = listOf("أخبرني نكتة! 💬", "اكتب لي قصيدة! 📝", "ساعدني بالدراسة 🎓")
    }

    private suspend fun simulateOfflineResponse(userText: String) {
        withContext(Dispatchers.Default) {
            kotlinx.coroutines.delay(1000)
            val cleanInput = userText.lowercase().trim()
            val text: String
            val translation: String
            val emojis: String
            val replies: List<String>

            when {
                cleanInput.contains("نكتة") || cleanInput.contains("اضحك") -> {
                    text = "مرة واحد ميكانيكي اشترى سرير، نام تحته! 😄⚙️"
                    translation = "Once a mechanic bought a bed, and slept under it!"
                    emojis = "😂🛠️🛌"
                    replies = listOf("نكتة أخرى! 😂", "قصيدة قصيرة 📜", "شكرًا بشار 🌸")
                }
                cleanInput.contains("قصيدة") || cleanInput.contains("شعر") -> {
                    text = "يا صديقي في دروب المعرفة،\nأنت روح بالجمال مترفة.\nنمضي سوياً نحو الآفاق نبتسم،\nوكل صعب بالصداقة نهزمه! 🌟✍️"
                    translation = "O my friend in the paths of knowledge, you are a soul filled with beauty. Together we go towards horizons, smiling, and overcoming every difficulty with friendship!"
                    emojis = "✍️📖💖🌟"
                    replies = listOf("جميل جداً! 😍", "اكتب نكتة أخرى 💬", "ساعدني بالدراسة 🎓")
                }
                cleanInput.contains("ترجم") -> {
                    text = "أنا جاهز دائماً للترجمة الفورية! اكتب أي نص باللغة العربية وسأترجمه للإنجليزية فوراً."
                    translation = "I am always ready for instant translation! Write any text in Arabic and I will translate it into English immediately."
                    emojis = "🗣️🌐🗺️"
                    replies = listOf("ترجم: مرحباً صديقي 👋", "شكراً لك 💙")
                }
                cleanInput.contains("سياس") || cleanInput.contains("حزب") -> {
                    text = "أنا هنا للمرح، العلم والمساعدة الإيجابية فقط! تجنب المواضيع السياسية ودعنا نكتشف ميزات أخرى رائعة. 😊"
                    translation = "I am here for fun, knowledge, and positive help only! Let's avoid political topics and explore other cool features."
                    emojis = "🌸🛡️✨🤖"
                    replies = listOf("اكتب نكتة 💬", "شرح طبي 🩺", "تلخيص نص 📝")
                }
                else -> {
                    text = "سعدت جداً بسؤالك! أود أن أخبرك أنني في 'وضع التجربة المحلي' حالياً حتى تقوم بإدخال مفتاح API في لوحة الأسرار. دعنا نستمتع معاً!"
                    translation = "I am very happy with your question! I'd like to tell you that I am currently in 'Local Demo Mode' until you enter an API key in the Secrets panel."
                    emojis = "💡💖😊🤖"
                    replies = listOf("نكتة اليوم! 😂", "قصيدة لليوم 📜", "شرح طبي 🩺")
                }
            }

            chatDao.insertMessage(
                ChatMessage(
                    sender = "bashar",
                    text = text,
                    englishTranslation = translation,
                    emojiSuggestions = emojis,
                    timestamp = System.currentTimeMillis()
                )
            )
            _activeQuickReplies.value = replies
        }
    }

    // Text Summarizer Function
    fun summarizeText(inputText: String) {
        if (inputText.isBlank()) return
        viewModelScope.launch {
            _isGenerating.value = true
            _summaryResult.value = null

            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                // Mock summary
                kotlinx.coroutines.delay(1200)
                val mockSummary = """
                    • تم تفعيل وضع المحاكاة المحلي (يرجى إدخال API Key للتشغيل الحقيقي).
                    • النص المُدخل يحتوي على معلومات قيمة تم استيعابها.
                    • الفكرة الرئيسية: أهمية التواصل وبناء جسور المعرفة.
                    • الاستنتاج: يجب استخدام التقنيات الذكية لتبسيط التعلم وحل المسائل اليومية.
                """.trimIndent()
                _summaryResult.value = mockSummary
                savedItemDao.insertSavedItem(
                    SavedItem(
                        type = "summary",
                        title = if (inputText.length > 30) inputText.take(30) + "..." else inputText,
                        inputContent = inputText,
                        outputContent = mockSummary
                    )
                )
                _isGenerating.value = false
                return@launch
            }

            try {
                val request = GeminiRequest(
                    contents = listOf(Content(parts = listOf(Part(text = SUMMARIZE_PROMPT + inputText)))),
                    generationConfig = GenerationConfig(temperature = 0.5)
                )
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiKey, request)
                }
                val summary = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "عذراً، لم أستطع تلخيص النص."

                _summaryResult.value = summary

                // Save to saved items database
                savedItemDao.insertSavedItem(
                    SavedItem(
                        type = "summary",
                        title = if (inputText.length > 30) inputText.take(30) + "..." else inputText,
                        inputContent = inputText,
                        outputContent = summary
                    )
                )
            } catch (e: Exception) {
                Log.e("API", "Error summarizing", e)
                _summaryResult.value = "حدث خطأ أثناء محاولة التلخيص. يرجى المحاولة لاحقاً."
            } finally {
                _isGenerating.value = false
            }
        }
    }

    // Study Helper Function
    fun solveOrExplain(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isGenerating.value = true
            _studyResult.value = null

            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                // Mock study explanation
                kotlinx.coroutines.delay(1200)
                val mockExplanation = """
                    🎓 **شرح مبسط وتفاعلي (وضع المحاكاة)**:
                    
                    1. **التحليل الأولي**: استلمت استفسارك حول: "$query".
                    2. **الشرح العلمي**:
                       - هذا المفهوم يشير إلى بنية أساسية في التعلم والتحصيل العلمي.
                       - طبياً أو رياضياً، نقوم بتقسيم المسألة إلى أجزاء صغيرة يسهل فهمها.
                    3. **الخلاصة**:
                       - الصداقة والتعليم هما المحركان الأساسيان لبناء عقل ذكي وواعٍ!
                """.trimIndent()
                _studyResult.value = mockExplanation
                savedItemDao.insertSavedItem(
                    SavedItem(
                        type = "study",
                        title = if (query.length > 30) query.take(30) + "..." else query,
                        inputContent = query,
                        outputContent = mockExplanation
                    )
                )
                _isGenerating.value = false
                return@launch
            }

            try {
                val request = GeminiRequest(
                    contents = listOf(Content(parts = listOf(Part(text = STUDY_PROMPT + query)))),
                    generationConfig = GenerationConfig(temperature = 0.6)
                )
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiKey, request)
                }
                val explanation = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "عذراً، لم أتمكن من إتمام الشرح."

                _studyResult.value = explanation

                savedItemDao.insertSavedItem(
                    SavedItem(
                        type = "study",
                        title = if (query.length > 30) query.take(30) + "..." else query,
                        inputContent = query,
                        outputContent = explanation
                    )
                )
            } catch (e: Exception) {
                Log.e("API", "Error solving study task", e)
                _studyResult.value = "حدث خطأ أثناء الشرح الدراسي. يرجى المحاولة لاحقاً."
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun deleteSavedItem(id: Int) {
        viewModelScope.launch {
            savedItemDao.deleteSavedItem(id)
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            chatDao.clearChat()
            seedWelcomeMessage()
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.shutdown()
    }

    companion object {
        private const val SYSTEM_PROMPT = """
أنت "بشّار"، المساعد الذكي والصديق الودود والفكاهي في تطبيق "أصدقاء بشّار".
مهمتك هي التفاعل مع المستخدمين بطريقة ودودة ومبتكرة ومرحة باللغة العربية الفصحى المبسطة أو اللهجة البيضاء السهلة.
تحدث بأسلوب بسيط وواضح يناسب جميع الأعمار، مع لمسة من الدعابة اللطيفة والفكاهة والنكات والقصائد القصيرة عند الطلب.

يجب عليك الالتزام التام بالقواعد التالية:
1. الإجابة دائماً باللغة العربية الودودة والممتعة.
2. ألا تتحدث في أي مواضيع سياسية، دينية، طائفية أو حساسة على الإطلاق. إذا سألك المستخدم عنها، اعتذر بلباقة باللغة العربية ووجّه الحديث لموضوع إيجابي وممتع.
3. إذا لم تعرف إجابة سؤال ما، قل بلطف: "دعني أتحقق من ذلك لاحقًا."

يجب أن تكون استجابتك دائماً بصيغة JSON صالحة ومطابقة تماماً لهذا النموذج الهيكلي، ولا تكتب أي نص خارج الـ JSON على الإطلاق:
{
  "text": "الرد العربي الذكي والودود والواضح هنا",
  "englishTranslation": "The direct English translation of your response here",
  "emojiSuggestions": "🤖✨🌸🚀 (اكتب 3 إلى 5 رموز تعبيرية مناسبة جداً لسياق الرد)",
  "quickReplies": ["اقتراح رد جاهز 1 سياقي ومناسب وقصير جداً", "اقتراح رد جاهز 2 سياقي ومناسب وقصير جداً", "اقتراح رد جاهز 3 سياقي ومناسب وقصير جداً"]
}
"""

        private const val SUMMARIZE_PROMPT = """
قم بتلخيص النص التالي بأسلوب نقاط واضحة ومبسطة باللغة العربية الفصحى. ركز على استخلاص الأفكار الرئيسية بدقة وتسهيل قراءتها:

"""

        private const val STUDY_PROMPT = """
أنت معلم متميز ومبسط للمصطلحات والمسائل الطبية والدراسية. اشرح المصطلح التالي أو حل المسألة التالية خطوة بخطوة باللغة العربية بأسلوب مشوق وسهل الفهم وجذاب لجميع الأعمار:

"""
    }
}
