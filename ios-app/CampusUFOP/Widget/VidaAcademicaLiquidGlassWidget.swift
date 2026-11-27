import SwiftUI
import WidgetKit
#if canImport(ActivityKit) && os(iOS)
import ActivityKit
#endif

// MARK: - Corridor Snapshot Models (Paridade 100% Android ↔ iOS)

public struct WidgetClassHighlight: Sendable {
    public let courseId: String
    public let title: String
    public let startTime: String
    public let endTime: String
    public let timeRange: String
    public let roomPill: String
    public let smallRoomPill: String
    public let isOngoing: Bool
    public let isEad: Bool
}

public struct WidgetCompactClassRow: Sendable {
    public let courseId: String
    public let startTime: String
    public let title: String
    public let inlineRoom: String
}

public struct WidgetDeadlineHighlight: Sendable {
    public let id: String
    public let title: String
    public let shortDate: String
    public let freeDayHeader: String
    public let dueDate: String
    public let dueTime: String
}

public struct WidgetNextDayClass: Sendable {
    public let dayBanner: String
    public let startTime: String
    public let title: String
    public let roomLabel: String
}

public struct CampusClassEntry: TimelineEntry {
    public let date: Date
    public let dayPillText: String
    public let headerSummary: String
    public let freeDayMessage: String
    public let countdownLabel: String
    public let compactCountdownBadge: String
    public let isOngoing: Bool
    public let remainingAfterSpotlightCount: Int
    public let spotlightClass: WidgetClassHighlight?
    public let followingClass: WidgetCompactClassRow?
    public let thirdClass: WidgetCompactClassRow?
    public let nextDeadline: WidgetDeadlineHighlight?
    public let nextSchoolDayClass: WidgetNextDayClass?

    public var primaryDeepLinkUrl: URL {
        if spotlightClass == nil, nextDeadline != nil {
            return URL(string: "vidaacademica://activities")!
        }
        return URL(string: "vidaacademica://agenda")!
    }

    public static let placeholder = CampusClassEntry(
        date: Date(),
        dayPillText: "QUA 25",
        headerSummary: "2 aulas hoje",
        freeDayMessage: "Sem aulas hoje",
        countdownLabel: "em 42m",
        compactCountdownBadge: "EM 42M",
        isOngoing: false,
        remainingAfterSpotlightCount: 1,
        spotlightClass: WidgetClassHighlight(
            courseId: "mat202",
            title: "Introdução às Equações Dif.",
            startTime: "13:30",
            endTime: "15:10",
            timeRange: "13:30–15:10",
            roomPill: "ICEB · S. 14",
            smallRoomPill: "ICEB · Sala 14",
            isOngoing: false,
            isEad: false
        ),
        followingClass: WidgetCompactClassRow(
            courseId: "mat203",
            startTime: "15:20",
            title: "Cálculo Diferencial III",
            inlineRoom: "ICEB 10"
        ),
        thirdClass: nil,
        nextDeadline: WidgetDeadlineHighlight(
            id: "demo-1",
            title: "Avaliação Circuitos",
            shortDate: "29 nov",
            freeDayHeader: "AMANHÃ · 29 NOV",
            dueDate: "2026-11-29",
            dueTime: "18:00"
        ),
        nextSchoolDayClass: nil
    )
}

// MARK: - Timeline Provider

