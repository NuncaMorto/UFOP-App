import Foundation
import UserNotifications
#if canImport(ActivityKit) && os(iOS)
import ActivityKit
#endif

// MARK: - 100% Local & Offline Notification Scheduler (UNUserNotificationCenter + ActivityKit)

@Observable
public final class CampusNotificationScheduler {
    public static let shared = CampusNotificationScheduler()

    public static let categoryClassReminder = "CAMPUS_CLASS_REMINDER"
    public static let categoryDeadlineReminder = "CAMPUS_DEADLINE_REMINDER"

    public static let actionClassAttend = "CAMPUS_ACTION_CLASS_ATTEND"
    public static let actionClassAbsent = "CAMPUS_ACTION_CLASS_ABSENT"
    public static let actionClassMap = "CAMPUS_ACTION_CLASS_MAP"

    public static let actionDeadlineComplete = "CAMPUS_ACTION_DEADLINE_COMPLETE"
    public static let actionDeadlineSnooze = "CAMPUS_ACTION_DEADLINE_SNOOZE"

    private static let managedIdentifierPrefix = "campus.notif."
    private static let absenceAlertedDefaultsKey = "campus.ufop.notifications.absenceAlertedKeys.v1"
    private static let isoDateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    public private(set) var authorizationStatus: UNAuthorizationStatus = .notDetermined
    public var pendingMapFocusRequest: CampusMapMeetingRequest? = nil
    public var pendingMapFocusNonce: Int = 0
    public var pendingTargetTab: CampusTab? = nil
    public var pendingTargetTabNonce: Int = 0

    private weak var attachedStore: CampusStore?
    private let delegateProxy = NotificationCenterDelegateProxy()
    private var debounceWorkItem: DispatchWorkItem?
    private var hasConfiguredCategories = false

    private init() {}

    public func attach(store: CampusStore) {
        attachedStore = store
        configureNotificationCenterIfNeeded()
        refreshAuthorizationStatus()
    }

    public func configureNotificationCenterIfNeeded() {
        guard !hasConfiguredCategories else { return }
        hasConfiguredCategories = true

        let center = UNUserNotificationCenter.current()
        delegateProxy.scheduler = self
        center.delegate = delegateProxy

        let attendAction = UNNotificationAction(
            identifier: Self.actionClassAttend,
            title: "Vou à aula",
            options: []
        )
        let absentAction = UNNotificationAction(
            identifier: Self.actionClassAbsent,
            title: "Vou faltar",
            options: []
        )
        let mapAction = UNNotificationAction(
            identifier: Self.actionClassMap,
            title: "Ver no mapa",
            options: [.foreground]
        )
        let classCategory = UNNotificationCategory(
            identifier: Self.categoryClassReminder,
            actions: [attendAction, absentAction, mapAction],
            intentIdentifiers: [],
            options: []
        )

        let completeAction = UNNotificationAction(
            identifier: Self.actionDeadlineComplete,
            title: "Marcar concluída",
            options: []
        )
        let snoozeAction = UNNotificationAction(
            identifier: Self.actionDeadlineSnooze,
            title: "Lembrar à noite",
            options: []
        )
        let deadlineCategory = UNNotificationCategory(
            identifier: Self.categoryDeadlineReminder,
            actions: [completeAction, snoozeAction],
            intentIdentifiers: [],
            options: []
        )

        center.setNotificationCategories([classCategory, deadlineCategory])
    }

