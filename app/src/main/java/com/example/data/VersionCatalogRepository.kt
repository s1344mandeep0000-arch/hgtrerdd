package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.CompatibilityStatus
import com.example.model.FirestoreVersionUpdateDoc
import com.example.model.MinecraftEdition
import com.example.model.VersionEntry
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class VersionCatalogRepository(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        context.applicationContext,
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    suspend fun loadBundledVersions(): List<VersionEntry> = withContext(Dispatchers.IO) {
        val raw = context.assets.open("minecraft_versions_catalog.json")
            .bufferedReader()
            .use { it.readText() }
        parseVersionJsonArray(JSONArray(raw), isUserAdded = false)
    }

    fun observeDynamicUpdates(userId: String): Flow<List<VersionEntry>> = flow {
        val path = "users/$userId/version_updates"
        emitAll(
            db.collection("users").document(userId).collection("version_updates")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreVersionUpdateDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toDomain() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addOrUpdateVersion(userId: String, entry: VersionEntry): Result<Unit> {
        val rawId = entry.id.ifBlank {
            "${entry.edition.name.lowercase()}_${entry.versionNumber}"
        }
        val sanitizedId = rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64)
        val path = "users/$userId/version_updates/$sanitizedId"
        return try {
            val uid = auth.currentUser?.uid
                ?: throw IllegalStateException("Sign in with Google before updating the version catalog.")
            check(uid == userId) { "Signed-in user mismatch." }
            require(entry.versionNumber.isNotBlank() && entry.versionNumber.length <= 32) {
                "Version number must be 1–32 characters."
            }
            require(entry.releaseDate.isNotBlank() && entry.releaseDate.length <= 32) {
                "Release date must be 1–32 characters."
            }

            val docRef = db.collection("users").document(userId)
                .collection("version_updates").document(sanitizedId)
            val existing = docRef.get().await()
            val payload = hashMapOf<String, Any>(
                "userId" to uid,
                "versionId" to sanitizedId,
                "edition" to entry.edition.name,
                "versionNumber" to entry.versionNumber.take(32),
                "updateName" to entry.updateName.take(120),
                "releaseDate" to entry.releaseDate.take(32),
                "sortOrder" to entry.sortOrder.coerceIn(0L, 100_000_000L),
                "historicalCategory" to entry.historicalCategory.ifBlank { "Custom Update" }.take(64),
                "releaseType" to entry.releaseType.ifBlank { "Release" }.take(32),
                "compatibilityStatus" to entry.compatibilityStatus.name,
                "supportedCapabilities" to entry.supportedCapabilities.joinToString(",").take(2000),
                "compatibleLoaders" to entry.compatibleLoaders.joinToString(",").take(500),
                "supportedFileFormats" to entry.supportedFileFormats.joinToString(",").take(500),
                "requiredRuntime" to entry.requiredRuntime.take(160),
                "adaptationNotes" to entry.adaptationNotes.take(1500),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "updateName" to payload["updateName"]!!,
                        "compatibilityStatus" to payload["compatibilityStatus"]!!,
                        "supportedCapabilities" to payload["supportedCapabilities"]!!,
                        "compatibleLoaders" to payload["compatibleLoaders"]!!,
                        "supportedFileFormats" to payload["supportedFileFormats"]!!,
                        "requiredRuntime" to payload["requiredRuntime"]!!,
                        "adaptationNotes" to payload["adaptationNotes"]!!,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                payload["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun importVersionCatalogJson(userId: String, jsonText: String): Result<Int> {
        return try {
            val trimmed = jsonText.trim()
            val array = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                JSONArray().apply { put(JSONObject(trimmed)) }
            }
            val parsed = parseVersionJsonArray(array, isUserAdded = true)
            require(parsed.isNotEmpty()) { "JSON contained no valid Minecraft version entries." }
            var count = 0
            for (entry in parsed) {
                addOrUpdateVersion(userId, entry).getOrThrow()
                count++
            }
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import version catalog JSON", e)
            Result.failure(e)
        }
    }

    private fun parseVersionJsonArray(array: JSONArray, isUserAdded: Boolean): List<VersionEntry> {
        val list = mutableListOf<VersionEntry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val editionRaw = obj.optString("edition", "JAVA").uppercase()
            val edition = enumValues<MinecraftEdition>().firstOrNull { it.name == editionRaw }
                ?: MinecraftEdition.JAVA
            val statusRaw = obj.optString("compatibilityStatus", "COMPATIBLE").uppercase()
            val status = enumValues<CompatibilityStatus>().firstOrNull { it.name == statusRaw }
                ?: CompatibilityStatus.PARTIALLY_COMPATIBLE
            val releaseDate = obj.optString("releaseDate", "2026-11-24")
            val computedSort = releaseDate.replace("-", "").toLongOrNull() ?: 20261124L
            list.add(
                VersionEntry(
                    id = obj.optString("id")
                        .ifBlank { "${edition.name.lowercase()}_${obj.getString("versionNumber").replace(Regex("[^a-zA-Z0-9]"), "_")}" },
                    edition = edition,
                    versionNumber = obj.getString("versionNumber"),
                    updateName = obj.optString("updateName", ""),
                    releaseDate = releaseDate,
                    sortOrder = obj.optLong("sortOrder", computedSort),
                    historicalCategory = obj.optString("historicalCategory", "Custom Release"),
                    releaseType = obj.optString("releaseType", "Release"),
                    compatibilityStatus = status,
                    supportedCapabilities = obj.optJsonStringList("supportedCapabilities"),
                    compatibleLoaders = obj.optJsonStringList("compatibleLoaders"),
                    supportedFileFormats = obj.optJsonStringList("supportedFileFormats"),
                    requiredRuntime = obj.optString("requiredRuntime", ""),
                    adaptationNotes = obj.optString("adaptationNotes", ""),
                    isUserAdded = isUserAdded
                )
            )
        }
        return list
    }

    private fun JSONObject.optJsonStringList(key: String): List<String> {
        val arr = optJSONArray(key)
        if (arr != null) {
            return (0 until arr.length()).mapNotNull { idx ->
                arr.optString(idx).takeIf { it.isNotBlank() }
            }
        }
        val str = optString(key, "")
        return str.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun FirestoreVersionUpdateDoc.toDomain(): VersionEntry {
        val editionEnum = enumValues<MinecraftEdition>().firstOrNull { it.name == edition }
            ?: MinecraftEdition.JAVA
        val statusEnum = enumValues<CompatibilityStatus>().firstOrNull { it.name == compatibilityStatus }
            ?: CompatibilityStatus.PARTIALLY_COMPATIBLE
        return VersionEntry(
            id = versionId.ifBlank { id },
            edition = editionEnum,
            versionNumber = versionNumber,
            updateName = updateName,
            releaseDate = releaseDate,
            sortOrder = sortOrder,
            historicalCategory = historicalCategory,
            releaseType = releaseType,
            compatibilityStatus = statusEnum,
            supportedCapabilities = supportedCapabilities.split(",").map { it.trim() }.filter { it.isNotBlank() },
            compatibleLoaders = compatibleLoaders.split(",").map { it.trim() }.filter { it.isNotBlank() },
            supportedFileFormats = supportedFileFormats.split(",").map { it.trim() }.filter { it.isNotBlank() },
            requiredRuntime = requiredRuntime,
            adaptationNotes = adaptationNotes,
            isUserAdded = true
        )
    }

    private companion object {
        const val TAG = "VersionCatalogRepo"
    }
}
