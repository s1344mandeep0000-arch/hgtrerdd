package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.engine.BedrockAddonEngine
import com.example.engine.CompatibilityValidator
import com.example.engine.JavaModEngine
import com.example.engine.VersionCapabilityAnalyzer
import com.example.model.BedrockExportMode
import com.example.model.CompatibilityStatus
import com.example.model.FeatureCategory
import com.example.model.MinecraftEdition
import com.example.model.ModProject
import com.example.model.UiState
import com.example.model.VersionEntry
import com.example.ui.components.EditionDualSelector
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.VersionsScreen
import com.example.ui.theme.CraftForgeTheme
import com.example.ui.viewmodel.VersionFilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleJavaVersion = VersionEntry(
        id = "java_26_3_1",
        edition = MinecraftEdition.JAVA,
        versionNumber = "26.3.1",
        updateName = "Wilderness Bound",
        releaseDate = "2026-10-01",
        sortOrder = 20261001L,
        historicalCategory = "Year-Based Drop (2026)",
        releaseType = "Release",
        compatibilityStatus = CompatibilityStatus.COMPATIBLE,
        supportedCapabilities = listOf("Blocks", "Items", "Entities", "Recipes"),
        compatibleLoaders = listOf("NeoForge (26.3.x)", "Fabric"),
        supportedFileFormats = listOf(".java", ".json", ".png", ".toml"),
        requiredRuntime = "Java 21",
        adaptationNotes = "Modern Java 21 release"
    )

    private val sampleBedrockVersion = VersionEntry(
        id = "bedrock_26_63",
        edition = MinecraftEdition.BEDROCK,
        versionNumber = "26.63",
        updateName = "November 2026 Update",
        releaseDate = "2026-11-11",
        sortOrder = 20261111L,
        historicalCategory = "Year-Based Drop (2026)",
        releaseType = "Release",
        compatibilityStatus = CompatibilityStatus.COMPATIBLE,
        supportedCapabilities = listOf("Behavior Packs", "Resource Packs", "Script API"),
        compatibleLoaders = listOf("Native Add-on", "Script API"),
        supportedFileFormats = listOf(".mcaddon", ".mcpack", ".json", ".js", ".png"),
        requiredRuntime = "Bedrock Engine 26.63",
        adaptationNotes = "Latest November 2026 Bedrock release"
    )

    @Test
    fun `app name matches Minecraft AI Mod Maker Studio`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Minecraft AI Mod Maker Studio", appName)
    }

    @Test
    fun `phone viewport renders dual edition selector and studio workspace cleanly`() {
        val profile = VersionCapabilityAnalyzer.analyze(sampleJavaVersion, "NeoForge (26.3.x)")
        val template = JavaModEngine().buildStarterTemplate(
            modId = "emerald_forge",
            capability = profile
        )
        val project = ModProject(
            id = "proj_1",
            title = template.title,
            modId = template.modId,
            edition = MinecraftEdition.JAVA,
            targetVersionId = sampleJavaVersion.id,
            targetVersionNumber = sampleJavaVersion.versionNumber,
            selectedLoader = "NeoForge (26.3.x)",
            featureCategory = FeatureCategory.BLOCK,
            prompt = "Create an emerald forge block",
            adaptationNotes = template.adaptationNotes,
            files = template.files
        )
        val report = CompatibilityValidator.validate(project, profile)
        val validatedProject = project.copy(validationReport = report)

        var selectedEdition = MinecraftEdition.JAVA

        composeTestRule.setContent {
            CraftForgeTheme(edition = selectedEdition) {
                Box(modifier = Modifier.size(width = 360.dp, height = 780.dp)) {
                    StudioScreen(
                        activeEdition = selectedEdition,
                        capabilityProfile = profile,
                        selectedLoader = "NeoForge (26.3.x)",
                        selectedCategory = FeatureCategory.BLOCK,
                        modIdInput = "emerald_forge",
                        promptInput = "Create an emerald forge block",
                        texturePromptInput = "",
                        activeProject = validatedProject,
                        selectedFilePath = validatedProject.files.firstOrNull()?.path,
                        generationState = UiState.Idle,
                        textureState = UiState.Idle,
                        cloudSyncState = UiState.Idle,
                        onOpenVersionBrowser = {},
                        onSelectLoader = {},
                        onSelectCategory = {},
                        onModIdChange = {},
                        onPromptChange = {},
                        onTexturePromptChange = {},
                        onGenerateAi = {},
                        onLoadTemplate = {},
                        onGenerateTexture = {},
                        onSelectFile = {},
                        onEditFileContent = { _, _ -> },
                        onFixFileWithAi = {},
                        onSaveToCloud = {},
                        onOpenExport = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("change_version_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("generate_mod_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("load_template_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_cloud_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("validate_export_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `dual edition selector switches between Java and Bedrock`() {
        var activeEdition = MinecraftEdition.JAVA
        composeTestRule.setContent {
            CraftForgeTheme(edition = activeEdition) {
                Box(modifier = Modifier.size(width = 360.dp, height = 100.dp)) {
                    EditionDualSelector(
                        activeEdition = activeEdition,
                        onSelectEdition = { activeEdition = it }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edition_selector_java").assertIsDisplayed()
        composeTestRule.onNodeWithTag("edition_selector_bedrock").assertIsDisplayed().performClick()
        assertEquals(MinecraftEdition.BEDROCK, activeEdition)
    }

    @Test
    fun `versions projects and export screens render cleanly on phone viewport`() {
        val profile = VersionCapabilityAnalyzer.analyze(sampleBedrockVersion, "Script API")
        val template = BedrockAddonEngine().buildStarterTemplate(
            namespace = "emerald_pack",
            capability = profile
        )
        val project = ModProject(
            id = "proj_bedrock",
            title = template.title,
            modId = template.modId,
            edition = MinecraftEdition.BEDROCK,
            targetVersionId = sampleBedrockVersion.id,
            targetVersionNumber = sampleBedrockVersion.versionNumber,
            selectedLoader = "Script API",
            featureCategory = FeatureCategory.ITEM_TOOL,
            prompt = "Emerald wand",
            adaptationNotes = template.adaptationNotes,
            files = template.files
        )
        val validated = project.copy(validationReport = CompatibilityValidator.validate(project, profile))

        composeTestRule.setContent {
            CraftForgeTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 780.dp)) {
                    VersionsScreen(
                        activeEdition = MinecraftEdition.BEDROCK,
                        allEditionVersions = listOf(sampleBedrockVersion),
                        filteredVersions = listOf(sampleBedrockVersion),
                        selectedVersion = sampleBedrockVersion,
                        filterState = VersionFilterState(),
                        onSearchChange = {},
                        onCategoryFilterChange = {},
                        onReleaseTypeFilterChange = {},
                        onLoaderFilterChange = {},
                        onToggleSortOrder = {},
                        onSelectVersion = {},
                        onAddCustomVersion = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
                        onImportJsonCatalog = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("26.63").assertIsDisplayed()
        assertTrue(validated.validationReport?.canExport == true)
    }

    @Test
    fun `empty projects screen renders cleanly on phone viewport`() {
        composeTestRule.setContent {
            CraftForgeTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 780.dp)) {
                    ProjectsScreen(
                        projectsState = UiState.Success(emptyList()),
                        onOpenProject = {},
                        onDeleteProject = {},
                        onGoToStudio = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No Cloud Projects Yet").assertIsDisplayed()
    }

    @Test
    fun `export screen renders cleanly on phone viewport`() {
        composeTestRule.setContent {
            CraftForgeTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 780.dp)) {
                    ExportScreen(
                        activeProject = null,
                        bedrockExportMode = BedrockExportMode.MCADDON,
                        suggestedFileName = "test.mcaddon",
                        onSelectBedrockExportMode = {},
                        onRerunValidation = {},
                        onFixFileWithAi = {},
                        onSaveArchive = {},
                        onShareArchive = {},
                        onBackToStudio = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No Active Project to Validate").assertIsDisplayed()
    }
}
