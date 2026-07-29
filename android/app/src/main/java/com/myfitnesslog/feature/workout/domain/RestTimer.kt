package com.myfitnesslog.feature.workout.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Transient state of the rest countdown. UI-only; never persisted. */
sealed interface RestTimerState {
    data object Idle : RestTimerState
    data class Running(val remainingSeconds: Int, val totalSeconds: Int) : RestTimerState
    data object Finished : RestTimerState
}

/**
 * A transient rest countdown, implemented independently of Compose so it can be
 * unit-tested with virtual time. Supports start / restart / cancel / skip.
 *
 * The remaining time is deliberately not persisted — it is acceptable to lose it
 * on process death (no foreground service, alarm, or WorkManager in scope).
 *
 * The [scope] drives the countdown; tests pass a test scope, the ViewModel
 * passes its viewModelScope. [tickMillis] is injectable for fast tests.
 */
class RestTimer(
    private val scope: CoroutineScope,
    private val tickMillis: Long = 1_000,
) {
    private val _state = MutableStateFlow<RestTimerState>(RestTimerState.Idle)
    val state: StateFlow<RestTimerState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastTotalSeconds: Int = 0

    fun start(totalSeconds: Int) {
        if (totalSeconds <= 0) return
        lastTotalSeconds = totalSeconds
        startCountdown(remainingStart = totalSeconds, totalSeconds = totalSeconds)
    }

    /**
     * Adjusts the running countdown by [deltaSeconds] (e.g. +15 / -15). Clamps the
     * remaining time to >= 0; reaching 0 finishes the rest. No-op if not running.
     */
    fun adjust(deltaSeconds: Int) {
        val current = _state.value as? RestTimerState.Running ?: return
        val newRemaining = (current.remainingSeconds + deltaSeconds).coerceAtLeast(0)
        if (newRemaining == 0) {
            job?.cancel()
            _state.value = RestTimerState.Finished
            return
        }
        val newTotal = maxOf(current.totalSeconds, newRemaining)
        startCountdown(remainingStart = newRemaining, totalSeconds = newTotal)
    }

    private fun startCountdown(remainingStart: Int, totalSeconds: Int) {
        job?.cancel()
        _state.value = RestTimerState.Running(remainingStart, totalSeconds)
        job = scope.launch {
            var remaining = remainingStart
            while (remaining > 0) {
                delay(tickMillis)
                remaining--
                _state.value = if (remaining > 0) {
                    RestTimerState.Running(remaining, totalSeconds)
                } else {
                    RestTimerState.Finished
                }
            }
        }
    }

    /** Restarts using the most recent duration. */
    fun restart() {
        if (lastTotalSeconds > 0) start(lastTotalSeconds)
    }

    /** Cancels the countdown and returns to idle. */
    fun cancel() {
        job?.cancel()
        _state.value = RestTimerState.Idle
    }

    /** Ends the rest immediately (as if it completed). */
    fun skip() {
        job?.cancel()
        _state.value = RestTimerState.Finished
    }
}
