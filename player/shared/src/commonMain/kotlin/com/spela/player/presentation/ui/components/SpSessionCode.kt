package com.spela.player.presentation.ui.components

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpSessionCode(
    code: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
) {
    SpCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.XLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Invite Code",
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
            )

            Spacer(Modifier.height(SpSpacing.Small))

            Text(
                text = code,
                style = SpTypography.DisplaySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp,
                ),
                color = SpColor.OnCard,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    contentDescription = "Invite code: ${code.toList().joinToString(" ")}"
                },
            )

            Spacer(Modifier.height(SpSpacing.Medium))

            Row(
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                SpButton(
                    text = "Copy Code",
                    onClick = onCopy,
                    style = SpButtonStyle.Outlined,
                )
                if (onShare != null) {
                    SpButton(
                        text = "Share",
                        onClick = onShare,
                        style = SpButtonStyle.Outlined,
                    )
                }
            }
        }
    }
}
