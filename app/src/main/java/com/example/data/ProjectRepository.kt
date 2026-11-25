package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CompatibilityStatus
import com.example.model.FeatureCategory
import com.example.model.FirestoreProjectDoc
import com.example.model.FirestoreProjectFileDoc
import com.example.model.GeneratedFile
import com.example.model.MinecraftEdition
import com.example.model.ModProject
import com.example.model.ValidationReport
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ProjectRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing projects.")
    }

    fun observeProjects(userId: String): Flow<List<ModProject>> = flow {
        val path = "users/$userId/projects"
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreProjectDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { doc ->
                        val edition = enumValues<MinecraftEdition>().firstOrNull { it.name == doc.edition }
                            ?: MinecraftEdition.JAVA
                        val category = enumValues<FeatureCategory>().firstOrNull { it.name == doc.featureCategory }
                            ?: FeatureCategory.FULL_PROJECT
                        val status = enumValues<CompatibilityStatus>().firstOrNull { it.name == doc.validationStatus }
                            ?: CompatibilityStatus.PARTIALLY_COMPATIBLE
                        ModProject(
                            id = doc.id,
                            userId = doc.userId,
                            title = doc.title,
                            modId = doc.modId,
                            edition = edition,
                            targetVersionId = doc.targetVersionId,
                            targetVersionNumber = doc.targetVersionNumber,
                            selectedLoader = doc.selectedLoader,
                            featureCategory = category,
                            prompt = doc.prompt,
                            adaptationNotes = doc.adaptationNotes,
                            validationReport = ValidationReport(
                                status = status,
                                summary = doc.validationSummary,
                                checks = emptyList(),
                                canExport = status != CompatibilityStatus.NOT_SUPPORTED
                            ),
                            files = emptyList(),
                            createdAt = doc.createdAt,
                            updatedAt = doc.updatedAt
                        )
                    }.sortedByDescending { it.updatedAt?.seconds ?: 0L }
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

    suspend fun loadProjectWithFiles(userId: String, projectId: String): Result<ModProject> {
        val projPath = "users/$userId/projects/$projectId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            val projSnap = db.collection("users").document(userId)
                .collection("projects").document(projectId).get().await()
            val doc = projSnap.toObject(
                FirestoreProjectDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: throw IllegalStateException("Project not found.")

            val filesSnap = db.collection("users").document(userId)
                .collection("projects").document(projectId)
                .collection("files").get().await()
            val files = filesSnap.toObjects(
                FirestoreProjectFileDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).map { f ->
                GeneratedFile(
                    id = f.id,
                    path = f.path,
                    content = f.content,
                    fileType = f.fileType,
                    binaryAsset = f.binaryAsset
                )
            }.sortedBy { it.path }

            val edition = enumValues<MinecraftEdition>().firstOrNull { it.name == doc.edition }
                ?: MinecraftEdition.JAVA
            val category = enumValues<FeatureCategory>().firstOrNull { it.name == doc.featureCategory }
                ?: FeatureCategory.FULL_PROJECT
            val status = enumValues<CompatibilityStatus>().firstOrNull { it.name == doc.validationStatus }
                ?: CompatibilityStatus.PARTIALLY_COMPATIBLE

            Result.success(
                ModProject(
                    id = doc.id,
                    userId = doc.userId,
                    title = doc.title,
                    modId = doc.modId,
                    edition = edition,
                    targetVersionId = doc.targetVersionId,
                    targetVersionNumber = doc.targetVersionNumber,
                    selectedLoader = doc.selectedLoader,
                    featureCategory = category,
                    prompt = doc.prompt,
                    adaptationNotes = doc.adaptationNotes,
                    validationReport = ValidationReport(
                        status = status,
                        summary = doc.validationSummary,
                        checks = emptyList(),
                        canExport = status != CompatibilityStatus.NOT_SUPPORTED
                    ),
                    files = files,
                    createdAt = doc.createdAt,
                    updatedAt = doc.updatedAt
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, projPath)
            Result.failure(e)
        }
    }

    suspend fun saveProject(userId: String, project: ModProject): Result<String> {
        val projectsCol = db.collection("users").document(userId).collection("projects")
        val projectId = project.id.takeIf { it.isNotBlank() && it.matches(Regex("^[a-zA-Z0-9_\\-]+$")) }
            ?: projectsCol.document().id
        val docPath = "users/$userId/projects/$projectId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            val projRef = projectsCol.document(projectId)
            val existing = projRef.get().await()
            val status = project.validationReport?.status ?: CompatibilityStatus.PARTIALLY_COMPATIBLE
            val summary = (project.validationReport?.summary ?: "Pending validation.").take(1000)
            val boundedFiles = project.files.take(100)

            if (existing.exists()) {
                projRef.update(
                    mapOf(
                        "title" to project.title.ifBlank { "Untitled Mod" }.take(120),
                        "adaptationNotes" to project.adaptationNotes.take(4000),
                        "validationStatus" to status.name,
                        "validationSummary" to summary,
                        "fileCount" to boundedFiles.size.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "title" to project.title.ifBlank { "Untitled Mod" }.take(120),
                    "modId" to project.modId.ifBlank { "craftforge_mod" }.take(64),
                    "edition" to project.edition.name,
                    "targetVersionId" to project.targetVersionId.ifBlank { "unknown" }.take(64),
                    "targetVersionNumber" to project.targetVersionNumber.ifBlank { "1.20.1" }.take(40),
                    "selectedLoader" to project.selectedLoader.ifBlank { "Standard" }.take(64),
                    "featureCategory" to project.featureCategory.name.take(64),
                    "prompt" to project.prompt.ifBlank { "Generated project" }.take(4000),
                    "adaptationNotes" to project.adaptationNotes.take(4000),
                    "validationStatus" to status.name,
                    "validationSummary" to summary,
                    "fileCount" to boundedFiles.size.toLong(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                projRef.set(createPayload).await()
            }

            // Sync files subcollection
            val filesCol = projRef.collection("files")
            val existingFiles = filesCol.get().await()
            val existingById = existingFiles.documents.associateBy { it.id }
            val keptIds = mutableSetOf<String>()

            for ((index, file) in boundedFiles.withIndex()) {
                val fileId = file.id.takeIf { it.isNotBlank() && it.matches(Regex("^[a-zA-Z0-9_\\-]+$")) }
                    ?: "file_${index}_${file.path.hashCode().toUInt()}"
                keptIds.add(fileId)
                val fileRef = filesCol.document(fileId)
                val safeContent = file.content.take(250_000)
                if (existingById.containsKey(fileId)) {
                    fileRef.update(
                        mapOf(
                            "path" to file.path.take(256),
                            "content" to safeContent,
                            "fileType" to file.fileType.ifBlank { "TEXT" }.take(32),
                            "binaryAsset" to file.binaryAsset,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    fileRef.set(
                        mapOf(
                            "userId" to uid,
                            "projectId" to projectId,
                            "path" to file.path.take(256),
                            "content" to safeContent,
                            "fileType" to file.fileType.ifBlank { "TEXT" }.take(32),
                            "binaryAsset" to file.binaryAsset,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            for (oldDoc in existingFiles.documents) {
                if (oldDoc.id !in keptIds) {
                    oldDoc.reference.delete().await()
                }
            }

            Result.success(projectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(userId: String, projectId: String): Result<Unit> {
        val docPath = "users/$userId/projects/$projectId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            val projRef = db.collection("users").document(userId).collection("projects").document(projectId)
            val files = projRef.collection("files").get().await()
            for (doc in files.documents) {
                doc.reference.delete().await()
            }
            projRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