public struct VidaAcademicaTimelineProvider: TimelineProvider {
    private static let weekdayShorts = ["", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM"]
    private static let monthShorts = ["", "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"]

    public init() {}

    public func placeholder(in context: Context) -> CampusClassEntry {
        .placeholder
    }

    public func getSnapshot(in context: Context, completion: @escaping (CampusClassEntry) -> Void) {
        if context.isPreview {
            completion(.placeholder)
        } else {
            completion(loadCorridorEntry(now: Date()))
        }
    }

    public func getTimeline(in context: Context, completion: @escaping (Timeline<CampusClassEntry>) -> Void) {
        let now = Date()
        let entry = loadCorridorEntry(now: now)
        let nextRefresh = computeNextRefreshDate(now: now, entry: entry)
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }

    private func computeNextRefreshDate(now: Date, entry: CampusClassEntry) -> Date {
        let fallback = Calendar.current.date(byAdding: .minute, value: 15, to: now) ?? now.addingTimeInterval(900)
        guard let spotlight = entry.spotlightClass else {
            return fallback
        }
        let targetHHMM = spotlight.isOngoing ? spotlight.endTime : spotlight.startTime
        guard let targetMin = Self.minutesFromHHMM(targetHHMM) else {
            return fallback
        }
        let comps = Calendar(identifier: .gregorian).dateComponents([.hour, .minute], from: now)
        let nowMin = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
        let diffMin = targetMin - nowMin
        if diffMin > 0 && diffMin < 15 {
            return Calendar.current.date(byAdding: .minute, value: diffMin, to: now) ?? fallback
        }
        return fallback
    }

    private static let isoDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private func loadCorridorEntry(now: Date) -> CampusClassEntry {
        let snapshot = CampusWidgetSnapshotStore.load()
        let cal = Calendar(identifier: .gregorian)
        let todayIso = Self.isoString(from: now)
        let weekdayIdx = Self.isoWeekdayIndex(from: now)
        let dayNum = cal.component(.day, from: now)
        let dayPillText = "\(Self.weekdayShorts[min(max(weekdayIdx, 1), 7)]) \(dayNum)"

        guard let snapshot else {
            return CampusClassEntry(
                date: now,
                dayPillText: dayPillText,
                headerSummary: "Dia livre",
                freeDayMessage: "Sem aulas hoje",
                countdownLabel: "",
                compactCountdownBadge: dayPillText,
                isOngoing: false,
                remainingAfterSpotlightCount: 0,
                spotlightClass: nil,
                followingClass: nil,
                thirdClass: nil,
                nextDeadline: nil,
                nextSchoolDayClass: nil
            )
        }

        let comps = cal.dateComponents([.hour, .minute], from: now)
        let nowMin = (comps.hour ?? 8) * 60 + (comps.minute ?? 0)

        let todayCandidates = collectClasses(
            snapshot: snapshot,
            dateIso: todayIso,
            weekdayIndex: weekdayIdx
        )

        let spotlightIndex = todayCandidates.firstIndex { cand in
            (nowMin >= cand.startMinutes && nowMin < cand.endMinutes) || nowMin < cand.startMinutes
        }

        let spotlightCand = spotlightIndex.map { todayCandidates[$0] }
        let followingCand = spotlightIndex.flatMap { idx in
            idx + 1 < todayCandidates.count ? todayCandidates[idx + 1] : nil
        }
        let thirdCand = spotlightIndex.flatMap { idx in
            idx + 2 < todayCandidates.count ? todayCandidates[idx + 2] : nil
        }
        let remainingAfterSpotlightCount = spotlightIndex.map { max(0, todayCandidates.count - $0 - 1) } ?? 0

        let isOngoing = spotlightCand.map { nowMin >= $0.startMinutes && nowMin < $0.endMinutes } ?? false

        let countdownLabel: String = {
            guard let spotlightCand else { return "" }
            if isOngoing { return "AGORA" }
            return Self.formatCountdown(minutesUntilStart: max(0, spotlightCand.startMinutes - nowMin), uppercase: false)
        }()

        let compactCountdownBadge: String = {
            guard let spotlightCand else { return dayPillText }
            if isOngoing { return "AGORA" }
            return Self.formatCountdown(minutesUntilStart: max(0, spotlightCand.startMinutes - nowMin), uppercase: true)
        }()

        let headerSummary: String = {
            if todayCandidates.isEmpty { return "Dia livre" }
            return todayCandidates.count == 1 ? "1 aula hoje" : "\(todayCandidates.count) aulas hoje"
        }()

        let freeDayMessage = todayCandidates.isEmpty ? "Sem aulas hoje" : "Aulas de hoje concluídas"

        let nextDeadline = findNextPendingDeadline(snapshot: snapshot, today: now, todayIso: todayIso)
        let nextSchoolDay = spotlightCand == nil ? findNextSchoolDayClass(snapshot: snapshot, today: now) : nil

        return CampusClassEntry(
            date: now,
            dayPillText: dayPillText,
            headerSummary: headerSummary,
            freeDayMessage: freeDayMessage,
            countdownLabel: countdownLabel,
            compactCountdownBadge: compactCountdownBadge,
            isOngoing: isOngoing,
            remainingAfterSpotlightCount: remainingAfterSpotlightCount,
            spotlightClass: spotlightCand.map {
                WidgetClassHighlight(
                    courseId: $0.courseId,
                    title: $0.title,
                    startTime: $0.startTime,
                    endTime: $0.endTime,
                    timeRange: "\($0.startTime)–\($0.endTime)",
                    roomPill: $0.roomPill,
                    smallRoomPill: $0.smallRoomPill,
                    isOngoing: isOngoing,
                    isEad: $0.isEad
                )
            },
            followingClass: followingCand.map {
                WidgetCompactClassRow(
                    courseId: $0.courseId,
                    startTime: $0.startTime,
                    title: $0.title,
                    inlineRoom: $0.inlineRoom
                )
            },
            thirdClass: thirdCand.map {
                WidgetCompactClassRow(
                    courseId: $0.courseId,
                    startTime: $0.startTime,
                    title: $0.title,
                    inlineRoom: $0.inlineRoom
                )
            },
            nextDeadline: nextDeadline,
            nextSchoolDayClass: nextSchoolDay
        )
    }

    private struct CandidateClass {
        let courseId: String
        let title: String
        let startTime: String
        let endTime: String
        let startMinutes: Int
        let endMinutes: Int
        let roomPill: String
        let smallRoomPill: String
        let inlineRoom: String
        let isEad: Bool
    }

    private func collectClasses(
        snapshot: WidgetSharedSnapshotPayload,
        dateIso: String,
        weekdayIndex: Int
    ) -> [CandidateClass] {
        let statusesForDay = snapshot.sessionStatuses.filter { $0.dateIso == dateIso }
        let daySlots = snapshot.slots
            .filter { $0.dayOfWeek == weekdayIndex }
            .sorted { $0.startTime < $1.startTime }

        return daySlots.compactMap { slot in
            let matchedStatus = statusesForDay.first { status in
                status.slotId == slot.slotId ||
                    (status.courseId == slot.courseId && status.startTime == slot.startTime)
            }
            if matchedStatus?.statusRaw.uppercased() == "CANCELLED" {
                return nil
            }
            guard let startMin = Self.minutesFromHHMM(slot.startTime) else { return nil }
            let endMin = Self.minutesFromHHMM(slot.endTime) ?? (startMin + 50)
            let isEad = slot.isEad || Self.isEadLocation(slot.building) || Self.isEadLocation(slot.fallbackLocation)
            let badges = Self.formatRoomBadges(
                building: slot.building,
                room: slot.room,
                shortLocation: slot.shortLocation,
                fallbackLocation: slot.fallbackLocation,
                isEad: isEad
            )
            return CandidateClass(
                courseId: slot.courseId,
                title: slot.courseName,
                startTime: slot.startTime,
                endTime: slot.endTime,
                startMinutes: startMin,
                endMinutes: endMin,
                roomPill: badges.spotlight,
                smallRoomPill: badges.small,
                inlineRoom: badges.inline,
                isEad: isEad
            )
        }
    }

    private func findNextSchoolDayClass(snapshot: WidgetSharedSnapshotPayload, today: Date) -> WidgetNextDayClass? {
        let cal = Calendar(identifier: .gregorian)
        for offset in 1...7 {
            guard let candidateDate = cal.date(byAdding: .day, value: offset, to: today) else { continue }
            let iso = Self.isoString(from: candidateDate)
            let weekdayIdx = Self.isoWeekdayIndex(from: candidateDate)
            let classes = collectClasses(snapshot: snapshot, dateIso: iso, weekdayIndex: weekdayIdx)
            guard let first = classes.first else { continue }
            return WidgetNextDayClass(
                dayBanner: Self.formatRelativeDayBanner(date: candidateDate, today: today),
                startTime: first.startTime,
                title: first.title,
                roomLabel: first.roomPill
            )
        }
        return nil
    }

    private func findNextPendingDeadline(
        snapshot: WidgetSharedSnapshotPayload,
        today: Date,
        todayIso: String
    ) -> WidgetDeadlineHighlight? {
        let pending = snapshot.assessments.filter { item in
            let dateTrimmed = item.dueDate.trimmingCharacters(in: .whitespacesAndNewlines)
            return !item.completed && !dateTrimmed.isEmpty && dateTrimmed >= todayIso && !item.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }

        let earliest = pending.min { lhs, rhs in
            if lhs.dueDate != rhs.dueDate {
                return lhs.dueDate < rhs.dueDate
            }
            let lm = Self.minutesFromHHMM(lhs.dueTime) ?? Int.max
            let rm = Self.minutesFromHHMM(rhs.dueTime) ?? Int.max
            if lm != rm {
                return lm < rm
            }
            return lhs.title < rhs.title
        }

        guard let earliest else { return nil }
        let formattedTitle = formatAcademicAssessmentTitle(earliest.title)
        let parsedDate = Self.parseIsoDate(earliest.dueDate)
        let cal = Calendar(identifier: .gregorian)
        let shortDate: String = {
            guard let parsedDate else { return earliest.dueDate }
            let day = cal.component(.day, from: parsedDate)
            let month = cal.component(.month, from: parsedDate)
            return "\(day) \(Self.monthShorts[min(max(month, 1), 12)])"
        }()
        let freeDayHeader: String = {
            guard let parsedDate else { return shortDate.uppercased() }
            return Self.formatRelativeDayBanner(date: parsedDate, today: today)
        }()

        return WidgetDeadlineHighlight(
            id: earliest.id,
            title: formattedTitle,
            shortDate: shortDate,
            freeDayHeader: freeDayHeader,
            dueDate: earliest.dueDate,
            dueTime: earliest.dueTime.isEmpty ? "18:00" : earliest.dueTime
        )
    }

    private static func isoString(from date: Date) -> String {
        isoDateFormatter.string(from: date)
    }

    private static func parseIsoDate(_ iso: String) -> Date? {
        isoDateFormatter.date(from: iso.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    private static func isoWeekdayIndex(from date: Date) -> Int {
        let weekday = Calendar(identifier: .gregorian).component(.weekday, from: date)
        return weekday == 1 ? 7 : (weekday - 1)
    }

    private static func formatRelativeDayBanner(date: Date, today: Date) -> String {
        let cal = Calendar(identifier: .gregorian)
        let day = cal.component(.day, from: date)
        let month = cal.component(.month, from: date)
        let monthUpper = monthShorts[min(max(month, 1), 12)].uppercased()
        if cal.isDate(date, inSameDayAs: today) {
            return "HOJE · \(day) \(monthUpper)"
        }
        if let tomorrow = cal.date(byAdding: .day, value: 1, to: today),
           cal.isDate(date, inSameDayAs: tomorrow) {
            return "AMANHÃ · \(day) \(monthUpper)"
        }
        let wIdx = isoWeekdayIndex(from: date)
        return "\(weekdayShorts[min(max(wIdx, 1), 7)]) · \(day) \(monthUpper)"
    }

    private static func formatCountdown(minutesUntilStart: Int, uppercase: Bool) -> String {
        let text: String
        if minutesUntilStart <= 0 {
            text = "agora"
        } else if minutesUntilStart < 60 {
            text = "em \(minutesUntilStart)m"
        } else {
            let h = minutesUntilStart / 60
            let m = minutesUntilStart % 60
            text = m == 0 ? "em \(h)h" : "em \(h)h \(m)m"
        }
        return uppercase ? text.uppercased() : text
    }

    private static func isEadLocation(_ text: String) -> Bool {
        let lower = text.lowercased()
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

    private static func formatRoomBadges(
        building: String,
        room: String,
        shortLocation: String,
        fallbackLocation: String,
        isEad: Bool
    ) -> (spotlight: String, small: String, inline: String) {
        if isEad {
            return ("EAD", "EAD", "EAD")
        }
        let fallbackParts = fallbackLocation.components(separatedBy: "·")
        let resolvedBuilding = simplifyBuilding(building.isEmpty ? (fallbackParts.first ?? "") : building)
        let resolvedRoom = room.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? (fallbackParts.count > 1 ? fallbackParts[1].trimmingCharacters(in: .whitespacesAndNewlines) : "")
            : room.trimmingCharacters(in: .whitespacesAndNewlines)

        let roomToken: String? = {
            let lower = resolvedRoom.lowercased()
            if lower.hasPrefix("sala ") {
                return String(resolvedRoom.dropFirst(5)).trimmingCharacters(in: .whitespacesAndNewlines)
            }
            return nil
        }()

        let spotlight: String = {
            if !resolvedBuilding.isEmpty, let roomToken, !roomToken.isEmpty {
                return "\(resolvedBuilding) · S. \(roomToken)"
            }
            if !resolvedBuilding.isEmpty, !resolvedRoom.isEmpty {
                return "\(resolvedBuilding) · \(resolvedRoom)"
            }
            if !shortLocation.isEmpty { return shortLocation }
            if !resolvedBuilding.isEmpty { return resolvedBuilding }
            return "Campus"
        }()

        let small = (!resolvedBuilding.isEmpty && !resolvedRoom.isEmpty)
            ? "\(resolvedBuilding) · \(resolvedRoom)"
            : spotlight

        let inline: String = {
            if !resolvedBuilding.isEmpty, let roomToken, !roomToken.isEmpty {
                return "\(resolvedBuilding) \(roomToken)"
            }
            if !resolvedBuilding.isEmpty, !resolvedRoom.isEmpty {
                return "\(resolvedBuilding) \(resolvedRoom)"
            }
            if !shortLocation.isEmpty { return shortLocation.replacingOccurrences(of: " · ", with: " ") }
            if !resolvedBuilding.isEmpty { return resolvedBuilding }
            return "Campus"
        }()

        return (spotlight, small, inline)
    }

    private static func minutesFromHHMM(_ text: String) -> Int? {
        let parts = text.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: ":")
        guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]), (0...23).contains(h), (0...59).contains(m) else {
            return nil
        }
        return h * 60 + m
    }
}

// MARK: - Widget Views (Zero 1px Border · 9 Paletas Tonais + Light/Dark Synced)

public struct VidaAcademicaWidgetEntryView: View {
    public let entry: CampusClassEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var systemScheme

