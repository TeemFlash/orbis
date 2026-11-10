package kz.semey.livepulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF247A92),
                background = Color(0xFFEEFFF8), surface = Color(0xFFF6FFFC), onSurface = Color(0xFF243D45))) {
                val repository = remember { runCatching { FirebasePulseRepository(applicationContext) } }
                val repo = repository.getOrNull()
                if (repo == null) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(repository.exceptionOrNull()?.message ?: "Firebase configuration is missing.")
                    }
                } else {
                    val model: MainViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repo) as T
                    })
                    PulseScreen(model)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseScreen(model: MainViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.lastIndex)
    }
    Scaffold(containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFA8DFF5), Color(0xFFEEFFF3)))),
        topBar = {
            TopAppBar(title = { Column {
                Text("LivePulse", style = MaterialTheme.typography.headlineSmall)
                Text(if (state.uid == null) state.connection else "${state.uid!!.take(8)} · ${state.connection}",
                    style = MaterialTheme.typography.labelMedium)
            } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
        }, bottomBar = {
            Surface(color = Color(0xFFF6FFFC)) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(12.dp)) {
                    if (state.sending) Text("Waiting for confirmation…", style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = state.draft, onValueChange = model::edit,
                            enabled = !state.sending, modifier = Modifier.weight(1f), maxLines = 4,
                            placeholder = { Text("Message") }, shape = RoundedCornerShape(24.dp))
                        IconButton(onClick = model::send, enabled = state.uid != null && !state.sending && state.draft.isNotBlank()) {
                            if (state.sending) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = model::retry) { Text("Retry") }
                    }
                }
            }
            if (state.loading) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.messages, key = { it.id }) { message ->
                    val mine = message.authorId == state.uid
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Surface(color = if (mine) Color(0xFFC4EEDD) else Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 310.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(if (mine) "You" else message.authorId.take(8), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(message.text, fontSize = 16.sp, modifier = Modifier.padding(vertical = 5.dp))
                                Text(if (message.pending) "Pending" else message.timeMillis?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: "",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
