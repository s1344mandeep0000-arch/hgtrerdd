package com.example.engine

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.model.EngineGenerationResult
import com.example.model.FeatureCategory
import com.example.model.GeneratedFile
import com.example.model.VersionCapabilityProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

class BedrockAddonEngine {

    private val addonOutputSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Human-readable title of the Bedrock Add-on"),
            "packNamespace" to Schema.string(description = "Lowercase namespace prefix for identifiers (e.g. craftforge)"),
            "adaptationNotes" to Schema.string(description = "Technical summary of Bedrock format_version, min_engine_version, and API compatibility"),
            "impossibleFeatureNotice" to Schema.string(description = "Empty string if fully possible; otherwise explanation of unsupported feature and compatible Bedrock alternative"),
            "files" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "path" to Schema.string(description = "Relative pack path starting with behavior_pack/ or resource_pack/"),
                        "fileType" to Schema.string(description = "File type: JSON, JS, LANG, or TEXT"),
                        "content" to Schema.string(description = "Complete valid JSON, Script API JS, or text file content")
                    )
                ),
                description = "Complete list of Behavior Pack and Resource Pack files"
            )
        )
    )

    private val fileFixSchema = Schema.obj(
        properties = mapOf(
            "fixedContent" to Schema.string(description = "Corrected complete Bedrock JSON or Script API JS content"),
            "explanation" to Schema.string(description = "Short summary of the Bedrock schema fix")
        )
    )

    private val addonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = addonOutputSchema
                temperature = 0.25f
            },
            systemInstruction = content {
                text(
                    """
                    You are the MINECRAFT BEDROCK EDITION ADD-ON GENERATION ENGINE.
                    You generate ONLY authentic Minecraft Bedrock Edition Behavior Packs (behavior_pack/...) and Resource Packs (resource_pack/...).
                    STRICT ISOLATION RULES:
                    1. NEVER generate Java Edition files (never output .java, build.gradle, mods.toml, fabric.mod.json, or Forge/Fabric APIs).
                    2. Use valid UUID v4 strings in behavior_pack/manifest.json and resource_pack/manifest.json, linking BP and RP via dependencies when both exist.
                    3. Match the exact target Bedrock/PE version's format_version, min_engine_version, and Script API availability provided in the prompt.
                    4. Only generate scripts/main.js with @minecraft/server when the target version is Bedrock 1.19.80+.
                    5. If the user asks for Custom Dimensions or unsupported historical features (such as custom blocks in PE 0.16.0 or pre-add-on PE 0.8.1), populate impossibleFeatureNotice explaining why and provide a compatible Bedrock alternative.
                    6. Every .json file must be strictly valid JSON (no trailing commas or comments).
                    """.trimIndent()
                )
            }
        )
    }

    private val fixModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = fileFixSchema
                temperature = 0.15f
            }
        )
    }

    private val textureModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    suspend fun generateAddon(
        prompt: String,
        requestedNamespace: String,
        category: FeatureCategory,
        capability: VersionCapabilityProfile
    ): Result<EngineGenerationResult> {
        return try {
            val safeNs = requestedNamespace.lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .trim('_')
                .ifBlank { "craftforge" }
                .take(24)

            val bpHeaderUuid = UUID.randomUUID().toString()
            val bpModuleUuid = UUID.randomUUID().toString()
            val rpHeaderUuid = UUID.randomUUID().toString()
            val rpModuleUuid = UUID.randomUUID().toString()

            val fullPrompt = buildString {
                appendLine("=== BEDROCK EDITION ADD-ON GENERATION REQUEST ===")
                appendLine("Target Bedrock Version: ${capability.version.versionNumber} (${capability.version.updateName})")
                appendLine("Release Date: ${capability.version.releaseDate} | Category: ${capability.version.historicalCategory}")
                appendLine("Compatibility Status: ${capability.version.compatibilityStatus.displayLabel}")
                appendLine("Selected Runtime/Loader: ${capability.selectedLoader}")
                appendLine("Required Runtime: ${capability.version.requiredRuntime}")
                appendLine("Supported APIs: ${capability.supportedApis.joinToString("; ")}")
                appendLine("Block Schema: ${capability.blockRegistrationMethod}")
                appendLine("Entity Schema: ${capability.entityRegistrationMethod}")
                appendLine("Script API Support: ${capability.scriptingSystem}")
                appendLine("Texture Layout: ${capability.textureRequirement}")
                appendLine("Model Format: ${capability.modelFormat}")
                appendLine("Manifest Requirement: ${capability.manifestRequirement}")
                appendLine("Pre-generated Valid UUIDs (use these exact UUIDs in manifests):")
                appendLine("  BP header uuid: $bpHeaderUuid | BP module uuid: $bpModuleUuid")
                appendLine("  RP header uuid: $rpHeaderUuid | RP module uuid: $rpModuleUuid")
                if (capability.impossibleFeatures.isNotEmpty()) {
                    appendLine("UNSUPPORTED IN THIS VERSION (DO NOT USE):")
                    capability.impossibleFeatures.forEach { appendLine("- $it") }
                }
                appendLine("Feature Focus: ${category.label} (${category.bedrockSupportedNote})")
                appendLine("Pack Namespace: $safeNs")
                appendLine("User Add-on Specification: $prompt")
                appendLine()
                appendLine("Generate complete Bedrock files (6 to 12 key files under behavior_pack/ and resource_pack/, including valid manifest.json files, blocks/items/entities/recipes/loot_tables/animations/models/sounds/scripts as appropriate for this version).")
            }

            val response = withTimeout(60_000L) {
                addonModel.generateContent(fullPrompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Bedrock engine returned an empty response.")
            val json = JSONObject(rawJson)
            val title = json.optString("title", "Bedrock Add-on")
            val ns = json.optString("packNamespace", safeNs)
                .lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .ifBlank { safeNs }
            val adaptationNotes = json.optString("adaptationNotes", capability.version.adaptationNotes)
            val impossibleNotice = json.optString("impossibleFeatureNotice", "")

            val filesArray = json.optJSONArray("files")
                ?: throw IllegalStateException("Bedrock engine response missing files array.")
            val files = mutableListOf<GeneratedFile>()
            for (i in 0 until filesArray.length()) {
                val obj = filesArray.getJSONObject(i)
                val path = obj.optString("path").trimStart('/')
                val content = obj.optString("content")
                val fileType = obj.optString("fileType", "JSON").uppercase()
                if (path.isNotBlank()) {
                    files.add(
                        GeneratedFile(
                            id = "bedrock_${i}_${path.hashCode().toUInt()}",
                            path = path,
                            content = content,
                            fileType = fileType,
                            binaryAsset = false
                        )
                    )
                }
            }
            require(files.isNotEmpty()) { "Bedrock engine generated 0 files." }

            Result.success(
                EngineGenerationResult(
                    title = title,
                    modId = ns,
                    adaptationNotes = adaptationNotes,
                    impossibleFeatureNotice = impossibleNotice,
                    files = files
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Bedrock add-on generation timed out", e)
            Result.failure(IllegalStateException("Bedrock Add-on Engine timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Bedrock add-on generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generatePixelTexture(
        texturePrompt: String,
        assetName: String,
        category: FeatureCategory
    ): Result<GeneratedFile> {
        return try {
            val cleanName = assetName.lowercase().replace(Regex("[^a-z0-9_]"), "_").ifBlank { "custom_asset" }
            val folder = when (category) {
                FeatureCategory.BLOCK -> "blocks"
                FeatureCategory.ENTITY_MOB -> "entity"
                else -> "items"
            }
            val targetPath = "resource_pack/textures/$folder/$cleanName.png"

            val fullPrompt = "Minecraft Bedrock Edition 16x16 pixel art sprite texture for $texturePrompt. " +
                "Crisp blocky pixel art, flat game texture tile, sharp pixels, no text."

            val response = withTimeout(60_000L) {
                textureModel.generateContent(fullPrompt)
            }
            val bitmap = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()?.image
                ?: throw IllegalStateException("Texture model returned no image.")

            val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, false)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val base64Png = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            Result.success(
                GeneratedFile(
                    id = "tex_${targetPath.hashCode().toUInt()}",
                    path = targetPath,
                    content = base64Png,
                    fileType = "PNG",
                    binaryAsset = true
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Bedrock texture generation timed out", e)
            Result.failure(IllegalStateException("Texture generation timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Bedrock texture generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun correctFileWithAi(
        file: GeneratedFile,
        validationIssues: String,
        capability: VersionCapabilityProfile
    ): Result<Pair<GeneratedFile, String>> {
        return try {
            val prompt = buildString {
                appendLine("Fix this Minecraft Bedrock Edition file for ${capability.version.versionNumber}.")
                appendLine("Manifest Requirement: ${capability.manifestRequirement}")
                appendLine("Script API Support: ${capability.scriptingSystem}")
                appendLine("Detected Validation Issues:")
                appendLine(validationIssues)
                appendLine("File Path: ${file.path}")
                appendLine("Current Content:")
                appendLine(file.content)
            }
            val response = withTimeout(60_000L) {
                fixModel.generateContent(prompt)
            }
            val json = JSONObject(response.text ?: throw IllegalStateException("Empty fix response."))
            val fixed = json.getString("fixedContent")
            val explanation = json.optString("explanation", "File repaired for Bedrock ${capability.version.versionNumber}.")
            Result.success(file.copy(content = fixed) to explanation)
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("AI error correction timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Bedrock AI fix failed", e)
            Result.failure(e)
        }
    }

    fun buildStarterTemplate(
        namespace: String,
        capability: VersionCapabilityProfile
    ): EngineGenerationResult {
        val cleanNs = namespace.lowercase().replace(Regex("[^a-z0-9_]"), "_").ifBlank { "craftforge" }
        val sort = capability.version.sortOrder
        if (sort < 20161021L) {
            return EngineGenerationResult(
                title = "$cleanNs Historical Note",
                modId = cleanNs,
                adaptationNotes = capability.version.adaptationNotes,
                impossibleFeatureNotice = "Official .mcaddon / .mcpack Behavior and Resource Packs were introduced in PE 0.16.0 (2016) and are unsupported in ${capability.version.versionNumber}.",
                files = listOf(
                    GeneratedFile(
                        id = "pe_historical_note",
                        path = "PE_COMPATIBILITY_NOTICE.txt",
                        fileType = "TEXT",
                        content = "Target: ${capability.version.versionNumber}\n${capability.version.adaptationNotes}"
                    )
                )
            )
        }

        val engineVersion = parseMinEngineVersion(capability.version.versionNumber)
        val bpUuid = UUID.randomUUID().toString()
        val bpModUuid = UUID.randomUUID().toString()
        val rpUuid = UUID.randomUUID().toString()
        val rpModUuid = UUID.randomUUID().toString()
        val formatVer = if (sort < 20190319L) 1 else 2

        val files = mutableListOf(
            GeneratedFile(
                id = "bp_manifest",
                path = "behavior_pack/manifest.json",
                fileType = "JSON",
                content = """
                    {
                      "format_version": $formatVer,
                      "header": {
                        "name": "${cleanNs}_bp",
                        "description": "CraftForge Bedrock Behavior Pack (${capability.version.versionNumber})",
                        "uuid": "$bpUuid",
                        "version": [1, 0, 0],
                        "min_engine_version": $engineVersion
                      },
                      "modules": [
                        {
                          "type": "data",
                          "uuid": "$bpModUuid",
                          "version": [1, 0, 0]
                        }
                      ],
                      "dependencies": [
                        {
                          "uuid": "$rpUuid",
                          "version": [1, 0, 0]
                        }
                      ]
                    }
                """.trimIndent()
            ),
            GeneratedFile(
                id = "rp_manifest",
                path = "resource_pack/manifest.json",
                fileType = "JSON",
                content = """
                    {
                      "format_version": $formatVer,
                      "header": {
                        "name": "${cleanNs}_rp",
                        "description": "CraftForge Bedrock Resource Pack (${capability.version.versionNumber})",
                        "uuid": "$rpUuid",
                        "version": [1, 0, 0],
                        "min_engine_version": $engineVersion
                      },
                      "modules": [
                        {
                          "type": "resources",
                          "uuid": "$rpModUuid",
                          "version": [1, 0, 0]
                        }
                      ]
                    }
                """.trimIndent()
            ),
            GeneratedFile(
                id = "rp_lang",
                path = "resource_pack/texts/en_US.lang",
                fileType = "TEXT",
                content = "item.${cleanNs}:starter_crystal.name=Starter Crystal\n"
            )
        )

        if (sort >= 20201117L) {
            files.add(
                GeneratedFile(
                    id = "bp_item",
                    path = "behavior_pack/items/starter_crystal.json",
                    fileType = "JSON",
                    content = """
                        {
                          "format_version": "1.20.80",
                          "minecraft:item": {
                            "description": {
                              "identifier": "${cleanNs}:starter_crystal",
                              "menu_category": {
                                "category": "items"
                              }
                            },
                            "components": {
                              "minecraft:icon": "starter_crystal",
                              "minecraft:max_stack_size": 64
                            }
                          }
                        }
                    """.trimIndent()
                )
            )
            files.add(
                GeneratedFile(
                    id = "rp_item_tex",
                    path = "resource_pack/textures/item_texture.json",
                    fileType = "JSON",
                    content = """
                        {
                          "resource_pack_name": "${cleanNs}_rp",
                          "texture_name": "atlas.items",
                          "texture_data": {
                            "starter_crystal": {
                              "textures": "textures/items/starter_crystal"
                            }
                          }
                        }
                    """.trimIndent()
                )
            )
        }

        return EngineGenerationResult(
            title = "${cleanNs} Bedrock Template",
            modId = cleanNs,
            adaptationNotes = "Loaded Bedrock BP+RP template for ${capability.version.versionNumber}.",
            impossibleFeatureNotice = "",
            files = files
        )
    }

    private fun parseMinEngineVersion(versionNumber: String): String {
        val nums = Regex("\\d+").findAll(versionNumber).map { it.value.toIntOrNull() ?: 0 }.toList()
        val major = nums.getOrElse(0) { 1 }
        val minor = nums.getOrElse(1) { 21 }
        val patch = nums.getOrElse(2) { 0 }
        return "[$major, $minor, $patch]"
    }

    private companion object {
        const val TAG = "BedrockAddonEngine"
    }
}