    public init(entry: CampusClassEntry) {
        self.entry = entry
    }

    private var appearanceConfig: (accent: CampusAccentColor, scheme: ColorScheme) {
        let loaded = CampusAppearanceController.loadWidgetAppearance()
        return (loaded.accentColor, loaded.resolvedScheme ?? systemScheme)
    }

    public var body: some View {
        let config = appearanceConfig
        let accent = config.accent
        let scheme = config.scheme
        let canvasColor = accent.canvas(for: scheme)

        let content = Group {
            #if os(iOS)
            switch family {
            case .systemSmall:
                smallWidgetBody(accent: accent, scheme: scheme)
            case .accessoryRectangular:
                accessoryRectangularBody
            case .accessoryInline:
                accessoryInlineBody
            case .accessoryCircular:
                accessoryCircularBody
            default:
                mediumWidgetBody(accent: accent, scheme: scheme)
            }
            #else
            if family == .systemSmall {
                smallWidgetBody(accent: accent, scheme: scheme)
            } else {
                mediumWidgetBody(accent: accent, scheme: scheme)
            }
            #endif
        }
        .widgetURL(entry.primaryDeepLinkUrl)

        if #available(iOS 17.0, *) {
            content.containerBackground(for: .widget) {
                #if os(iOS)
                if family == .accessoryRectangular || family == .accessoryInline || family == .accessoryCircular {
                    Color.clear
                } else {
                    canvasColor
                }
                #else
                canvasColor
                #endif
            }
        } else {
            content.background(canvasColor)
        }
    }

