package com.debanshu.shaderlab.audiolib

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A device adapter. [stop] must be safe alongside a blocked [read] and unblock it. */
internal interface BlockingAudioInput {
    fun start()
    fun read(samples: FloatArray): Int
    fun stop()
    fun close()
}

/**
 * Owns blocking Android/JVM devices on a worker dispatcher. Lifecycle generations prevent a late
 * permission/open/read result from reviving a stopped capture. A replacement waits for the old
 * worker's finally block, so native inputs never overlap during a rapid stop/start.
 */
internal class BlockingAudioCapture(
    override val binCount: Int,
    dispatcher: CoroutineDispatcher,
    private val frameSamples: Int,
    private val createInput: () -> BlockingAudioInput?,
    private val requestPermission: suspend () -> Boolean = { true },
) : AudioCapture {
    private class Session(binCount: Int, val predecessorFinished: CompletableDeferred<Unit>?) {
        val input = MutableStateFlow<BlockingAudioInput?>(null)
        val deviceLifecycle = Mutex()
        val workerClaimed = MutableStateFlow(false)
        val finishClaimed = MutableStateFlow(false)
        val latest = MutableStateFlow(FloatArray(binCount))
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
    }

    private data class Lifecycle(
        val active: Session? = null,
        val tail: Session? = null,
        val state: AudioCaptureState = AudioCaptureState.IDLE,
        val stoppedSpectrum: FloatArray? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lifecycle = MutableStateFlow(Lifecycle())
    private val silence = FloatArray(binCount)
    override val state: StateFlow<AudioCaptureState> =
        lifecycle.map { it.state }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, AudioCaptureState.IDLE)

    override fun currentSpectrum(): FloatArray {
        val current = lifecycle.value
        return current.active?.latest?.value ?: current.stoppedSpectrum ?: silence
    }

    override suspend fun start() {
        val session: Session
        while (true) {
            val before = lifecycle.value
            if (before.active != null) return
            val candidate = Session(binCount, before.tail?.finished)
            if (lifecycle.compareAndSet(before, Lifecycle(candidate, candidate))) {
                session = candidate
                break
            }
        }
        var workerStarted = false
        try {
            if (!requestPermission()) {
                updateState(session, AudioCaptureState.PERMISSION_DENIED)
                return
            }
            if (!isCurrent(session) || !session.workerClaimed.compareAndSet(false, true)) return
            workerStarted = true
            scope.launch {
                var input: BlockingAudioInput? = null
                try {
                    session.predecessorFinished?.await()
                    if (!isCurrent(session)) return@launch
                    input = createInput()
                    if (input == null) {
                        updateState(session, AudioCaptureState.UNSUPPORTED)
                        return@launch
                    }
                    session.input.value = input
                    if (!isCurrent(session)) return@launch
                    session.deviceLifecycle.withLock {
                        if (isCurrent(session)) input.start()
                    }
                    if (!isCurrent(session)) return@launch
                    updateState(session, AudioCaptureState.RUNNING)
                    session.started.complete(Unit)
                    val samples = FloatArray(frameSamples)
                    val analyzer = SpectrumAnalyzer()
                    while (isCurrent(session)) {
                        val count = input.read(samples)
                        if (!isCurrent(session)) break
                        check(count in 0..samples.size) { "Microphone read failed: $count" }
                        if (count == 0) {
                            // Zero is a legal device result. Back off instead of a hot retry loop.
                            delay(10)
                            continue
                        }
                        session.latest.value = analyzer.magnitudes(samples, binCount, count)
                    }
                } catch (_: Exception) {
                    updateState(session, AudioCaptureState.ERROR)
                } finally {
                    session.deviceLifecycle.withLock {
                        try {
                            input?.close()
                        } catch (_: Exception) {
                        } finally {
                            session.input.value = null
                        }
                    }
                    finish(session)
                }
            }
            session.started.await()
        } catch (cancelled: CancellationException) {
            stop(session)
            throw cancelled
        } catch (_: Exception) {
            updateState(session, AudioCaptureState.ERROR)
        } finally {
            if (!workerStarted) finish(session)
        }
    }

    override fun stop() {
        lifecycle.value.active?.let(::stop)
    }

    private fun stop(session: Session) {
        lifecycle.update {
            if (it.active === session) {
                it.copy(active = null, state = AudioCaptureState.IDLE, stoppedSpectrum = session.latest.value)
            } else it
        }
        if (session.workerClaimed.compareAndSet(false, true)) {
            finish(session)
            return
        }
        // Device stop can block too. Keep it off the caller/UI thread and let the capture worker
        // exclusively own close(); no timeout can release a device while its worker is still live.
        scope.launch {
            session.deviceLifecycle.withLock {
                try {
                    session.input.value?.stop()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun isCurrent(session: Session): Boolean = lifecycle.value.active === session

    private fun updateState(session: Session, state: AudioCaptureState) {
        lifecycle.update { if (it.active === session) it.copy(state = state) else it }
    }

    private fun finish(session: Session) {
        if (!session.finishClaimed.compareAndSet(false, true)) return
        lifecycle.update {
            if (it.active === session) {
                it.copy(
                    active = null,
                    state = if (it.state == AudioCaptureState.RUNNING) AudioCaptureState.IDLE else it.state,
                    stoppedSpectrum = session.latest.value,
                )
            } else it
        }
        session.started.complete(Unit)
        // Even a denied/cancelled permission generation is part of the shutdown chain. Completing
        // it early would let a later retry bypass an older worker that still owns its native input.
        val predecessor = session.predecessorFinished
        if (predecessor == null || predecessor.isCompleted) {
            session.finished.complete(Unit)
        } else {
            scope.launch {
                predecessor.await()
                session.finished.complete(Unit)
            }
        }
    }
}
