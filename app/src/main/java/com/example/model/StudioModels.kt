package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MinecraftEdition(val label: String, val engineTitle: String, val exportHint: String) {
    JAVA(
        label = "Java Edition",
        engineTitle = "Java AI Mod Engine",
        exportHint = ".zip Gradle/Mod Workspace"
    ),
    BEDROCK(
        label = "Bedrock Edition",
        engineTitle = "Bedrock AI Add-on Engine",
        exportHint = ".mcaddon / .mcpack"
    )
}

enum class CompatibilityStatus(val displayLabel: String) {
    COMPATIBLE("Compatible"),
    PARTIALLY_COMPATIBLE("Partially Compatible"),
    REQUIRES_MODIFICATION("Requires Modification"),
    NOT_SUPPORTED("Not Supported")
}

enum class FeatureCategory(val label: String, val javaSupportedNote: String, val bedrockSupportedNote: String) {
    BLOCK("Custom Block", "Block & BlockItem Java registration + blockstate/model/loot", "Behavior block JSON + Resource blocks.json + geometry"),
    ITEM_TOOL("Item & Tool", "Item / Tiered Tool Java registration + item model", "Behavior item JSON + Resource item_texture.json"),
    WEAPON_ARMOR("Weapon & Armor", "Sword/Weapon/ArmorMaterial Java classes + attributes", "Behavior weapon/armor components + attachable JSON"),
    ENTITY_MOB("Entity & Mob", "EntityType, AI Goals, Renderer & Spawn Egg", "Entity BP behavior + RP client entity + geo + animations"),
    RECIPE("Crafting Recipe", "Shaped/Shapeless/Smelting JSON (1.12+) or GameRegistry (<=1.11)", "Bedrock recipe JSON (1.12+/1.16+)"),
    LOOT_TABLE("Loot Table", "Loot table JSON (1.9+) or drops in Block class (<=1.8)", "Bedrock loot_tables/*.json"),
    STRUCTURE("Structure", "Structure / Feature / Jigsaw NBT & worldgen JSON", "Feature / feature_rule JSON + .mcstructure notes"),
    CUSTOM_DIMENSION("Custom Dimension", "Dimension + DimensionType JSON / WorldProvider", "Not natively supported in Bedrock Add-ons (alternative offered)"),
    ANIMATION_MODEL("Animation & Model", "Entity/Block JSON models & keyframe animations", "Bedrock .geo.json + animation & controller JSON"),
    FULL_PROJECT("Full Mod / Add-on", "Complete multi-file Java mod workspace", "Complete Behavior Pack + Resource Pack bundle")
}

enum class SortOrderOption(val label: String) {
    NEWEST_FIRST("Newest First (2026 -> 2009)"),
    OLDEST_FIRST("Oldest First (2009 -> 2026)")
}

enum class StudioTab(val label: String) {
    STUDIO("Studio"),
    VERSIONS("Versions"),
    PROJECTS("Projects"),
    EXPORT("Export")
}

enum class BedrockExportMode(val extension: String, val label: String) {
    MCADDON(".mcaddon", "Combined Add-on (.mcaddon)"),
    MCPACK_BEHAVIOR(".mcpack", "Behavior Pack Only (.mcpack)"),
    MCPACK_RESOURCE(".mcpack", "Resource Pack Only (.mcpack)")
}

data class VersionEntry(
    val id: String,
    val edition: MinecraftEdition,
    val versionNumber: String,
    val updateName: String,
    val releaseDate: String,
    val sortOrder: Long,
    val historicalCategory: String,
    val releaseType: String,
    val compatibilityStatus: CompatibilityStatus,
    val supportedCapabilities: List<String>,
    val compatibleLoaders: List<String>,
    val supportedFileFormats: List<String>,
    val requiredRuntime: String,
    val adaptationNotes: String,
    val isUserAdded: Boolean = false
)

data class VersionCapabilityProfile(
    val version: VersionEntry,
    val selectedLoader: String,
    val supportedApis: List<String>,
    val blockRegistrationMethod: String,
    val entityRegistrationMethod: String,
    val scriptingSystem: String,
    val textureRequirement: String,
    val modelFormat: String,
    val manifestRequirement: String,
    val projectStructureSummary: String,
    val impossibleFeatures: List<String>
)

data class GeneratedFile(
    val id: String = "",
    val path: String = "",
    val content: String = "",
    val fileType: String = "TEXT",
    val binaryAsset: Boolean = false
)

enum class CheckSeverity {
    PASS, WARNING, ERROR
}

data class ValidationCheck(
    val category: String,
    val title: String,
    val detail: String,
    val severity: CheckSeverity,
    val affectedFilePath: String? = null
)

data class ValidationReport(
    val status: CompatibilityStatus,
    val summary: String,
    val checks: List<ValidationCheck>,
    val canExport: Boolean
)

data class EngineGenerationResult(
    val title: String,
    val modId: String,
    val adaptationNotes: String,
    val impossibleFeatureNotice: String,
    val files: List<GeneratedFile>
)

data class ModProject(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val modId: String = "",
    val edition: MinecraftEdition = MinecraftEdition.JAVA,
    val targetVersionId: String = "",
    val targetVersionNumber: String = "",
    val selectedLoader: String = "",
    val featureCategory: FeatureCategory = FeatureCategory.FULL_PROJECT,
    val prompt: String = "",
    val adaptationNotes: String = "",
    val impossibleFeatureNotice: String = "",
    val validationReport: ValidationReport? = null,
    val files: List<GeneratedFile> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreProjectDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val modId: String = "",
    val edition: String = "JAVA",
    val targetVersionId: String = "",
    val targetVersionNumber: String = "",
    val selectedLoader: String = "",
    val featureCategory: String = "FULL_PROJECT",
    val prompt: String = "",
    val adaptationNotes: String = "",
    val validationStatus: String = "COMPATIBLE",
    val validationSummary: String = "",
    val fileCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreProjectFileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val path: String = "",
    val content: String = "",
    val fileType: String = "TEXT",
    val binaryAsset: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreVersionUpdateDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val versionId: String = "",
    val edition: String = "JAVA",
    val versionNumber: String = "",
    val updateName: String = "",
    val releaseDate: String = "",
    val sortOrder: Long = 0L,
    val historicalCategory: String = "",
    val releaseType: String = "Release",
    val compatibilityStatus: String = "COMPATIBLE",
    val supportedCapabilities: String = "",
    val compatibleLoaders: String = "",
    val supportedFileFormats: String = "",
    val requiredRuntime: String = "",
    val adaptationNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Streaming(val statusText: String) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
