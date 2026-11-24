import Foundation

// MARK: - Dynamic Interactive Curriculum Grid Models

public struct DynamicSemesterTerm: Comparable, Equatable, Codable, Sendable {
    public let year: Int
    public let semester: Int

    public init(year: Int, semester: Int) {
        self.year = year
        self.semester = semester
    }

    public static func < (lhs: DynamicSemesterTerm, rhs: DynamicSemesterTerm) -> Bool {
        if lhs.year != rhs.year { return lhs.year < rhs.year }
        return lhs.semester < rhs.semester
    }

    public func formatLabel() -> String {
        "\(year)/\(semester)"
    }
}

public struct DynamicCourseNode: Identifiable, Equatable, Codable, Sendable {
    public let code: String
    public let name: String
    public let workloadHours: Int
    public let period: Int
    public let prerequisites: [String]
    public let minHoursRequired: Int
    public let isElective: Bool

    public var id: String { code }

    public init(
        code: String,
        name: String,
        workloadHours: Int,
        period: Int,
        prerequisites: [String] = [],
        minHoursRequired: Int = 0,
        isElective: Bool = false
    ) {
        self.code = code
        self.name = name
        self.workloadHours = workloadHours
        self.period = period
        self.prerequisites = prerequisites
        self.minHoursRequired = minHoursRequired
        self.isElective = isElective
    }
}

public struct DynamicCurriculumMatrix: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let courseName: String
    public let curriculumLabel: String
    public let totalPeriods: Int
    public let requiredActivitiesHours: Int
    public let totalCourseHours: Int
    public let courses: [DynamicCourseNode]
    public let courseCode: String
    public let courseAliases: [String]
    public let effectiveFrom: DynamicSemesterTerm
    public let effectiveUntil: DynamicSemesterTerm?
    public let legacyIds: [String]
    public let campus: String

    public var totalGridHours: Int {
        courses.reduce(0) { $0 + $1.workloadHours }
    }

    public func validityLabel() -> String {
        if let effectiveUntil {
            return "\(effectiveFrom.formatLabel())–\(effectiveUntil.formatLabel())"
        }
        return "\(effectiveFrom.formatLabel())+"
    }

    public init(
        id: String,
        courseName: String,
        curriculumLabel: String,
        totalPeriods: Int,
        requiredActivitiesHours: Int,
        totalCourseHours: Int,
        courses: [DynamicCourseNode],
        courseCode: String = "",
        courseAliases: [String] = [],
        effectiveFrom: DynamicSemesterTerm = DynamicSemesterTerm(year: 2024, semester: 1),
        effectiveUntil: DynamicSemesterTerm? = nil,
        legacyIds: [String] = [],
        campus: String = ""
    ) {
        self.id = id
        self.courseName = courseName
        self.curriculumLabel = curriculumLabel
        self.totalPeriods = totalPeriods
        self.requiredActivitiesHours = requiredActivitiesHours
        self.totalCourseHours = totalCourseHours
        self.courses = courses
        self.courseCode = courseCode
        self.courseAliases = courseAliases
        self.effectiveFrom = effectiveFrom
        self.effectiveUntil = effectiveUntil
        self.legacyIds = legacyIds
        self.campus = campus
    }

    public func course(forCode code: String) -> DynamicCourseNode? {
        let normalized = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return courses.first { $0.code.uppercased() == normalized }
    }

    public func courses(inPeriod period: Int) -> [DynamicCourseNode] {
        courses.filter { $0.period == period }
    }
}

public struct DynamicMatrixResolution: Equatable, Sendable {
    public let matrix: DynamicCurriculumMatrix
    public let studentEntryTerm: DynamicSemesterTerm?
    public let isCourseMatched: Bool
    public let isExactVersionMatch: Bool
    public let note: String?

    public init(
        matrix: DynamicCurriculumMatrix,
        studentEntryTerm: DynamicSemesterTerm?,
        isCourseMatched: Bool,
        isExactVersionMatch: Bool,
        note: String?
    ) {
        self.matrix = matrix
        self.studentEntryTerm = studentEntryTerm
        self.isCourseMatched = isCourseMatched
        self.isExactVersionMatch = isExactVersionMatch
        self.note = note
    }
}

public enum DynamicCourseStatus: String, Codable, Equatable, Sendable {
    case completed
    case available
    case locked
}

public enum DynamicGridToggleAction: String, Codable, Equatable, Sendable {
    case markedCompleted
    case unmarkedWithCascade
    case blockedByPrerequisites
    case noChange
}

public struct DynamicGridToggleResult: Equatable, Sendable {
    public let updatedCompletedCodes: Set<String>
    public let action: DynamicGridToggleAction
    public let affectedCode: String
    public let cascadeRemovedCodes: [String]
    public let missingPrerequisites: [String]
    public let missingHours: Int

    public init(
        updatedCompletedCodes: Set<String>,
        action: DynamicGridToggleAction,
        affectedCode: String,
        cascadeRemovedCodes: [String],
        missingPrerequisites: [String],
        missingHours: Int
    ) {
        self.updatedCompletedCodes = updatedCompletedCodes
        self.action = action
        self.affectedCode = affectedCode
        self.cascadeRemovedCodes = cascadeRemovedCodes
        self.missingPrerequisites = missingPrerequisites
        self.missingHours = missingHours
    }
}

public struct DynamicGridSummary: Equatable, Sendable {
    public let completedHours: Int
    public let totalGridHours: Int
    public let completionPercent: Int
    public let completedCount: Int
    public let totalCount: Int

    public var progressFraction: Double {
        guard totalGridHours > 0 else { return 0 }
        return min(1.0, max(0.0, Double(completedHours) / Double(totalGridHours)))
    }

    public init(
        completedHours: Int,
        totalGridHours: Int,
        completionPercent: Int,
        completedCount: Int,
        totalCount: Int
    ) {
        self.completedHours = completedHours
        self.totalGridHours = totalGridHours
        self.completionPercent = completionPercent
        self.completedCount = completedCount
        self.totalCount = totalCount
    }
}