    // MARK: 4×2 Medium Widget (Leitura em 1 segundo no corredor)

    @ViewBuilder
    private func mediumWidgetBody(accent: CampusAccentColor, scheme: ColorScheme) -> some View {
        let surface1 = accent.surface1(for: scheme)
        let surface2 = accent.surface2(for: scheme)
        let ink = accent.ink(for: scheme)
        let variant = CampusTheme.secondaryText(for: scheme)
        let accentText = accent.highlight(for: scheme)
        let liveBg = CampusAccentColor.green.surface2(for: scheme)
        let liveFg = CampusAccentColor.green.textOnHighlightSurface(for: scheme)

        VStack(alignment: .leading, spacing: 8) {
            // Cabeçalho em linha única compacta
            Link(destination: URL(string: "vidaacademica://agenda")!) {
                HStack(spacing: 8) {
                    Text(entry.dayPillText)
                        .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                        .foregroundStyle(accent.onPrimary(for: scheme))
                        .padding(.horizontal, 9)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(accent.primary(for: scheme)))

                    Text(entry.headerSummary)
                        .font(CampusTheme.font(size: 13, weight: .bold))
                        .foregroundStyle(ink)
                        .lineLimit(1)

                    Spacer(minLength: 4)

                    if !entry.countdownLabel.isEmpty {
                        Text(entry.countdownLabel)
                            .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                            .foregroundStyle(entry.isOngoing ? liveFg : accentText)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 3)
                            .background(Capsule().fill(entry.isOngoing ? liveBg : surface2))
                    }
                }
            }

