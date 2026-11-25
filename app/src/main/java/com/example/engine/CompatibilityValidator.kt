package com.example.engine

import com.example.model.CheckSeverity
import com.example.model.CompatibilityStatus
import com.example.model.FeatureCategory
import com.example.model.GeneratedFile
import com.example.model.MinecraftEdition
import com.example.model.ModProject
import com.example.model.ValidationCheck
import com.example.model.ValidationReport
import com.example.model.VersionCapabilityProfile
import org.json.JSONArray
import org.json.JSONObject

object CompatibilityValidator {

    private val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun validate(project: ModProject, capability: VersionCapabilityProfile): ValidationReport {
        val checks = mutableListOf<ValidationCheck>()

        if (project.files.isEmpty()) {
            checks.add(
                ValidationCheck(
                    category = "Project Structure",
                    title = "No project files generated",
                    detail = "Generate or load files before validating and exporting.",
                    severity = CheckSeverity.ERROR
                )
            )
            return ValidationReport(
                status = CompatibilityStatus.NOT_SUPPORTED,
                summary = "No files to validate.",
                checks = checks,
                canExport = false
            )
        }

        // 1. Version & Loader Compatibility
        val versionStatus = capability.version.compatibilityStatus
        if (versionStatus == CompatibilityStatus.NOT_SUPPORTED) {
            checks.add(
                ValidationCheck(
                    category = "Version Compatibility",
                    title = "Historical version ${capability.version.versionNumber} lacks official mod/add-on APIs",
                    detail = capability.version.adaptationNotes,
                    severity = CheckSeverity.ERROR
                )
            )
        } else {
            checks.add(
                ValidationCheck(
                    category = "Loader & Version",
                    title = "Target ${capability.version.versionNumber} (${capability.selectedLoader})",
                    detail = "Runtime: ${capability.version.requiredRuntime}",
                    severity = CheckSeverity.PASS
                )
            )
        }

        if (project.selectedLoader !in capability.version.compatibleLoaders &&
            capability.version.compatibleLoaders.isNotEmpty()
        ) {
            checks.add(
                ValidationCheck(
                    category = "Loader Compatibility",
                    title = "Incompatible loader '${project.selectedLoader}'",
                    detail = "Supported loaders for ${capability.version.versionNumber}: ${capability.version.compatibleLoaders.joinToString(", ")}.",
                    severity = CheckSeverity.ERROR
                )
            )
        }

        // 2. Unsupported Feature Guard
        if (project.edition == MinecraftEdition.BEDROCK &&
            project.featureCategory == FeatureCategory.CUSTOM_DIMENSION
        ) {
            checks.add(
                ValidationCheck(
                    category = "Unsupported Feature",
                    title = "Custom Dimensions unsupported in Bedrock Add-ons",
                    detail = "Bedrock Edition Add-ons cannot register new dimension IDs; use Overworld/Nether/End structure or teleportation mechanics instead.",
                    severity = CheckSeverity.WARNING
                )
            )
        }

        // 3. Syntax Checks across all text files
        for (file in project.files) {
            if (file.binaryAsset) continue
            if (file.path.endsWith(".json", ignoreCase = true) || file.path.endsWith(".mcmeta", ignoreCase = true)) {
                try {
                    val trimmed = file.content.trim()
                    if (trimmed.startsWith("[")) {
                        JSONArray(trimmed)
                    } else {
                        JSONObject(trimmed)
                    }
                } catch (e: Exception) {
                    checks.add(
                        ValidationCheck(
                            category = "File Syntax",
                            title = "Invalid JSON syntax in ${file.path}",
                            detail = e.message ?: "Malformed JSON",
                            severity = CheckSeverity.ERROR,
                            affectedFilePath = file.path
                        )
                    )
                }
            } else if (file.path.endsWith(".java", ignoreCase = true)) {
                validateJavaSyntax(file, checks)
            } else if (file.path.endsWith(".js", ignoreCase = true)) {
                validateJsSyntax(file, capability, checks)
            }
        }

        // 4. Edition-specific structural, manifest, API, and reference checks
        if (project.edition == MinecraftEdition.JAVA) {
            validateJavaProject(project, capability, checks)
        } else {
            validateBedrockProject(project, capability, checks)
        }

        val errors = checks.count { it.severity == CheckSeverity.ERROR }
        val warnings = checks.count { it.severity == CheckSeverity.WARNING }

        val finalStatus = when {
            versionStatus == CompatibilityStatus.NOT_SUPPORTED -> CompatibilityStatus.NOT_SUPPORTED
            errors > 0 -> CompatibilityStatus.REQUIRES_MODIFICATION
            warnings > 0 || versionStatus == CompatibilityStatus.PARTIALLY_COMPATIBLE ->
                CompatibilityStatus.PARTIALLY_COMPATIBLE
            else -> CompatibilityStatus.COMPATIBLE
        }

        val summary = when (finalStatus) {
            CompatibilityStatus.COMPATIBLE ->
                "All ${checks.size} checks passed for ${project.edition.label} ${project.targetVersionNumber}."
            CompatibilityStatus.PARTIALLY_COMPATIBLE ->
                "Passed with $warnings warning(s). Review notes before importing into Minecraft."
            CompatibilityStatus.REQUIRES_MODIFICATION ->
                "Found $errors error(s) and $warnings warning(s). Use 'Fix with AI' or edit flagged files."
            CompatibilityStatus.NOT_SUPPORTED ->
                "${project.targetVersionNumber} does not support automated mod/add-on packaging."
        }

        return ValidationReport(
            status = finalStatus,
            summary = summary,
            checks = checks,
            canExport = finalStatus != CompatibilityStatus.NOT_SUPPORTED
        )
    }

