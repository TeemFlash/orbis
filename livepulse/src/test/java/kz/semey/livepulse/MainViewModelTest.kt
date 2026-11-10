package kz.semey.livepulse

import org.junit.Assert.*
import org.junit.Test

class MainViewModelTest {
    class Fake : PulseRepository {
        var authCalls = 0
        var sendCalls = 0
        var auth: ((Result<String>) -> Unit)? = null
        var events: ((Result<Feed>) -> Unit)? = null
        var completion: ((Result<Unit>) -> Unit)? = null
        var cancelled = 0
        var sent: String? = null
        override fun authenticate(done: (Result<String>) -> Unit) { authCalls++; auth = done }
        override fun listen(onChange: (Result<Feed>) -> Unit): Subscription { events = onChange; return Subscription { cancelled++ } }
        override fun send(text: String, authorId: String, done: (Result<Unit>) -> Unit) { sendCalls++; sent = text; completion = done }
    }
    @Test fun authFailureCanRetryWithoutDuplicateListener() {
        val repo = Fake(); val vm = MainViewModel(repo)
        repo.auth!!(Result.failure(Exception("offline")))
        assertNotNull(vm.state.value.error); assertFalse(vm.state.value.loading)
        vm.retry(); repo.auth!!(Result.success("anonymous-1"))
        assertEquals("anonymous-1", vm.state.value.uid)
        vm.retry(); assertEquals(2, repo.authCalls)
    }
    @Test fun whitespaceIsNotSentAndFailedSendKeepsDraft() {
        val repo = Fake(); val vm = MainViewModel(repo); repo.auth!!(Result.success("u1"))
        vm.edit("   "); vm.send(); assertNull(repo.sent)
        vm.edit("hello"); vm.send(); assertTrue(vm.state.value.sending)
        repo.completion!!(Result.failure(Exception("denied")))
        assertEquals("hello", vm.state.value.draft); assertFalse(vm.state.value.sending)
        vm.retry(); assertEquals(2, repo.sendCalls)
    }
    @Test fun realtimeSnapshotReplacesFeedAndAcknowledgedSendClearsDraft() {
        val repo = Fake(); val vm = MainViewModel(repo); repo.auth!!(Result.success("u1"))
        repo.events!!(Result.success(Feed(listOf(Message("m1", "Hi", "u2")), false, false)))
        assertEquals("Hi", vm.state.value.messages.single().text)
        vm.edit("hello"); vm.send(); vm.send(); assertEquals(1, repo.sendCalls); repo.completion!!(Result.success(Unit))
        assertEquals("", vm.state.value.draft); assertFalse(vm.state.value.sending)
        vm.close(); assertEquals(1, repo.cancelled)
    }
    @Test fun listenerErrorRetryReattachesOnlyOneListener() {
        val repo = Fake(); val vm = MainViewModel(repo); repo.auth!!(Result.success("u1"))
        repo.events!!(Result.failure(Exception("permission denied")))
        vm.retry(); assertEquals(1, repo.cancelled)
        assertEquals(1, repo.authCalls)
    }
}
