package com.debanshu.shaderlab.audiolib

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedAudioPermissionTest {
    @Test
    fun generationsShareOnePromptAndReceiveItsResult() = runBlocking {
        val permission = SharedAudioPermission()
        var launches = 0
        val first = async(start = CoroutineStart.UNDISPATCHED) { permission.await { launches++ } }
        val second = async(start = CoroutineStart.UNDISPATCHED) { permission.await { launches++ } }
        assertEquals(1, launches)
        permission.complete(true)
        assertTrue(first.await())
        assertTrue(second.await())
        val retry = async(start = CoroutineStart.UNDISPATCHED) { permission.await { launches++ } }
        assertEquals(2, launches)
        permission.complete(false)
        assertFalse(retry.await())
    }

    @Test
    fun cancellingOneGenerationDoesNotLoseAnotherWaiterOrResult() = runBlocking {
        val permission = SharedAudioPermission()
        var launches = 0
        val first = async(start = CoroutineStart.UNDISPATCHED) { permission.await { launches++ } }
        val second = async(start = CoroutineStart.UNDISPATCHED) { permission.await { launches++ } }
        second.cancelAndJoin()
        val third = async(start = CoroutineStart.UNDISPATCHED) { permission.await { launches++ } }
        assertEquals(1, launches)
        permission.complete(true)
        assertTrue(first.await())
        assertTrue(third.await())
    }

    @Test
    fun launchFailureClearsPendingAndImmediateCallbackIsSafe() = runBlocking {
        val permission = SharedAudioPermission()
        assertFailsWith<IllegalStateException> {
            permission.await { error("No activity") }
        }
        assertTrue(permission.await { permission.complete(true) })
        // A duplicate/late callback with no pending prompt is harmless.
        permission.complete(false)
    }
}
