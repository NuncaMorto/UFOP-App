import Foundation
#if canImport(ActivityKit) && os(iOS)
import ActivityKit

@available(iOS 16.1, *)
public struct CampusClassLiveActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable, Sendable {
        public var countdownLabel: String
        public var isOngoing: Bool
        public var progress: Double
        public var roomPill: String
        public var timeRange: String

        public init(
            countdownLabel: String,
            isOngoing: Bool,
            progress: Double,
            roomPill: String,
            timeRange: String
        ) {
            self.countdownLabel = countdownLabel
            self.isOngoing = isOngoing
            self.progress = progress
            self.roomPill = roomPill
            self.timeRange = timeRange
        }
    }

    public var courseId: String
    public var courseName: String
    public var startTime: String
    public var endTime: String

    public init(
        courseId: String,
        courseName: String,
        startTime: String,
        endTime: String
    ) {
        self.courseId = courseId
        self.courseName = courseName
        self.startTime = startTime
        self.endTime = endTime
    }
}
#endif
