package io.droidmcp.mlkit

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspends until this GMS [Task] completes, resuming with its result, its failure exception, or a
 * [CancellationException] if the task is cancelled.
 *
 * @return the task's successful result value.
 */
internal suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener {
        cont.resumeWithException(CancellationException("ML Kit task cancelled"))
    }
}
