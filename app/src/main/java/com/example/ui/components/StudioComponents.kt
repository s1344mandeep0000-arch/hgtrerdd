package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CompatibilityStatus
import com.example.model.MinecraftEdition
import com.example.ui.theme.BedrockCyanPrimary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JavaAmberPrimary
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.StatusCompatible
import com.example.ui.theme.StatusNotSupported
import com.example.ui.theme.StatusPartial
import com.example.ui.theme.StatusRequiresMod

@Composable
fun EditionDualSelector(
    activeEdition: MinecraftEdition,
    onSelectEdition: (MinecraftEdition) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ObsidianSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ObsidianOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EditionPill(
                edition = MinecraftEdition.JAVA,
                selected = activeEdition == MinecraftEdition.JAVA,
                iconAccent = JavaAmberPrimary,
                onClick = { onSelectEdition(MinecraftEdition.JAVA) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("edition_selector_java")
            )
            EditionPill(
                edition = MinecraftEdition.BEDROCK,
                selected = activeEdition == MinecraftEdition.BEDROCK,
                iconAccent = BedrockCyanPrimary,
                onClick = { onSelectEdition(MinecraftEdition.BEDROCK) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("edition_selector_bedrock")
            )
        }
    }
}

@Composable
private fun EditionPill(
    edition: MinecraftEdition,
    selected: Boolean,
    iconAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) EmeraldContainer else Color.Transparent,
        label = "editionBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) EmeraldPrimary else Color.Transparent,
        label = "editionBorder"
    )
    val iconColor = if (selected) EmeraldPrimary else iconAccent
    val subtitleColor = if (selected) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = if (selected) BorderStroke(1.5.dp, borderColor) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (edition == MinecraftEdition.JAVA) Icons.Filled.Code else Icons.Filled.Layers,
                contentDescription = edition.engineTitle,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = edition.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (edition == MinecraftEdition.JAVA) "Java Mod Engine" else "Bedrock Add-on Engine",
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CompatibilityStatusBadge(
    status: CompatibilityStatus,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        CompatibilityStatus.COMPATIBLE -> StatusCompatible
        CompatibilityStatus.PARTIALLY_COMPATIBLE -> StatusPartial
        CompatibilityStatus.REQUIRES_MODIFICATION -> StatusRequiresMod
        CompatibilityStatus.NOT_SUPPORTED -> StatusNotSupported
    }

    Surface(
        modifier = modifier,
        color = ObsidianSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color)
    ) {
        Text(
            text = status.displayLabel,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun Base64PngPreview(
    base64Content: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(base64Content) {
        try {
            val bytes = Base64.decode(base64Content, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianSurface),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Invalid PNG binary data",
                style = MaterialTheme.typography.bodySmall,
                color = StatusNotSupported
            )
        }
    }
}
