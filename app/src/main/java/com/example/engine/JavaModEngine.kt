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

class JavaModEngine {

    private val modOutputSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Human-readable title of the Java Edition mod"),
            "modId" to Schema.string(description = "Lowercase alphanumeric/underscore mod ID (e.g. obsidian_forge)"),
            "adaptationNotes" to Schema.string(description = "Technical summary of mappings, loader APIs, and version adaptations used"),
            "impossibleFeatureNotice" to Schema.string(description = "Empty string if fully possible; otherwise explanation of why a requested feature is impossible in this version and the compatible alternative generated"),
            "files" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "path" to Schema.string(description = "Relative Java Gradle/mod workspace path"),
                        "fileType" to Schema.string(description = "File type: JAVA, GRADLE, TOML, JSON, PROPERTIES, or TEXT"),
                        "content" to Schema.string(description = "Complete valid source code, JSON, TOML, or Gradle file content")
                    )
                ),
                description = "Complete list of generated Java mod files"
            )
        )
    )

    private val fileFixSchema = Schema.obj(
        properties = mapOf(
            "fixedContent" to Schema.string(description = "Corrected complete file content"),
            "explanation" to Schema.string(description = "Short note explaining what was fixed for the target Java Minecraft version")
        )
    )

    private val codeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = modOutputSchema
                temperature = 0.25f
            },
            systemInstruction = content {
                text(
                    """
                    You are the MINECRAFT JAVA EDITION MOD GENERATION ENGINE.
                    You generate ONLY authentic Minecraft Java Edition mods (Java source code, Gradle build configs, TOML/JSON mod metadata, blockstates, models, recipes, loot tables, dimensions, structures).
                    STRICT ISOLATION RULES:
                    1. NEVER generate Bedrock Edition Behavior Pack or Resource Pack files (never output manifest.json with format_version/uuid, .mcaddon, .mcpack, or @minecraft/server scripts).
                    2. Strictly obey the target Minecraft Java version's historical capabilities, Java runtime, mappings, and mod loader APIs provided in the prompt.
                    3. Never apply modern APIs (like DeferredRegister, ResourceLocation.fromNamespaceAndPath, DataComponents, JSON recipes, or Fabric/NeoForge) to historical versions where they did not exist.
                    4. If a requested feature is impossible in the selected historical version, populate impossibleFeatureNotice explaining why and generate a compatible alternative or honest historical reference class.
                    5. Ensure all generated Java classes compile cleanly in concept (balanced braces, proper package declarations, imports, and registry patterns) and all JSON files are valid JSON.
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

    suspend fun generateMod(
        prompt: String,
        requestedModId: String,
        category: FeatureCategory,
        capability: VersionCapabilityProfile
    ): Result<EngineGenerationResult> {
        return try {
            val safeModId = requestedModId.lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .trim('_')
                .ifBlank { "craftforge_mod" }
                .take(32)

            val fullPrompt = buildString {
                appendLine("=== JAVA EDITION MOD GENERATION REQUEST ===")
                appendLine("Target Minecraft Java Version: ${capability.version.versionNumber} (${capability.version.updateName})")
                appendLine("Release Date: ${capability.version.releaseDate} | Category: ${capability.version.historicalCategory}")
                appendLine("Compatibility Status: ${capability.version.compatibilityStatus.displayLabel}")
                appendLine("Selected Mod Loader: ${capability.selectedLoader}")
                appendLine("Required Runtime & Mappings: ${capability.version.requiredRuntime}")
                appendLine("Supported APIs: ${capability.supportedApis.joinToString("; ")}")
                appendLine("Block Registration: ${capability.blockRegistrationMethod}")
                appendLine("Entity Registration: ${capability.entityRegistrationMethod}")
                appendLine("Texture Layout: ${capability.textureRequirement}")
                appendLine("Model Format: ${capability.modelFormat}")
                appendLine("Manifest Requirement: ${capability.manifestRequirement}")
                appendLine("Project Structure: ${capability.projectStructureSummary}")
                if (capability.impossibleFeatures.isNotEmpty()) {
                    appendLine("IMPOSSIBLE IN THIS VERSION (DO NOT USE):")
                    capability.impossibleFeatures.forEach { appendLine("- $it") }
                }
                appendLine("Feature Focus: ${category.label} (${category.javaSupportedNote})")
                appendLine("Preferred Mod ID: $safeModId")
                appendLine("User Mod Specification: $prompt")
                appendLine()
                appendLine("Generate a complete, well-structured set of Java Edition files (6 to 12 key files including main @Mod/ModInitializer class, registration class, build.gradle, manifest, and JSON resources/models/recipes/loot tables where supported by this exact version).")
            }

            val response = withTimeout(60_000L) {
                codeModel.generateContent(fullPrompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Java engine returned an empty response.")
            val json = JSONObject(rawJson)
            val title = json.optString("title", "Java Mod")
            val modId = json.optString("modId", safeModId)
                .lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .ifBlank { safeModId }
            val adaptationNotes = json.optString("adaptationNotes", capability.version.adaptationNotes)
            val impossibleNotice = json.optString("impossibleFeatureNotice", "")

            val filesArray = json.optJSONArray("files")
                ?: throw IllegalStateException("Java engine response missing files array.")
            val files = mutableListOf<GeneratedFile>()
            for (i in 0 until filesArray.length()) {
                val obj = filesArray.getJSONObject(i)
                val path = obj.optString("path").trimStart('/')
                val content = obj.optString("content")
                val fileType = obj.optString("fileType", "TEXT").uppercase()
                if (path.isNotBlank()) {
                    files.add(
                        GeneratedFile(
                            id = "java_${i}_${path.hashCode().toUInt()}",
                            path = path,
                            content = content,
                            fileType = fileType,
                            binaryAsset = false
                        )
                    )
                }
            }
            require(files.isNotEmpty()) { "Java engine generated 0 files." }

            Result.success(
                EngineGenerationResult(
                    title = title,
                    modId = modId,
                    adaptationNotes = adaptationNotes,
                    impossibleFeatureNotice = impossibleNotice,
                    files = files
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Java mod generation timed out", e)
            Result.failure(IllegalStateException("Java Mod Engine timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Java mod generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generatePixelTexture(
        texturePrompt: String,
        modId: String,
        assetName: String,
        category: FeatureCategory,
        capability: VersionCapabilityProfile
    ): Result<GeneratedFile> {
        return try {
            val cleanName = assetName.lowercase().replace(Regex("[^a-z0-9_]"), "_").ifBlank { "custom_asset" }
            val folder = when {
                capability.version.sortOrder < 20190423L -> when (category) {
                    FeatureCategory.BLOCK -> "blocks"
                    FeatureCategory.ENTITY_MOB -> "entity"
                    else -> "items"
                }
                else -> when (category) {
                    FeatureCategory.BLOCK -> "block"
                    FeatureCategory.ENTITY_MOB -> "entity"
                    else -> "item"
                }
            }
            val targetPath = "src/main/resources/assets/$modId/textures/$folder/$cleanName.png"

            val fullPrompt = "Minecraft Java Edition 16x16 pixel art sprite texture for $texturePrompt. " +
                "Crisp blocky pixel art, top-down/front flat sprite tile, sharp unblurred pixels, game texture asset, no text."

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
            Log.e(TAG, "Java texture generation timed out", e)
            Result.failure(IllegalStateException("Texture generation timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Java texture generation failed", e)
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
                appendLine("Fix this Minecraft Java Edition file for ${capability.version.versionNumber} (${capability.selectedLoader}).")
                appendLine("Manifest Requirement: ${capability.manifestRequirement}")
                appendLine("Block API: ${capability.blockRegistrationMethod}")
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
            val explanation = json.optString("explanation", "File repaired for ${capability.version.versionNumber}.")
            Result.success(file.copy(content = fixed) to explanation)
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("AI error correction timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Java AI fix failed", e)
            Result.failure(e)
        }
    }

    fun buildStarterTemplate(
        modId: String,
        capability: VersionCapabilityProfile
    ): EngineGenerationResult {
        val cleanId = modId.lowercase().replace(Regex("[^a-z0-9_]"), "_").ifBlank { "craftforge_mod" }
        val className = cleanId.split("_").joinToString("") { part ->
            part.replaceFirstChar { it.uppercaseChar() }
        }.ifBlank { "CraftForgeMod" }
        val loader = capability.selectedLoader
        val sort = capability.version.sortOrder

        val files = mutableListOf<GeneratedFile>()
        if (sort < 20101203L) {
            files.add(
                GeneratedFile(
                    id = "java_hist_note",
                    path = "HISTORICAL_NOTES_${cleanId}.txt",
                    fileType = "TEXT",
                    content = """
                        CraftForge Java Engine — Historical Compatibility Notice
                        Target Version: ${capability.version.versionNumber} (${capability.version.releaseDate})
                        Status: ${capability.version.compatibilityStatus.displayLabel}
                        
                        ${capability.version.adaptationNotes}
                        
                        Recommended Alternative: Target Alpha v1.2.6+, Beta 1.7.3, 1.7.10, 1.12.2, 1.20.1, or 26.3 for loader-backed modding.
                    """.trimIndent()
                )
            )
            return EngineGenerationResult(
                title = "$className (Historical Ref)",
                modId = cleanId,
                adaptationNotes = capability.version.adaptationNotes,
                impossibleFeatureNotice = "External mod loaders did not exist in ${capability.version.versionNumber}. Generated historical reference notes instead.",
                files = files
            )
        }

        if (loader.contains("Fabric", ignoreCase = true)) {
            files.add(
                GeneratedFile(
                    id = "java_fabric_main",
                    path = "src/main/java/com/example/$cleanId/$className.java",
                    fileType = "JAVA",
                    content = """
                        package com.example.$cleanId;

                        import net.fabricmc.api.ModInitializer;
                        import net.minecraft.registry.Registries;
                        import net.minecraft.registry.Registry;
                        import net.minecraft.util.Identifier;
                        import net.minecraft.item.Item;
                        import org.slf4j.Logger;
                        import org.slf4j.LoggerFactory;

                        public class $className implements ModInitializer {
                            public static final String MOD_ID = "$cleanId";
                            public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

                            public static final Item STARTER_CRYSTAL = Registry.register(
                                Registries.ITEM,
                                Identifier.of(MOD_ID, "starter_crystal"),
                                new Item(new Item.Settings())
                            );

                            @Override
                            public void onInitialize() {
                                LOGGER.info("Initialized {} for Minecraft {}", MOD_ID, "${capability.version.versionNumber}");
                            }
                        }
                    """.trimIndent()
                )
            )
            files.add(
                GeneratedFile(
                    id = "java_fabric_json",
                    path = "src/main/resources/fabric.mod.json",
                    fileType = "JSON",
                    content = """
                        {
                          "schemaVersion": 1,
                          "id": "$cleanId",
                          "version": "1.0.0",
                          "name": "$className",
                          "description": "CraftForge Java Engine Fabric starter for ${capability.version.versionNumber}",
                          "entrypoints": {
                            "main": ["com.example.$cleanId.$className"]
                          },
                          "depends": {
                            "fabricloader": ">=0.16.0",
                            "minecraft": "~${capability.version.versionNumber}"
                          }
                        }
                    """.trimIndent()
                )
            )
        } else {
            val isNeo = loader.contains("NeoForge", ignoreCase = true)
            val pkg = if (isNeo) "net.neoforged" else "net.minecraftforge"
            val manifestPath = if (isNeo && sort >= 20240429L) {
                "src/main/resources/META-INF/neoforge.mods.toml"
            } else if (sort < 20190719L) {
                "src/main/resources/mcmod.info"
            } else {
                "src/main/resources/META-INF/mods.toml"
            }

            files.add(
                GeneratedFile(
                    id = "java_forge_main",
                    path = "src/main/java/com/example/$cleanId/$className.java",
                    fileType = "JAVA",
                    content = if (sort < 20190719L) {
                        """
                        package com.example.$cleanId;

                        import net.minecraftforge.fml.common.Mod;
                        import net.minecraftforge.fml.common.event.FMLInitializationEvent;

                        @Mod(modid = $className.MODID, name = "$className", version = "1.0.0")
                        public class $className {
                            public static final String MODID = "$cleanId";

                            @Mod.EventHandler
                            public void init(FMLInitializationEvent event) {
                                System.out.println("Initialized $cleanId for ${capability.version.versionNumber}");
                            }
                        }
                        """.trimIndent()
                    } else {
                        """
                        package com.example.$cleanId;

                        import $pkg.fml.common.Mod;

                        @Mod($className.MOD_ID)
                        public class $className {
                            public static final String MOD_ID = "$cleanId";

                            public $className() {
                                // Registry setup for ${capability.version.versionNumber} ($loader)
                            }
                        }
                        """.trimIndent()
                    }
                )
            )
            files.add(
                GeneratedFile(
                    id = "java_manifest",
                    path = manifestPath,
                    fileType = if (manifestPath.endsWith(".json") || manifestPath.endsWith(".info")) "JSON" else "TOML",
                    content = if (manifestPath.endsWith("mcmod.info")) {
                        """
                        [
                          {
                            "modid": "$cleanId",
                            "name": "$className",
                            "description": "Generated for Minecraft ${capability.version.versionNumber}",
                            "version": "1.0.0",
                            "mcversion": "${capability.version.versionNumber}"
                          }
                        ]
                        """.trimIndent()
                    } else {
                        """
                        modLoader="javafml"
                        loaderVersion="[1,)"
                        license="MIT"

                        [[mods]]
                        modId="$cleanId"
                        version="1.0.0"
                        displayName="$className"
                        description='''Generated by CraftForge Java Engine for ${capability.version.versionNumber}'''
                        """.trimIndent()
                    }
                )
            )
        }

        files.add(
            GeneratedFile(
                id = "java_gradle",
                path = "build.gradle",
                fileType = "GRADLE",
                content = """
                    plugins {
                        id 'java'
                    }
                    group = 'com.example.$cleanId'
                    version = '1.0.0'
                    // Target Loader: $loader | Minecraft: ${capability.version.versionNumber} | Runtime: ${capability.version.requiredRuntime}
                """.trimIndent()
            )
        )

        return EngineGenerationResult(
            title = "$className Template",
            modId = cleanId,
            adaptationNotes = "Loaded ${capability.selectedLoader} template for Java ${capability.version.versionNumber}.",
            impossibleFeatureNotice = "",
            files = files
        )
    }

    private companion object {
        const val TAG = "JavaModEngine"
    }
}