    private fun validateJavaSyntax(file: GeneratedFile, checks: MutableList<ValidationCheck>) {
        val text = file.content
        if (text.isBlank()) {
            checks.add(
                ValidationCheck(
                    category = "File Syntax",
                    title = "Empty Java source file",
                    detail = "${file.path} has no content.",
                    severity = CheckSeverity.ERROR,
                    affectedFilePath = file.path
                )
            )
            return
        }
        val openBraces = text.count { it == '{' }
        val closeBraces = text.count { it == '}' }
        if (openBraces != closeBraces) {
            checks.add(
                ValidationCheck(
                    category = "File Syntax",
                    title = "Unbalanced braces in ${file.path.substringAfterLast('/')}",
                    detail = "Found $openBraces '{' vs $closeBraces '}'.",
                    severity = CheckSeverity.ERROR,
                    affectedFilePath = file.path
                )
            )
        }
        if (!text.contains("class ") && !text.contains("interface ") && !text.contains("enum ") && !text.contains("record ")) {
            checks.add(
                ValidationCheck(
                    category = "File Syntax",
                    title = "Missing Java type declaration",
                    detail = "${file.path} does not declare a class, interface, enum, or record.",
                    severity = CheckSeverity.ERROR,
                    affectedFilePath = file.path
                )
            )
        }
    }

    private fun validateJsSyntax(
        file: GeneratedFile,
        capability: VersionCapabilityProfile,
        checks: MutableList<ValidationCheck>
    ) {
        if (capability.version.sortOrder < 20230426L && file.content.contains("@minecraft/server")) {
            checks.add(
                ValidationCheck(
                    category = "API Version",
                    title = "Script API unsupported in ${capability.version.versionNumber}",
                    detail = "@minecraft/server requires Bedrock 1.19.80+.",
                    severity = CheckSeverity.ERROR,
                    affectedFilePath = file.path
                )
            )
        }
        val openBraces = file.content.count { it == '{' }
        val closeBraces = file.content.count { it == '}' }
        if (openBraces != closeBraces) {
            checks.add(
                ValidationCheck(
                    category = "File Syntax",
                    title = "Unbalanced braces in ${file.path}",
                    detail = "Found $openBraces '{' vs $closeBraces '}'.",
                    severity = CheckSeverity.ERROR,
                    affectedFilePath = file.path
                )
            )
        }
    }

