package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.db.ChatMessage
import com.example.data.db.SavedItem
import com.example.ui.viewmodel.BasharViewModel
import kotlinx.coroutines.launch

enum class Tab(val title: String) {
    CHAT("الدردشة"),
    TOOLS("أدوات بشار"),
    SAVED("المحفوظات")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: BasharViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(Tab.CHAT) }
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()

    // Enforce RTL Layout Direction for natural Arabic rendering
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.bashar_mascot),
                                    contentDescription = "أصدقاء بشار ماسكوت",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Column {
                                Text(
                                    text = "أصدقاء بشّار",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "مساعدك الودود والمرح 🤖✨",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        // Display API status badge
                        val apiKey = BuildConfig.GEMINI_API_KEY
                        val isDemoMode = apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY"

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDemoMode) Color(0xFFE57373) else Color(0xFF81C784),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Text(
                                    text = if (isDemoMode) "الوضع التجريبي 💡" else "متصل ذكياً ✨",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isSpeaking) {
                            IconButton(
                                onClick = { viewModel.stopSpeaking() },
                                modifier = Modifier.testTag("stop_speak_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeOff,
                                    contentDescription = "إيقاف الصوت",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    NavigationBarItem(
                        selected = currentTab == Tab.CHAT,
                        onClick = { currentTab = Tab.CHAT },
                        label = { Text("الدردشة", fontWeight = FontWeight.Medium) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == Tab.CHAT) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "الدردشة"
                            )
                        },
                        modifier = Modifier.testTag("nav_chat")
                    )
                    NavigationBarItem(
                        selected = currentTab == Tab.TOOLS,
                        onClick = { currentTab = Tab.TOOLS },
                        label = { Text("أدوات بشار", fontWeight = FontWeight.Medium) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == Tab.TOOLS) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                contentDescription = "أدوات بشار"
                            )
                        },
                        modifier = Modifier.testTag("nav_tools")
                    )
                    NavigationBarItem(
                        selected = currentTab == Tab.SAVED,
                        onClick = { currentTab = Tab.SAVED },
                        label = { Text("المحفوظات", fontWeight = FontWeight.Medium) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == Tab.SAVED) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "المحفوظات"
                            )
                        },
                        modifier = Modifier.testTag("nav_saved")
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            )
                        )
                    )
            ) {
                when (currentTab) {
                    Tab.CHAT -> ChatTab(viewModel, isGenerating)
                    Tab.TOOLS -> ToolsTab(viewModel, isGenerating)
                    Tab.SAVED -> SavedTab(viewModel)
                }
            }
        }
    }
}

