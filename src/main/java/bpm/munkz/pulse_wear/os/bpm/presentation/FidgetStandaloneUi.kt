package bpm.munkz.pulse_wear.os.bpm.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
internal fun GlassCommandButton(
    text: String,
    modifier: Modifier,
    fontSize: TextUnit,
    circular: Boolean = false,
    selected: Boolean = false,
    prominent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = if (circular) CircleShape else RoundedCornerShape(50)
    val enabledAlpha = if (enabled) 1f else 0.34f
    val buttonColor = when {
        prominent -> MaterialTheme.colorScheme.primary.copy(
            alpha = (if (selected) 0.92f else 0.78f) * enabledAlpha,
        )
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.34f * enabledAlpha)
        else -> Color.White.copy(alpha = 0.13f * enabledAlpha)
    }
    val borderColor = if (prominent || selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.95f * enabledAlpha)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.58f * enabledAlpha)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(buttonColor, shape)
            .border(1.dp, borderColor, shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = fontSize.capDenseControlScale(),
            fontWeight = FontWeight.Bold,
            color = if (prominent) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onBackground
            }.copy(alpha = enabledAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TextUnit.capDenseControlScale(maxScale: Float = 1.15f): TextUnit {
    val fontScale = LocalDensity.current.fontScale
    val compensation = (fontScale / maxScale).coerceAtLeast(1f)
    return (value / compensation).sp
}
