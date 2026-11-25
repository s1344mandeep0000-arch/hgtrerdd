package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.model.BedrockExportMode
import com.example.model.GeneratedFile
import com.example.model.MinecraftEdition
import com.example.model.ModProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ArchiveExporter {

    fun suggestedFileName(project: ModProject, bedrockMode: BedrockExportMode): String {
        val base = project.modId.ifBlank { "craftforge_project" }
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val ver = project.targetVersionNumber.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        return if (project.edition == MinecraftEdition.JAVA) {
            "${base}_java_${ver}.zip"
        } else {
            val suffix = when (bedrockMode) {
                BedrockExportMode.MCADDON -> ".mcaddon"
                BedrockExportMode.MCPACK_BEHAVIOR -> "_bp.mcpack"
                BedrockExportMode.MCPACK_RESOURCE -> "_rp.mcpack"
            }
            "${base}_bedrock_${ver}$suffix"
        }
    }

    suspend fun writeArchiveToUri(
        context: Context,
        uri: Uri,
        project: ModProject,
        bedrockMode: BedrockExportMode
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Unable to open destination file stream.")
            outputStream.use { os ->
                val count = writeProjectZip(os, project, bedrockMode)
                Result.success(count)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createShareIntent(
        context: Context,
        project: ModProject,
        bedrockMode: BedrockExportMode
    ): Result<Intent> = withContext(Dispatchers.IO) {
        try {
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val fileName = suggestedFileName(project, bedrockMode)
            val outFile = File(exportsDir, fileName)
            FileOutputStream(outFile).use { fos ->
                writeProjectZip(fos, project, bedrockMode)
            }
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val mimeType = if (fileName.endsWith(".zip")) "application/zip" else "application/octet-stream"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Result.success(Intent.createChooser(intent, "Export $fileName"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun writeProjectZip(
        outputStream: OutputStream,
        project: ModProject,
        bedrockMode: BedrockExportMode
    ): Int {
        val entries = selectFilesForExport(project, bedrockMode)
        require(entries.isNotEmpty()) { "No matching files available for the chosen export mode." }

        ZipOutputStream(outputStream).use { zos ->
            val seenPaths = mutableSetOf<String>()
            for ((entryPath, file) in entries) {
                val cleanPath = entryPath.trimStart('/')
                if (cleanPath.isBlank() || !seenPaths.add(cleanPath)) continue
                zos.putNextEntry(ZipEntry(cleanPath))
                val bytes = if (file.binaryAsset) {
                    Base64.decode(file.content, Base64.DEFAULT)
                } else {
                    file.content.toByteArray(Charsets.UTF_8)
                }
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return entries.size
    }

    private fun selectFilesForExport(
        project: ModProject,
        bedrockMode: BedrockExportMode
    ): List<Pair<String, GeneratedFile>> {
        if (project.edition == MinecraftEdition.JAVA) {
            return project.files.map { it.path to it }
        }
        return when (bedrockMode) {
            BedrockExportMode.MCADDON -> project.files.map { it.path to it }
            BedrockExportMode.MCPACK_BEHAVIOR -> project.files
                .filter { it.path.startsWith("behavior_pack/") }
                .map { it.path.removePrefix("behavior_pack/") to it }
            BedrockExportMode.MCPACK_RESOURCE -> project.files
                .filter { it.path.startsWith("resource_pack/") }
                .map { it.path.removePrefix("resource_pack/") to it }
        }
    }
}
