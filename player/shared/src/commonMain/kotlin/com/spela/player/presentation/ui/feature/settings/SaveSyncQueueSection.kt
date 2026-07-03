package com.spela.player.presentation.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.spela.player.domain.model.PendingSaveUploadQueueJob
import com.spela.player.domain.model.PendingSaveUploadQueueSnapshot
import com.spela.player.domain.model.PendingUploadKind
import com.spela.player.presentation.ui.TestTags
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpStatusChip
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatBytes
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
internal fun SaveSyncQueueSection(
    snapshot: PendingSaveUploadQueueSnapshot,
    modifier: Modifier = Modifier,
) {
    SpCard(
        onGradient = true,
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.SETTINGS_SAVE_SYNC_QUEUE_SUMMARY),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Save uploads",
                        style = SpTypography.TitleMedium,
                        color = SpColor.OnCard,
                    )
                    Text(
                        text = queueSummary(snapshot),
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
                SpStatusChip(
                    text = queueStatus(snapshot),
                    color = queueStatusColor(snapshot),
                )
            }

            Spacer(Modifier.height(SpSpacing.Medium))
            SettingsInfoRow(label = "Pending", value = snapshot.pendingCount.toString())
            Spacer(Modifier.height(SpSpacing.Small))
            SettingsInfoRow(label = "Retrying", value = snapshot.retryingCount.toString())
            Spacer(Modifier.height(SpSpacing.Small))
            SettingsInfoRow(label = "Stuck", value = snapshot.stuckCount.toString())

            if (snapshot.jobs.isEmpty()) {
                SettingsDivider()
                Text(
                    text = "No queued save uploads",
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnBackgroundSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SpSpacing.Medium)
                        .testTag(TestTags.SETTINGS_SAVE_SYNC_QUEUE_EMPTY),
                )
            } else {
                SettingsDivider()
                snapshot.jobs.forEachIndexed { index, job ->
                    SaveSyncJobRow(job = job)
                    if (index < snapshot.jobs.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveSyncJobRow(job: PendingSaveUploadQueueJob) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SpSpacing.Medium)
            .testTag(TestTags.settingsSaveSyncJob(job.id)),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = jobTitle(job),
                style = SpTypography.TitleMedium,
                color = SpColor.OnCard,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SpStatusChip(
                text = if (job.retryCount > 0) "Retrying" else "Queued",
                color = if (job.retryCount > 0) SpColor.Warning else SpColor.Accent,
            )
        }
        Text(
            text = "Session ${job.sessionId}",
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${formatBytes(job.size)} - Retries: ${job.retryCount} - Queued ${formatQueuedAt(job.createdAt)}",
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val error = job.lastError
        if (!error.isNullOrBlank()) {
            Text(
                text = "Last error: $error",
                style = SpTypography.BodySmall,
                color = SpColor.Warning,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun queueSummary(snapshot: PendingSaveUploadQueueSnapshot): String =
    "${snapshot.pendingCount} pending - " +
        "${snapshot.retryingCount} retrying - " +
        "${snapshot.stuckCount} stuck"

private fun queueStatus(snapshot: PendingSaveUploadQueueSnapshot): String = when {
    snapshot.isDraining -> "Syncing"
    snapshot.stuckCount > 0 -> "Stuck"
    snapshot.pendingCount > 0 -> "Waiting"
    else -> "Synced"
}

private fun queueStatusColor(snapshot: PendingSaveUploadQueueSnapshot) = when {
    snapshot.isDraining -> SpColor.Accent
    snapshot.stuckCount > 0 -> SpColor.Error
    snapshot.pendingCount > 0 -> SpColor.Warning
    else -> SpColor.Success
}

private fun jobTitle(job: PendingSaveUploadQueueJob): String = when (job.kind) {
    PendingUploadKind.Manual -> job.name.ifBlank { "Manual save" }
    PendingUploadKind.Auto -> "Auto-save"
    PendingUploadKind.Slot -> "Slot ${job.slot ?: 1}"
}

private fun formatQueuedAt(epochMillis: Long): String {
    val local = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "%04d-%02d-%02d %02d:%02d".format(
        local.year,
        local.month.ordinal + 1,
        local.day,
        local.hour,
        local.minute,
    )
}
