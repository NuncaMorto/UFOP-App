package br.ufop.campus

import br.ufop.campus.core.ffi.FriendScheduleInfo
import br.ufop.campus.core.ffi.StudentProfile
import br.ufop.campus.core.ffi.WeeklyTimetable
import br.ufop.campus.ui.timetable.buildInitials
import br.ufop.campus.ui.timetable.buildTimetableHeaderSubtitle
import br.ufop.campus.ui.timetable.resolveGradeSelectorAvatarSource
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableSharedScheduleTest {

    private fun emptyTimetable(semester: String = "2026.2"): WeeklyTimetable = WeeklyTimetable(
        semester = semester,
        gridTitle = "Grade $semester",
        days = emptyList(),
        rows = emptyList(),
        legend = emptyList()
    )

    private fun sampleAvatarBase64(tag: String): String {
        val encoded = Base64.getEncoder().encodeToString("avatar-$tag".toByteArray(Charsets.UTF_8))
        return "data:image/png;base64,$encoded"
    }

    @Test
    fun resolveGradeSelectorAvatarSource_usesStudentAvatarForMyGrade() {
        val student = StudentProfile(
            fullName = "Mariana Souza",
            avatarPath = "/data/user/0/br.ufop.campus/files/avatar.jpg"
        )

        val source = resolveGradeSelectorAvatarSource(
            activeFriend = null,
            studentProfile = student
        )

        assertEquals("Mariana Souza", source.displayName)
        assertNull(source.avatarBase64)
        assertEquals("/data/user/0/br.ufop.campus/files/avatar.jpg", source.avatarFilePath)

        val fallbackStudent = StudentProfile(fullName = "   ", avatarPath = "   ")
        val fallbackSource = resolveGradeSelectorAvatarSource(
            activeFriend = null,
            studentProfile = fallbackStudent
        )
        assertEquals("Você", fallbackSource.displayName)
        assertNull(fallbackSource.avatarBase64)
        assertNull(fallbackSource.avatarFilePath)
    }

    @Test
    fun resolveGradeSelectorAvatarSource_usesFriendBase64AndClearsLocalFilePath() {
        val student = StudentProfile(
            fullName = "Mariana Souza",
            avatarPath = "/data/user/0/br.ufop.campus/files/avatar.jpg"
        )
        val friendAvatar = sampleAvatarBase64("friend-a")
        val friendA = FriendScheduleInfo(
            id = "friend-a",
            friendName = "Carlos Andrade",
            courseName = "Ciência da Computação",
            semester = "2026.2",
            avatarBase64 = friendAvatar,
            timetable = emptyTimetable("2026.2")
        )

        val source = resolveGradeSelectorAvatarSource(
            activeFriend = friendA,
            studentProfile = student
        )

        assertEquals("Carlos Andrade", source.displayName)
        assertEquals(friendAvatar, source.avatarBase64)
        assertNull(source.avatarFilePath)
    }

    @Test
    fun resolveGradeSelectorAvatarSource_returnsNullAvatarForFriendWithoutPhoto() {
        val student = StudentProfile(
            fullName = "Mariana Souza",
            avatarPath = "/data/user/0/br.ufop.campus/files/avatar.jpg"
        )
        val friendWithoutPhoto = FriendScheduleInfo(
            id = "friend-no-photo",
            friendName = "Beatriz Lima",
            courseName = "Engenharia de Produção",
            semester = "2026.2",
            avatarBase64 = null,
            timetable = emptyTimetable("2026.2")
        )
        val friendBlankPhoto = friendWithoutPhoto.copy(
            id = "friend-blank-photo",
            avatarBase64 = "   "
        )

        val sourceNull = resolveGradeSelectorAvatarSource(
            activeFriend = friendWithoutPhoto,
            studentProfile = student
        )
        assertEquals("Beatriz Lima", sourceNull.displayName)
        assertNull(sourceNull.avatarBase64)
        assertNull(sourceNull.avatarFilePath)

        val sourceBlank = resolveGradeSelectorAvatarSource(
            activeFriend = friendBlankPhoto,
            studentProfile = student
        )
        assertEquals("Beatriz Lima", sourceBlank.displayName)
        assertNull(sourceBlank.avatarBase64)
        assertNull(sourceBlank.avatarFilePath)
    }

    @Test
    fun resolveGradeSelectorAvatarSource_switchesCleanlyBetweenFriendsAndMyGrade() {
        val student = StudentProfile(
            fullName = "Mariana Souza",
            avatarPath = "/data/user/0/br.ufop.campus/files/avatar.jpg"
        )
        val avatarA = sampleAvatarBase64("alice")
        val avatarB = sampleAvatarBase64("bruno")
        val friendA = FriendScheduleInfo(
            id = "friend-a",
            friendName = "Alice Mendes",
            courseName = "Direito",
            semester = "2026.2",
            avatarBase64 = avatarA,
            timetable = emptyTimetable("2026.2")
        )
        val friendB = FriendScheduleInfo(
            id = "friend-b",
            friendName = "Bruno Rocha",
            courseName = "Arquitetura",
            semester = "2026.2",
            avatarBase64 = avatarB,
            timetable = emptyTimetable("2026.2")
        )
        val friendCNoPhoto = FriendScheduleInfo(
            id = "friend-c",
            friendName = "Camila Costa",
            courseName = "Farmácia",
            semester = "2026.2",
            avatarBase64 = null,
            timetable = emptyTimetable("2026.2")
        )

        val myGrade = resolveGradeSelectorAvatarSource(null, student)
        val sourceA = resolveGradeSelectorAvatarSource(friendA, student)
        val sourceB = resolveGradeSelectorAvatarSource(friendB, student)
        val sourceC = resolveGradeSelectorAvatarSource(friendCNoPhoto, student)
        val backToMyGrade = resolveGradeSelectorAvatarSource(null, student)

        assertNotEquals(sourceA.avatarBase64, sourceB.avatarBase64)
        assertEquals(avatarA, sourceA.avatarBase64)
        assertNull(sourceA.avatarFilePath)

        assertEquals(avatarB, sourceB.avatarBase64)
        assertNull(sourceB.avatarFilePath)

        assertNull(sourceC.avatarBase64)
        assertNull(sourceC.avatarFilePath)

        assertEquals(myGrade, backToMyGrade)
        assertNull(backToMyGrade.avatarBase64)
        assertEquals(student.avatarPath, backToMyGrade.avatarFilePath)
    }

    @Test
    fun buildInitials_handlesMultiWordSingleWordAndBlankNames() {
        assertEquals("CA", buildInitials("Carlos Andrade"))
        assertEquals("MS", buildInitials("  Mariana   Oliveira   Souza  "))
        assertEquals("AN", buildInitials("Ana"))
        assertEquals("X", buildInitials("x"))
        assertEquals("UF", buildInitials(""))
        assertEquals("UF", buildInitials("   "))
    }

    @Test
    fun buildTimetableHeaderSubtitle_formatsMyGradeAndSharedFriendWithoutRedundantName() {
        val myGradeSubtitle = buildTimetableHeaderSubtitle(
            effectiveSemester = "2026.2",
            timetableSemester = "2026.2",
            activeFriend = null
        )
        assertEquals(
            "Semestre 2026.2 · Toque em uma aula para ver detalhes",
            myGradeSubtitle
        )

        val friendWithCourse = FriendScheduleInfo(
            id = "friend-1",
            friendName = "Lucas Ferreira",
            courseName = "Ciência da Computação",
            semester = "2026.2",
            timetable = emptyTimetable("2026.2")
        )
        val subtitleWithCourse = buildTimetableHeaderSubtitle(
            effectiveSemester = "2026.2",
            timetableSemester = "2026.2",
            activeFriend = friendWithCourse
        )
        assertEquals(
            "Grade compartilhada · Ciência da Computação · 2026.2",
            subtitleWithCourse
        )
        assertFalse(subtitleWithCourse.contains("Lucas"))
        assertFalse(subtitleWithCourse.contains("Ferreira"))
        assertTrue(subtitleWithCourse.contains("Ciência da Computação"))
        assertTrue(subtitleWithCourse.contains("2026.2"))

        val friendWithoutCourse = FriendScheduleInfo(
            id = "friend-2",
            friendName = "Fernanda Alves",
            courseName = "   ",
            semester = "2027.1",
            timetable = emptyTimetable("2027.1")
        )
        val subtitleWithoutCourse = buildTimetableHeaderSubtitle(
            effectiveSemester = "2026.2",
            timetableSemester = "2027.1",
            activeFriend = friendWithoutCourse
        )
        assertEquals(
            "Grade compartilhada · Semestre 2027.1",
            subtitleWithoutCourse
        )
        assertFalse(subtitleWithoutCourse.contains("Fernanda"))
        assertFalse(subtitleWithoutCourse.contains("Alves"))
        assertTrue(subtitleWithoutCourse.contains("2027.1"))
    }
}