@Composable
fun ChatTab(viewModel: BasharViewModel, isGenerating: Boolean) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val quickReplies by viewModel.activeQuickReplies.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val apiKey = BuildConfig.GEMINI_API_KEY
    val isDemoMode = apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY"

    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isDemoMode) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠️ لم يتم تكوين مفتاح Gemini API. يعمل التطبيق حالياً بوضع المحاكاة المحلي التفاعلي. يرجى تكوين مفتاحك في لوحة Secrets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(10.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                ChatMessageItem(
                    message = message,
                    onSpeakClick = { viewModel.speak(message.text) },
                    onCopyClick = { text ->
                        copyToClipboard(viewModel.getApplication(), text)
                    }
                )
            }

            if (isGenerating) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "بشار يفكر ويكتب... 🤖💬",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Quick replies chips
        AnimatedVisibility(
            visible = quickReplies.isNotEmpty() && !isGenerating,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickReplies.forEach { reply ->
                    SuggestionChip(
                        onClick = {
                            viewModel.sendMessage(reply)
                            keyboardController?.hide()
                        },
                        label = { Text(reply, fontSize = 12.sp) },
                        modifier = Modifier.testTag("quick_reply_$reply")
                    )
                }
            }
        }

        // Enter Entertainment pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { viewModel.sendMessage("ألقِ نكتة مضحكة لليوم 😄") },
                label = { Text("ألقِ نكتة! 💬") },
                leadingIcon = { Icon(Icons.Default.SentimentSatisfiedAlt, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = { viewModel.sendMessage("اكتب لي قصيدة قصيرة وجميلة") },
                label = { Text("قصيدة قصيرة 📝") },
                leadingIcon = { Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        // Input Box
        Surface(
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("تحدث مع صديقك بشار...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input"),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                                keyboardController?.hide()
                            }
                        }
                    )
                )

                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                            keyboardController?.hide()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_button"),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "إرسال"
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onSpeakClick: () -> Unit,
    onCopyClick: (String) -> Unit
) {
    val isUser = message.sender == "user"
    var showEnglish by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .align(Alignment.Top)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.bashar_mascot),
                    contentDescription = "بشار",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp,
                modifier = Modifier.testTag("message_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // English Translation toggle block
                    if (!isUser && !message.englishTranslation.isNullOrBlank()) {
                        AnimatedVisibility(visible = showEnglish) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = message.englishTranslation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Message actions toolbar (Speak, translate, copy)
            if (!isUser) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speak TTS
                    IconButton(
                        onClick = onSpeakClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "استمع للرد",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    // Translate Arabic <-> English
                    if (!message.englishTranslation.isNullOrBlank()) {
                        IconButton(
                            onClick = { showEnglish = !showEnglish },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "ترجمة",
                                modifier = Modifier.size(16.dp),
                                tint = if (showEnglish) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Copy text to clipboard
                    IconButton(
                        onClick = { onCopyClick(message.text) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "نسخ الرد",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    // Emoji suggestions tag
                    if (!message.emojiSuggestions.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = message.emojiSuggestions,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .align(Alignment.Top)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "المستخدم",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun ToolsTab(viewModel: BasharViewModel, isGenerating: Boolean) {
    var activeTool by remember { mutableStateOf(0) } // 0: Summarizer, 1: Study Helper
    var summarizerInput by remember { mutableStateOf("") }
    val summaryResult by viewModel.summaryResult.collectAsStateWithLifecycle()

    var studyInput by remember { mutableStateOf("") }
    val studyResult by viewModel.studyResult.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Tab Headers
        TabRow(selectedTabIndex = activeTool) {
            Tab(
                selected = activeTool == 0,
                onClick = { activeTool = 0 },
                text = { Text("ملخص النصوص الذكي 📝", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            )
            Tab(
                selected = activeTool == 1,
                onClick = { activeTool = 1 },
                text = { Text("المساعد الدراسي والمسائل 🎓", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activeTool == 0) {
            // Text Summarizer Layout
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "الصق أي مقال أو نص طويل باللغة العربية، وسيقوم بشار بتلخيصه فوراً في نقاط بسيطة ومركزة:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = summarizerInput,
                        onValueChange = { summarizerInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("summarizer_input"),
                        placeholder = { Text("اكتب أو الصق النص هنا...") },
                        maxLines = 15
                    )

                    Button(
                        onClick = {
                            viewModel.summarizeText(summarizerInput)
                            keyboardController?.hide()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("summarize_button"),
                        enabled = summarizerInput.isNotBlank() && !isGenerating
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Text("لخّص النص الآن ✨", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (!summaryResult.isNullOrBlank()) {
                        Divider()
                        Text(
                            text = "ملخص بشار الذكي:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.2f)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                ) {
                                    item {
                                        Text(
                                            text = summaryResult!!,
                                            style = MaterialTheme.typography.bodyLarge,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(viewModel.getApplication(), summaryResult!!)
                                        },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الملخص")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Study Helper Layout
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "هل لديك مصطلح طبي غامض، أو مسألة رياضية دراسية؟ اسأل بشار ليقوم بتبسيطها وحلها خطوة بخطوة بطريقة فكاهية وسهلة الفهم:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = studyInput,
                        onValueChange = { studyInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("study_input"),
                        placeholder = { Text("مثال: ما هو فقر الدم؟ أو حل المسألة: 2x + 5 = 15") }
                    )

                    // Popular study shortcut prompts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InputChip(
                            selected = false,
                            onClick = { studyInput = "ما هو مرض السكري وما أسبابه؟🩺" },
                            label = { Text("شرح طبي لمرض السكري") }
                        )
                        InputChip(
                            selected = false,
                            onClick = { studyInput = "حل مسألة: 3x - 7 = 14" },
                            label = { Text("حل مسألة رياضية 📐") }
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.solveOrExplain(studyInput)
                            keyboardController?.hide()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("study_button"),
                        enabled = studyInput.isNotBlank() && !isGenerating
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.School, contentDescription = null)
                                Text("اشرح لي وبسطها! 🎓🔬", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (!studyResult.isNullOrBlank()) {
                        Divider()
                        Text(
                            text = "شرح بشار المبسط:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                ) {
                                    item {
                                        Text(
                                            text = studyResult!!,
                                            style = MaterialTheme.typography.bodyLarge,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(viewModel.getApplication(), studyResult!!)
                                        },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الشرح")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedTab(viewModel: BasharViewModel) {
    val savedItems by viewModel.savedItems.collectAsStateWithLifecycle()
    var selectedItemForView by remember { mutableStateOf<SavedItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "سجل الأنشطة والمحفوظات 💾",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )

            // Clear Chat Action
            TextButton(
                onClick = { viewModel.clearChatHistory() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("مسح سجل الدردشة")
                }
            }
        }

        if (savedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "لا توجد محفوظات تلخيص أو دراسة حالياً.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "جرب استخدام أدوات التلخيص أو المساعد الدراسي لحفظها هنا تلقائياً!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(savedItems) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedItemForView = item },
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.type == "summary")
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (item.type == "summary")
                                                MaterialTheme.colorScheme.primaryContainer
                                            else
                                                MaterialTheme.colorScheme.secondaryContainer
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.type == "summary") Icons.Default.Description else Icons.Default.School,
                                        contentDescription = null,
                                        tint = if (item.type == "summary")
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (item.type == "summary") "ملخص نص ذكي" else "مساعدة دراسية",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.deleteSavedItem(item.id) },
                                modifier = Modifier.testTag("delete_saved_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف المحفوظات",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail dialog view
    if (selectedItemForView != null) {
        val item = selectedItemForView!!
        Dialog(onDismissRequest = { selectedItemForView = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (item.type == "summary") "تفاصيل الملخص" else "تفاصيل الشرح الدراسي",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { selectedItemForView = null }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق")
                        }
                    }

                    Divider()

                    Text(
                        text = "المدخل الأساسي:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.inputContent,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .fillMaxWidth()
                    )

                    Text(
                        text = "النتيجة الذكية:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 250.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .background(
                                    if (item.type == "summary")
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                    else
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp)
                                .fillMaxWidth()
                        ) {
                            item {
                                Text(
                                    text = item.outputContent,
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                copyToClipboard(viewModel.getApplication(), item.outputContent)
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("نسخ النتيجة")
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("BasharFriends", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "تم نسخ النص بنجاح! 📋✨", Toast.LENGTH_SHORT).show()
}
