package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BedrockExportMode
import com.example.model.CheckSeverity
import com.example.model.MinecraftEdition
import com.example.model.ModProject
import com.example.ui.components.CompatibilityStatusBadge
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.StatusCompatible
import com.example.ui.theme.StatusNotSupported
import com.example.ui.theme.StatusPartial

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(
    activeProject: ModProject?,
    bedrockExportMode: BedrockExportMode,
    suggestedFileName: String,
    onSelectBedrockExportMode: (BedrockExportMode) -> Unit,
    onRerunValidation: () -> Unit,
    onFixFileWithAi: (String) -> Unit,
    onSaveArchive: () -> Unit,
    onShareArchive: () -> Unit,
    onBackToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (activeProject == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "No Active Project to Validate",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Create or open a Java mod or Bedrock add-on first to run pre-export compatibility checks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onBackToStudio) {
                    Text("Go to Studio")
                }
            }
        }
        return
    }

    val report = activeProject.validationReport

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Compatibility Report Summary Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, ObsidianOutline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pre-Export Compatibility Report",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${activeProject.title} • ${activeProject.edition.label} ${activeProject.targetVersionNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    report?.let {
                        CompatibilityStatusBadge(status = it.status)
                    }
                }

                if (report != null) {
                    Text(
                        text = report.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedButton(
                    onClick = onRerunValidation,
                    modifier = Modifier.testTag("rerun_validation_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Re-run Full Validation")
                }
            }
        }

        // Export Packaging Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, ObsidianOutline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (activeProject.edition == MinecraftEdition.JAVA) {
                        "Java Edition Package Export (.zip)"
                    } else {
                        "Bedrock Edition Add-on Export (.mcaddon / .mcpack)"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (activeProject.edition == MinecraftEdition.BEDROCK) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BedrockExportMode.entries.forEach { mode ->
                            FilterChip(
                                selected = mode == bedrockExportMode,
                                onClick = { onSelectBedrockExportMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                }

                Surface(
                    color = ObsidianSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Output Archive: $suggestedFileName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                val canExport = report?.canExport != false
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onSaveArchive,
                        enabled = canExport,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("save_archive_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Save Archive", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save File", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onShareArchive,
                        enabled = canExport,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("share_archive_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share Archive", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Itemized Validation Checks
        if (report != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Detailed Validation Checks (${report.checks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                report.checks.forEach { check ->
                    val (icon, tint) = when (check.severity) {
                        CheckSeverity.PASS -> Icons.Filled.CheckCircle to StatusCompatible
                        CheckSeverity.WARNING -> Icons.Filled.Warning to StatusPartial
                        CheckSeverity.ERROR -> Icons.Filled.Error to StatusNotSupported
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = check.severity.name,
                                    tint = tint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "[${check.category}] ${check.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = check.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val path = check.affectedFilePath
                            if (path != null && check.severity != CheckSeverity.PASS) {
                                TextButton(onClick = { onFixFileWithAi(path) }) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Auto-Fix ${path.substringAfterLast('/')} with AI")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
