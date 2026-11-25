package com.zoai.smarttools

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val GEMINI_MODEL = "gemini-2.0-flash"
private const val MAX_HISTORY = 20

const val CHAT_SYSTEM = "You are ZoAi, a helpful and friendly AI assistant. Reply in the same language the user writes in (Hindi, Roman Hindi, Urdu or English). Be clear and concise, and use step-by-step answers when needed."

const val CODING_SYSTEM = "You are an expert programming assistant. Put all code in fenced markdown blocks with the language name. Explain briefly what the code does, point out bugs or errors, and reply in the same language the user writes in."

private val CODE_LANGS = setOf(
    "kotlin", "kt", "java", "python", "py", "html", "css", "javascript", "js",
    "typescript", "ts", "xml", "json", "cpp", "c++", "c", "csharp", "c#", "sql",
    "bash", "sh", "yaml", "yml", "gradle", "rust", "go", "php", "text", "txt",
    "dart", "swift"
)

data class ChatMessage(val fromUser: Boolean, val text: String)

private fun loadHistory(prefs: SharedPreferences, key: String): List<ChatMessage> =
    runCatching {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ChatMessage(o.getBoolean("user"), o.getString("text"))
        }
    }.getOrDefault(emptyList())

private fun saveHistory(prefs: SharedPreferences, key: String, msgs: List<ChatMessage>) {
    val arr = JSONArray()
    msgs.filter { it.fromUser || it.text.isNotEmpty() }.forEach {
        arr.put(JSONObject().put("user", it.fromUser).put("text", it.text))
    }
    prefs.edit().putString(key, arr.toString()).apply()
}

suspend fun streamGemini(
    apiKey: String,
    system: String,
    history: List<ChatMessage>,
    onDelta: suspend (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:streamGenerateContent?alt=sse"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 120000

            val contents = JSONArray()
            history.takeLast(MAX_HISTORY).dropWhile { !it.fromUser }.forEach { m ->
                contents.put(
                    JSONObject()
                        .put("role", if (m.fromUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", m.text)))
                )
            }

            val body = JSONObject()
                .put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
                )
                .put("contents", contents)
                .put("generationConfig", JSONObject().put("maxOutputTokens", 2000))

            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw IllegalStateException("HTTP $code: $err")
            }

            conn.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data: ")) continue
                    val json = JSONObject(line.removePrefix("data: "))
                    val candidates = json.optJSONArray("candidates") ?: continue
                    if (candidates.length() == 0) continue
                    val parts = candidates.getJSONObject(0)
                        .optJSONObject("content")
                        ?.optJSONArray("parts") ?: continue
                    for (i in 0 until parts.length()) {
                        val text = parts.getJSONObject(i).optString("text")
                        if (text.isNotEmpty()) onDelta(text)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}

@Composable
fun ChatScreen(
    title: String,
    accent: Color,
    storageKey: String,
    systemPrompt: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zoai_prefs", Context.MODE_PRIVATE) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply { addAll(loadHistory(prefs, storageKey)) }
    }

    fun persist() = saveHistory(prefs, storageKey, messages)

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || loading) return
        if (apiKey.isBlank()) {
            error = "Pehle upar API key daalo"
            return
        }
        input = ""
        error = null
        messages.add(ChatMessage(true, text))
        messages.add(ChatMessage(false, ""))
        val replyIndex = messages.lastIndex
        persist()
        loading = true

        job = scope.launch {
            val sb = StringBuilder()
            try {
                val history = messages.subList(0, replyIndex).toList()
                streamGemini(apiKey.trim(), systemPrompt, history) { delta ->
                    withContext(Dispatchers.Main) {
                        sb.append(delta)
                        if (replyIndex <= messages.lastIndex) {
                            messages[replyIndex] = ChatMessage(false, sb.toString())
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Kuch galat hua"
            } finally {
                if (replyIndex <= messages.lastIndex && messages[replyIndex].text.isEmpty()) {
                    messages.removeAt(replyIndex)
                }
                loading = false
                job = null
                persist()
            }
        }
    }

    fun newChat() {
        job?.cancel()
        messages.clear()
        error = null
        persist()
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B1E))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Color.White) }
            Text(
                text = title,
                color = accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { newChat() }) { Text("Naya chat", color = accent) }
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                prefs.edit().putString("api_key", it).apply()
            },
            label = { Text("Gemini API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(
                    msg = msg,
                    accent = accent,
                    onCopy = { clipboard.setText(AnnotatedString(it)) }
                )
            }
        }

        error?.let {
            Text(
                text = it,
                color = Color(0xFFFF6B6B),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Kuch bhi poocho...") },
                maxLines = 5,
                modifier = Modifier.weight(1f)
            )
            if (loading) {
                OutlinedButton(
                    onClick = { job?.cancel() },
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("Rok do") }
            } else {
                Button(
                    onClick = { send() },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("Send") }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, accent: Color, onCopy: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.fromUser) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (msg.fromUser) 0.85f else 1f)
                .background(
                    if (msg.fromUser) accent.copy(alpha = 0.25f) else Color(0xFF07071A),
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            if (msg.text.isEmpty()) {
                Text("Soch raha hoon...", color = accent)
            } else {
                MarkdownLite(msg.text)
            }
        }
        if (!msg.fromUser && msg.text.isNotEmpty()) {
            TextButton(onClick = { onCopy(msg.text) }) {
                Text("Copy", color = accent)
            }
        }
    }
}

@Composable
private fun MarkdownLite(text: String) {
    val parts = text.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEachIndexed { i, part ->
            if (i % 2 == 0) {
                val t = part.trim()
                if (t.isNotEmpty()) {
                    SelectionContainer {
                        Text(text = t, color = Color.White)
                    }
                }
            } else {
                CodeBlock(extractCode(part))
            }
        }
    }
}

private fun extractCode(part: String): String {
    val trimmed = part.trim('\n').trimEnd()
    val lines = trimmed.lines()
    val first = lines.firstOrNull()?.trim()?.lowercase() ?: ""
    return if (lines.size > 1 && first in CODE_LANGS) {
        lines.drop(1).joinToString("\n")
    } else {
        trimmed
    }
}

@Composable
private fun CodeBlock(code: String) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF05050F), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        SelectionContainer {
            Text(
                text = code,
                color = Color(0xFFB8FFB8),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
        TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
            Text("Code copy karo", color = Color(0xFF22E5FF))
        }
    }
}
++
