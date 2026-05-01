package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.repository.OnboardingRepository

/**
 * SQLDelight-backed implementation of [OnboardingRepository].
 *
 * Hint-dismissal rows are tiny (just a key + epoch-millis timestamp)
 * so we don't bother caching — every call hits SQLite. SQLite reads
 * on a primary key are sub-millisecond, well below any threshold a
 * user would feel.
 *
 * Idempotent semantics:
 *
 *   markDismissed twice with the same key → the second call updates
 *     the timestamp; the row stays a row.
 *   clearDismissed on an unknown key → no-op, no error.
 *   isDismissed on an unknown key → false.
 */
class OnboardingRepositoryImpl(
    database: SpelaDatabase,
) : OnboardingRepository {

    private val queries = database.spelaDatabaseQueries

    override suspend fun isDismissed(key: String): Boolean {
        return queries.isOnboardingHintDismissed(key).executeAsOne() > 0
    }

    override suspend fun markDismissed(key: String) {
        queries.markOnboardingHintDismissed(
            hint_key = key,
            dismissed_at = kotlin.time.Clock.System.now().toEpochMilliseconds(),
        )
    }

    override suspend fun clearDismissed(key: String) {
        queries.clearOnboardingHint(key)
    }
}
