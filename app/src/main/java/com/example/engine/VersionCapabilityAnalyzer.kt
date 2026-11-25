package com.example.engine

import com.example.model.CompatibilityStatus
import com.example.model.MinecraftEdition
import com.example.model.VersionCapabilityProfile
import com.example.model.VersionEntry

object VersionCapabilityAnalyzer {

    fun analyze(version: VersionEntry, requestedLoader: String): VersionCapabilityProfile {
        val loader = if (requestedLoader in version.compatibleLoaders) {
            requestedLoader
        } else {
            version.compatibleLoaders.firstOrNull() ?: "Standard"
        }

        return if (version.edition == MinecraftEdition.JAVA) {
            analyzeJava(version, loader)
        } else {
            analyzeBedrock(version, loader)
        }
    }

    private fun analyzeJava(version: VersionEntry, loader: String): VersionCapabilityProfile {
        val sort = version.sortOrder
        val isPreLoader = sort < 20101203L // Pre-Classic, Classic, Indev, Infdev, early Alpha
        val isEarlyModLoader = sort in 20101203L..20130502L // Alpha 1.2.6 .. 1.5.2
        val is16To17 = sort in 20130919L..20150101L // 1.6.4 .. 1.7.10
        val is18To111 = sort in 20150102L..20170917L // 1.8 .. 1.11.2
        val is112 = sort in 20170918L..20190718L // 1.12.2
        val is114To119 = sort in 20190719L..20230611L // 1.14.4 .. 1.19.4
        val is120Plus = sort >= 20230612L // 1.20.1 .. 26.x
        val is1206Plus = sort >= 20240429L // Java 21 + DataComponents + neoforge.mods.toml
        val is1214Plus = sort >= 20241203L // assets/<id>/items/*.json client item definitions

        val impossible = mutableListOf<String>()
        if (isPreLoader) {
            impossible.add("Forge, NeoForge, and Fabric mod loaders did not exist in ${version.versionNumber}.")
            impossible.add("JSON block/item models, JSON recipes, loot tables, and data packs are unsupported.")
            impossible.add("Custom dimensions and data-driven entities cannot be registered via external mod APIs.")
        } else if (isEarlyModLoader) {
            impossible.add("JSON recipes, JSON loot tables, and JSON blockstates do not exist in ${version.versionNumber}.")
            impossible.add("Fabric and NeoForge are unavailable; only Risugami's ModLoader or legacy FML hooks apply.")
        } else if (is16To17) {
            impossible.add("JSON block models (1.8+), JSON loot tables (1.9+), and JSON recipes (1.12+) are not supported; recipes and models must be registered in Java code.")
            impossible.add("Fabric and NeoForge do not support ${version.versionNumber}.")
        } else if (is18To111) {
            impossible.add("JSON recipes are not supported until 1.12; recipes must be registered in Java code via GameRegistry.addRecipe.")
            impossible.add("Fabric and NeoForge do not support ${version.versionNumber}.")
        } else if (is112) {
            impossible.add("Fabric, NeoForge, and DeferredRegister do not exist on 1.12.2; use Forge RegistryEvent.Register<T>.")
        }

        val apis = buildList {
            when {
                isPreLoader -> add("No Mod API (Direct decompiled class reference notes only)")
                isEarlyModLoader -> {
                    add("Risugami BaseMod / Legacy FML @Mod")
                    add("Numeric Block/Item IDs (Block.blocksList / Item.itemsList)")
                }
                is16To17 -> {
                    add("Forge FML @Mod + GameRegistry.registerBlock/registerItem")
                    add("MinecraftForge.EVENT_BUS + LanguageRegistry / .lang files")
                }
                is18To111 -> {
                    add("Forge @Mod + IForgeRegistry / ModelLoader.setCustomModelResourceLocation")
                }
                is112 -> {
                    add("Forge @Mod + RegistryEvent.Register<Block/Item> + ModelRegistryEvent")
                    add("Data-driven assets/<modid>/recipes & loot_tables JSON")
                }
                loader.contains("Fabric", ignoreCase = true) -> {
                    add("Fabric ModInitializer + Registry.register / BuiltInRegistries")
                    add("Fabric API (FabricItemSettings / ItemGroupEvents)")
                }
                loader.contains("NeoForge", ignoreCase = true) -> {
                    add("NeoForge @Mod + DeferredRegister.createBlocks / createItems")
                    if (is1206Plus) add("DataComponents (1.20.5+ ItemStack components)")
                }
                else -> {
                    add("Forge @Mod + DeferredRegister<Block/Item/EntityType> + FMLJavaModLoadingContext")
                }
            }
        }

        val blockReg = when {
            isPreLoader -> "Direct core Block.java static field modification (no mod loader)."
            isEarlyModLoader -> "Block subclass with numeric ID -> ModLoader.registerBlock(block)."
            is16To17 -> "Block subclass -> GameRegistry.registerBlock(block, \"block_name\")."
            is18To111 -> "GameRegistry.register(block.setRegistryName(modId, name))."
            is112 -> "@SubscribeEvent on RegistryEvent.Register<Block> with setRegistryName(modId, name)."
            loader.contains("Fabric", ignoreCase = true) -> "Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, name), new Block(...))."
            loader.contains("NeoForge", ignoreCase = true) -> "BLOCKS.registerSimpleBlock(\"name\", BlockBehaviour.Properties.of()...) via DeferredRegister.Blocks."
            else -> "BLOCKS.register(\"name\", () -> new Block(BlockBehaviour.Properties.of()...)) via DeferredRegister."
        }

        val entityReg = when {
            isPreLoader -> "Direct entity class registration (unsupported via external mod)."
            isEarlyModLoader -> "ModLoader.registerEntityID(EntityClass.class, \"Name\", id)."
            is16To17 || is18To111 -> "EntityRegistry.registerModEntity(..., id, modInstance, ...)."
            is112 -> "RegistryEvent.Register<EntityEntry> via EntityEntryBuilder."
            loader.contains("Fabric", ignoreCase = true) -> "Registry.register(Registries.ENTITY_TYPE, Identifier, FabricEntityTypeBuilder/EntityType.Builder)."
            else -> "DeferredRegister<EntityType<?>> + EntityAttributeCreationEvent + EntityRenderersEvent.RegisterRenderers."
        }

        val textureReq = when {
            sort < 20130313L -> "16x16 tiles inside monolithic terrain.png / gui/items.png sprite sheet."
            sort < 20190423L -> "16x16 PNG under assets/<modid>/textures/blocks/*.png and textures/items/*.png (plural folders)."
            else -> "16x16 PNG (powers of 2) under assets/<modid>/textures/block/*.png and textures/item/*.png (singular folders)."
        }

        val modelFormat = when {
            sort < 20140902L -> "No JSON block/item models; hardcoded BlockRenderer / IIcon rendering in Java."
            is1214Plus -> "assets/<modid>/items/<id>.json client item definition + assets/<modid>/models/{block,item}/*.json + blockstates/*.json."
            else -> "assets/<modid>/blockstates/*.json + assets/<modid>/models/block/*.json + assets/<modid>/models/item/*.json."
        }

        val manifestReq = when {
            isPreLoader -> "No mod manifest supported."
            sort < 20190719L -> "src/main/resources/mcmod.info (JSON array) + build.gradle (ForgeGradle)."
            loader.contains("Fabric", ignoreCase = true) -> "src/main/resources/fabric.mod.json + build.gradle (fabric-loom)."
            loader.contains("NeoForge", ignoreCase = true) && is1206Plus -> "src/main/resources/META-INF/neoforge.mods.toml + build.gradle."
            else -> "src/main/resources/META-INF/mods.toml + build.gradle."
        }

        val structure = if (isPreLoader) {
            "Historical reference notes & conceptual class patch files only (not a modern Gradle mod)."
        } else {
            "build.gradle, gradle.properties, src/main/java/com/example/<modid>/..., src/main/resources/(META-INF|fabric.mod.json|mcmod.info), assets/<modid>/..., data/<modid>/..."
        }

        return VersionCapabilityProfile(
            version = version,
            selectedLoader = loader,
            supportedApis = apis,
            blockRegistrationMethod = blockReg,
            entityRegistrationMethod = entityReg,
            scriptingSystem = "Java (${version.requiredRuntime}) — never Bedrock JavaScript/JSON packs",
            textureRequirement = textureReq,
            modelFormat = modelFormat,
            manifestRequirement = manifestReq,
            projectStructureSummary = structure,
            impossibleFeatures = impossible
        )
    }

