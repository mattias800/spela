package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spela.player.presentation.ui.theme.SpColor

@Composable
fun WildFeaturesSection(
    onSurpriseMe: () -> Unit,
    onWizardSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Feeling Adventurous?",
            color = SpColor.OnBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // I'm Feeling Lucky
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSurpriseMe)
                    .testTag("wild_lucky_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SpColor.SurfaceVariant),
                color = SpColor.Surface,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Casino,
                        contentDescription = null,
                        tint = SpColor.Warning,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "I'm Feeling Lucky",
                            color = SpColor.OnBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = "Random game",
                            color = SpColor.OnBackgroundSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // Decision Wizard
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onWizardSelected)
                    .testTag("wild_wizard_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SpColor.SurfaceVariant),
                color = SpColor.Surface,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoFixHigh,
                        contentDescription = null,
                        tint = SpColor.Primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Decision Wizard",
                            color = SpColor.OnBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = "3 questions",
                            color = SpColor.OnBackgroundSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}
