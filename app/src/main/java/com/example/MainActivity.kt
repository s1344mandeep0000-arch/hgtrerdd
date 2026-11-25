package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.configureAppCheck
import com.example.engine.ArchiveExporter
import com.example.model.MinecraftEdition
import com.example.model.StudioTab
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.EditionDualSelector
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.VersionsScreen
import com.example.ui.theme.CraftForgeTheme
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            CraftForgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener drives navigation */ })
    } else {
        key(user.uid) {
            CraftForgeApp(currentUserId = user.uid, userEmail = user.email.orEmpty())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CraftForgeApp(
    currentUserId: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: StudioViewModel = viewModel(
        key = currentUserId,
        factory = StudioViewModel.provideFactory(currentUserId)
    )

    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val activeEdition by viewModel.activeEdition.collectAsStateWithLifecycle()
    val allVersions by viewModel.allVersions.collectAsStateWithLifecycle()
    val filteredVersions by viewModel.filteredVersions.collectAsStateWithLifecycle()
    val selectedVersion by viewModel.selectedVersion.collectAsStateWithLifecycle()
    val selectedLoader by viewModel.selectedLoader.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val capabilityProfile by viewModel.capabilityProfile.collectAsStateWithLifecycle()
    val versionFilter by viewModel.versionFilter.collectAsStateWithLifecycle()
    val modIdInput by viewModel.modIdInput.collectAsStateWithLifecycle()
    val promptInput by viewModel.promptInput.collectAsStateWithLifecycle()
    val texturePromptInput by viewModel.texturePromptInput.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val selectedFilePath by viewModel.selectedFilePath.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val textureState by viewModel.textureState.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val savedProjectsState by viewModel.savedProjectsState.collectAsStateWithLifecycle()
    val bedrockExportMode by viewModel.bedrockExportMode.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToSafUri(context, uri)
        }
    }

    BackHandler(enabled = activeTab != StudioTab.STUDIO) {
        viewModel.selectTab(StudioTab.STUDIO)
    }

    CraftForgeTheme(edition = activeEdition) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = stringResource(R.string.studio_full_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${activeEdition.engineTitle} • ${userEmail.ifBlank { "Creator" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { signOut(credentialManager, scope) },
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sign out"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            StudioTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = tab == activeTab,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            StudioTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = tab == activeTab,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        // Independent Edition Selector always visible at top of workspace
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            EditionDualSelector(
                                activeEdition = activeEdition,
                                onSelectEdition = viewModel::selectEdition
                            )
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (activeTab) {
                                StudioTab.STUDIO -> {
                                    StudioScreen(
                                        activeEdition = activeEdition,
                                        capabilityProfile = capabilityProfile,
                                        selectedLoader = selectedLoader,
                                        selectedCategory = selectedCategory,
                                        modIdInput = modIdInput,
                                        promptInput = promptInput,
                                        texturePromptInput = texturePromptInput,
                                        activeProject = activeProject,
                                        selectedFilePath = selectedFilePath,
                                        generationState = generationState,
                                        textureState = textureState,
                                        cloudSyncState = cloudSyncState,
                                        onOpenVersionBrowser = { viewModel.selectTab(StudioTab.VERSIONS) },
                                        onSelectLoader = viewModel::selectLoader,
                                        onSelectCategory = viewModel::selectFeatureCategory,
                                        onModIdChange = viewModel::updateModIdInput,
                                        onPromptChange = viewModel::updatePromptInput,
                                        onTexturePromptChange = viewModel::updateTexturePromptInput,
                                        onGenerateAi = viewModel::generateWithActiveEngine,
                                        onLoadTemplate = viewModel::loadStarterTemplate,
                                        onGenerateTexture = viewModel::generateAiPixelTexture,
                                        onSelectFile = viewModel::selectFile,
                                        onEditFileContent = viewModel::updateFileContent,
                                        onFixFileWithAi = viewModel::fixFileWithAi,
                                        onSaveToCloud = viewModel::saveActiveProjectToCloud,
                                        onOpenExport = {
                                            viewModel.runValidation()
                                            viewModel.selectTab(StudioTab.EXPORT)
                                        }
                                    )
                                }
                                StudioTab.VERSIONS -> {
                                    val editionVersions = remember(allVersions, activeEdition) {
                                        allVersions.filter { it.edition == activeEdition }
                                    }
                                    VersionsScreen(
                                        activeEdition = activeEdition,
                                        allEditionVersions = editionVersions,
                                        filteredVersions = filteredVersions,
                                        selectedVersion = selectedVersion,
                                        filterState = versionFilter,
                                        onSearchChange = viewModel::updateSearchQuery,
                                        onCategoryFilterChange = viewModel::updateCategoryFilter,
                                        onReleaseTypeFilterChange = viewModel::updateReleaseTypeFilter,
                                        onLoaderFilterChange = viewModel::updateLoaderFilter,
                                        onToggleSortOrder = viewModel::toggleSortOrder,
                                        onSelectVersion = { version ->
                                            viewModel.selectVersion(version)
                                            viewModel.selectTab(StudioTab.STUDIO)
                                        },
                                        onAddCustomVersion = viewModel::addCustomVersionEntry,
                                        onImportJsonCatalog = viewModel::importCatalogJson
                                    )
                                }
                                StudioTab.PROJECTS -> {
                                    ProjectsScreen(
                                        projectsState = savedProjectsState,
                                        onOpenProject = viewModel::openSavedProject,
                                        onDeleteProject = viewModel::deleteSavedProject,
                                        onGoToStudio = { viewModel.selectTab(StudioTab.STUDIO) }
                                    )
                                }
                                StudioTab.EXPORT -> {
                                    val suggestedName = activeProject?.let {
                                        ArchiveExporter.suggestedFileName(it, bedrockExportMode)
                                    } ?: "craftforge_export.zip"
                                    ExportScreen(
                                        activeProject = activeProject,
                                        bedrockExportMode = bedrockExportMode,
                                        suggestedFileName = suggestedName,
                                        onSelectBedrockExportMode = viewModel::selectBedrockExportMode,
                                        onRerunValidation = { viewModel.runValidation() },
                                        onFixFileWithAi = { path ->
                                            viewModel.fixFileWithAi(path)
                                            viewModel.selectTab(StudioTab.STUDIO)
                                            viewModel.selectFile(path)
                                        },
                                        onSaveArchive = {
                                            createDocumentLauncher.launch(suggestedName)
                                        },
                                        onShareArchive = {
                                            viewModel.buildShareArchiveIntent(context) { intent ->
                                                context.startActivity(intent)
                                            }
                                        },
                                        onBackToStudio = { viewModel.selectTab(StudioTab.STUDIO) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: StudioTab) = when (tab) {
    StudioTab.STUDIO -> Icons.Filled.Build
    StudioTab.VERSIONS -> Icons.Filled.History
    StudioTab.PROJECTS -> Icons.Filled.Folder
    StudioTab.EXPORT -> Icons.Filled.Verified
}
