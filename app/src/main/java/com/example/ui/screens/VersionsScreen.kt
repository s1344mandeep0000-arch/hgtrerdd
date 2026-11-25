package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CompatibilityStatus
import com.example.model.MinecraftEdition
import com.example.model.VersionEntry
import com.example.ui.components.CompatibilityStatusBadge
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.viewmodel.VersionFilterState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VersionsScreen(
    activeEdition: MinecraftEdition,
    allEditionVersions: List<VersionEntry>,
    filteredVersions: List<VersionEntry>,
    selectedVersion: VersionEntry?,
    filterState: VersionFilterState,
    onSearchChange: (String) -> Unit,
    onCategoryFilterChange: (String) -> Unit,
    onReleaseTypeFilterChange: (String) -> Unit,
    onLoaderFilterChange: (String) -> Unit,
    onToggleSortOrder: () -> Unit,
    onSelectVersion: (VersionEntry) -> Unit,
    onAddCustomVersion: (
        edition: MinecraftEdition,
        versionNumber: String,
        updateName: String,
        releaseDate: String,
        historicalCategory: String,
        releaseType: String,
        status: CompatibilityStatus,
        loadersCsv: String,
        capabilitiesCsv: String,
        formatsCsv: String,
        requiredRuntime: String,
        notes: String
    ) -> Unit,
    onImportJsonCatalog: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    val categories = remember(allEditionVersions) {
        listOf("All") + allEditionVersions.map { it.historicalCategory }.distinct()
    }
    val releaseTypes = remember(allEditionVersions) {
        listOf("All") + allEditionVersions.map { it.releaseType }.distinct()
    }
    val loaderOptions = remember(activeEdition) {
        if (activeEdition == MinecraftEdition.JAVA) {
            listOf("All", "NeoForge", "Forge", "Fabric", "Legacy Forge", "ModLoader", "Quilt")
        } else {
            listOf("All", "Native Add-on", "Script API")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Historical Version Matrix (2009–Nov 2026)",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Showing ${filteredVersions.size} of ${allEditionVersions.size} ${activeEdition.label} releases",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = filterState.searchQuery,
                        onValueChange = onSearchChange,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search versions") },
                        label = { Text("Search version, update name, or date (e.g. 26.3, 1.7.10, 2009)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("version_search_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onToggleSortOrder,
                            modifier = Modifier
                                .weight(1.3f)
                                .heightIn(min = 48.dp)
                                .testTag("version_sort_button")
                        ) {
                            Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(filterState.sortOrder.label, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showFilters = !showFilters },
                            modifier = Modifier
                                .weight(0.7f)
                                .heightIn(min = 48.dp)
                                .testTag("version_filters_toggle")
                        ) {
                            Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Filters", maxLines = 1)
                        }
                    }

                    AnimatedVisibility(visible = showFilters) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Historical Category",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                categories.forEach { cat ->
                                    FilterChip(
                                        selected = filterState.categoryFilter == cat,
                                        onClick = { onCategoryFilterChange(cat) },
                                        label = { Text(cat) }
                                    )
                                }
                            }

                            Text(
                                text = "Release Type & Mod Loader",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                releaseTypes.forEach { rt ->
                                    FilterChip(
                                        selected = filterState.releaseTypeFilter == rt,
                                        onClick = { onReleaseTypeFilterChange(rt) },
                                        label = { Text(rt) }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                loaderOptions.forEach { loader ->
                                    FilterChip(
                                        selected = filterState.loaderFilter == loader,
                                        onClick = { onLoaderFilterChange(loader) },
                                        label = { Text(loader) }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("add_version_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Version", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Release", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("import_catalog_button")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = "Import JSON", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import JSON", maxLines = 1)
                        }
                    }
                }
            }
        }

        items(filteredVersions, key = { it.id }) { version ->
            val isSelected = version.id == selectedVersion?.id
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else ObsidianOutline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectVersion(version) }
                    .testTag("version_card_${version.id}")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = version.versionNumber,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Active Target Version",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${version.updateName} • ${version.releaseDate} (${version.releaseType})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        CompatibilityStatusBadge(status = version.compatibilityStatus)
                    }

                    Text(
                        text = "${version.historicalCategory} • Runtime: ${version.requiredRuntime}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        version.compatibleLoaders.forEach { loader ->
                            Surface(
                                color = ObsidianSurfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = loader,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        version.supportedFileFormats.forEach { fmt ->
                            Surface(
                                color = ObsidianSurfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = fmt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Capabilities: ${version.supportedCapabilities.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = version.adaptationNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddVersionDialog(
            defaultEdition = activeEdition,
            onDismiss = { showAddDialog = false },
            onSave = { ed, ver, name, date, cat, type, status, loaders, caps, fmts, runtime, notes ->
                onAddCustomVersion(ed, ver, name, date, cat, type, status, loaders, caps, fmts, runtime, notes)
                showAddDialog = false
            }
        )
    }

    if (showImportDialog) {
        ImportCatalogJsonDialog(
            onDismiss = { showImportDialog = false },
            onImport = { jsonText ->
                onImportJsonCatalog(jsonText)
                showImportDialog = false
            }
        )
    }
}

@Composable
private fun AddVersionDialog(
    defaultEdition: MinecraftEdition,
    onDismiss: () -> Unit,
    onSave: (
        MinecraftEdition, String, String, String, String, String,
        CompatibilityStatus, String, String, String, String, String
    ) -> Unit
) {
    var versionNumber by remember { mutableStateOf("") }
    var updateName by remember { mutableStateOf("") }
    var releaseDate by remember { mutableStateOf("2026-12-01") }
    var loaders by remember {
        mutableStateOf(
            if (defaultEdition == MinecraftEdition.JAVA) "NeoForge (26.4.x), Fabric" else "Native Add-on, Script API"
        )
    }
    var capabilities by remember { mutableStateOf("Blocks, Items, Entities, Recipes, Loot Tables") }
    var formats by remember {
        mutableStateOf(
            if (defaultEdition == MinecraftEdition.JAVA) ".java,.json,.png,.toml,.gradle" else ".mcaddon,.mcpack,.json,.js,.png"
        )
    }
    var runtime by remember {
        mutableStateOf(if (defaultEdition == MinecraftEdition.JAVA) "Java 21" else "Bedrock Engine")
    }
    var notes by remember { mutableStateOf("Added dynamically to CraftForge catalog.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${defaultEdition.label} Release") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = versionNumber,
                    onValueChange = { versionNumber = it },
                    label = { Text("Version Number (e.g. 26.4 or 26.70)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = updateName,
                    onValueChange = { updateName = it },
                    label = { Text("Update Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = releaseDate,
                    onValueChange = { releaseDate = it },
                    label = { Text("Release Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = loaders,
                    onValueChange = { loaders = it },
                    label = { Text("Compatible Loaders (comma-separated)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = capabilities,
                    onValueChange = { capabilities = it },
                    label = { Text("Capabilities (comma-separated)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = runtime,
                    onValueChange = { runtime = it },
                    label = { Text("Required Runtime") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Compatibility Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (versionNumber.isNotBlank() && releaseDate.isNotBlank()) {
                        onSave(
                            defaultEdition,
                            versionNumber,
                            updateName,
                            releaseDate,
                            "Dynamic Catalog Update",
                            "Release",
                            CompatibilityStatus.COMPATIBLE,
                            loaders,
                            capabilities,
                            formats,
                            runtime,
                            notes
                        )
                    }
                }
            ) {
                Text("Save to Catalog")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ImportCatalogJsonDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var jsonText by remember {
        mutableStateOf(
            """
            [
              {
                "edition": "JAVA",
                "versionNumber": "26.4",
                "updateName": "Ice Caves Drop",
                "releaseDate": "2026-12-08",
                "historicalCategory": "Year-Based Drop (2026)",
                "releaseType": "Release",
                "compatibilityStatus": "COMPATIBLE",
                "supportedCapabilities": ["Blocks", "Items", "Entities", "Recipes"],
                "compatibleLoaders": ["NeoForge (26.4.x)", "Fabric"],
                "supportedFileFormats": [".java", ".json", ".png", ".toml"],
                "requiredRuntime": "Java 21",
                "adaptationNotes": "Dynamic catalog entry imported without rebuilding app."
              }
            ]
            """.trimIndent()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Version Catalog JSON") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste a JSON object or array of Minecraft version records to sync into your updateable cloud catalog.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onImport(jsonText) }) {
                Text("Import & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