            if let spotlight = entry.spotlightClass {
                // Card único elevado em Surface 1 (18pt de raio, 0pt de borda)
                Link(destination: URL(string: "vidaacademica://agenda")!) {
                    VStack(alignment: .leading, spacing: 5) {
                        HStack(spacing: 8) {
                            Text(spotlight.timeRange)
                                .font(CampusTheme.tabularFont(size: 12, weight: .bold))
                                .foregroundStyle(accentText)
                                .lineLimit(1)

                            Spacer(minLength: 4)

                            Text(spotlight.roomPill)
                                .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                                .foregroundStyle(ink)
                                .lineLimit(1)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Capsule().fill(surface2))
                        }

                        Text(spotlight.title)
                            .font(CampusTheme.font(size: 15, weight: .bold))
                            .foregroundStyle(ink)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .fill(surface1)
                    )
                }

                // Aula seguinte em linha aberta compacta (sem caixa / sem borda)
                if let following = entry.followingClass {
                    Link(destination: URL(string: "vidaacademica://agenda")!) {
                        HStack(spacing: 6) {
                            Text(following.startTime)
                                .font(CampusTheme.tabularFont(size: 12, weight: .bold))
                                .foregroundStyle(accentText)

                            Text("·")
                                .font(CampusTheme.font(size: 12, weight: .semibold))
                                .foregroundStyle(variant)

                            Text(following.title)
                                .font(CampusTheme.font(size: 12, weight: .semibold))
                                .foregroundStyle(ink)
                                .lineLimit(1)

                            Spacer(minLength: 4)

                            Text(following.inlineRoom)
                                .font(CampusTheme.tabularFont(size: 11, weight: .medium))
                                .foregroundStyle(variant)
                                .lineLimit(1)
                        }
                        .padding(.horizontal, 2)
                    }
                }

