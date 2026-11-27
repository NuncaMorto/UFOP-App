import Foundation

// MARK: - Shared App Group Snapshot Models (App <-> WidgetExtension)

public struct WidgetSharedCourseSlot: Codable, Equatable, Sendable {
    public let slotId: String
    public let courseId: String
    public let courseName: String
    public let dayOfWeek: Int
    public let startTime: String
    public let endTime: String
    public let building: String
    public let room: String
    public let shortLocation: String
    public let fallbackLocation: String
    public let isEad: Bool

    public init(
        slotId: String,
        courseId: String,
        courseName: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String,
        fallbackLocation: String,
        isEad: Bool
    ) {
        self.slotId = slotId
        self.courseId = courseId
        self.courseName = courseName
        self.dayOfWeek = dayOfWeek
        self.startTime = startTime
        self.endTime = endTime
        self.building = building
        self.room = room
        self.shortLocation = shortLocation
        self.fallbackLocation = fallbackLocation
        self.isEad = isEad
    }
}

public struct WidgetSharedSessionStatus: Codable, Equatable, Sendable {
    public let dateIso: String
    public let slotId: String
    public let courseId: String
    public let startTime: String
    public let statusRaw: String

    public init(
        dateIso: String,
        slotId: String,
        courseId: String,
        startTime: String,
        statusRaw: String
    ) {
        self.dateIso = dateIso
        self.slotId = slotId
        self.courseId = courseId
        self.startTime = startTime
        self.statusRaw = statusRaw
    }
}

public struct WidgetSharedAssessment: Codable, Equatable, Sendable {
    public let id: String
    public let courseId: String
    public let courseCode: String
    public let courseName: String
    public let title: String
    public let kind: String
    public let dueDate: String
    public let dueTime: String
    public let completed: Bool

    public init(
        id: String,
        courseId: String,
        courseCode: String,
        courseName: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String,
        completed: Bool
    ) {
        self.id = id
        self.courseId = courseId
        self.courseCode = courseCode
        self.courseName = courseName
        self.title = title
        self.kind = kind
        self.dueDate = dueDate
        self.dueTime = dueTime
        self.completed = completed
    }
}

public struct WidgetSharedSnapshotPayload: Codable, Equatable, Sendable {
    public let schemaVersion: Int
    public let updatedAtEpochSeconds: TimeInterval
    public let activeSemester: String
    public let slots: [WidgetSharedCourseSlot]
    public let sessionStatuses: [WidgetSharedSessionStatus]
    public let assessments: [WidgetSharedAssessment]

    public init(
        schemaVersion: Int = 1,
        updatedAtEpochSeconds: TimeInterval = Date().timeIntervalSince1970,
        activeSemester: String,
        slots: [WidgetSharedCourseSlot],
        sessionStatuses: [WidgetSharedSessionStatus],
        assessments: [WidgetSharedAssessment]
    ) {
        self.schemaVersion = schemaVersion
        self.updatedAtEpochSeconds = updatedAtEpochSeconds
        self.activeSemester = activeSemester
        self.slots = slots
        self.sessionStatuses = sessionStatuses
        self.assessments = assessments
    }
}

public enum CampusWidgetSnapshotStore {
    public static let appGroupSuiteName = "group.io.github.nuncamorto.vidaacademica"
    public static let snapshotKey = "campus.ufop.widget.sharedSnapshot.v1"
    private static let snapshotFileName = "widget_shared_snapshot.json"

    private static var sharedContainerFileUrl: URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroupSuiteName)?
            .appendingPathComponent(snapshotFileName)
    }

    public static func save(_ payload: WidgetSharedSnapshotPayload) {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        guard let data = try? encoder.encode(payload) else { return }

        if let defaults = UserDefaults(suiteName: appGroupSuiteName) {
            defaults.set(data, forKey: snapshotKey)
        }
        UserDefaults.standard.set(data, forKey: snapshotKey)

        if let fileUrl = sharedContainerFileUrl {
            try? data.write(to: fileUrl, options: [.atomic])
        }
    }

    public static func load() -> WidgetSharedSnapshotPayload? {
        let decoder = JSONDecoder()

        if let defaults = UserDefaults(suiteName: appGroupSuiteName),
           let data = defaults.data(forKey: snapshotKey),
           let decoded = try? decoder.decode(WidgetSharedSnapshotPayload.self, from: data) {
            return decoded
        }

        if let fileUrl = sharedContainerFileUrl,
           let data = try? Data(contentsOf: fileUrl),
           let decoded = try? decoder.decode(WidgetSharedSnapshotPayload.self, from: data) {
            return decoded
        }

        if let fallbackData = UserDefaults.standard.data(forKey: snapshotKey),
           let decoded = try? decoder.decode(WidgetSharedSnapshotPayload.self, from: fallbackData) {
            return decoded
        }

        return nil
    }

    public static func clear() {
        UserDefaults(suiteName: appGroupSuiteName)?.removeObject(forKey: snapshotKey)
        UserDefaults.standard.removeObject(forKey: snapshotKey)
        if let fileUrl = sharedContainerFileUrl {
            try? FileManager.default.removeItem(at: fileUrl)
        }
    }
}