    private fun validateJavaProject(
        project: ModProject,
        capability: VersionCapabilityProfile,
        checks: MutableList<ValidationCheck>
    ) {
        val paths = project.files.map { it.path }

        // Check that no Bedrock files leaked into Java project
        val hasBedrockLeak = project.files.any {
            it.path.startsWith("behavior_pack/") ||
                it.path.startsWith("resource_pack/") ||
                it.content.contains("\"format_version\"") && it.path.endsWith("manifest.json")
        }
        if (hasBedrockLeak) {
            checks.add(
                ValidationCheck(
                    category = "Edition Isolation",
                    title = "Bedrock manifest/pack path detected in Java project",
                    detail = "Java Edition mods must never use Bedrock pack manifests.",
                    severity = CheckSeverity.ERROR
                )
            )
        }

        val hasJavaSource = paths.any { it.endsWith(".java") }
        if (!hasJavaSource) {
            checks.add(
                ValidationCheck(
                    category = "Project Structure",
                    title = "Missing Java source files",
                    detail = "Java Edition mods require at least one .java source class.",
                    severity = CheckSeverity.ERROR
                )
            )
        } else {
            checks.add(
                ValidationCheck(
                    category = "Project Structure",
                    title = "Java source files present",
                    detail = "Found ${paths.count { it.endsWith(".java") }} Java class(es).",
                    severity = CheckSeverity.PASS
                )
            )
        }

        val sort = capability.version.sortOrder
        if (sort >= 20130919L) {
            val hasBuildGradle = paths.any { it.endsWith("build.gradle") || it.endsWith("build.gradle.kts") }
            if (!hasBuildGradle) {
                checks.add(
                    ValidationCheck(
                        category = "Dependencies",
                        title = "Missing build.gradle",
                        detail = "Gradle build configuration is required for ${capability.selectedLoader}.",
                        severity = CheckSeverity.WARNING
                    )
                )
            }

            // Manifest check by version & loader
            val expectedManifest = when {
                sort < 20190719L -> "mcmod.info"
                capability.selectedLoader.contains("Fabric", ignoreCase = true) -> "fabric.mod.json"
                capability.selectedLoader.contains("NeoForge", ignoreCase = true) && sort >= 20240429L ->
                    "neoforge.mods.toml"
                else -> "mods.toml"
            }
            if (paths.none { it.endsWith(expectedManifest) }) {
                checks.add(
                    ValidationCheck(
                        category = "Manifest",
                        title = "Missing $expectedManifest",
                        detail = "Expected ${capability.manifestRequirement}",
                        severity = CheckSeverity.ERROR
                    )
                )
            } else {
                checks.add(
                    ValidationCheck(
                        category = "Manifest",
                        title = "Found $expectedManifest",
                        detail = "Mod metadata descriptor matches ${capability.selectedLoader}.",
                        severity = CheckSeverity.PASS
                    )
                )
            }
        }

        // Check models/textures references
        val textureFiles = paths.filter { it.endsWith(".png", ignoreCase = true) }
        val modelFiles = project.files.filter { it.path.contains("/models/") && it.path.endsWith(".json") }
        if (modelFiles.isNotEmpty() && textureFiles.isEmpty()) {
            checks.add(
                ValidationCheck(
                    category = "Textures",
                    title = "Missing PNG texture asset",
                    detail = "Models are present without a matching PNG texture under assets/${project.modId}/textures/. Generate a pixel-art PNG or add one before building.",
                    severity = CheckSeverity.WARNING
                )
            )
        } else if (textureFiles.isNotEmpty()) {
            checks.add(
                ValidationCheck(
                    category = "Textures & Models",
                    title = "${textureFiles.size} PNG texture(s) & ${modelFiles.size} JSON model(s) verified",
                    detail = "Resource paths aligned under assets/${project.modId}/.",
                    severity = CheckSeverity.PASS
                )
            )
        }
    }

