package kz.semey.livepulse

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PulseState(
    val uid: String? = null,
    val loading: Boolean = true,
    val sending: Boolean = false,
    val messages: List<Message> = emptyList(),
    val draft: String = "",
    val connection: String = "Connecting",
    val error: String? = null
)

class MainViewModel(private val repository: PulseRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(PulseState())
    val state = mutableState.asStateFlow()
    private var subscription: Subscription? = null
    private var listenerFailed = false
    private var sendFailed = false
    private var closed = false
    init { authenticate() }

    private fun authenticate() {
        mutableState.update { it.copy(loading = true, error = null) }
        repository.authenticate { result ->
            if (!closed) result.fold(onSuccess = { uid ->
                mutableState.update { it.copy(uid = uid) }
                attachListener()
            }, onFailure = { failure ->
                mutableState.update { it.copy(loading = false, connection = "Unavailable", error = failure.message ?: "Sign-in failed.") }
            })
        }
    }
    private fun attachListener() {
        subscription?.cancel()
        listenerFailed = false
        mutableState.update { it.copy(loading = true, error = null) }
        subscription = repository.listen { result ->
            if (!closed) result.fold(onSuccess = { feed ->
                mutableState.update { it.copy(messages = feed.messages, loading = false,
                    connection = when { feed.pendingWrites -> "Syncing"; feed.fromCache -> "Cached"; else -> "Connected" }) }
            }, onFailure = { failure ->
                listenerFailed = true
                mutableState.update { it.copy(loading = false, connection = "Unavailable", error = failure.message ?: "Could not load messages.") }
            })
        }
    }
    fun retry() {
        if (closed || (state.value.loading && !sendFailed) || state.value.sending) return
        if (state.value.uid == null) authenticate()
        else if (listenerFailed) attachListener()
        else if (sendFailed) send()
        else mutableState.update { it.copy(error = null) }
    }
    fun edit(text: String) {
        if (!state.value.sending) mutableState.update { it.copy(draft = text.take(1000)) }
    }
    fun send() {
        val current = state.value
        val uid = current.uid ?: return
        val text = current.draft.trim()
        if (closed || current.sending || text.isEmpty()) return
        sendFailed = false
        mutableState.update { it.copy(sending = true, error = null) }
        repository.send(text, uid) { result ->
            if (!closed) result.fold(onSuccess = {
                mutableState.update { it.copy(sending = false, draft = "") }
            }, onFailure = { failure ->
                sendFailed = true
                mutableState.update { it.copy(sending = false, error = failure.message ?: "Message was not sent.") }
            })
        }
    }
    internal fun close() { closed = true; subscription?.cancel(); subscription = null }
    override fun onCleared() { close() }
}
