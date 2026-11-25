package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.FeatureCategory
import com.example.model.GeneratedFile
import com.example.model.MinecraftEdition
import com.example.model.ModProject
import com.example.model.UiState
import com.example.model.VersionCapabilityProfile
import com.example.ui.components.Base64PngPreview
import com.example.ui.components.CompatibilityStatusBadge
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.StatusCompatible
import com.example.ui.theme.StatusPartial

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    activeEdition: MinecraftEdition,
    capabilityProfile: VersionCapabilityProfile?,
    selectedLoader: String,
    selectedCategory: FeatureCategory,
    modIdInput: String,
    promptInput: String,
    texturePromptInput: String,
    activeProject: ModProject?,
    selectedFilePath: String?,
    generationState: UiState<String>,
    textureState: UiState<String>,
    cloudSyncState: UiState<String>,
    onOpenVersionBrowser: () -> Unit,
    onSelectLoader: (String) -> Unit,
    onSelectCategory: (FeatureCategory) -> Unit,
    onModIdChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onTexturePromptChange: (String) -> Unit,
    onGenerateAi: () -> Unit,
    onLoadTemplate: () -> Unit,
    onGenerateTexture: () -> Unit,
    onSelectFile: (String) -> Unit,
    onEditFileContent: (String, String) -> Unit,
    onFixFileWithAi: (String) -> Unit,
    onSaveToCloud: () -> Unit,
    onOpenExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCapabilities by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Engine & Version Context Card
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeEdition.engineTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = capabilityProfile?.let {
                                "${it.version.versionNumber} — ${it.version.updateName.ifBlank { it.version.historicalCategory }}"
                            } ?: "Loading version catalog...",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (capabilityProfile != null) {
                            Text(
                                text = "Released ${capabilityProfile.version.releaseDate} • ${capabilityProfile.version.releaseType} • ${activeEdition.exportHint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenVersionBrowser,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("change_version_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Select Version",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Versions", maxLines = 1, softWrap = false)
                    }
                }

                if (capabilityProfile != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompatibilityStatusBadge(status = capabilityProfile.version.compatibilityStatus)
                        TextButton(
                            onClick = { showCapabilities = !showCapabilities },
                            modifier = Modifier.testTag("toggle_capabilities_button")
                        ) {
                            Icon(
                                imageVector = if (showCapabilities) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = "Toggle Technical Specs"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Technical Matrix")
                        }
                    }

                    // Compatible Loader Chips
                    if (capabilityProfile.version.compatibleLoaders.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (activeEdition == MinecraftEdition.JAVA) "Mod Loader" else "Target Runtime",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                capabilityProfile.version.compatibleLoaders.forEach { loader ->
                                    FilterChip(
                                        selected = loader == selectedLoader,
                                        onClick = { onSelectLoader(loader) },
                                        label = { Text(loader, style = MaterialTheme.typography.bodySmall) }
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = showCapabilities) {
                        CapabilityMatrixPanel(profile = capabilityProfile)
                    }
                }
            }
        }

        // Mod Generator Controls Card
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
                    text = if (activeEdition == MinecraftEdition.JAVA) {
                        "Java Mod Generator"
                    } else {
                        "Bedrock Add-on Generator"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(FeatureCategory.entries) { category ->
                        FilterChip(
                            selected = category == selectedCategory,
                            onClick = { onSelectCategory(category) },
                            label = { Text(category.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = modIdInput,
                    onValueChange = onModIdChange,
                    label = {
                        Text(if (activeEdition == MinecraftEdition.JAVA) "Mod ID (e.g. obsidian_forge)" else "Pack Namespace (e.g. craftforge)")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mod_id_input")
                )

                OutlinedTextField(
                    value = promptInput,
                    onValueChange = onPromptChange,
                    label = {
                        Text(
                            if (activeEdition == MinecraftEdition.JAVA) {
                                "Describe your Java mod (blocks, mobs, weapons, dimensions, recipes...)"
                            } else {
                                "Describe your Bedrock add-on (BP/RP blocks, entities, Script API, loot...)"
                            }
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mod_prompt_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onGenerateAi,
                        enabled = generationState !is UiState.Loading,
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("generate_mod_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (generationState is UiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generating...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = stringResource(R.string.generate_mod_action),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.generate_mod_action),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onLoadTemplate,
                        enabled = generationState !is UiState.Loading,
                        modifier = Modifier
                            .weight(0.9f)
                            .heightIn(min = 48.dp)
                            .testTag("load_template_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = stringResource(R.string.load_template_action),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Template", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // AI Pixel Texture Synthesis Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = texturePromptInput,
                        onValueChange = onTexturePromptChange,
                        label = { Text("Pixel-art PNG texture prompt (optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("texture_prompt_input")
                    )
                    OutlinedButton(
                        onClick = onGenerateTexture,
                        enabled = textureState !is UiState.Loading,
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("generate_texture_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (textureState is UiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = stringResource(R.string.generate_texture_action),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PNG", maxLines = 1)
                        }
                    }
                }

                StatusFeedbackBanner(state = generationState)
                StatusFeedbackBanner(state = textureState)
                StatusFeedbackBanner(state = cloudSyncState)
            }
        }

        // Workspace Files & Inspector
        if (activeProject == null) {
            EmptyWorkspaceCard()
        } else {
            ActiveProjectWorkspace(
                project = activeProject,
                selectedFilePath = selectedFilePath,
                onSelectFile = onSelectFile,
                onEditFileContent = onEditFileContent,
                onFixFileWithAi = onFixFileWithAi,
                onSaveToCloud = onSaveToCloud,
                onOpenExport = onOpenExport
            )
        }
    }
}

@Composable
private fun CapabilityMatrixPanel(profile: VersionCapabilityProfile) {
    Surface(
        color = ObsidianSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CapabilityRow("APIs", profile.supportedApis.joinToString(" • "))
            CapabilityRow("Block Reg.", profile.blockRegistrationMethod)
            CapabilityRow("Entity Reg.", profile.entityRegistrationMethod)
            CapabilityRow("Scripting", profile.scriptingSystem)
            CapabilityRow("Textures", profile.textureRequirement)
            CapabilityRow("Models", profile.modelFormat)
            CapabilityRow("Manifest", profile.manifestRequirement)
            CapabilityRow("Structure", profile.projectStructureSummary)
            if (profile.impossibleFeatures.isNotEmpty()) {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, StatusPartial),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Version Guardrails (Unsupported APIs)",
                            style = MaterialTheme.typography.labelMedium,
                            color = StatusPartial
                        )
                        profile.impossibleFeatures.forEach { note ->
                            Text(
                                text = "• $note",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(84.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusFeedbackBanner(state: UiState<String>) {
    when (state) {
        is UiState.Success -> {
            Surface(
                color = ObsidianSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, StatusCompatible),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.data,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusCompatible,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        is UiState.Error -> {
            Surface(
                color = ObsidianSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun EmptyWorkspaceCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_workbench),
                contentDescription = stringResource(R.string.empty_workbench_desc),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "Workbench Ready",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Select a Minecraft version (2009–Nov 2026), describe what you want to craft, or load a starter template to inspect and export project files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActiveProjectWorkspace(
    project: ModProject,
    selectedFilePath: String?,
    onSelectFile: (String) -> Unit,
    onEditFileContent: (String, String) -> Unit,
    onFixFileWithAi: (String) -> Unit,
    onSaveToCloud: () -> Unit,
    onOpenExport: () -> Unit
) {
    val activeFile: GeneratedFile? = project.files.firstOrNull { it.path == selectedFilePath }
        ?: project.files.firstOrNull()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier.fillMaxWidth()
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
                        text = project.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${project.edition.label} ${project.targetVersionNumber} • ${project.selectedLoader} • ${project.files.size} files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                project.validationReport?.let {
                    CompatibilityStatusBadge(status = it.status)
                }
            }

            if (project.impossibleFeatureNotice.isNotBlank()) {
                Surface(
                    color = ObsidianSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StatusPartial),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Compatibility Adaptation",
                            tint = StatusPartial,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = project.impossibleFeatureNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (project.adaptationNotes.isNotBlank()) {
                Surface(
                    color = ObsidianSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = project.adaptationNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveToCloud,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("save_cloud_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = stringResource(R.string.save_cloud_action), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Cloud", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = onOpenExport,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("validate_export_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.Verified, contentDescription = stringResource(R.string.validate_export_action), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Validate & Export", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Adaptive File Tree + Editor
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 600.dp
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.width(220.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            project.files.forEach { file ->
                                FileTreeRow(
                                    file = file,
                                    selected = file.path == activeFile?.path,
                                    onClick = { onSelectFile(file.path) }
                                )
                            }
                        }
                        if (activeFile != null) {
                            FileEditorPane(
                                file = activeFile,
                                onEditContent = { onEditFileContent(activeFile.path, it) },
                                onFixWithAi = { onFixFileWithAi(activeFile.path) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            project.files.forEach { file ->
                                FilterChip(
                                    selected = file.path == activeFile?.path,
                                    onClick = { onSelectFile(file.path) },
                                    label = {
                                        Text(
                                            text = file.path.substringAfterLast('/'),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                            }
                        }
                        if (activeFile != null) {
                            FileEditorPane(
                                file = activeFile,
                                onEditContent = { onEditFileContent(activeFile.path, it) },
                                onFixWithAi = { onFixFileWithAi(activeFile.path) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileTreeRow(
    file: GeneratedFile,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else ObsidianSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (file.binaryAsset) Icons.Filled.Image else Icons.Filled.Description,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = file.path,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FileEditorPane(
    file: GeneratedFile,
    onEditContent: (String) -> Unit,
    onFixWithAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianBackground,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = file.path,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!file.binaryAsset) {
                    TextButton(
                        onClick = onFixWithAi,
                        modifier = Modifier.testTag("fix_with_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = stringResource(R.string.fix_with_ai_action),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fix with AI")
                    }
                }
            }

            if (file.binaryAsset) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Base64PngPreview(
                        base64Content = file.content,
                        contentDescription = "Generated texture ${file.path}",
                        modifier = Modifier.size(150.dp)
                    )
                }
            } else {
                OutlinedTextField(
                    value = file.content,
                    onValueChange = onEditContent,
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 380.dp)
                        .testTag("code_editor_field")
                )
            }
        }
    }
}
