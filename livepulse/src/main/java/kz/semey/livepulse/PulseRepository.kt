package kz.semey.livepulse

fun interface Subscription { fun cancel() }
data class Message(val id: String, val text: String, val authorId: String, val timeMillis: Long? = null, val pending: Boolean = false)
data class Feed(val messages: List<Message>, val fromCache: Boolean, val pendingWrites: Boolean)
interface PulseRepository {
    fun authenticate(done: (Result<String>) -> Unit)
    fun listen(onChange: (Result<Feed>) -> Unit): Subscription
    fun send(text: String, authorId: String, done: (Result<Unit>) -> Unit)
}
