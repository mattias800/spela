package com.spela.player.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Regression guards for the `fromApiId` helpers on enum classes that
 * narrow a wire string to a domain enum. If the server ever adds a new
 * value the client doesn't handle, these throw loudly instead of silently
 * coercing — matching the behaviour of the web-side narrowing helpers.
 */
class RuntimeNarrowingTest {

    // --- ChallengeType ---

    @Test
    fun challengeTypeFromApiIdAcceptsAllMembers() {
        assertEquals(ChallengeType.COMPLETION, ChallengeType.fromApiId("completion"))
        assertEquals(ChallengeType.SPEEDRUN, ChallengeType.fromApiId("speedrun"))
        assertEquals(ChallengeType.SURVIVAL, ChallengeType.fromApiId("survival"))
    }

    @Test
    fun challengeTypeFromApiIdThrowsOnUnknown() {
        val err = assertFailsWith<IllegalStateException> {
            ChallengeType.fromApiId("marathon")
        }
        assertEquals(true, err.message?.contains("marathon"))
    }

    @Test
    fun challengeTypeFromApiIdThrowsOnEmpty() {
        assertFailsWith<IllegalStateException> { ChallengeType.fromApiId("") }
    }

    // --- ChallengeDifficulty ---

    @Test
    fun challengeDifficultyFromApiIdAcceptsAllMembers() {
        assertEquals(ChallengeDifficulty.EASY, ChallengeDifficulty.fromApiId("easy"))
        assertEquals(ChallengeDifficulty.MEDIUM, ChallengeDifficulty.fromApiId("medium"))
        assertEquals(ChallengeDifficulty.HARD, ChallengeDifficulty.fromApiId("hard"))
    }

    @Test
    fun challengeDifficultyFromApiIdThrowsOnUnknown() {
        val err = assertFailsWith<IllegalStateException> {
            ChallengeDifficulty.fromApiId("insane")
        }
        assertEquals(true, err.message?.contains("insane"))
    }

    // --- NetplaySessionStatus ---

    @Test
    fun netplaySessionStatusFromApiIdAcceptsAllMembers() {
        assertEquals(
            NetplaySessionStatus.WAITING,
            NetplaySessionStatus.fromApiId("waiting"),
        )
        assertEquals(
            NetplaySessionStatus.IN_PROGRESS,
            NetplaySessionStatus.fromApiId("in_progress"),
        )
        assertEquals(
            NetplaySessionStatus.ENDED,
            NetplaySessionStatus.fromApiId("ended"),
        )
    }

    @Test
    fun netplaySessionStatusFromApiIdIsCaseSensitive() {
        // Server emits the lowercase wire form; mixed-case must throw so that
        // a schema drift is caught rather than silently accepted.
        assertFailsWith<IllegalStateException> {
            NetplaySessionStatus.fromApiId("IN_PROGRESS")
        }
        assertFailsWith<IllegalStateException> {
            NetplaySessionStatus.fromApiId("Waiting")
        }
    }

    @Test
    fun netplaySessionStatusFromApiIdThrowsOnUnknown() {
        val err = assertFailsWith<IllegalStateException> {
            NetplaySessionStatus.fromApiId("stalled")
        }
        assertEquals(true, err.message?.contains("stalled"))
    }
}
