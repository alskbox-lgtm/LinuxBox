package com.linuxbox.android.ui.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxbox.android.ui.theme.LinuxBoxColors

@Composable
fun ExtraKeysBar(
    ctrlArmed: Boolean,
    altArmed: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onSendSequence: (String) -> Unit,
    onArrow: (Char) -> Unit,
    onInterrupt: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LinuxBoxColors.Surface)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: Essential Linux terminal keys & arrows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyCap("ESC", Modifier.weight(1f)) { onSendSequence("\u001B") }
            KeyCap("TAB", Modifier.weight(1f)) { onSendSequence("\t") }
            KeyCap("CTRL", Modifier.weight(1.1f), active = ctrlArmed) { onToggleCtrl() }
            KeyCap("ALT", Modifier.weight(1f), active = altArmed) { onToggleAlt() }
            KeyCap("←", Modifier.weight(0.9f)) { onArrow('D') }
            KeyCap("↓", Modifier.weight(0.9f)) { onArrow('B') }
            KeyCap("↑", Modifier.weight(0.9f)) { onArrow('A') }
            KeyCap("→", Modifier.weight(0.9f)) { onArrow('C') }
        }

        // Row 2: Frequent shell punctuation, interrupt & clipboard
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyCap("/", Modifier.weight(0.85f)) { onSendSequence("/") }
            KeyCap("-", Modifier.weight(0.85f)) { onSendSequence("-") }
            KeyCap("|", Modifier.weight(0.85f)) { onSendSequence("|") }
            KeyCap("~", Modifier.weight(0.85f)) { onSendSequence("~") }
            KeyCap("^C", Modifier.weight(1f), tint = LinuxBoxColors.Danger) { onInterrupt() }
            KeyCap("HOME", Modifier.weight(1.1f)) { onSendSequence("\u001B[H") }
            KeyCap("END", Modifier.weight(1f)) { onSendSequence("\u001B[F") }
            KeyCap("COLAR", Modifier.weight(1.3f), tint = LinuxBoxColors.Secondary) { onPaste() }
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tint: Color = LinuxBoxColors.TextPrimary,
    onClick: () -> Unit
) {
    val bg = if (active) LinuxBoxColors.Primary else LinuxBoxColors.SurfaceElevated
    val fg = if (active) Color(0xFF041309) else tint
    val borderColor = if (active) LinuxBoxColors.Primary else LinuxBoxColors.Border

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = bg,
            contentColor = fg
        )
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
