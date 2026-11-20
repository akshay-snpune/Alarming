package com.alarming.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val GlassCardShape = RoundedCornerShape(26.dp)
internal val CapsuleShape = RoundedCornerShape(percent = 50)

@Composable
private fun isDarkGlass(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * A translucent "liquid glass" surface: a frosted fill over the ambient background, a light-catching rim that fades from
 * the top edge, and a soft shadow so the layer floats above the content beneath it.
 */
@Composable
internal fun Modifier.glass(shape: Shape = GlassCardShape, tint: Color = Color.Unspecified, elevation: Dp = 10.dp): Modifier {
    val dark = isDarkGlass()
    val fill = when {
        tint != Color.Unspecified -> tint
        dark -> Color.White.copy(alpha = 0.07f)
        else -> Color.White.copy(alpha = 0.58f)
    }
    val rim = Brush.verticalGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.04f))
        else listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.3f)),
    )
    val shadowColor = if (dark) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    return this
        .shadow(elevation, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
        .background(fill)
        .background(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.05f else 0.25f), Color.Transparent)),
        )
        .border(1.dp, rim, shape)
}

@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .glass(tint = tint)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}

@Composable
internal fun GlassSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Soft, water-tinted light pools behind every screen; translucent glass layers pick up their colour. */
@Composable
internal fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = isDarkGlass()
    val pools = listOf(
        Triple(Offset(0.1f, 0.05f), colors.secondary.copy(alpha = if (dark) 0.16f else 0.45f), 0.75f),
        Triple(Offset(0.95f, 0.35f), colors.primary.copy(alpha = if (dark) 0.14f else 0.35f), 0.7f),
        Triple(Offset(0.2f, 0.95f), colors.tertiary.copy(alpha = if (dark) 0.1f else 0.18f), 0.8f),
    )
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .drawBehind {
                pools.forEach { (center, color, radius) ->
                    val c = Offset(size.width * center.x, size.height * center.y)
                    val r = size.maxDimension * radius
                    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), c, r), r, c)
                }
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            content()
        }
    }
}
