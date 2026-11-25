package br.ufop.campus.core.ffi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import br.ufop.campus.core.security.CampusCryptoEngine
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun InMemoryCampusRepository.exportSchedulePayloadImpl(semester: String): String =
    synchronized(lock) {
        val cleanSemester = semester.ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        buildScheduleJsonObjectLocked(cleanSemester).toString(2)
    }

internal fun InMemoryCampusRepository.exportMyGradeBundleJsonImpl(semester: String): String =
    synchronized(lock) {
        val cleanSemester = semester.ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val base = buildScheduleJsonObjectLocked(cleanSemester)
        val avatarBase64 = encodeProfileAvatarBase64(studentProfile.avatarPath)
        base.apply {
            put("format", "ufopgrade-v1")
            put("friendName", studentProfile.fullName.trim().ifBlank { "Estudante UFOP" })
            put("courseName", studentProfile.courseName.trim())
            if (!avatarBase64.isNullOrBlank()) {
                put("avatarBase64", avatarBase64)
            }
        }.toString(2)
    }

private fun InMemoryCampusRepository.buildScheduleJsonObjectLocked(cleanSemester: String): JSONObject {
    val semesterCourses = courses.values.filter { it.semester == cleanSemester }
    val courseIds = semesterCourses.map { it.courseId }.toSet()
    val semesterSlots = slots.filter { it.courseId in courseIds }

    val coursesArr = JSONArray()
    semesterCourses.forEach { c ->
        val dept = resolveDepartmentOption(c.departmentId)
        coursesArr.put(
            JSONObject().apply {
                put("courseId", c.courseId)
                put("code", resolveCourseCode(c))
                put("semester", c.semester)
                put("name", c.name)
                put("departmentId", dept.id)
                put("departmentName", dept.name)
                put("colorHex", dept.colorHex)
                put("workloadHours", c.workloadHours)
                put("maxAbsences", c.maxAbsences)
                put("professor", c.professor)
                put("buildingAndRoom", c.buildingAndRoom)
                put("shortLocation", c.shortLocation)
            }
        )
    }

    val slotsArr = JSONArray()
    semesterSlots.forEach { s ->
        slotsArr.put(
            JSONObject().apply {
                put("slotId", s.slotId)
                put("courseId", s.courseId)
                put("dayOfWeek", s.dayOfWeek)
                put("startTime", s.startTime)
                put("endTime", s.endTime)
                put("building", s.building)
                put("room", s.room)
                put("shortLocation", s.shortLocation)
                put("classWeight", s.classWeight)
            }
        )
    }

    return JSONObject().apply {
        put("semester", cleanSemester)
        put("campusId", activeCampusId)
        put("courses", coursesArr)
        put("slots", slotsArr)
    }
}

