package com.spela.player.libretro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode

/**
 * The fading "how do I pause?" hint shown when a game starts.
 *
 * The text follows the input method in use: a player on a gamepad (Big
 * Picture, Steam Deck) has no keyboard, so telling them about Escape names a
 * key they cannot press — they get the Select+Start combo instead (#1682).
 */
@Composable
fun PauseHintText() {
    val text = if (LocalInputMode.current == InputMode.GAMEPAD) {
        "Hold Select + Start to pause"
    } else {
        "Press Esc to pause"
    }
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
