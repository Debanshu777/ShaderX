package com.debanshu.shaderlab.audiolib

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BlockingAudioCaptureTest {
    @Test
    fun deniedGenerationCannotBypassEarlierShutdown() = checkIntermediatePermissionGeneration(false)

    @Test
    fun stoppedPendingPermissionCannotBypassEarlierShutdown() = checkIntermediatePermissionGeneration(true)

    private fun checkIntermediatePermissionGeneration(stopDuringPermission: Boolean) = runBlocking {
        withTimeout(10_000) {
            Executors.newFixedThreadPool(3).asCoroutineDispatcher().use { dispatcher ->
                val events = Collections.synchronizedList(mutableListOf<String>())
                val first = FakeInput("first", events, blockStop = true)
                val second = FakeInput("second", events, readResult = 0)
                val creations = AtomicInteger()
                val permissions = AtomicInteger()
                val pendingPermission = CompletableDeferred<Boolean>()
                val capture = BlockingAudioCapture(8, dispatcher, 32, {
                    if (creations.incrementAndGet() == 1) first else second
                }) {
                    if (permissions.incrementAndGet() == 2) {
                        if (stopDuringPermission) pendingPermission.await() else false
                    } else true
                }
                try {
                    capture.start()
                    first.readEntered.awaitChecked()
                    capture.stop()
                    first.stopEntered.awaitChecked()
                    val intermediate = async(start = CoroutineStart.UNDISPATCHED) { capture.start() }
                    if (stopDuringPermission) {
                        capture.stop()
                        intermediate.cancelAndJoin()
                    } else {
                        intermediate.await()
                    }
                    val restarting = async(start = CoroutineStart.UNDISPATCHED) { capture.start() }
                    // Two executor threads are blocked in first.read/stop. This queued barrier
                    // runs on the third only after the replacement worker has entered its await.
                    withContext(dispatcher) { }
                    assertEquals(1, creations.get(), "A no-worker generation must retain the older close barrier")
                    first.releaseStop.countDown()
                    restarting.await()
                    second.readEntered.awaitChecked()
                    assertTrue(events.indexOf("first.close") < events.indexOf("second.start"))
                } finally {
                    first.releaseStop.countDown()
                    first.releaseRead.countDown()
                    pendingPermission.complete(false)
                    capture.stop()
                }
            }
        }
    }

    @Test
    fun stopReturnsBeforeBlockedDriverAndRestartWaitsForClosure() = runBlocking {
        withTimeout(10_000) {
            val events = Collections.synchronizedList(mutableListOf<String>())
            val first = FakeInput("first", events, blockStop = true)
            val second = FakeInput("second", events)
            val creations = AtomicInteger()
            val capture = capture { if (creations.incrementAndGet() == 1) first else second }
            try {
                capture.start()
                first.readEntered.awaitChecked()
                val returned = CountDownLatch(1)
                thread(name = "test-ui-stop") {
                    capture.stop()
                    returned.countDown()
                }
                returned.awaitChecked()
                first.stopEntered.awaitChecked()
                val restart = async(Dispatchers.Default) { capture.start() }
                capture.state.first { it == AudioCaptureState.IDLE }
                assertEquals(1, creations.get(), "The new input must wait while old stop is blocked")
                first.releaseStop.countDown()
                first.closed.awaitChecked()
                restart.await()
                second.readEntered.awaitChecked()
                assertTrue(events.indexOf("first.close") < events.indexOf("second.start"))
                assertFalse(events.any { it == "test-ui-stop.device" }, "Device operations must leave the caller thread")
            } finally {
                first.releaseStop.countDown()
                capture.stop()
                first.releaseRead.countDown()
                second.releaseRead.countDown()
            }
        }
    }

    @Test
    fun stopDuringOpenClosesInputWithoutPublishingRunning() = runBlocking {
        withTimeout(10_000) {
            val input = FakeInput(blockStart = true)
            val capture = capture { input }
            try {
                val starting = async(Dispatchers.Default) { capture.start() }
                input.startEntered.awaitChecked()
                capture.stop()
                input.releaseStart.countDown()
                starting.await()
                input.closed.awaitChecked()
                assertEquals(AudioCaptureState.IDLE, capture.state.value)
                assertEquals(1, input.closes.get())
                assertEquals(1L, input.readEntered.count, "A stopped opening input must never read PCM")
            } finally {
                input.releaseStart.countDown()
                input.releaseRead.countDown()
                capture.stop()
            }
        }
    }

    @Test
    fun permissionCancellationAndDenialNeverOpenDevice() = runBlocking {
        withTimeout(10_000) {
            val permission = CompletableDeferred<Boolean>()
            val permissionEntered = CompletableDeferred<Unit>()
            val creations = AtomicInteger()
            val capture = BlockingAudioCapture(8, Dispatchers.IO, 32, {
                creations.incrementAndGet()
                FakeInput()
            }) {
                permissionEntered.complete(Unit)
                permission.await()
            }
            val starting = launch { capture.start() }
            permissionEntered.await()
            // A repeated start must not replace an in-flight permission request.
            capture.start()
            capture.stop()
            starting.cancelAndJoin()
            permission.complete(false)
            capture.start()
            capture.state.first { it == AudioCaptureState.PERMISSION_DENIED }
            assertEquals(0, creations.get())
        }
    }

    @Test
    fun startupAndReadFailuresCloseDeviceAndPermitRetry() = runBlocking {
        withTimeout(10_000) {
            val failing = FakeInput(failStart = true)
            val badRead = FakeInput(readResult = -1)
            val calls = AtomicInteger()
            val capture = capture { if (calls.incrementAndGet() == 1) failing else badRead }
            capture.start()
            capture.state.first { it == AudioCaptureState.ERROR }
            failing.closed.awaitChecked()
            assertEquals(1, failing.closes.get())
            capture.start()
            badRead.closed.awaitChecked()
            capture.state.first { it == AudioCaptureState.ERROR }
            assertEquals(1, badRead.closes.get())
            assertEquals(2, calls.get())
        }
    }

    @Test
    fun zeroReadRetriesAndStoppedSnapshotStaysFrozen() = runBlocking {
        withTimeout(10_000) {
            val reads = AtomicInteger()
            val waiting = CountDownLatch(1)
            val release = CountDownLatch(1)
            val closed = CountDownLatch(1)
            val capture = capture {
                object : BlockingAudioInput {
                    override fun start() {}
                    override fun read(samples: FloatArray): Int = when (reads.incrementAndGet()) {
                        1 -> 0
                        2 -> {
                            samples.fill(0.5f)
                            samples.size
                        }
                        else -> {
                            waiting.countDown()
                            release.awaitChecked()
                            samples.fill(0f)
                            samples.size
                        }
                    }
                    override fun stop() { release.countDown() }
                    override fun close() { closed.countDown() }
                }
            }
            try {
                capture.start()
                waiting.awaitChecked()
                val snapshot = capture.currentSpectrum()
                assertTrue(snapshot.any { it > 0f })
                capture.stop()
                closed.awaitChecked()
                assertSame(snapshot, capture.currentSpectrum())
                assertEquals(3, reads.get())
            } finally {
                capture.stop()
                release.countDown()
            }
        }
    }

    private fun capture(factory: () -> BlockingAudioInput): BlockingAudioCapture =
        BlockingAudioCapture(8, Dispatchers.IO, 32, factory)

    private class FakeInput(
        private val name: String = "input",
        private val events: MutableList<String> = Collections.synchronizedList(mutableListOf()),
        blockStart: Boolean = false,
        blockStop: Boolean = false,
        private val failStart: Boolean = false,
        private val readResult: Int? = null,
    ) : BlockingAudioInput {
        val startEntered = CountDownLatch(1)
        val releaseStart = CountDownLatch(if (blockStart) 1 else 0)
        val readEntered = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val stopEntered = CountDownLatch(1)
        val releaseStop = CountDownLatch(if (blockStop) 1 else 0)
        val closed = CountDownLatch(1)
        val closes = AtomicInteger()

        override fun start() {
            record("start")
            startEntered.countDown()
            releaseStart.awaitChecked()
            check(!failStart) { "Fake startup failed" }
        }

        override fun read(samples: FloatArray): Int {
            record("read")
            readEntered.countDown()
            if (readResult != null) return readResult
            releaseRead.awaitChecked()
            return 0
        }

        override fun stop() {
            record("stop")
            stopEntered.countDown()
            releaseStop.awaitChecked()
            releaseRead.countDown()
        }

        override fun close() {
            record("close")
            closes.incrementAndGet()
            releaseRead.countDown()
            closed.countDown()
        }

        private fun record(operation: String) {
            events += "$name.$operation"
            events += "${Thread.currentThread().name}.device"
        }
    }
}

private fun CountDownLatch.awaitChecked() {
    check(await(3, TimeUnit.SECONDS)) { "Fake device did not reach its expected lifecycle state" }
}
