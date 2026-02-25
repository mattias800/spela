package com.spela.player.presentation.viewmodel

import com.spela.player.domain.controller.ScreenshotCapture
import com.spela.player.domain.repository.ChallengeRepository
import com.spela.player.presentation.state.EmulationState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages challenge mode: timer, creation, submission, completion,
 * restart, and give-up flows.
 */
class ChallengeManager(
    private val challengeRepository: ChallengeRepository,
    private val libretroController: LibretroController,
    private val screenshotCapture: ScreenshotCapture?,
    private val _state: MutableStateFlow<EmulationState>,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private var challengeSaveData: ByteArray? = null
    private var challengeCreationSaveData: ByteArray? = null
    private var challengeCreationScreenshot: ByteArray? = null
    var currentCoreName: String = ""
    private var challengeTimerJob: Job? = null
    private var challengeTimerPausedAccumulatedMs: Long = 0
    private var challengeTimerPauseStartNanos: Long = 0

    /**
     * Load challenge save state and start an attempt.
     * Called from within an IO coroutine in startGame().
     */
    suspend fun loadChallengeSave(challengeId: String, providedSaveData: ByteArray?) {
        val saveBytes = providedSaveData
            ?: challengeRepository.downloadChallengeSave(challengeId).getOrNull()
        if (saveBytes != null) {
            challengeSaveData = saveBytes
            libretroController.unserialize(saveBytes)
            // Start the attempt server-side
            challengeRepository.startAttempt(challengeId).onSuccess { attempt ->
                withContext(dispatchers.main) {
                    _state.update { it.copy(challengeAttemptId = attempt.id) }
                }
            }
        }
    }

    fun startChallengeTimer() {
        challengeTimerJob?.cancel()
        val startNanos = System.nanoTime()
        challengeTimerPausedAccumulatedMs = 0
        challengeTimerPauseStartNanos = 0
        challengeTimerJob = scope.launch(dispatchers.default) {
            while (isActive) {
                delay(100) // 100ms tick resolution for display-only timer
                val current = _state.value
                if (!current.isPaused && !current.showOverlay && current.isChallengeMode) {
                    if (challengeTimerPauseStartNanos != 0L) {
                        // Resuming from pause: accumulate the paused duration
                        challengeTimerPausedAccumulatedMs += (System.nanoTime() - challengeTimerPauseStartNanos) / 1_000_000
                        challengeTimerPauseStartNanos = 0
                    }
                    val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000 - challengeTimerPausedAccumulatedMs
                    withContext(dispatchers.main) {
                        _state.update { it.copy(challengeElapsedMs = elapsedMs) }
                    }
                } else if (challengeTimerPauseStartNanos == 0L) {
                    // Entering paused state: record when pause started
                    challengeTimerPauseStartNanos = System.nanoTime()
                }
            }
        }
    }

    fun initChallengeCreation(pauseGame: () -> Unit) {
        pauseGame()
        scope.launch(dispatchers.io) {
            challengeCreationSaveData = libretroController.serialize()
            challengeCreationScreenshot = screenshotCapture?.captureScreenshot()
            withContext(dispatchers.main) {
                _state.update {
                    it.copy(
                        showChallengeCreation = true,
                        showOverlay = false,
                    )
                }
            }
        }
    }

    fun submitChallenge(name: String, description: String, type: String, difficulty: String, resumeGame: () -> Unit) {
        val saveData = challengeCreationSaveData ?: return
        val screenshot = challengeCreationScreenshot
        val gameId = _state.value.gameId
        _state.update { it.copy(isCreatingChallenge = true) }
        scope.launch(dispatchers.io) {
            challengeRepository.createChallenge(
                gameId = gameId,
                name = name,
                description = description,
                type = type,
                difficulty = difficulty,
                coreName = currentCoreName,
                saveData = saveData,
                screenshotData = screenshot,
            ).fold(
                onSuccess = {
                    challengeCreationSaveData = null
                    challengeCreationScreenshot = null
                    withContext(dispatchers.main) {
                        _state.update {
                            it.copy(
                                showChallengeCreation = false,
                                isCreatingChallenge = false,
                                challengeCreationSuccess = true,
                                statusMessage = "Challenge created!",
                            )
                        }
                    }
                    resumeGame()
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update {
                            it.copy(
                                isCreatingChallenge = false,
                                error = "Failed to create challenge: ${error.message}",
                            )
                        }
                    }
                },
            )
        }
    }

    fun completeChallenge(pauseGame: () -> Unit) {
        val challengeId = _state.value.challengeId ?: return
        val attemptId = _state.value.challengeAttemptId ?: return
        pauseGame()
        challengeTimerJob?.cancel()
        scope.launch(dispatchers.io) {
            challengeRepository.completeAttempt(challengeId, attemptId).fold(
                onSuccess = { attempt ->
                    withContext(dispatchers.main) {
                        _state.update {
                            it.copy(
                                challengeCompletedAttempt = attempt,
                                showOverlay = false,
                            )
                        }
                    }
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = "Failed to submit: ${error.message}") }
                    }
                    // Restart timer since submission failed
                    startChallengeTimer()
                    scope.launch(dispatchers.main) {
                        libretroController.resume()
                        _state.update { it.copy(isPaused = false) }
                    }
                },
            )
        }
    }

    fun restartChallenge(resumeGame: () -> Unit) {
        val challengeId = _state.value.challengeId ?: return
        val saveData = challengeSaveData ?: return
        val currentAttemptId = _state.value.challengeAttemptId

        challengeTimerJob?.cancel()

        scope.launch(dispatchers.io) {
            // Abandon current attempt first (await completion before starting new one)
            if (currentAttemptId != null) {
                challengeRepository.abandonAttempt(challengeId, currentAttemptId)
            }

            // Reload challenge save state
            libretroController.unserialize(saveData)

            // Start new server-side attempt
            challengeRepository.startAttempt(challengeId).fold(
                onSuccess = { attempt ->
                    withContext(dispatchers.main) {
                        _state.update {
                            it.copy(
                                challengeAttemptId = attempt.id,
                                challengeElapsedMs = 0,
                                showOverlay = false,
                            )
                        }
                    }
                    startChallengeTimer()
                    resumeGame()
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = "Failed to restart: ${error.message}") }
                    }
                },
            )
        }
    }

    fun giveUpChallenge(onStopGame: () -> Unit) {
        val challengeId = _state.value.challengeId ?: return
        val attemptId = _state.value.challengeAttemptId

        challengeTimerJob?.cancel()
        _state.update { it.copy(showGiveUpConfirm = false, requestExit = true) }

        if (attemptId != null) {
            scope.launch(dispatchers.io) {
                challengeRepository.abandonAttempt(challengeId, attemptId)
            }
        }

        onStopGame()
    }

    fun dismissChallengeCreation(resumeGame: () -> Unit) {
        challengeCreationSaveData = null
        challengeCreationScreenshot = null
        _state.update { it.copy(showChallengeCreation = false, challengeCreationSuccess = false) }
        resumeGame()
    }

    /**
     * Cancel all challenge-related jobs and clear internal state.
     * Called from stopGame().
     */
    fun cleanup() {
        challengeTimerJob?.cancel()
        challengeTimerJob = null
        challengeSaveData = null
        challengeCreationSaveData = null
        challengeCreationScreenshot = null
    }
}