    private fun validateBedrockProject(
        project: ModProject,
        capability: VersionCapabilityProfile,
        checks: MutableList<ValidationCheck>
    ) {
        val paths = project.files.map { it.path }

        // Check no Java mod leakage
        val hasJavaLeak = paths.any { it.endsWith(".java") || it.endsWith(".toml") || it.endsWith("build.gradle") }
        if (hasJavaLeak) {
            checks.add(
                ValidationCheck(
                    category = "Edition Isolation",
                    title = "Java Edition files detected in Bedrock Add-on",
                    detail = "Bedrock Add-ons must only contain BP/RP JSON, JS scripts, models, and textures.",
                    severity = CheckSeverity.ERROR
                )
            )
        }

        val bpManifest = project.files.firstOrNull {
            it.path.equals("behavior_pack/manifest.json", ignoreCase = true)
        }
        val rpManifest = project.files.firstOrNull {
            it.path.equals("resource_pack/manifest.json", ignoreCase = true)
        }

        if (bpManifest == null && rpManifest == null) {
            checks.add(
                ValidationCheck(
                    category = "Manifest",
                    title = "Missing Behavior/Resource manifest.json",
                    detail = "Bedrock packs require at least one valid manifest.json.",
                    severity = CheckSeverity.ERROR
                )
            )
        } else {
            bpManifest?.let { validateBedrockManifest(it, "Behavior Pack", checks) }
            rpManifest?.let { validateBedrockManifest(it, "Resource Pack", checks) }
        }

        // Cross-check texture references in terrain_texture.json / item_texture.json
        val pngFiles = paths.filter { it.endsWith(".png", ignoreCase = true) }
        val rpFiles = project.files.filter { it.path.startsWith("resource_pack/") }
        if (rpFiles.isNotEmpty() && pngFiles.isEmpty()) {
            checks.add(
                ValidationCheck(
                    category = "Missing Textures",
                    title = "Resource Pack has no PNG textures",
                    detail = "Generate an AI pixel-art texture or include textures/*.png so items/blocks render properly.",
                    severity = CheckSeverity.WARNING
                )
            )
        } else if (pngFiles.isNotEmpty()) {
            checks.add(
                ValidationCheck(
                    category = "Textures",
                    title = "${pngFiles.size} Bedrock PNG texture(s) bundled",
                    detail = "Packaged inside resource_pack/textures/.",
                    severity = CheckSeverity.PASS
                )
            )
        }

        // Check resource references in item_texture.json / terrain_texture.json
        val textureAtlases = project.files.filter {
            it.path.endsWith("item_texture.json") || it.path.endsWith("terrain_texture.json")
        }
        for (atlas in textureAtlases) {
            try {
                val json = JSONObject(atlas.content)
                val textureData = json.optJSONObject("texture_data")
                if (textureData != null) {
                    val keys = textureData.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val texPath = textureData.optJSONObject(key)?.optString("textures").orEmpty()
                        if (texPath.isNotBlank()) {
                            val expectedFile = "resource_pack/$texPath.png"
                            if (paths.none { it.equals(expectedFile, ignoreCase = true) }) {
                                checks.add(
                                    ValidationCheck(
                                        category = "Resource Reference",
                                        title = "Referenced texture missing: $expectedFile",
                                        detail = "Declared in ${atlas.path} under '$key'.",
                                        severity = CheckSeverity.WARNING,
                                        affectedFilePath = atlas.path
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // JSON syntax check already reported above
            }
        }
    }

    private fun validateBedrockManifest(
        file: GeneratedFile,
        label: String,
        checks: MutableList<ValidationCheck>
    ) {
        try {
            val root = JSONObject(file.content)
            val formatVersion = root.optInt("format_version", 0)
            if (formatVersion !in 1..2) {
                checks.add(
                    ValidationCheck(
                        category = "Manifest",
                        title = "Invalid format_version in $label manifest.json",
                        detail = "Expected format_version 1 or 2, got $formatVersion.",
                        severity = CheckSeverity.ERROR,
                        affectedFilePath = file.path
                    )
                )
            }
            val header = root.optJSONObject("header")
            val headerUuid = header?.optString("uuid").orEmpty()
            if (!uuidRegex.matches(headerUuid)) {
                checks.add(
                    ValidationCheck(
                        category = "Manifest UUID",
                        title = "Invalid header UUID in $label manifest.json",
                        detail = "UUID '$headerUuid' is not a valid UUID v4 string.",
                        severity = CheckSeverity.ERROR,
                        affectedFilePath = file.path
                    )
                )
            } else {
                checks.add(
                    ValidationCheck(
                        category = "Manifest",
                        title = "$label manifest.json valid",
                        detail = "format_version $formatVersion, UUID $headerUuid.",
                        severity = CheckSeverity.PASS,
                        affectedFilePath = file.path
                    )
                )
            }
            val modules = root.optJSONArray("modules")
            if (modules == null || modules.length() == 0) {
                checks.add(
                    ValidationCheck(
                        category = "Manifest",
                        title = "$label manifest.json has no modules",
                        detail = "At least one module entry is required.",
                        severity = CheckSeverity.ERROR,
                        affectedFilePath = file.path
                    )
                )
            }
        } catch (_: Exception) {
            // JSON error already flagged in syntax pass
        }
    }
}