    private fun analyzeBedrock(version: VersionEntry, loader: String): VersionCapabilityProfile {
        val sort = version.sortOrder
        val isPreAddons = sort < 20161021L // PE 0.1.0 .. 0.14.0
        val isEarlyAddons = sort in 20161021L..20201116L // PE 0.16.0 .. 1.15
        val supportsCustomBlocksItems = sort >= 20201117L // 1.16.100+
        val supportsScriptApi = sort >= 20230426L // 1.19.80+

        val impossible = mutableListOf<String>()
        impossible.add("Custom Dimensions are not natively supported in official Bedrock Behavior/Resource Pack schemas (offer Teleport/Structure/Overworld biome-zone alternative).")
        if (isPreAddons) {
            impossible.add("Official .mcaddon / .mcpack Behavior & Resource Packs did not exist in ${version.versionNumber} (introduced in PE 0.16.0).")
            impossible.add("Custom blocks, custom items, custom entities, JSON recipes, and Script API are unsupported.")
        } else if (isEarlyAddons) {
            impossible.add("Custom Blocks and Custom Items require Bedrock 1.16.100+ and cannot be declared cleanly in ${version.versionNumber}.")
            impossible.add("@minecraft/server Script API requires Bedrock 1.19.80+ and is unavailable in ${version.versionNumber}.")
        } else if (!supportsScriptApi) {
            impossible.add("Stable @minecraft/server Script API requires Bedrock 1.19.80+; use JSON entity/block events instead in ${version.versionNumber}.")
        }

        val apis = buildList {
            if (isPreAddons) {
                add("No official Add-on API (Legacy PE texture notes only)")
            } else {
                add("Bedrock Behavior Pack JSON (${version.versionNumber})")
                add("Bedrock Resource Pack JSON (${version.versionNumber})")
                if (supportsScriptApi) add("@minecraft/server Script API (${version.requiredRuntime})")
            }
        }

        val blockReg = when {
            isPreAddons -> "Not supported in ${version.versionNumber}."
            !supportsCustomBlocksItems -> "Not supported cleanly; upgrade target to Bedrock 1.16.100+ for minecraft:block JSON."
            else -> "behavior_pack/blocks/<block>.json (format_version 1.20.80+ / 1.21.x) + resource_pack/blocks.json + terrain_texture.json."
        }

        val entityReg = when {
            isPreAddons -> "Not supported in ${version.versionNumber}."
            isEarlyAddons -> "Vanilla entity behavior override JSON in behavior_pack/entities/*.json."
            else -> "behavior_pack/entities/<mob>.json + resource_pack/entity/<mob>.entity.json + render_controllers + models/entity/*.geo.json."
        }

        val scripting = when {
            !supportsScriptApi -> "Unavailable in ${version.versionNumber} (JSON components/events only)."
            else -> "behavior_pack/scripts/main.js importing { world, system } from \"@minecraft/server\" with manifest script module entry."
        }

        val manifestReq = when {
            isPreAddons -> "No manifest.json format supported."
            isEarlyAddons -> "manifest.json format_version: 1 with header.uuid + modules[].uuid (UUID v4)."
            else -> "manifest.json format_version: 2 with unique header.uuid, modules[].uuid, min_engine_version, and BP->RP dependencies link."
        }

        return VersionCapabilityProfile(
            version = version,
            selectedLoader = loader,
            supportedApis = apis,
            blockRegistrationMethod = blockReg,
            entityRegistrationMethod = entityReg,
            scriptingSystem = scripting,
            textureRequirement = "16x16 / 32x32 PNGs in resource_pack/textures/(blocks|items|entity)/ + terrain_texture.json & item_texture.json.",
            modelFormat = "Bedrock Geometry JSON (models/entity/*.geo.json & models/blocks/*.geo.json, format_version 1.12.0+).",
            manifestRequirement = manifestReq,
            projectStructureSummary = "behavior_pack/(manifest.json, blocks/, items/, entities/, recipes/, loot_tables/, scripts/) + resource_pack/(manifest.json, textures/, models/, animations/, entity/, sounds/)",
            impossibleFeatures = impossible
        )
    }

    fun isCompatibleForExport(status: CompatibilityStatus): Boolean {
        return status != CompatibilityStatus.NOT_SUPPORTED
    }
}
