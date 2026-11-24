package br.ufop.campus.core.ffi

import br.ufop.campus.core.security.CampusCryptoEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.spec.SecretKeySpec

class UfopGradeCodecTest {

    @Before
    fun setUpTestCryptoKey() {
        val syntheticKeyBytes = ByteArray(32) { idx -> (0xA5 xor (idx * 7 + 3)).toByte() }
        CampusCryptoEngine.setTestKeyOverrideForJvm(SecretKeySpec(syntheticKeyBytes, "AES"))
    }

    @After
    fun tearDownTestCryptoKey() {
        CampusCryptoEngine.setTestKeyOverrideForJvm(null)
    }

    @Test
    fun parseAndValidatePreview_canonicalV1WithAvatar_succeedsAndImportsIntoRepository() {
        val avatarBytes = "fake-png-avatar".toByteArray(Charsets.UTF_8)
        val avatarB64 = Base64.getEncoder().encodeToString(avatarBytes)
        val payload = JSONObject().apply {
            put("format", UfopGradeCodec.FORMAT_V1)
            put("friendName", "Mariana Souza")
            put("courseName", "Ciência da Computação")
            put("semester", "2026.2")
            put("campusId", "op_cruzeiro")
            put("avatarBase64", avatarB64)
            put(
                "courses",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("courseId", "bcc202_2026_2")
                            put("code", "BCC202")
                            put("name", "Estruturas de Dados I")
                            put("departmentId", "decom")
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("courseId", "mtm125_2026_2")
                            put("code", "MTM125")
                            put("name", "Equações Diferenciais")
                            put("departmentId", "demat")
                        }
                    )
                }
            )
            put(
                "slots",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("slotId", "slot_1")
                            put("courseId", "bcc202_2026_2")
                            put("dayOfWeek", 1)
                            put("startTime", "08:00")
                            put("endTime", "09:40")
                            put("shortLocation", "ICEB · 101")
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("slotId", "slot_2")
                            put("courseId", "mtm125_2026_2")
                            put("dayOfWeek", 3)
                            put("startTime", "10:00")
                            put("endTime", "11:40")
                            put("shortLocation", "ICEB · 204")
                        }
                    )
                }
            )
        }.toString(2)

        val preview = UfopGradeCodec.parseAndValidatePreview(
            rawText = payload,
            sourceLabel = "grade_mariana.ufopgrade"
        ).getOrThrow()

        assertEquals("Mariana Souza", preview.friendName)
        assertEquals("Ciência da Computação", preview.courseName)
        assertEquals("2026.2", preview.semester)
        assertEquals(2, preview.courseCount)
        assertEquals(2, preview.slotCount)
        assertEquals(avatarB64, preview.avatarBase64)
        assertEquals("grade_mariana.ufopgrade", preview.sourceLabel)

        val repo = InMemoryCampusRepository()
        val imported = repo.importFriendScheduleJson("", preview.rawJsonPayload)
        assertEquals("Mariana Souza", imported.friendName)
        assertEquals("Ciência da Computação", imported.courseName)
        assertEquals("2026.2", imported.semester)
        assertEquals(avatarB64, imported.avatarBase64)
        assertTrue(imported.timetable.rows.isNotEmpty())
        val importedCells = imported.timetable.rows.flatMap { it.cellsByDay.values }.filterNotNull()
        assertTrue(importedCells.any { it.courseName.contains("Estruturas de Dados") })
        assertEquals(2, imported.timetable.legend.size)
    }

    @Test
    fun parseAndValidatePreview_legacyAndroidAndRustPayloads_succeed() {
        val legacyAndroid = """
            {
              "semester": "2026.2",
              "campusId": "op_cruzeiro",
              "courses": [
                {
                  "courseId": "bcc202_2026_2",
                  "code": "BCC202",
                  "name": "Estruturas de Dados I",
                  "departmentId": "comp"
                }
              ],
              "slots": [
                {
                  "slotId": "s1",
                  "courseId": "bcc202_2026_2",
                  "dayOfWeek": 2,
                  "startTime": "08:00",
                  "endTime": "09:40",
                  "building": "ICEB",
                  "room": "Sala 102"
                }
              ]
            }
        """.trimIndent()

        val androidPreview = UfopGradeCodec.parseAndValidatePreview(legacyAndroid).getOrThrow()
        assertEquals("Amigo UFOP", androidPreview.friendName)
        assertEquals("2026.2", androidPreview.semester)
        assertEquals(1, androidPreview.courseCount)
        assertEquals(1, androidPreview.slotCount)
        assertNull(androidPreview.avatarBase64)

        val androidCanonical = JSONObject(androidPreview.rawJsonPayload)
        assertEquals(UfopGradeCodec.FORMAT_V1, androidCanonical.getString("format"))
        assertEquals(
            "decom",
            androidCanonical.getJSONArray("courses").getJSONObject(0).getString("departmentId")
        )
        assertEquals(
            "ICEB · 102",
            androidCanonical.getJSONArray("slots").getJSONObject(0).getString("shortLocation")
        )

        val legacyRust = """
            {
              "semester": "2027.1",
              "departments": [],
              "courses": [
                {
                  "id": "fis105_2027_1",
                  "semester": "2027.1",
                  "department_id": "phys",
                  "name": "Física I",
                  "professor": "Prof. Silva",
                  "workload_hours": 60,
                  "notes": "Código: FIS105"
                }
              ],
              "schedule_slots": [
                {
                  "id": "slot_r1",
                  "course_id": "fis105_2027_1",
                  "day_of_week": 4,
                  "start_time": "13:30",
                  "end_time": "15:10",
                  "building": "ICEB",
                  "room": "Sala 301",
                  "short_location": ""
                }
              ]
            }
        """.trimIndent()

        val rustPreview = UfopGradeCodec.parseAndValidatePreview(legacyRust).getOrThrow()
        assertEquals("2027.1", rustPreview.semester)
        assertEquals(1, rustPreview.courseCount)
        assertEquals(1, rustPreview.slotCount)
        val rustCanonical = JSONObject(rustPreview.rawJsonPayload)
        val rustCourse = rustCanonical.getJSONArray("courses").getJSONObject(0)
        assertEquals("FIS105", rustCourse.getString("code"))
        assertEquals("defis", rustCourse.getString("departmentId"))
        val rustSlot = rustCanonical.getJSONArray("slots").getJSONObject(0)
        assertEquals(4, rustSlot.getInt("dayOfWeek"))
        assertEquals("13:30", rustSlot.getString("startTime"))
        assertEquals("15:10", rustSlot.getString("endTime"))
        assertEquals("ICEB · 301", rustSlot.getString("shortLocation"))
    }

    @Test
    fun parseAndValidatePreview_legacyIosScheduleExportPayload_mapsTimetableAndCourseSlots() {
        val iosTimetablePayload = """
            {
              "semester": "2026.2",
              "activeCampusId": "op_cruzeiro",
              "disciplines": [
                {
                  "id": "disc_bcc",
                  "code": "BCC202",
                  "name": "Estruturas de Dados I",
                  "department": "Computação",
                  "colorKey": "COMP_PURPLE"
                },
                {
                  "id": "disc_fis",
                  "code": "FIS105",
                  "name": "Física I",
                  "department": "Física",
                  "colorKey": "PHYSICS_AMBER"
                }
              ],
              "timetableSlots": [
                {
                  "id": "tt_1",
                  "weekdayShort": "SEG",
                  "timeRange": "08:00–09:40",
                  "disciplineId": "disc_bcc",
                  "disciplineShortName": "BCC202",
                  "roomLabel": "ICEB · 101",
                  "colorKey": "COMP_PURPLE"
                },
                {
                  "id": "tt_2",
                  "weekdayShort": "SÁB",
                  "timeRange": "10:00-11:40",
                  "disciplineId": "disc_fis",
                  "disciplineShortName": "FIS105",
                  "roomLabel": "ICEB · 202",
                  "colorKey": "PHYSICS_AMBER"
                }
              ]
            }
        """.trimIndent()

        val preview = UfopGradeCodec.parseAndValidatePreview(iosTimetablePayload).getOrThrow()
        assertEquals(2, preview.courseCount)
        assertEquals(2, preview.slotCount)

        val canonical = JSONObject(preview.rawJsonPayload)
        val courses = canonical.getJSONArray("courses")
        assertEquals("decom", courses.getJSONObject(0).getString("departmentId"))
        assertEquals("defis", courses.getJSONObject(1).getString("departmentId"))

        val slots = canonical.getJSONArray("slots")
        assertEquals(1, slots.getJSONObject(0).getInt("dayOfWeek"))
        assertEquals("08:00", slots.getJSONObject(0).getString("startTime"))
        assertEquals("09:40", slots.getJSONObject(0).getString("endTime"))
        assertEquals("ICEB · 101", slots.getJSONObject(0).getString("shortLocation"))
        assertEquals(6, slots.getJSONObject(1).getInt("dayOfWeek"))
        assertEquals("10:00", slots.getJSONObject(1).getString("startTime"))
        assertEquals("11:40", slots.getJSONObject(1).getString("endTime"))

        val imported = InMemoryCampusRepository().importFriendScheduleJson("", preview.rawJsonPayload)
        assertEquals(2, imported.timetable.legend.size)

        val iosWithCourseSlots = """
            {
              "semester": "2026.2",
              "activeCampusId": "jm_icea",
              "disciplines": [
                {
                  "id": "csi101",
                  "code": "CSI101",
                  "name": "Programação de Computadores I",
                  "department": "Matemática",
                  "colorKey": "MATH_BLUE"
                }
              ],
              "courseScheduleSlots": [
                {
                  "slotId": "cs_1",
                  "courseId": "csi101",
                  "dayOfWeek": 5,
                  "startTime": "19:00",
                  "endTime": "20:40",
                  "shortLocation": "ICEA · A103"
                }
              ],
              "timetableSlots": [
                {
                  "id": "tt_ignored",
                  "weekdayShort": "SEG",
                  "timeRange": "08:00–09:40",
                  "disciplineId": "csi101",
                  "roomLabel": "IGNORED"
                }
              ]
            }
        """.trimIndent()

        val courseSlotsPreview = UfopGradeCodec.parseAndValidatePreview(iosWithCourseSlots).getOrThrow()
        val courseSlotsRoot = JSONObject(courseSlotsPreview.rawJsonPayload)
        assertEquals("jm_icea", courseSlotsRoot.getString("campusId"))
        assertEquals(1, courseSlotsRoot.getJSONArray("slots").length())
        assertEquals(5, courseSlotsRoot.getJSONArray("slots").getJSONObject(0).getInt("dayOfWeek"))
        assertEquals("ICEA · A103", courseSlotsRoot.getJSONArray("slots").getJSONObject(0).getString("shortLocation"))
    }

    @Test
    fun parseAndValidatePreview_rejectsInvalidEmptyCorruptAndOversizedInputs() {
        assertTrue(UfopGradeCodec.parseAndValidatePreview("").isFailure)
        assertTrue(UfopGradeCodec.parseAndValidatePreview("   \n\t  ").isFailure)
        assertTrue(UfopGradeCodec.parseAndValidatePreview("not a json payload").isFailure)

        val emptyCollections = """
            {
              "format": "ufopgrade-v1",
              "friendName": "Ana",
              "semester": "2026.2",
              "courses": [],
              "slots": []
            }
        """.trimIndent()
        assertTrue(UfopGradeCodec.parseAndValidatePreview(emptyCollections).isFailure)

        assertTrue(UfopGradeCodec.decodeCompactQr("INVALID:abc").isFailure)
        assertTrue(UfopGradeCodec.decodeCompactQr("${UfopGradeCodec.QR_PREFIX}").isFailure)
        assertTrue(UfopGradeCodec.decodeCompactQr("${UfopGradeCodec.QR_PREFIX}!!!invalid-b64!!!").isFailure)
        assertTrue(UfopGradeCodec.decodeCompactQr("${UfopGradeCodec.QR_PREFIX}YWJjZGVm").isFailure)
        assertTrue(UfopGradeCodec.parseAndValidatePreview("${UfopGradeCodec.QR_PREFIX}corrompido").isFailure)

        val oversized = "x".repeat(UfopGradeCodec.MAX_PAYLOAD_BYTES + 1)
        assertTrue(UfopGradeCodec.parseAndValidatePreview(oversized).isFailure)

        // Verifica que optSafeString trata "null" literal e JSON null corretamente
        val nullFieldsJson = """
            {
              "format": "ufopgrade-v1",
              "friendName": null,
              "courseName": "null",
              "avatarBase64": "null",
              "semester": null,
              "courses": [
                {
                  "courseId": "bcc101",
                  "code": "BCC101",
                  "name": "Algoritmos",
                  "departmentId": "decom"
                }
              ],
              "slots": []
            }
        """.trimIndent()
        val nullPreview = UfopGradeCodec.parseAndValidatePreview(nullFieldsJson).getOrThrow()
        assertEquals("Amigo UFOP", nullPreview.friendName)
        assertEquals("", nullPreview.courseName)
        assertEquals("2026.2", nullPreview.semester)
        assertNull(nullPreview.avatarBase64)
    }

    @Test
    fun encodeAndDecodeCompactQr_roundTripStripsAvatarIsUrlSafeAndCompact() {
        val largeAvatar = Base64.getEncoder().encodeToString(ByteArray(4096) { 0x42 })
        val courses = JSONArray()
        val slots = JSONArray()
        val sampleCourses = listOf(
            Triple("bcc202_2026_2", "BCC202", "Estruturas de Dados I"),
            Triple("bcc221_2026_2", "BCC221", "Programação Orientada a Objetos"),
            Triple("mtm125_2026_2", "MTM125", "Equações Diferenciais"),
            Triple("fis105_2026_2", "FIS105", "Física I"),
            Triple("cat101_2026_2", "CAT101", "Introdução à Instrumentação")
        )
        sampleCourses.forEachIndexed { idx, (id, code, name) ->
            courses.put(
                JSONObject().apply {
                    put("courseId", id)
                    put("code", code)
                    put("name", name)
                    put("departmentId", if (code.startsWith("MTM")) "demat" else if (code.startsWith("FIS")) "defis" else "decom")
                }
            )
            slots.put(
                JSONObject().apply {
                    put("slotId", "orig_${idx * 2 + 1}")
                    put("courseId", id)
                    put("dayOfWeek", (idx % 5) + 1)
                    put("startTime", "08:00")
                    put("endTime", "09:40")
                    put("shortLocation", "ICEB · 10${idx + 1}")
                }
            )
            slots.put(
                JSONObject().apply {
                    put("slotId", "orig_${idx * 2 + 2}")
                    put("courseId", id)
                    put("dayOfWeek", ((idx + 2) % 5) + 1)
                    put("startTime", "10:00")
                    put("endTime", "11:40")
                    put("shortLocation", "ICEB · 20${idx + 1}")
                }
            )
        }

        val bundleJson = JSONObject().apply {
            put("format", UfopGradeCodec.FORMAT_V1)
            put("friendName", "João Pedro")
            put("courseName", "Ciência da Computação")
            put("semester", "2026.2")
            put("campusId", "op_cruzeiro")
            put("avatarBase64", largeAvatar)
            put("courses", courses)
            put("slots", slots)
        }.toString(2)

        val qrString = UfopGradeCodec.encodeCompactQr(bundleJson)
        assertTrue(qrString.startsWith(UfopGradeCodec.QR_PREFIX))
        assertTrue("Tamanho esperado < 600 chars, obtido=${qrString.length}", qrString.length < 600)

        val encodedPart = qrString.removePrefix(UfopGradeCodec.QR_PREFIX)
        assertFalse(encodedPart.contains('='))
        assertFalse(encodedPart.contains('+'))
        assertFalse(encodedPart.contains('/'))

        val decodedJson = UfopGradeCodec.decodeCompactQr(qrString).getOrThrow()
        val decodedRoot = JSONObject(decodedJson)
        assertFalse(decodedRoot.has("avatarBase64"))
        assertEquals(UfopGradeCodec.FORMAT_V1, decodedRoot.getString("format"))
        assertEquals("João Pedro", decodedRoot.getString("friendName"))
        assertEquals("Ciência da Computação", decodedRoot.getString("courseName"))
        assertEquals(5, decodedRoot.getJSONArray("courses").length())
        assertEquals(10, decodedRoot.getJSONArray("slots").length())
        assertEquals("qr_1", decodedRoot.getJSONArray("slots").getJSONObject(0).getString("slotId"))

        val preview = UfopGradeCodec.parseAndValidatePreview(qrString).getOrThrow()
        assertEquals("QR Code", preview.sourceLabel)
        assertEquals("João Pedro", preview.friendName)
        assertEquals("Ciência da Computação", preview.courseName)
        assertEquals("2026.2", preview.semester)
        assertEquals(5, preview.courseCount)
        assertEquals(10, preview.slotCount)
        assertNull(preview.avatarBase64)

        val imported = InMemoryCampusRepository().importFriendScheduleJson("", preview.rawJsonPayload)
        assertEquals("João Pedro", imported.friendName)
        assertNull(imported.avatarBase64)
        val importedCourseIds = imported.timetable.rows
            .flatMap { it.cellsByDay.values }
            .filterNotNull()
            .map { it.courseId }
            .toSet()
        assertEquals(5, importedCourseIds.size)
        assertEquals(3, imported.timetable.legend.size)
    }

    @Test
    fun decodeCompactQr_deterministicRawDeflateVectorWithoutZlibHeader_decodesCleanly() {
        val compactVector = JSONObject().apply {
            put("v", 1)
            put("n", "Lucas Mendes")
            put("c", "Engenharia de Computação")
            put("s", "2026.2")
            put("m", "jm_icea")
            put(
                "d",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("i", "csi101_2026_2")
                            put("k", "CSI101")
                            put("t", "Programação I")
                            put("p", "decsi")
                        }
                    )
                }
            )
            put(
                "h",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("c", "csi101_2026_2")
                            put("w", 2)
                            put("a", "13:30")
                            put("b", "15:10")
                            put("l", "ICEA · C201")
                        }
                    )
                }
            )
        }.toString()

        val rawBytes = compactVector.toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        val compressed = try {
            deflater.setInput(rawBytes)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                if (n > 0) out.write(buf, 0, n)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }

        // Confirma ausência de cabeçalho zlib (0x78 0x9C) — fluxo RFC 1951 puro
        assertTrue(compressed.size > 2)
        assertFalse(compressed[0] == 0x78.toByte() && compressed[1] == 0x9C.toByte())

        // Verifica também que Inflater(true) decodifica os bytes originais sem trailing byte adicional
        val verifyInflater = Inflater(true)
        val inflatedDirect = try {
            verifyInflater.setInput(compressed)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!verifyInflater.finished()) {
                val n = verifyInflater.inflate(buf)
                if (n > 0) out.write(buf, 0, n) else break
            }
            out.toByteArray().toString(Charsets.UTF_8)
        } finally {
            verifyInflater.end()
        }
        assertEquals(compactVector, inflatedDirect)

        val qrToken = UfopGradeCodec.QR_PREFIX +
            Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)

        val decodedJson = UfopGradeCodec.decodeCompactQr(qrToken).getOrThrow()
        assertNotNull(decodedJson)
        val preview = UfopGradeCodec.parseAndValidatePreview(qrToken, sourceLabel = "QR Code").getOrThrow()
        assertEquals("Lucas Mendes", preview.friendName)
        assertEquals("Engenharia de Computação", preview.courseName)
        assertEquals("2026.2", preview.semester)
        assertEquals(1, preview.courseCount)
        assertEquals(1, preview.slotCount)
        assertEquals("QR Code", preview.sourceLabel)
        assertNull(preview.avatarBase64)

        val canonical = JSONObject(preview.rawJsonPayload)
        assertEquals("jm_icea", canonical.getString("campusId"))
        val course = canonical.getJSONArray("courses").getJSONObject(0)
        assertEquals("csi101_2026_2", course.getString("courseId"))
        assertEquals("CSI101", course.getString("code"))
        assertEquals("Programação I", course.getString("name"))
        assertEquals("decsi", course.getString("departmentId"))

        val slot = canonical.getJSONArray("slots").getJSONObject(0)
        assertEquals("qr_1", slot.getString("slotId"))
        assertEquals("csi101_2026_2", slot.getString("courseId"))
        assertEquals(2, slot.getInt("dayOfWeek"))
        assertEquals("13:30", slot.getString("startTime"))
        assertEquals("15:10", slot.getString("endTime"))
        assertEquals("ICEA · C201", slot.getString("shortLocation"))
    }
}