                Spacer(minLength: 0)

                // Rodapé inteligente: próximo prazo pendente -> Atividades, senão 3ª aula -> Agenda
                if let deadline = entry.nextDeadline {
                    Link(destination: URL(string: "vidaacademica://activities")!) {
                        HStack(spacing: 6) {
                            Text("Próximo prazo: \(deadline.title)")
                                .font(CampusTheme.font(size: 11, weight: .medium))
                                .foregroundStyle(variant)
                                .lineLimit(1)

                            Spacer(minLength: 4)

                            Text(deadline.shortDate)
                                .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                                .foregroundStyle(accentText)
                                .lineLimit(1)
                        }
                        .padding(.horizontal, 2)
                    }
                } else if let third = entry.thirdClass {
                    Link(destination: URL(string: "vidaacademica://agenda")!) {
                        HStack(spacing: 6) {
                            Text("\(third.startTime) · \(third.title)")
                                .font(CampusTheme.font(size: 11, weight: .medium))
                                .foregroundStyle(variant)
                                .lineLimit(1)

                            Spacer(minLength: 4)

                            Text(third.inlineRoom)
                                .font(CampusTheme.tabularFont(size: 11, weight: .medium))
                                .foregroundStyle(variant)
                                .lineLimit(1)
                        }
                        .padding(.horizontal, 2)
                    }
                }
            } else {
                // Estado Dia Livre (4×2)
                VStack(alignment: .leading, spacing: 8) {
                    Text(entry.freeDayMessage)
                        .font(CampusTheme.font(size: 16, weight: .bold))
                        .foregroundStyle(ink)

                    let targetUrl = entry.nextDeadline != nil
                        ? URL(string: "vidaacademica://activities")!
                        : URL(string: "vidaacademica://agenda")!

                    Link(destination: targetUrl) {
                        VStack(alignment: .leading, spacing: 5) {
                            HStack(spacing: 6) {
                                Text(freeDayKickerText)
                                    .font(CampusTheme.tabularFont(size: 10, weight: .bold))
                                    .foregroundStyle(accentText)
                                    .lineLimit(1)

                                Spacer(minLength: 4)

                                if !freeDayTrailingText.isEmpty {
                                    Text(freeDayTrailingText)
                                        .font(CampusTheme.tabularFont(size: 10, weight: .semibold))
                                        .foregroundStyle(variant)
                                        .lineLimit(1)
                                }
                            }

                            Text(freeDayDetailText)
                                .font(CampusTheme.font(size: 13, weight: .bold))
                                .foregroundStyle(entry.nextDeadline != nil || entry.nextSchoolDayClass != nil ? ink : variant)
                                .lineLimit(2)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .fill(surface1)
                        )
                    }

                    Spacer(minLength: 0)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    // MARK: 2×2 Small Widget ("Painel de Embarque")

    @ViewBuilder
    private func smallWidgetBody(accent: CampusAccentColor, scheme: ColorScheme) -> some View {
        let surface1 = accent.surface1(for: scheme)
        let surface2 = accent.surface2(for: scheme)
        let ink = accent.ink(for: scheme)
        let variant = CampusTheme.secondaryText(for: scheme)
        let accentText = accent.highlight(for: scheme)
        let liveBg = CampusAccentColor.green.surface2(for: scheme)
        let liveFg = CampusAccentColor.green.textOnHighlightSurface(for: scheme)

        if let spotlight = entry.spotlightClass {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(entry.compactCountdownBadge)
                        .font(CampusTheme.tabularFont(size: 10, weight: .bold))
                        .foregroundStyle(entry.isOngoing ? liveFg : accent.onPrimary(for: scheme))
                        .lineLimit(1)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(entry.isOngoing ? liveBg : accent.primary(for: scheme)))

                    Spacer(minLength: 2)

                    Text(entry.dayPillText)
                        .font(CampusTheme.tabularFont(size: 10, weight: .semibold))
                        .foregroundStyle(variant)
                        .lineLimit(1)
                }

                Spacer(minLength: 2)

                Text(spotlight.startTime)
                    .font(CampusTheme.tabularFont(size: 26, weight: .bold))
                    .foregroundStyle(accentText)
                    .lineLimit(1)

                Text(spotlight.title)
                    .font(CampusTheme.font(size: 13, weight: .bold))
                    .foregroundStyle(ink)
                    .lineLimit(2)

                Text(spotlight.smallRoomPill)
                    .font(CampusTheme.tabularFont(size: 10, weight: .bold))
                    .foregroundStyle(ink)
                    .lineLimit(1)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(surface2))

                Spacer(minLength: 2)

                if let following = entry.followingClass {
                    let footerText = entry.remainingAfterSpotlightCount <= 1
                        ? "+1 aula às \(following.startTime)"
                        : "+\(entry.remainingAfterSpotlightCount) aulas · \(following.startTime)"
                    Text(footerText)
                        .font(CampusTheme.tabularFont(size: 10, weight: .medium))
                        .foregroundStyle(variant)
                        .lineLimit(1)
                } else if let deadline = entry.nextDeadline {
                    Text("Prazo: \(deadline.shortDate)")
                        .font(CampusTheme.tabularFont(size: 10, weight: .semibold))
                        .foregroundStyle(accentText)
                        .lineLimit(1)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        } else {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Text(entry.dayPillText)
                        .font(CampusTheme.tabularFont(size: 10, weight: .bold))
                        .foregroundStyle(accent.onPrimary(for: scheme))
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(accent.primary(for: scheme)))

                    Spacer(minLength: 2)

                    Text("Dia livre")
                        .font(CampusTheme.font(size: 10, weight: .semibold))
                        .foregroundStyle(variant)
                }

                Text(entry.freeDayMessage)
                    .font(CampusTheme.font(size: 13, weight: .bold))
                    .foregroundStyle(ink)
                    .lineLimit(1)

                Spacer(minLength: 2)

                VStack(alignment: .leading, spacing: 3) {
                    Text(smallFreeDayKicker)
                        .font(CampusTheme.tabularFont(size: 9, weight: .bold))
                        .foregroundStyle(accentText)
                        .lineLimit(1)

                    Text(freeDayDetailText)
                        .font(CampusTheme.font(size: 11, weight: .semibold))
                        .foregroundStyle(entry.nextDeadline != nil || entry.nextSchoolDayClass != nil ? ink : variant)
                        .lineLimit(2)
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(surface1)
                )
            }
            .padding(12)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }

    // MARK: Lock Screen Widgets (iOS Accessory Families)

    #if os(iOS)
    @ViewBuilder
    private var accessoryRectangularBody: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let spotlight = entry.spotlightClass {
                HStack(spacing: 4) {
                    Text(entry.compactCountdownBadge)
                        .font(CampusTheme.tabularFont(size: 10, weight: .bold))
                    Text("· \(spotlight.timeRange)")
                        .font(CampusTheme.tabularFont(size: 10, weight: .semibold))
                }
                .foregroundStyle(.secondary)
                .lineLimit(1)

                Text(spotlight.title)
                    .font(CampusTheme.font(size: 13, weight: .bold))
                    .lineLimit(1)

                Text(spotlight.roomPill)
                    .font(CampusTheme.font(size: 11, weight: .medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            } else {
                Text(freeDayKickerText)
                    .font(CampusTheme.tabularFont(size: 10, weight: .bold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Text(entry.freeDayMessage)
                    .font(CampusTheme.font(size: 13, weight: .bold))
                    .lineLimit(1)
                Text(freeDayDetailText)
                    .font(CampusTheme.font(size: 11, weight: .medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private var accessoryInlineBody: some View {
        if let spotlight = entry.spotlightClass {
            Text("\(spotlight.startTime) \(spotlight.title) · \(spotlight.roomPill)")
        } else if let deadline = entry.nextDeadline {
            Text("\(deadline.shortDate) · \(deadline.title)")
        } else {
            Text("\(entry.dayPillText) · Dia livre")
        }
    }

    @ViewBuilder
    private var accessoryCircularBody: some View {
        ZStack {
            if let spotlight = entry.spotlightClass {
                VStack(spacing: 1) {
                    Text(spotlight.startTime)
                        .font(CampusTheme.tabularFont(size: 12, weight: .bold))
                    Text(entry.isOngoing ? "AGORA" : spotlight.smallRoomPill)
                        .font(CampusTheme.tabularFont(size: 8, weight: .semibold))
                        .lineLimit(1)
                }
            } else {
                VStack(spacing: 1) {
                    Text(entry.dayPillText)
                        .font(CampusTheme.tabularFont(size: 10, weight: .bold))
                    Text("LIVRE")
                        .font(CampusTheme.font(size: 8, weight: .semibold))
                }
            }
        }
    }
    #endif

    // MARK: Free-Day Helpers

    private var freeDayKickerText: String {
        if let deadline = entry.nextDeadline {
            return deadline.freeDayHeader
        }
        if let nextDay = entry.nextSchoolDayClass {
            return "PRÓXIMA AULA · \(nextDay.dayBanner)"
        }
        return "AGENDA E PRAZOS"
    }

    private var smallFreeDayKicker: String {
        if let deadline = entry.nextDeadline {
            return deadline.freeDayHeader
        }
        if let nextDay = entry.nextSchoolDayClass {
            return nextDay.dayBanner
        }
        return "TUDO EM DIA"
    }

    private var freeDayTrailingText: String {
        if let deadline = entry.nextDeadline {
            return deadline.dueTime
        }
        if let nextDay = entry.nextSchoolDayClass {
            return nextDay.roomLabel
        }
        return ""
    }

    private var freeDayDetailText: String {
        if let deadline = entry.nextDeadline {
            return "\(deadline.title) · \(deadline.dueTime)"
        }
        if let nextDay = entry.nextSchoolDayClass {
            return "\(nextDay.startTime) · \(nextDay.title)"
        }
        return "Nenhuma aula ou prazo pendente"
    }
}

// MARK: - Widget Configuration

public struct VidaAcademicaLiquidGlassWidget: Widget {
    public let kind: String = "io.github.nuncamorto.vidaacademica.nextclass.widget"

    public init() {}

    public var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: VidaAcademicaTimelineProvider()) { entry in
            VidaAcademicaWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Vida Acadêmica")
        .description("Próxima aula, sala, aula seguinte e próximo prazo UFOP com sincronização tonal.")
        #if os(iOS)
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryInline, .accessoryCircular])
        #else
        .supportedFamilies([.systemSmall, .systemMedium])
        #endif
    }
}

// MARK: - Live Activity Widget (iOS 16.2+ ActivityKit)

#if canImport(ActivityKit) && os(iOS)
@available(iOS 16.2, *)
public struct CampusClassLiveActivityWidget: Widget {
    public init() {}

    public var body: some WidgetConfiguration {
        ActivityConfiguration(for: CampusClassLiveActivityAttributes.self) { context in
            let config = CampusAppearanceController.loadWidgetAppearance()
            let accent = config.accentColor
            let scheme = config.resolvedScheme ?? .light
            let primary = accent.primary(for: scheme)
            let surface2 = accent.surface2(for: scheme)
            let ink = accent.ink(for: scheme)

            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Text(context.state.countdownLabel)
                        .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                        .foregroundStyle(accent.onPrimary(for: scheme))
                        .padding(.horizontal, 9)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(primary))

                    Text(context.state.timeRange)
                        .font(CampusTheme.tabularFont(size: 12, weight: .semibold))
                        .foregroundStyle(ink)

                    Spacer(minLength: 4)

                    Text(context.state.roomPill)
                        .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                        .foregroundStyle(ink)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(surface2))
                }

                Text(context.attributes.courseName)
                    .font(CampusTheme.font(size: 15, weight: .bold))
                    .foregroundStyle(ink)
                    .lineLimit(1)

                ProgressView(value: min(1.0, max(0.0, context.state.progress)))
                    .tint(primary)
            }
            .padding(14)
            .activityBackgroundTint(accent.surface1(for: scheme))
            .activitySystemActionForegroundColor(primary)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Text(context.state.countdownLabel)
                        .font(CampusTheme.tabularFont(size: 12, weight: .bold))
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(context.state.roomPill)
                        .font(CampusTheme.tabularFont(size: 11, weight: .semibold))
                        .lineLimit(1)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(context.attributes.courseName)
                            .font(CampusTheme.font(size: 13, weight: .bold))
                            .lineLimit(1)
                        ProgressView(value: min(1.0, max(0.0, context.state.progress)))
                    }
                }
            } compactLeading: {
                Text(context.state.isOngoing ? "AGORA" : context.state.countdownLabel)
                    .font(CampusTheme.tabularFont(size: 10, weight: .bold))
            } compactTrailing: {
                Text(context.state.roomPill)
                    .font(CampusTheme.tabularFont(size: 10, weight: .semibold))
                    .lineLimit(1)
            } minimal: {
                Text(context.state.isOngoing ? "AGORA" : context.attributes.startTime)
                    .font(CampusTheme.tabularFont(size: 10, weight: .bold))
            }
            .widgetURL(URL(string: "vidaacademica://agenda"))
        }
    }
}
#endif

// MARK: - Widget Extension Entrypoint

@main
public struct VidaAcademicaWidgetBundle: WidgetBundle {
    public init() {}

    public var body: some Widget {
        VidaAcademicaLiquidGlassWidget()
        #if canImport(ActivityKit) && os(iOS)
        CampusClassLiveActivityWidget()
        #endif
    }
}

#Preview("Widget Medium", as: .systemMedium) {
    VidaAcademicaLiquidGlassWidget()
} timeline: {
    CampusClassEntry.placeholder
}

#Preview("Widget Small", as: .systemSmall) {
    VidaAcademicaLiquidGlassWidget()
} timeline: {
    CampusClassEntry.placeholder
}