    public func refreshAuthorizationStatus() {
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            DispatchQueue.main.async {
                self?.authorizationStatus = settings.authorizationStatus
            }
        }
    }

    public func requestPermissionIfNeeded(completion: ((Bool) -> Void)? = nil) {
        configureNotificationCenterIfNeeded()
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { [weak self] granted, _ in
            DispatchQueue.main.async {
                self?.refreshAuthorizationStatus()
                if granted, let store = self?.attachedStore {
                    self?.rescheduleAll(from: store)
                }
                completion?(granted)
            }
        }
    }

    public func scheduleDebouncedReschedule(for store: CampusStore) {
        attachedStore = store
        debounceWorkItem?.cancel()
        let item = DispatchWorkItem { [weak self, weak store] in
            guard let self, let store else { return }
            self.rescheduleAll(from: store)
        }
        debounceWorkItem = item
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35, execute: item)
    }

    public func rescheduleAll(from store: CampusStore, now: Date = Date()) {
        attachedStore = store
        configureNotificationCenterIfNeeded()

        let prefs = store.notificationPreferences.normalized()
        let center = UNUserNotificationCenter.current()

        center.getPendingNotificationRequests { [weak self] pending in
            guard let self else { return }
            let managedIds = pending
                .map(\.identifier)
                .filter { $0.hasPrefix(Self.managedIdentifierPrefix) }
            if !managedIds.isEmpty {
                center.removePendingNotificationRequests(withIdentifiers: managedIds)
            }

            let requests = self.buildNotificationRequests(from: store, prefs: prefs, now: now)
            for req in requests.prefix(60) {
                center.add(req, withCompletionHandler: nil)
            }
        }

        syncLiveActivity(for: store, now: now)
        checkAndPostAbsenceAlerts(from: store)
    }

    public func checkAndPostAbsenceAlerts(from store: CampusStore) {
        let prefs = store.notificationPreferences.normalized()
        guard prefs.absenceAlertEnabled else { return }

        let activeSem = store.studentProfile.activeSemester.trimmingCharacters(in: .whitespacesAndNewlines)
        let semKey = activeSem.isEmpty ? store.selectedSemesterFilter : activeSem
        let semSlug = semKey.isEmpty ? "default" : semKey
        let courses = store.disciplines.filter { semKey.isEmpty || $0.semester == semKey }

        var alertedKeys = Set(UserDefaults.standard.stringArray(forKey: Self.absenceAlertedDefaultsKey) ?? [])
        let center = UNUserNotificationCenter.current()

        for course in courses where course.maxAbsences > 0 {
            let used = max(0, course.currentAbsences)
            let pct = Int(((Double(used) * 100.0) / Double(course.maxAbsences)).rounded())
            let key65 = "\(semSlug)_\(course.id)_65"
            let key85 = "\(semSlug)_\(course.id)_85"

            if pct < 65 { alertedKeys.remove(key65) }
            if pct < 85 { alertedKeys.remove(key85) }

            let crossed: Int? = {
                if pct >= 85 && !alertedKeys.contains(key85) { return 85 }
                if pct >= 65 && pct < 85 && !alertedKeys.contains(key65) { return 65 }
                return nil
            }()

            if pct >= 85 {
                alertedKeys.insert(key65)
                alertedKeys.insert(key85)
            } else if pct >= 65 {
                alertedKeys.insert(key65)
            }

            if let threshold = crossed {
                let remaining = max(0, course.maxAbsences - used)
                let dayWord = remaining == 1 ? "dia" : "dias"
                let content = UNMutableNotificationContent()
                content.title = "Limite próximo em \(course.name)"
                content.body = "\(used)/\(course.maxAbsences) faltas usadas (\(pct)%). Restam \(remaining) \(dayWord) de aula antes de reprovar por falta."
                content.sound = .default
                content.userInfo = [
                    "targetTab": "disciplines",
                    "courseId": course.id
                ]
                let id = "\(Self.managedIdentifierPrefix)absence.\(semSlug).\(course.id).\(threshold)"
                let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
                center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger), withCompletionHandler: nil)
            }
        }

        UserDefaults.standard.set(Array(alertedKeys), forKey: Self.absenceAlertedDefaultsKey)
    }

    public func syncLiveActivity(for store: CampusStore, now: Date = Date()) {
        #if canImport(ActivityKit) && os(iOS)
        if #available(iOS 16.2, *) {
            let prefs = store.notificationPreferences.normalized()
            guard ActivityAuthorizationInfo().areActivitiesEnabled, prefs.liveActivityEnabled else {
                Task {
                    for activity in Activity<CampusClassLiveActivityAttributes>.activities {
                        await activity.end(nil, dismissalPolicy: .immediate)
                    }
                }
                return
            }

            guard let window = resolveActiveLiveWindow(store: store, prefs: prefs, now: now) else {
                Task {
                    for activity in Activity<CampusClassLiveActivityAttributes>.activities {
                        await activity.end(nil, dismissalPolicy: .immediate)
                    }
                }
                return
            }

            let state = CampusClassLiveActivityAttributes.ContentState(
                countdownLabel: window.countdownLabel,
                isOngoing: window.isOngoing,
                progress: window.progress,
                roomPill: window.roomPill,
                timeRange: "\(window.startTime)–\(window.endTime)"
            )

            Task {
                let existing = Activity<CampusClassLiveActivityAttributes>.activities
                if let matching = existing.first(where: {
                    $0.attributes.courseId == window.courseId && $0.attributes.startTime == window.startTime
                }) {
                    await matching.update(ActivityContent(state: state, staleDate: window.endDate))
                    for other in existing where other.id != matching.id {
                        await other.end(nil, dismissalPolicy: .immediate)
                    }
                } else {
                    for other in existing {
                        await other.end(nil, dismissalPolicy: .immediate)
                    }
                    let attrs = CampusClassLiveActivityAttributes(
                        courseId: window.courseId,
                        courseName: window.courseName,
                        startTime: window.startTime,
                        endTime: window.endTime
                    )
                    let content = ActivityContent(state: state, staleDate: window.endDate)
                    _ = try? Activity.request(attributes: attrs, content: content, pushType: .none)
                }
            }
        }
        #endif
    }

    public func cancelAllAndClearTracking() {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { pending in
            let managed = pending.map(\.identifier).filter { $0.hasPrefix(Self.managedIdentifierPrefix) }
            if !managed.isEmpty {
                center.removePendingNotificationRequests(withIdentifiers: managed)
            }
        }
        center.removeAllDeliveredNotifications()
        UserDefaults.standard.removeObject(forKey: Self.absenceAlertedDefaultsKey)
        #if canImport(ActivityKit) && os(iOS)
        if #available(iOS 16.2, *) {
            Task {
                for activity in Activity<CampusClassLiveActivityAttributes>.activities {
                    await activity.end(nil, dismissalPolicy: .immediate)
                }
            }
        }
        #endif
    }

    // MARK: - Planning Builders

    private struct ScheduledItemDraft {
        let identifier: String
        let fireDate: Date
        let content: UNMutableNotificationContent
    }

    private func buildNotificationRequests(
        from store: CampusStore,
        prefs: NotificationPreferences,
        now: Date
    ) -> [UNNotificationRequest] {
        let cal = Calendar(identifier: .gregorian)
        let today = cal.startOfDay(for: now)
        let activeSem = store.selectedSemesterFilter
        let semCourses = store.disciplines.filter { activeSem.isEmpty || $0.semester == activeSem }
        let discById = Dictionary(semCourses.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let slotsByDay = Dictionary(grouping: store.courseScheduleSlots.filter { discById[$0.courseId] != .none }, by: \.dayOfWeek)

        var drafts: [ScheduledItemDraft] = []

        for offset in 0..<7 {
            guard let date = cal.date(byAdding: .day, value: offset, to: today) else { continue }
            let isoDate = Self.isoDateFormatter.string(from: date)
            let weekdayIdx = CampusStore.isoWeekdayIndex(from: date)
            let timeline = store.timelineItems(for: isoDate, weekdayIndex: weekdayIdx)
            let daySlots = (slotsByDay[weekdayIdx] ?? []).sorted { $0.startTime < $1.startTime }

            var activeClasses: [(slot: CourseScheduleSlotItem, disc: DisciplineSummaryCard, roomPill: String, inlineRoom: String, agendaId: String)] = []

            for slot in daySlots {
                guard let disc = discById[slot.courseId] else { continue }
                let matchedItem = timeline.first { $0.kind == .lecture && $0.disciplineId == slot.courseId && $0.startTime == slot.startTime }
                if matchedItem?.attendanceStatus == .cancelled { continue }

                let isEad = store.isCourseEad(disc.id) || Self.isEadLocation(slot.building) || Self.isEadLocation(disc.location)
                let roomPill = Self.formatRoomPill(
                    building: slot.building,
                    room: slot.room,
                    shortLocation: slot.shortLocation,
                    fallback: disc.location,
                    isEad: isEad
                )
                let inlineRoom = Self.formatInlineRoom(
                    building: slot.building,
                    room: slot.room,
                    shortLocation: slot.shortLocation,
                    fallback: disc.location,
                    isEad: isEad
                )
                let agendaId = matchedItem?.id ?? "lecture-\(slot.slotId)@\(isoDate)"
                activeClasses.append((slot, disc, roomPill, inlineRoom, agendaId))

                if prefs.classReminderEnabled,
                   let startDate = Self.combine(date: date, hhmm: slot.startTime, calendar: cal),
                   let fireDate = cal.date(byAdding: .minute, value: -prefs.classLeadMinutes, to: startDate),
                   fireDate > now {
                    let content = UNMutableNotificationContent()
                    content.title = "\(disc.name) em \(prefs.classLeadMinutes) min"
                    let prof = disc.professor.trimmingCharacters(in: .whitespacesAndNewlines)
                    let timeRange = "\(slot.startTime)–\(slot.endTime)"
                    content.body = prof.isEmpty ? "\(roomPill) · \(timeRange)" : "\(roomPill) · \(timeRange) · \(prof)"
                    content.sound = .default
                    content.categoryIdentifier = Self.categoryClassReminder
                    content.userInfo = [
                        "agendaItemId": agendaId,
                        "courseId": disc.id,
                        "courseCode": disc.code,
                        "section": store.turmaByCourseId[disc.id] ?? "",
                        "weekday": weekdayIdx,
                        "startTime": slot.startTime,
                        "fallbackBuilding": slot.building.isEmpty ? disc.location : slot.building
                    ]
                    drafts.append(
                        ScheduledItemDraft(
                            identifier: "\(Self.managedIdentifierPrefix)class.\(slot.slotId).\(isoDate).\(prefs.classLeadMinutes)",
                            fireDate: fireDate,
                            content: content
                        )
                    )
                }
            }

            // Resumo matinal às 07:30 somente em dias letivos (Seg–Sex) com pelo menos 1 aula ativa
            if prefs.morningSummaryEnabled,
               (1...5).contains(weekdayIdx),
               !activeClasses.isEmpty,
               let morningDate = cal.date(bySettingHour: 7, minute: 30, second: 0, of: date),
               morningDate > now {
                let dayLabel = CampusStore.weekdayLabel(for: weekdayIdx)
                let classWord = activeClasses.count == 1 ? "aula" : "aulas"
                let weekEndIso = cal.date(byAdding: .day, value: 6, to: date).map { Self.isoDateFormatter.string(from: $0) } ?? isoDate
                let allAssessments = store.assessmentsByDiscipline.values.flatMap { $0 }
                let weekExams = allAssessments.filter { item in
                    !item.completed && item.dueDate >= isoDate && item.dueDate <= weekEndIso
                }.count

                let slotsPart = activeClasses.prefix(2).map { "\($0.slot.startTime) \($0.inlineRoom)" }.joined(separator: " · ")
                let extraSuffix = activeClasses.count > 2 ? " · +\(activeClasses.count - 2)" : ""
                let examWord = weekExams == 1 ? "prova" : "provas"

                let content = UNMutableNotificationContent()
                content.title = "\(dayLabel) no campus: \(activeClasses.count) \(classWord)"
                content.body = "\(slotsPart)\(extraSuffix) · \(weekExams) \(examWord) esta semana"
                content.sound = .default
                content.userInfo = ["targetTab": "agenda"]

                drafts.append(
                    ScheduledItemDraft(
                        identifier: "\(Self.managedIdentifierPrefix)morning.\(isoDate)",
                        fireDate: morningDate,
                        content: content
                    )
                )
            }
        }

        if prefs.deadlineReminderEnabled {
            let allDiscById = Dictionary(store.disciplines.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            let allAssessments = store.assessmentsByDiscipline.values.flatMap { $0 }
            for item in allAssessments where !item.completed {
                guard let dueDate = Self.isoDateFormatter.date(from: item.dueDate.trimmingCharacters(in: .whitespacesAndNewlines)) else {
                    continue
                }
                let disc = allDiscById[item.courseId]
                let formattedTitle = formatAcademicAssessmentTitle(item.title)
                let cleanTitle = formattedTitle.isEmpty ? item.title : formattedTitle
                let codeLabel = (disc?.code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false)
                    ? disc!.code
                    : (disc?.name ?? "Disciplina")
                let kindLabel = item.kind.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Avaliação" : item.kind
                let dueTimeLabel = item.dueTime.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "18:00" : item.dueTime

                for leadDays in [3, 1] {
                    guard let triggerDay = cal.date(byAdding: .day, value: -leadDays, to: dueDate),
                          let triggerAt = cal.date(bySettingHour: 9, minute: 0, second: 0, of: triggerDay),
                          triggerAt > now else {
                        continue
                    }
                    let title = leadDays == 1 ? "\(cleanTitle) amanhã" : "\(cleanTitle) em \(leadDays) dias"
                    let daysSuffix = leadDays == 1 ? "1 dia restante" : "\(leadDays) dias restantes"
                    let body = "\(codeLabel) · \(kindLabel) às \(dueTimeLabel) · \(daysSuffix)"

                    let content = UNMutableNotificationContent()
                    content.title = title
                    content.body = body
                    content.sound = .default
                    content.categoryIdentifier = Self.categoryDeadlineReminder
                    content.userInfo = [
                        "targetTab": "activities",
                        "assessmentId": item.id,
                        "courseId": item.courseId,
                        "originalTitle": title,
                        "originalBody": body
                    ]

                    drafts.append(
                        ScheduledItemDraft(
                            identifier: "\(Self.managedIdentifierPrefix)deadline.\(item.id).d\(leadDays)",
                            fireDate: triggerAt,
                            content: content
                        )
                    )
                }
            }
        }

        return drafts
            .sorted { $0.fireDate < $1.fireDate }
            .prefix(60)
            .map { draft in
                let comps = cal.dateComponents([.year, .month, .day, .hour, .minute, .second], from: draft.fireDate)
                let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
                return UNNotificationRequest(identifier: draft.identifier, content: draft.content, trigger: trigger)
            }
    }

    private struct LiveWindowCandidate {
        let courseId: String
        let courseName: String
        let startTime: String
        let endTime: String
        let endDate: Date
        let roomPill: String
        let countdownLabel: String
        let isOngoing: Bool
        let progress: Double
    }

    private func resolveActiveLiveWindow(
        store: CampusStore,
        prefs: NotificationPreferences,
        now: Date
    ) -> LiveWindowCandidate? {
        let cal = Calendar(identifier: .gregorian)
        let isoDate = Self.isoDateFormatter.string(from: now)
        let weekdayIdx = CampusStore.isoWeekdayIndex(from: now)
        let timeline = store.timelineItems(for: isoDate, weekdayIndex: weekdayIdx)
        let activeSem = store.selectedSemesterFilter
        let discById = Dictionary(
            store.disciplines.filter { activeSem.isEmpty || $0.semester == activeSem }.map { ($0.id, $0) },
            uniquingKeysWith: { first, _ in first }
        )
        let daySlots = store.courseScheduleSlots
            .filter { $0.dayOfWeek == weekdayIdx && discById[$0.courseId] != .none }
            .sorted { $0.startTime < $1.startTime }

        for slot in daySlots {
            guard let disc = discById[slot.courseId] else { continue }
            let matchedItem = timeline.first { $0.kind == .lecture && $0.disciplineId == slot.courseId && $0.startTime == slot.startTime }
            if matchedItem?.attendanceStatus == .cancelled { continue }
            guard let startDate = Self.combine(date: now, hhmm: slot.startTime, calendar: cal),
                  let rawEnd = Self.combine(date: now, hhmm: slot.endTime, calendar: cal) else {
                continue
            }
            let endDate = rawEnd > startDate ? rawEnd : startDate.addingTimeInterval(3000)
            let windowStart = cal.date(byAdding: .minute, value: -prefs.classLeadMinutes, to: startDate) ?? startDate
            guard now >= windowStart && now < endDate else { continue }

            let isOngoing = now >= startDate && now < endDate
            let countdownLabel: String = {
                if isOngoing { return "AGORA" }
                let mins = max(1, Int(ceil(startDate.timeIntervalSince(now) / 60.0)))
                return "em \(mins) min"
            }()
            let progress: Double = {
                if !isOngoing { return 0.0 }
                let total = max(60.0, endDate.timeIntervalSince(startDate))
                let elapsed = max(0.0, min(total, now.timeIntervalSince(startDate)))
                return elapsed / total
            }()
            let isEad = store.isCourseEad(disc.id) || Self.isEadLocation(slot.building) || Self.isEadLocation(disc.location)
            let roomPill = Self.formatRoomPill(
                building: slot.building,
                room: slot.room,
                shortLocation: slot.shortLocation,
                fallback: disc.location,
                isEad: isEad
            )
            return LiveWindowCandidate(
                courseId: disc.id,
                courseName: disc.name,
                startTime: slot.startTime,
                endTime: slot.endTime,
                endDate: endDate,
                roomPill: roomPill,
                countdownLabel: countdownLabel,
                isOngoing: isOngoing,
                progress: progress
            )
        }
        return .none
    }

    // MARK: - Notification Response Handling

    fileprivate func handleNotificationResponse(_ response: UNNotificationResponse) {
        let userInfo = response.notification.request.content.userInfo
        let actionId = response.actionIdentifier

        Task { @MainActor [weak self] in
            guard let self, let store = self.attachedStore else { return }

            switch actionId {
            case Self.actionClassAttend:
                if let agendaId = userInfo["agendaItemId"] as? String {
                    let colorKey = Self.resolveColorKey(store: store, courseId: userInfo["courseId"] as? String)
                    store.saveClassCheckIn(agendaItemId: agendaId, status: .attending, colorKey: colorKey)
                    Self.postLocalConfirmation(title: response.notification.request.content.title, body: "✓ Presença confirmada")
                }

            case Self.actionClassAbsent:
                if let agendaId = userInfo["agendaItemId"] as? String {
                    let colorKey = Self.resolveColorKey(store: store, courseId: userInfo["courseId"] as? String)
                    store.saveClassCheckIn(agendaItemId: agendaId, status: .absent, colorKey: colorKey)
                    Self.postLocalConfirmation(title: response.notification.request.content.title, body: "Falta registrada na agenda")
                    self.checkAndPostAbsenceAlerts(from: store)
                }

            case Self.actionClassMap:
                let courseCode = userInfo["courseCode"] as? String ?? ""
                let section = userInfo["section"] as? String ?? ""
                let weekday = UInt32(max(1, userInfo["weekday"] as? Int ?? 1))
                let startTime = userInfo["startTime"] as? String ?? ""
                let fallback = userInfo["fallbackBuilding"] as? String ?? ""
                self.pendingMapFocusRequest = CampusMapMeetingRequest(
                    courseCode: courseCode,
                    section: section,
                    weekday: weekday,
                    startTime: startTime,
                    fallbackBuildingRaw: fallback
                )
                self.pendingMapFocusNonce += 1

            case Self.actionDeadlineComplete:
                if let assessmentId = userInfo["assessmentId"] as? String {
                    _ = store.setAssessmentCompleted(assessmentId: assessmentId, completed: true)
                    Self.postLocalConfirmation(title: response.notification.request.content.title, body: "✓ Avaliação concluída")
                }

            case Self.actionDeadlineSnooze:
                if let assessmentId = userInfo["assessmentId"] as? String {
                    let title = userInfo["originalTitle"] as? String ?? response.notification.request.content.title
                    let body = userInfo["originalBody"] as? String ?? response.notification.request.content.body
                    Self.scheduleEveningSnooze(assessmentId: assessmentId, courseId: userInfo["courseId"] as? String ?? "", title: title, body: body)
                }

            case UNNotificationDefaultActionIdentifier:
                if let targetTabRaw = userInfo["targetTab"] as? String {
                    switch targetTabRaw.lowercased() {
                    case "activities":
                        self.pendingTargetTab = .activities
                        self.pendingTargetTabNonce += 1
                    case "disciplines":
                        self.pendingTargetTab = .disciplines
                        self.pendingTargetTabNonce += 1
                    case "map":
                        self.pendingTargetTab = .map
                        self.pendingTargetTabNonce += 1
                    default:
                        self.pendingTargetTab = .agenda
                        self.pendingTargetTabNonce += 1
                    }
                } else if userInfo["agendaItemId"] != .none {
                    self.pendingTargetTab = .agenda
                    self.pendingTargetTabNonce += 1
                }

            default:
                break
            }
        }
    }

    private static func resolveColorKey(store: CampusStore, courseId: String?) -> DepartmentColorKey {
        guard let courseId, let disc = store.disciplines.first(where: { $0.id == courseId }) else {
            return .mathBlue
        }
        return disc.colorKey
    }

    private static func postLocalConfirmation(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        let req = UNNotificationRequest(
            identifier: "\(managedIdentifierPrefix)confirm.\(UUID().uuidString)",
            content: content,
            trigger: UNTimeIntervalNotificationTrigger(timeInterval: 0.5, repeats: false)
        )
        UNUserNotificationCenter.current().add(req, withCompletionHandler: nil)
    }

    private static func scheduleEveningSnooze(assessmentId: String, courseId: String, title: String, body: String) {
        let cal = Calendar(identifier: .gregorian)
        let now = Date()
        let tonight = cal.date(bySettingHour: 20, minute: 0, second: 0, of: now) ?? now.addingTimeInterval(1800)
        let fireDate = tonight > now ? tonight : now.addingTimeInterval(1800)

        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        content.categoryIdentifier = categoryDeadlineReminder
        content.userInfo = [
            "targetTab": "activities",
            "assessmentId": assessmentId,
            "courseId": courseId,
            "originalTitle": title,
            "originalBody": body
        ]
        let comps = cal.dateComponents([.year, .month, .day, .hour, .minute, .second], from: fireDate)
        let req = UNNotificationRequest(
            identifier: "\(managedIdentifierPrefix)snooze.\(assessmentId)",
            content: content,
            trigger: UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        )
        UNUserNotificationCenter.current().add(req, withCompletionHandler: nil)
    }

    // MARK: - Formatting Helpers

    private static func combine(date: Date, hhmm: String, calendar: Calendar) -> Date? {
        let parts = hhmm.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: ":")
        guard parts.count == 2,
              let h = Int(parts[0]),
              let m = Int(parts[1]),
              (0...23).contains(h),
              (0...59).contains(m) else {
            return .none
        }
        return calendar.date(bySettingHour: h, minute: m, second: 0, of: date)
    }

    private static func isEadLocation(_ raw: String) -> Bool {
        let lower = raw.lowercased()
        return lower.contains("ead") || lower.contains("distância") || lower.contains("distancia") || lower.contains("remoto")
    }

    private static func simplifyBuilding(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.caseInsensitiveCompare("Bloco de Salas") == .orderedSame { return "Bloco" }
        if trimmed.caseInsensitiveCompare("Escola de Minas") == .orderedSame { return "EM" }
        if trimmed.lowercased().hasPrefix("prédio ") {
            return String(trimmed.dropFirst("prédio ".count)).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return trimmed
    }

    private static func formatRoomPill(
        building: String,
        room: String,
        shortLocation: String,
        fallback: String,
        isEad: Bool
    ) -> String {
        if isEad { return "EAD" }
        let fallbackParts = fallback.components(separatedBy: "·")
        let cleanBuilding = simplifyBuilding(building.isEmpty ? (fallbackParts.first ?? "") : building)
        let cleanRoom = room.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? (fallbackParts.count > 1 ? fallbackParts[1].trimmingCharacters(in: .whitespacesAndNewlines) : "")
            : room.trimmingCharacters(in: .whitespacesAndNewlines)

        let knownRoomPrefixes = [
            "sala", "lab", "audit", "mini", "anf", "gin", "quadra",
            "bloco", "pavil", "biblio", "online", "ead", "virtual",
            "remoto", "prédio", "predio"
        ]
        let formattedRoom: String
        if cleanRoom.isEmpty {
            formattedRoom = ""
        } else {
            let lowerRoom = cleanRoom.lowercased()
            let hasRoomPrefix = knownRoomPrefixes.contains { lowerRoom.hasPrefix($0) }
            formattedRoom = hasRoomPrefix ? cleanRoom : "Sala \(cleanRoom)"
        }

        if !cleanBuilding.isEmpty && !formattedRoom.isEmpty {
            return "\(cleanBuilding) · \(formattedRoom)"
        }
        if !shortLocation.isEmpty { return shortLocation }
        if !cleanBuilding.isEmpty { return cleanBuilding }
        return "Campus UFOP"
    }

    private static func formatInlineRoom(
        building: String,
        room: String,
        shortLocation: String,
        fallback: String,
        isEad: Bool
    ) -> String {
        if isEad { return "EAD" }
        let fallbackParts = fallback.components(separatedBy: "·")
        let cleanBuilding = simplifyBuilding(building.isEmpty ? (fallbackParts.first ?? "") : building)
        let cleanRoom = room.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? (fallbackParts.count > 1 ? fallbackParts[1].trimmingCharacters(in: .whitespacesAndNewlines) : "")
            : room.trimmingCharacters(in: .whitespacesAndNewlines)

        let lowerRoom = cleanRoom.lowercased()
        let roomToken: String? = lowerRoom.hasPrefix("sala ")
            ? String(cleanRoom.dropFirst(5)).trimmingCharacters(in: .whitespacesAndNewlines)
            : .none

        if !cleanBuilding.isEmpty, let roomToken, !roomToken.isEmpty {
            return "\(cleanBuilding) \(roomToken)"
        }
        if !cleanBuilding.isEmpty, !cleanRoom.isEmpty {
            return "\(cleanBuilding) \(cleanRoom)"
        }
        if !shortLocation.isEmpty {
            return shortLocation.replacingOccurrences(of: " · ", with: " ")
        }
        if !cleanBuilding.isEmpty { return cleanBuilding }
        return "Campus"
    }
}

private final class NotificationCenterDelegateProxy: NSObject, UNUserNotificationCenterDelegate {
    weak var scheduler: CampusNotificationScheduler?

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .list])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        scheduler?.handleNotificationResponse(response)
        completionHandler()
    }
}
