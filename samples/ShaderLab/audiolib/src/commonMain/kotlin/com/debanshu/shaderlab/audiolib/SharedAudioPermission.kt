package com.debanshu.shaderlab.audiolib

import kotlinx.coroutines.CompletableDeferred

/** Coalesces one platform prompt across capture generations. Call only on the UI dispatcher. */
internal class SharedAudioPermission {
    private var pending: CompletableDeferred<Boolean>? = null

    suspend fun await(launch: () -> Unit): Boolean {
        pending?.let { return it.await() }
        val result = CompletableDeferred<Boolean>()
        pending = result
        try {
            launch()
        } catch (failure: Exception) {
            if (pending === result) pending = null
            result.completeExceptionally(failure)
        }
        // A cancelled caller must not cancel the OS prompt or another generation's waiter.
        return result.await()
    }

    fun complete(granted: Boolean) {
        val result = pending
        pending = null
        result?.complete(granted)
    }
}
