import Foundation
import Observation

public struct InitialScheduleSlotInput: Equatable, Sendable {
    public let dayOfWeek: Int
    public let startTime: String
    public let endTime: String
    public let building: String
    public let room: String
    public let shortLocation: String

    public init(
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String = ""
    ) {
        self.dayOfWeek = dayOfWeek
        self.startTime = startTime
        self.endTime = endTime
        self.building = building
        self.room = room
        self.shortLocation = shortLocation
    }
}

@Observable
public final class DisciplinesViewModel {
    public let store: CampusStore
    public var selectedDisciplineId: String?
    public var isTimetablePresented: Bool = false
    public var isAddCoursePresented: Bool = false

    public init(store: CampusStore) {
        self.store = store
    }

    public var departmentFilter: String {
        get { store.selectedDepartmentFilter }
        set { store.selectedDepartmentFilter = newValue }
    }

    public var semesterFilter: String {
        get { store.selectedSemesterFilter }
        set { store.selectedSemesterFilter = newValue }
    }

    public var availableDepartments: [String] {
        store.availableDepartments
    }

    public var availableSemesters: [String] {
        store.availableSemesters
    }

    public var disciplines: [DisciplineSummaryCard] {
        store.filteredDisciplines
    }

    public var activeDisciplineDetail: DisciplineDetail? {
        guard let selectedDisciplineId else { return nil }
        return store.getDisciplineDetail(disciplineId: selectedDisciplineId)
    }

    public func openDisciplineDetail(_ card: DisciplineSummaryCard) {
        selectedDisciplineId = card.id
    }

    public func dismissDisciplineDetail() {
        selectedDisciplineId = nil
    }

    public func openAddCourseSheet() {
        isAddCoursePresented = true
    }

    public func dismissAddCourseSheet() {
        isAddCoursePresented = false
    }

    public func previewMaxAbsences(for workloadHours: Int) -> Int {
        CampusStore.calculateMaxAbsences(workloadHours: workloadHours)
    }

    @discardableResult
    public func createCourse(
        semester: String? = nil,
        departmentId: String,
        name: String,
        professor: String,
        workloadHours: Int,
        colorOverrideHex: String? = nil,
        initialSlot: InitialScheduleSlotInput? = nil
    ) throws -> DisciplineSummaryCard {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            throw CampusScheduleError.emptyField("Nome da disciplina")
        }

        let trimmedProfessor = professor.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedProfessor.isEmpty else {
            throw CampusScheduleError.emptyField("Docente")
        }

        let targetSemester = semester ?? store.selectedSemesterFilter
        let created = store.createCourse(
            semester: targetSemester,
            departmentId: departmentId,
            name: trimmedName,
            professor: trimmedProfessor,
            workloadHours: workloadHours,
            colorOverrideHex: colorOverrideHex
        )

        if let initialSlot {
            do {
                _ = try store.addScheduleSlot(
                    courseId: created.id,
                    dayOfWeek: initialSlot.dayOfWeek,
                    startTime: initialSlot.startTime,
                    endTime: initialSlot.endTime,
                    building: initialSlot.building,
                    room: initialSlot.room,
                    shortLocation: initialSlot.shortLocation
                )
            } catch {
                // Garante atomicidade caso o horário inicial entre em conflito
                store.deleteCourse(courseId: created.id)
                throw error
            }
        }

        if store.selectedDepartmentFilter != "Todos os departamentos"
            && store.selectedDepartmentFilter != created.department {
            store.selectedDepartmentFilter = "Todos os departamentos"
        }

        isAddCoursePresented = false
        return created
    }

    public func deleteCourse(courseId: String) {
        store.deleteCourse(courseId: courseId)
        if selectedDisciplineId == courseId {
            selectedDisciplineId = nil
        }
    }

    public func listCourseSlots(courseId: String) -> [CourseScheduleSlotItem] {
        store.listCourseSlots(courseId: courseId)
    }

    @discardableResult
    public func addScheduleSlot(
        courseId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ) throws -> CourseScheduleSlotItem {
        try store.addScheduleSlot(
            courseId: courseId,
            dayOfWeek: dayOfWeek,
            startTime: startTime,
            endTime: endTime,
            building: building,
            room: room,
            shortLocation: shortLocation
        )
    }

    public func deleteScheduleSlot(slotId: String) {
        store.deleteScheduleSlot(slotId: slotId)
    }

    public func saveDisciplineNotes(disciplineId: String, notes: String) {
        store.updateDisciplineNotes(disciplineId: disciplineId, notes: notes)
    }

    public func toggleAssessmentCompleted(assessmentId: String) {
        _ = store.toggleAssessmentCompleted(assessmentId: assessmentId)
    }

    public func createAssessment(
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String
    ) {
        _ = store.createAssessment(
            courseId: courseId,
            title: title,
            kind: kind,
            dueDate: dueDate,
            dueTime: dueTime
        )
    }

    public func deleteAssessment(assessmentId: String) {
        store.deleteAssessment(assessmentId: assessmentId)
    }

    public func openTimetable() {
        isTimetablePresented = true
    }

    public func dismissTimetable() {
        isTimetablePresented = false
    }
}
