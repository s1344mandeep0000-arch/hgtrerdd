package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.ProjectRepository
import com.example.data.VersionCatalogRepository
import com.example.engine.ArchiveExporter
import com.example.engine.BedrockAddonEngine
import com.example.engine.CompatibilityValidator
import com.example.engine.JavaModEngine
import com.example.engine.VersionCapabilityAnalyzer
import com.example.model.BedrockExportMode
import com.example.model.CompatibilityStatus
import com.example.model.FeatureCategory
import com.example.model.GeneratedFile
import com.example.model.MinecraftEdition
import com.example.model.ModProject
import com.example.model.SortOrderOption
import com.example.model.StudioTab
import com.example.model.UiState
import com.example.model.VersionCapabilityProfile
import com.example.model.VersionEntry
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VersionFilterState(
    val searchQuery: String = "",
    val categoryFilter: String = "All",
    val releaseTypeFilter: String = "All",
    val loaderFilter: String = "All",
    val sortOrder: SortOrderOption = SortOrderOption.NEWEST_FIRST
)

class StudioViewModel(
    private val versionRepo: VersionCatalogRepository,
    private val projectRepo: ProjectRepository,
    private val javaEngine: JavaModEngine,
    private val bedrockEngine: BedrockAddonEngine,
    private val currentUserId: String
) : ViewModel() {

    private val _activeTab = MutableStateFlow(StudioTab.STUDIO)
    val activeTab: StateFlow<StudioTab> = _activeTab.asStateFlow()

    private val _activeEdition = MutableStateFlow(MinecraftEdition.JAVA)
    val activeEdition: StateFlow<MinecraftEdition> = _activeEdition.asStateFlow()

    private val _bundledVersions = MutableStateFlow<List<VersionEntry>>(emptyList())

    private val _versionFilter = MutableStateFlow(VersionFilterState())
    val versionFilter: StateFlow<VersionFilterState> = _versionFilter.asStateFlow()

    private val _selectedVersion = MutableStateFlow<VersionEntry?>(null)
    val selectedVersion: StateFlow<VersionEntry?> = _selectedVersion.asStateFlow()

    private val _selectedLoader = MutableStateFlow("NeoForge (26.3.x)")
    val selectedLoader: StateFlow<String> = _selectedLoader.asStateFlow()

    private val _selectedCategory = MutableStateFlow(FeatureCategory.FULL_PROJECT)
    val selectedCategory: StateFlow<FeatureCategory> = _selectedCategory.asStateFlow()

    private val _capabilityProfile = MutableStateFlow<VersionCapabilityProfile?>(null)
    val capabilityProfile: StateFlow<VersionCapabilityProfile?> = _capabilityProfile.asStateFlow()

    private val _modIdInput = MutableStateFlow("obsidian_forge")
    val modIdInput: StateFlow<String> = _modIdInput.asStateFlow()

    private val _promptInput = MutableStateFlow("")
    val promptInput: StateFlow<String> = _promptInput.asStateFlow()

    private val _texturePromptInput = MutableStateFlow("")
    val texturePromptInput: StateFlow<String> = _texturePromptInput.asStateFlow()

    private val _activeProject = MutableStateFlow<ModProject?>(null)
    val activeProject: StateFlow<ModProject?> = _activeProject.asStateFlow()

    private val _selectedFilePath = MutableStateFlow<String?>(null)
    val selectedFilePath: StateFlow<String?> = _selectedFilePath.asStateFlow()

    private val _generationState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val generationState: StateFlow<UiState<String>> = _generationState.asStateFlow()

    private val _textureState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val textureState: StateFlow<UiState<String>> = _textureState.asStateFlow()

    private val _cloudSyncState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val cloudSyncState: StateFlow<UiState<String>> = _cloudSyncState.asStateFlow()

    private val _bedrockExportMode = MutableStateFlow(BedrockExportMode.MCADDON)
    val bedrockExportMode: StateFlow<BedrockExportMode> = _bedrockExportMode.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val dynamicVersionsFlow = versionRepo.observeDynamicUpdates(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Dynamic version catalog listener error", firestoreError ?: error)
            emit(emptyList())
        }

    val allVersions: StateFlow<List<VersionEntry>> = combine(_bundledVersions, dynamicVersionsFlow) { bundled, dynamic ->
        val mergedById = LinkedHashMap<String, VersionEntry>()
        bundled.forEach { mergedById[it.id] = it }
        dynamic.forEach { mergedById[it.id] = it }
        mergedById.values.toList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val filteredVersions: StateFlow<List<VersionEntry>> = combine(
        allVersions,
        _activeEdition,
        _versionFilter
    ) { versions, edition, filter ->
        val query = filter.searchQuery.trim().lowercase()
        val filtered = versions.filter { v ->
            v.edition == edition &&
                (query.isBlank() ||
                    v.versionNumber.lowercase().contains(query) ||
                    v.updateName.lowercase().contains(query) ||
                    v.historicalCategory.lowercase().contains(query) ||
                    v.releaseDate.contains(query)) &&
                (filter.categoryFilter == "All" || v.historicalCategory == filter.categoryFilter) &&
                (filter.releaseTypeFilter == "All" || v.releaseType == filter.releaseTypeFilter) &&
                (filter.loaderFilter == "All" || v.compatibleLoaders.any {
                    it.contains(filter.loaderFilter, ignoreCase = true)
                })
        }
        if (filter.sortOrder == SortOrderOption.NEWEST_FIRST) {
            filtered.sortedByDescending { it.sortOrder }
        } else {
            filtered.sortedBy { it.sortOrder }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val savedProjectsState: StateFlow<UiState<List<ModProject>>> = projectRepo.observeProjects(currentUserId)
        .map<List<ModProject>, UiState<List<ModProject>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing saved projects", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load cloud projects."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            try {
                val bundled = versionRepo.loadBundledVersions()
                _bundledVersions.value = bundled
                selectDefaultVersionForEdition(MinecraftEdition.JAVA, bundled)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bundled version database", e)
                _bannerMessage.value = "Failed to load local version database: ${e.localizedMessage}"
            }
        }
    }

    fun selectTab(tab: StudioTab) {
        _activeTab.value = tab
    }

    fun selectEdition(edition: MinecraftEdition) {
        if (_activeEdition.value == edition) return
        _activeEdition.value = edition
        _versionFilter.value = VersionFilterState()
        _generationState.value = UiState.Idle
        _textureState.value = UiState.Idle
        selectDefaultVersionForEdition(edition, allVersions.value.ifEmpty { _bundledVersions.value })
    }

    private fun selectDefaultVersionForEdition(edition: MinecraftEdition, versions: List<VersionEntry>) {
        val candidates = versions.filter { it.edition == edition }
        val preferred = candidates
            .filter { it.compatibilityStatus == CompatibilityStatus.COMPATIBLE }
            .maxByOrNull { it.sortOrder }
            ?: candidates.maxByOrNull { it.sortOrder }
        if (preferred != null) {
            selectVersion(preferred)
        }
    }

    fun selectVersion(version: VersionEntry) {
        if (version.edition != _activeEdition.value) {
            _activeEdition.value = version.edition
        }
        _selectedVersion.value = version
        val defaultLoader = version.compatibleLoaders.firstOrNull() ?: "Standard"
        _selectedLoader.value = defaultLoader
        _capabilityProfile.value = VersionCapabilityAnalyzer.analyze(version, defaultLoader)
        _activeProject.value?.let { proj ->
            if (proj.edition == version.edition) {
                val updated = proj.copy(
                    targetVersionId = version.id,
                    targetVersionNumber = version.versionNumber,
                    selectedLoader = defaultLoader
                )
                revalidateProject(updated)
            }
        }
    }

    fun selectLoader(loader: String) {
        _selectedLoader.value = loader
        _selectedVersion.value?.let { ver ->
            _capabilityProfile.value = VersionCapabilityAnalyzer.analyze(ver, loader)
            _activeProject.value?.let { proj ->
                if (proj.edition == ver.edition) {
                    revalidateProject(proj.copy(selectedLoader = loader))
                }
            }
        }
    }

    fun selectFeatureCategory(category: FeatureCategory) {
        _selectedCategory.value = category
    }

    fun updateModIdInput(value: String) {
        _modIdInput.value = value
    }

    fun updatePromptInput(value: String) {
        _promptInput.value = value
    }

    fun updateTexturePromptInput(value: String) {
        _texturePromptInput.value = value
    }

    fun updateSearchQuery(query: String) {
        _versionFilter.update { it.copy(searchQuery = query) }
    }

    fun updateCategoryFilter(category: String) {
        _versionFilter.update { it.copy(categoryFilter = category) }
    }

    fun updateReleaseTypeFilter(releaseType: String) {
        _versionFilter.update { it.copy(releaseTypeFilter = releaseType) }
    }

    fun updateLoaderFilter(loader: String) {
        _versionFilter.update { it.copy(loaderFilter = loader) }
    }

    fun toggleSortOrder() {
        _versionFilter.update {
            val next = if (it.sortOrder == SortOrderOption.NEWEST_FIRST) {
                SortOrderOption.OLDEST_FIRST
            } else {
                SortOrderOption.NEWEST_FIRST
            }
            it.copy(sortOrder = next)
        }
    }

    fun selectBedrockExportMode(mode: BedrockExportMode) {
        _bedrockExportMode.value = mode
    }

    fun selectFile(path: String) {
        _selectedFilePath.value = path
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun generateWithActiveEngine() {
        val capability = _capabilityProfile.value ?: run {
            _generationState.value = UiState.Error("Select a Minecraft version first.")
            return
        }
        val prompt = _promptInput.value.trim()
        if (prompt.isBlank()) {
            _generationState.value = UiState.Error("Describe the mod or add-on feature you want to create.")
            return
        }

        val edition = _activeEdition.value
        val category = _selectedCategory.value
        val modId = _modIdInput.value

        viewModelScope.launch {
            _generationState.value = UiState.Loading
            val result = if (edition == MinecraftEdition.JAVA) {
                javaEngine.generateMod(prompt, modId, category, capability)
            } else {
                bedrockEngine.generateAddon(prompt, modId, category, capability)
            }

            result.fold(
                onSuccess = { gen ->
                    val draft = ModProject(
                        id = "",
                        userId = currentUserId,
                        title = gen.title,
                        modId = gen.modId,
                        edition = edition,
                        targetVersionId = capability.version.id,
                        targetVersionNumber = capability.version.versionNumber,
                        selectedLoader = capability.selectedLoader,
                        featureCategory = category,
                        prompt = prompt,
                        adaptationNotes = gen.adaptationNotes,
                        impossibleFeatureNotice = gen.impossibleFeatureNotice,
                        files = gen.files
                    )
                    revalidateProject(draft, capability)
                    _selectedFilePath.value = gen.files.firstOrNull()?.path
                    _generationState.value = UiState.Success("Generated ${gen.files.size} ${edition.label} file(s).")
                },
                onFailure = { error ->
                    _generationState.value = UiState.Error(
                        error.localizedMessage ?: "AI generation failed."
                    )
                }
            )
        }
    }

    fun loadStarterTemplate() {
        val capability = _capabilityProfile.value ?: return
        val edition = _activeEdition.value
        val gen = if (edition == MinecraftEdition.JAVA) {
            javaEngine.buildStarterTemplate(_modIdInput.value, capability)
        } else {
            bedrockEngine.buildStarterTemplate(_modIdInput.value, capability)
        }
        val draft = ModProject(
            id = "",
            userId = currentUserId,
            title = gen.title,
            modId = gen.modId,
            edition = edition,
            targetVersionId = capability.version.id,
            targetVersionNumber = capability.version.versionNumber,
            selectedLoader = capability.selectedLoader,
            featureCategory = _selectedCategory.value,
            prompt = _promptInput.value.ifBlank { "Starter template for ${capability.version.versionNumber}" },
            adaptationNotes = gen.adaptationNotes,
            impossibleFeatureNotice = gen.impossibleFeatureNotice,
            files = gen.files
        )
        revalidateProject(draft, capability)
        _selectedFilePath.value = gen.files.firstOrNull()?.path
        _generationState.value = UiState.Success("Loaded ${edition.label} starter template.")
    }

    fun generateAiPixelTexture() {
        val capability = _capabilityProfile.value ?: return
        val texPrompt = _texturePromptInput.value.trim()
            .ifBlank { _promptInput.value.trim() }
        if (texPrompt.isBlank()) {
            _textureState.value = UiState.Error("Enter a texture or mod description first.")
            return
        }

        val currentProj = _activeProject.value
        val modId = (currentProj?.modId ?: _modIdInput.value)
            .lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .ifBlank { "craftforge" }
        val assetName = "${_selectedCategory.value.name.lowercase()}_icon"

        viewModelScope.launch {
            _textureState.value = UiState.Loading
            val res = if (_activeEdition.value == MinecraftEdition.JAVA) {
                javaEngine.generatePixelTexture(texPrompt, modId, assetName, _selectedCategory.value, capability)
            } else {
                bedrockEngine.generatePixelTexture(texPrompt, assetName, _selectedCategory.value)
            }

            res.fold(
                onSuccess = { pngFile ->
                    val baseProj = currentProj ?: ModProject(
                        userId = currentUserId,
                        title = "${modId}_pack",
                        modId = modId,
                        edition = _activeEdition.value,
                        targetVersionId = capability.version.id,
                        targetVersionNumber = capability.version.versionNumber,
                        selectedLoader = capability.selectedLoader,
                        featureCategory = _selectedCategory.value,
                        prompt = texPrompt
                    )
                    val updatedFiles = baseProj.files
                        .filterNot { it.path == pngFile.path } + pngFile
                    revalidateProject(baseProj.copy(files = updatedFiles), capability)
                    _selectedFilePath.value = pngFile.path
                    _textureState.value = UiState.Success("Generated PNG texture: ${pngFile.path}")
                },
                onFailure = { error ->
                    _textureState.value = UiState.Error(
                        error.localizedMessage ?: "Texture synthesis failed."
                    )
                }
            )
        }
    }

    fun updateFileContent(path: String, newContent: String) {
        val proj = _activeProject.value ?: return
        val updatedFiles = proj.files.map { file ->
            if (file.path == path) file.copy(content = newContent) else file
        }
        revalidateProject(proj.copy(files = updatedFiles))
    }

    fun fixFileWithAi(path: String) {
        val proj = _activeProject.value ?: return
        val file = proj.files.firstOrNull { it.path == path && !it.binaryAsset } ?: return
        val capability = _capabilityProfile.value ?: return
        val issues = proj.validationReport?.checks
            ?.filter { it.severity != com.example.model.CheckSeverity.PASS }
            ?.joinToString("\n") { "- [${it.category}] ${it.title}: ${it.detail}" }
            .orEmpty()
            .ifBlank { "Ensure strict schema and version compliance for ${capability.version.versionNumber}." }

        viewModelScope.launch {
            _generationState.value = UiState.Loading
            val res = if (proj.edition == MinecraftEdition.JAVA) {
                javaEngine.correctFileWithAi(file, issues, capability)
            } else {
                bedrockEngine.correctFileWithAi(file, issues, capability)
            }
            res.fold(
                onSuccess = { (fixedFile, explanation) ->
                    val updatedFiles = proj.files.map {
                        if (it.path == path) fixedFile else it
                    }
                    revalidateProject(proj.copy(files = updatedFiles), capability)
                    _generationState.value = UiState.Success("AI Fix applied: $explanation")
                },
                onFailure = { error ->
                    _generationState.value = UiState.Error(
                        error.localizedMessage ?: "AI error correction failed."
                    )
                }
            )
        }
    }

    fun runValidation(): com.example.model.ValidationReport? {
        val proj = _activeProject.value ?: return null
        return revalidateProject(proj).validationReport
    }

    private fun revalidateProject(
        project: ModProject,
        explicitCapability: VersionCapabilityProfile? = null
    ): ModProject {
        val version = allVersions.value.firstOrNull { it.id == project.targetVersionId }
            ?: _selectedVersion.value
        val capability = explicitCapability ?: version?.let {
            VersionCapabilityAnalyzer.analyze(it, project.selectedLoader)
        }
        val report = capability?.let { CompatibilityValidator.validate(project, it) }
        val updated = project.copy(validationReport = report)
        _activeProject.value = updated
        return updated
    }

    fun saveActiveProjectToCloud() {
        val proj = _activeProject.value ?: run {
            _cloudSyncState.value = UiState.Error("Generate or load a project before saving.")
            return
        }
        val validated = revalidateProject(proj)
        viewModelScope.launch {
            _cloudSyncState.value = UiState.Loading
            projectRepo.saveProject(currentUserId, validated).fold(
                onSuccess = { savedId ->
                    _activeProject.value = validated.copy(id = savedId)
                    _cloudSyncState.value = UiState.Success("Saved '${validated.title}' to cloud.")
                },
                onFailure = { error ->
                    _cloudSyncState.value = UiState.Error(
                        error.localizedMessage ?: "Failed to save project."
                    )
                }
            )
        }
    }

    fun openSavedProject(projectId: String) {
        viewModelScope.launch {
            _cloudSyncState.value = UiState.Loading
            projectRepo.loadProjectWithFiles(currentUserId, projectId).fold(
                onSuccess = { loaded ->
                    _activeEdition.value = loaded.edition
                    val matchedVersion = allVersions.value.firstOrNull { it.id == loaded.targetVersionId }
                    if (matchedVersion != null) {
                        _selectedVersion.value = matchedVersion
                        _selectedLoader.value = loaded.selectedLoader
                        _capabilityProfile.value = VersionCapabilityAnalyzer.analyze(matchedVersion, loaded.selectedLoader)
                    }
                    _modIdInput.value = loaded.modId
                    _promptInput.value = loaded.prompt
                    _selectedCategory.value = loaded.featureCategory
                    revalidateProject(loaded)
                    _selectedFilePath.value = loaded.files.firstOrNull()?.path
                    _activeTab.value = StudioTab.STUDIO
                    _cloudSyncState.value = UiState.Success("Opened '${loaded.title}'.")
                },
                onFailure = { error ->
                    _cloudSyncState.value = UiState.Error(
                        error.localizedMessage ?: "Failed to load project files."
                    )
                }
            )
        }
    }

    fun deleteSavedProject(projectId: String) {
        viewModelScope.launch {
            projectRepo.deleteProject(currentUserId, projectId).fold(
                onSuccess = {
                    if (_activeProject.value?.id == projectId) {
                        _activeProject.value = null
                    }
                    _bannerMessage.value = "Project deleted."
                },
                onFailure = { error ->
                    _bannerMessage.value = error.localizedMessage ?: "Delete failed."
                }
            )
        }
    }

    fun addCustomVersionEntry(
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
    ) {
        val sort = releaseDate.replace("-", "").toLongOrNull() ?: 20261124L
        val entry = VersionEntry(
            id = "${edition.name.lowercase()}_${versionNumber.replace(Regex("[^a-zA-Z0-9]"), "_")}",
            edition = edition,
            versionNumber = versionNumber.trim(),
            updateName = updateName.trim(),
            releaseDate = releaseDate.trim(),
            sortOrder = sort,
            historicalCategory = historicalCategory.trim().ifBlank { "Updateable Release" },
            releaseType = releaseType.trim().ifBlank { "Release" },
            compatibilityStatus = status,
            supportedCapabilities = capabilitiesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() },
            compatibleLoaders = loadersCsv.split(",").map { it.trim() }.filter { it.isNotBlank() },
            supportedFileFormats = formatsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() },
            requiredRuntime = requiredRuntime.trim(),
            adaptationNotes = notes.trim(),
            isUserAdded = true
        )
        viewModelScope.launch {
            versionRepo.addOrUpdateVersion(currentUserId, entry).fold(
                onSuccess = {
                    _bannerMessage.value = "Added ${entry.edition.label} ${entry.versionNumber} to live catalog."
                },
                onFailure = { error ->
                    _bannerMessage.value = error.localizedMessage ?: "Failed to save version entry."
                }
            )
        }
    }

    fun importCatalogJson(jsonText: String) {
        viewModelScope.launch {
            versionRepo.importVersionCatalogJson(currentUserId, jsonText).fold(
                onSuccess = { count ->
                    _bannerMessage.value = "Imported $count version release(s) into live catalog."
                },
                onFailure = { error ->
                    _bannerMessage.value = "Import error: ${error.localizedMessage ?: "Invalid JSON"}"
                }
            )
        }
    }

    fun exportToSafUri(context: Context, uri: Uri) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            ArchiveExporter.writeArchiveToUri(context, uri, proj, _bedrockExportMode.value).fold(
                onSuccess = { count ->
                    val name = ArchiveExporter.suggestedFileName(proj, _bedrockExportMode.value)
                    _bannerMessage.value = "Exported $name ($count files)."
                },
                onFailure = { error ->
                    _bannerMessage.value = "Export failed: ${error.localizedMessage}"
                }
            )
        }
    }

    fun buildShareArchiveIntent(context: Context, onIntentReady: (Intent) -> Unit) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            ArchiveExporter.createShareIntent(context, proj, _bedrockExportMode.value).fold(
                onSuccess = onIntentReady,
                onFailure = { error ->
                    _bannerMessage.value = "Share failed: ${error.localizedMessage}"
                }
            )
        }
    }

    companion object {
        private const val TAG = "StudioViewModel"

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StudioViewModel(
                    versionRepo = VersionCatalogRepository(app, db),
                    projectRepo = ProjectRepository(db),
                    javaEngine = JavaModEngine(),
                    bedrockEngine = BedrockAddonEngine(),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