private fun encodeProfileAvatarBase64(avatarPath: String?): String? {
    val path = avatarPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        val file = File(path)
        if (!file.exists() || file.length() <= 0L) return null
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val squareSize = minOf(decoded.width, decoded.height)
        val cropX = (decoded.width - squareSize) / 2
        val cropY = (decoded.height - squareSize) / 2
        val cropped = if (decoded.width != decoded.height && squareSize > 0) {
            Bitmap.createBitmap(decoded, cropX, cropY, squareSize, squareSize)
        } else {
            decoded
        }
        val scaled = Bitmap.createScaledBitmap(cropped, 120, 120, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        val bytes = out.toByteArray()
        if (scaled !== cropped && !scaled.isRecycled) scaled.recycle()
        if (cropped !== decoded && !cropped.isRecycled) cropped.recycle()
        if (!decoded.isRecycled) decoded.recycle()
        if (bytes.isEmpty()) null else Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (_: Throwable) {
        null
    }
}

internal fun InMemoryCampusRepository.importSchedulePayloadImpl(jsonPayload: String): Int =
    synchronized(lock) {
        val trimmed = jsonPayload.trim()
        if (trimmed.isBlank() || !trimmed.contains("{")) {
            return@synchronized 0
        }

        var importedCount = 0
        val parsedRoot = try { JSONObject(trimmed) } catch (_: Throwable) { null }
        if (parsedRoot != null) {
            val defaultSemester = parsedRoot.optNonNullString("semester", studentProfile.activeSemester)
                .ifBlank { "2026.2" }
            val coursesArr = parsedRoot.optJSONArray("courses")
            if (coursesArr != null) {
                for (i in 0 until coursesArr.length()) {
                    val obj = coursesArr.optJSONObject(i) ?: continue
                    val id = obj.optNonNullString("courseId")
                        .ifBlank { obj.optNonNullString("id") }
                        .trim()
                    val name = obj.optNonNullString("name").trim()
                    if (id.isEmpty() || name.isEmpty()) continue
                    val semester = obj.optNonNullString("semester", defaultSemester).ifBlank { defaultSemester }
                    val rawDeptId = obj.optNonNullString("departmentId")
                        .ifBlank { obj.optNonNullString("department_id", "comp") }
                        .ifBlank { "comp" }
                    val dept = resolveDepartmentOption(rawDeptId)
                    val workloadHours = obj.optInt("workloadHours", obj.optInt("workload_hours", 60))
                        .coerceAtLeast(1)
                    val maxAbsences = obj.optInt(
                        "maxAbsences",
                        obj.optInt("max_absences", (workloadHours * 3) / 10)
                    ).coerceAtLeast(0)
                    val professor = obj.optNonNullString("professor", "").trim()
                    val buildingAndRoom = obj.optNonNullString("buildingAndRoom")
                        .ifBlank { obj.optNonNullString("building_and_room", "Campus UFOP") }
                        .ifBlank { "Campus UFOP" }
                    val shortLocation = obj.optNonNullString("shortLocation")
                        .ifBlank { obj.optNonNullString("short_location", "UFOP") }
                        .ifBlank { "UFOP" }

                    val existing = courses[id]
                    val resolvedCode = deriveCourseCodeFallback(
                        explicitCode = obj.optNonNullString("code"),
                        notes = existing?.initialNotes.orEmpty(),
                        courseId = id,
                        courseName = name
                    )
                    courses[id] = CourseSeed(
                        courseId = id,
                        semester = semester,
                        name = name,
                        departmentId = dept.id,
                        departmentName = dept.name,
                        defaultColorHex = dept.colorHex,
                        workloadHours = workloadHours,
                        baseAbsences = existing?.baseAbsences ?: 0,
                        maxAbsences = maxAbsences,
                        baseCancelledClasses = existing?.baseCancelledClasses ?: 0,
                        professor = professor,
                        buildingAndRoom = buildingAndRoom,
                        shortLocation = shortLocation,
                        assessments = existing?.assessments ?: emptyList(),
                        initialNotes = existing?.initialNotes ?: "",
                        code = resolvedCode
                    )
                    if (id !in assessmentsByCourseId) {
                        assessmentsByCourseId[id] = mutableListOf()
                    }
                    importedCount += 1
                }
            }

            val slotsArr = parsedRoot.optJSONArray("slots")
            if (slotsArr != null) {
                val activeCampus = activeCampusLocked()
                for (i in 0 until slotsArr.length()) {
                    val obj = slotsArr.optJSONObject(i) ?: continue
                    val slotId = obj.optNonNullString("slotId")
                        .ifBlank { obj.optNonNullString("slot_id") }
                        .trim()
                    val courseId = obj.optNonNullString("courseId")
                        .ifBlank { obj.optNonNullString("course_id") }
                        .trim()
                    if (slotId.isEmpty() || courseId.isEmpty()) continue
                    val dayOfWeek = obj.optInt("dayOfWeek", obj.optInt("day_of_week", 2)).coerceIn(1, 7)
                    val startTime = obj.optNonNullString("startTime")
                        .ifBlank { obj.optNonNullString("start_time", "08:00") }
                        .ifBlank { "08:00" }
                    val endTime = obj.optNonNullString("endTime")
                        .ifBlank { obj.optNonNullString("end_time", "09:40") }
                        .ifBlank { "09:40" }
                    val building = obj.optNonNullString("building", "Campus UFOP").ifBlank { "Campus UFOP" }
                    val room = obj.optNonNullString("room", "").trim()
                    val shortLocation = obj.optNonNullString("shortLocation")
                        .ifBlank { obj.optNonNullString("short_location") }
                        .ifBlank { courses[courseId]?.shortLocation ?: buildShortLocation(building, room) }
                    val classWeight = obj.optInt("classWeight", obj.optInt("class_weight", 1)).coerceAtLeast(1)

                    slots.removeAll { it.slotId == slotId }
                    slots.add(
                        SlotSeed(
                            slotId = slotId,
                            courseId = courseId,
                            dayOfWeek = dayOfWeek,
                            startTime = startTime,
                            endTime = endTime,
                            building = building,
                            room = room,
                            shortLocation = shortLocation,
                            defaultTempC = activeCampus.weatherTempC,
                            weatherCondition = activeCampus.weatherCondition,
                            classWeight = classWeight
                        )
                    )
                }
            }

            val campusIdInJson = parsedRoot.optNonNullString("campusId")
            if (campusIdInJson.isNotBlank() && ufopCampusesSeed.any { it.id == campusIdInJson }) {
                activeCampusId = campusIdInJson
                ufopCampusesSeed.find { it.id == campusIdInJson }?.let { campus ->
                    studentProfile = studentProfile.copy(campusName = campus.city)
                }
            }
        }

        if (importedCount == 0 && trimmed.startsWith("{")) {
            importedCount = courses.size
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        importedCount
    }

internal fun InMemoryCampusRepository.exportFullAccountSnapshotJsonImpl(): String =
    synchronized(lock) { buildPersistedJsonLocked().toString() }

internal fun InMemoryCampusRepository.restoreFullAccountSnapshotJsonImpl(rawJson: String): Boolean =
    synchronized(lock) {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return@synchronized false
        val root = try {
            JSONObject(trimmed)
        } catch (_: Throwable) {
            return@synchronized false
        }
        val schemaVersion = root.optInt("schemaVersion", -1)
        val hasValidData = schemaVersion >= 1 ||
            root.has("courses") ||
            root.has("studentProfile") ||
            root.has("transcriptEntries") ||
            root.has("dynamicGridCompletedByMatrix")
        if (!hasValidData) return@synchronized false

        val restored = runCatching {
            restoreStateFromJsonLocked(root)
            true
        }.getOrDefault(false)
        if (!restored) return@synchronized false

        val wasBlocked = persistenceBlockedDueToLoadError
        persistenceBlockedDueToLoadError = false
        val persisted = persistStateLocked()
        if (wasBlocked && stateFile != null && !persisted) {
            persistenceBlockedDueToLoadError = true
            applyVirginStateLocked()
            return@synchronized false
        }
        _persistenceLoadError.value = PersistenceLoadErrorState()
        _mutationVersion.update { it + 1L }
        true
    }

internal fun InMemoryCampusRepository.retryLoadPersistedStateImpl(): Boolean =
    synchronized(lock) {
        val targetFile = stateFile ?: return@synchronized false
        val recovered = loadPersistedStateOrFallbackLocked(
            file = targetFile,
            allowQuarantineCreation = false
        )
        if (recovered) {
            _mutationVersion.update { it + 1L }
        }
        recovered
    }

internal fun InMemoryCampusRepository.resetStateAfterLoadErrorAndUnlockPersistenceImpl() {
    synchronized(lock) {
        stateFile?.let { targetFile ->
            val parentDir = targetFile.absoluteFile.parentFile
            val candidates = listOfNotNull(
                targetFile,
                parentDir?.let { File(it, "${targetFile.name}.bak") },
                parentDir?.let { File(it, "${targetFile.name}.tmp") }
            )
            val existingCandidates = candidates.filter { it.exists() }
            if (existingCandidates.isNotEmpty() && _persistenceLoadError.value.quarantinedFiles.isEmpty()) {
                createCorruptQuarantineCopies(existingCandidates)
            }
            candidates.forEach { cand ->
                runCatching { cand.delete() }
            }
        }
        applyVirginStateLocked()
        persistenceBlockedDueToLoadError = false
        _persistenceLoadError.value = PersistenceLoadErrorState()
        _mutationVersion.update { it + 1L }
        runCatching { onStatePersisted?.invoke() }
    }
}

internal fun InMemoryCampusRepository.wipeAllUserDataForLogoutImpl() {
    synchronized(lock) {
        applyVirginStateLocked()
        persistenceBlockedDueToLoadError = false
        _persistenceLoadError.value = PersistenceLoadErrorState()
        stateFile?.let { targetFile ->
            val parentDir = targetFile.absoluteFile.parentFile
            runCatching { targetFile.delete() }
            if (parentDir != null) {
                runCatching { File(parentDir, "${targetFile.name}.bak").delete() }
                runCatching { File(parentDir, "${targetFile.name}.tmp").delete() }
            }
        }
        _mutationVersion.update { it + 1L }
        runCatching { onStatePersisted?.invoke() }
    }
}

internal fun InMemoryCampusRepository.loadPersistedStateOrFallbackLocked(
    file: File,
    allowQuarantineCreation: Boolean = true
): Boolean {
    val parentDir = file.absoluteFile.parentFile
    val candidates = listOf(
        file,
        File(parentDir, "${file.name}.bak"),
        File(parentDir, "${file.name}.tmp")
    )
    val existingCandidates = candidates.filter { it.exists() }
    if (existingCandidates.isEmpty()) {
        persistenceBlockedDueToLoadError = false
        _persistenceLoadError.value = PersistenceLoadErrorState()
        applyVirginStateLocked()
        return true
    }

    var sawSecurityFailure = false
    var sawInvalidOrCorruptFormat = false

    for (candidate in existingCandidates) {
        if (candidate.length() <= 2L) {
            sawInvalidOrCorruptFormat = true
            continue
        }
        var wasPlaintext = false
        val root = try {
            val raw = candidate.readText(Charsets.UTF_8)
            if (raw.isBlank()) {
                sawInvalidOrCorruptFormat = true
                null
            } else {
                val decrypted = CampusCryptoEngine.decryptStatePayloadIfNeeded(raw)
                wasPlaintext = (decrypted == raw)
                JSONObject(decrypted)
            }
        } catch (_: SecurityException) {
            sawSecurityFailure = true
            null
        } catch (_: Throwable) {
            sawInvalidOrCorruptFormat = true
            null
        } ?: continue

        val schemaVersion = root.optInt("schemaVersion", -1)
        val hasValidData = schemaVersion >= 1 ||
            root.has("courses") ||
            root.has("studentProfile") ||
            root.has("transcriptEntries")
        if (!hasValidData) {
            sawInvalidOrCorruptFormat = true
            continue
        }

        val restored = runCatching {
            restoreStateFromJsonLocked(root)
            true
        }.getOrDefault(false)
        if (restored) {
            persistenceBlockedDueToLoadError = false
            _persistenceLoadError.value = PersistenceLoadErrorState()
            if (wasPlaintext) {
                if (candidate != file) {
                    runCatching {
                        Files.copy(
                            candidate.toPath(),
                            file.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                }
                // Migração v1.7.5 -> v1.8.0: regrava imediatamente criptografado em disco.
                persistStateLocked(encryptExistingPlaintextBackup = true)
            } else if (candidate != file) {
                runCatching {
                    Files.copy(
                        candidate.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }
            return true
        }
        sawInvalidOrCorruptFormat = true
    }

    // Todos os candidatos existentes falharam: preserva os arquivos originais, cria cópia em quarentena
    // e bloqueia gravações futuras para impedir sobrescrita silenciosa dos dados do estudante.
    val quarantinedNames = if (allowQuarantineCreation && _persistenceLoadError.value.quarantinedFiles.isEmpty()) {
        createCorruptQuarantineCopies(existingCandidates)
    } else {
        _persistenceLoadError.value.quarantinedFiles
    }
    persistenceBlockedDueToLoadError = true
    applyVirginStateLocked()
    val reason = when {
        sawSecurityFailure && !sawInvalidOrCorruptFormat ->
            "A chave de proteção do aparelho está temporariamente indisponível ou foi alterada. Seus dados originais foram preservados intactos."
        sawSecurityFailure ->
            "Não foi possível descriptografar ou validar os arquivos salvos. Seus dados originais foram preservados em segurança."
        else ->
            "Os arquivos de dados salvos parecem incompletos ou corrompidos. Seus dados originais foram preservados em segurança."
    }
    _persistenceLoadError.value = PersistenceLoadErrorState(
        hasError = true,
        reason = reason,
        quarantinedFiles = quarantinedNames,
        detectedAtEpochMs = System.currentTimeMillis()
    )
    return false
}

private fun createCorruptQuarantineCopies(existingFiles: List<File>): List<String> {
    val timestamp = System.currentTimeMillis()
    val copiedNames = mutableListOf<String>()
    existingFiles.forEachIndexed { idx, source ->
        val parentDir = source.absoluteFile.parentFile ?: return@forEachIndexed
        var dest = File(parentDir, "${source.name}.corrupt-$timestamp")
        if (dest.exists()) {
            dest = File(parentDir, "${source.name}.corrupt-$timestamp-$idx")
        }
        val copied = runCatching {
            Files.copy(
                source.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            true
        }.getOrDefault(false)
        if (copied) {
            copiedNames.add(dest.name)
        }
    }
    return copiedNames
}

internal fun InMemoryCampusRepository.persistStateLocked(
    encryptExistingPlaintextBackup: Boolean = false
): Boolean {
    if (persistenceBlockedDueToLoadError) return false
    val targetFile = stateFile ?: return true
    var persisted = false
    try {
        val parentDir = targetFile.absoluteFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }
        val plaintextJson = buildPersistedJsonLocked().toString()
        val encryptedPayload = CampusCryptoEngine.encryptStatePayload(plaintextJson)
        val payloadBytes = encryptedPayload.toByteArray(Charsets.UTF_8)
        val tempFile = File(parentDir, "${targetFile.name}.tmp")
        val backupFile = File(parentDir, "${targetFile.name}.bak")

        FileOutputStream(tempFile).use { out ->
            out.write(payloadBytes)
            out.flush()
            runCatching { out.fd.sync() }
        }

        if (targetFile.exists() && targetFile.length() > 2L) {
            var backupWritten = false
            if (encryptExistingPlaintextBackup) {
                backupWritten = runCatching {
                    val prevRaw = targetFile.readText(Charsets.UTF_8)
                    val prevDecrypted = CampusCryptoEngine.decryptStatePayloadIfNeeded(prevRaw)
                    val encryptedBackupBytes = CampusCryptoEngine
                        .encryptStatePayload(prevDecrypted)
                        .toByteArray(Charsets.UTF_8)
                    FileOutputStream(backupFile).use { out ->
                        out.write(encryptedBackupBytes)
                        out.flush()
                        runCatching { out.fd.sync() }
                    }
                    true
                }.getOrDefault(false)
            }
            if (!backupWritten) {
                runCatching {
                    Files.copy(
                        targetFile.toPath(),
                        backupFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }.onFailure {
                    runCatching {
                        targetFile.inputStream().use { input ->
                            FileOutputStream(backupFile).use { out ->
                                input.copyTo(out)
                                out.flush()
                                runCatching { out.fd.sync() }
                            }
                        }
                    }
                }
            }
        } else if (encryptExistingPlaintextBackup && backupFile.exists()) {
            runCatching {
                val prevBakRaw = backupFile.readText(Charsets.UTF_8)
                val prevBakDecrypted = CampusCryptoEngine.decryptStatePayloadIfNeeded(prevBakRaw)
                if (prevBakRaw == prevBakDecrypted) {
                    val encryptedBakBytes = CampusCryptoEngine
                        .encryptStatePayload(prevBakDecrypted)
                        .toByteArray(Charsets.UTF_8)
                    FileOutputStream(backupFile).use { out ->
                        out.write(encryptedBakBytes)
                        out.flush()
                        runCatching { out.fd.sync() }
                    }
                }
            }
        }

        val moved = runCatching {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
            true
        }.recoverCatching {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            true
        }.getOrDefault(false)

        if (!moved) {
            FileOutputStream(targetFile).use { out ->
                out.write(payloadBytes)
                out.flush()
                runCatching { out.fd.sync() }
            }
            runCatching { tempFile.delete() }
        }
        persisted = true
    } catch (_: Throwable) {
        // Persistência best-effort: mantém estado em memória se I/O falhar
    }
    if (persisted) {
        runCatching { onStatePersisted?.invoke() }
    }
    return persisted
}
