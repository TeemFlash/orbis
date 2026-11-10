package kz.semey.livepulse

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query

class FirebasePulseRepository(context: Context) : PulseRepository {
    private val app: FirebaseApp = if (BuildConfig.USE_EMULATORS) {
        FirebaseApp.getApps(context).firstOrNull { it.name == "livepulse-local" }
            ?: FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                .setProjectId("demo-livepulse")
                .setApplicationId("1:123456789:android:livepulse")
                .setApiKey("fake-api-key-for-emulator-only")
                .build(), "livepulse-local")
    } else FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        ?: FirebaseApp.initializeApp(context)
        ?: error("Add livepulse/google-services.json from Firebase Console, then rebuild.")
    private val auth = FirebaseAuth.getInstance(app)
    private val db = FirebaseFirestore.getInstance(app)
    init {
        if (BuildConfig.USE_EMULATORS && configured.add(app.name)) {
            auth.useEmulator(BuildConfig.EMULATOR_HOST, 9098)
            db.useEmulator(BuildConfig.EMULATOR_HOST, 8088)
        }
    }
    override fun authenticate(done: (Result<String>) -> Unit) {
        val current = auth.currentUser
        if (current != null) { done(Result.success(current.uid)); return }
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid == null) done(Result.failure(IllegalStateException("No anonymous session returned.")))
                else done(Result.success(uid))
            }
            .addOnFailureListener { done(Result.failure(it)) }
    }
    override fun listen(onChange: (Result<Feed>) -> Unit): Subscription {
        val listener = db.collection("messages").orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) { onChange(Result.failure(error)); return@addSnapshotListener }
                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        val text = doc.getString("text") ?: return@mapNotNull null
                        val author = doc.getString("authorId") ?: return@mapNotNull null
                        Message(doc.id, text, author, doc.getTimestamp("timestamp")?.toDate()?.time,
                            doc.metadata.hasPendingWrites())
                    }.reversed()
                    onChange(Result.success(Feed(messages, snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites())))
                }
            }
        return Subscription { listener.remove() }
    }
    override fun send(text: String, authorId: String, done: (Result<Unit>) -> Unit) {
        val doc = db.collection("messages").document()
        doc.set(mapOf("id" to doc.id, "text" to text, "authorId" to authorId,
            "timestamp" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { done(Result.success(Unit)) }
            .addOnFailureListener { done(Result.failure(it)) }
    }
    companion object { private val configured = mutableSetOf<String>() }
}
