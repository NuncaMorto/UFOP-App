import SwiftUI
import WidgetKit

// MARK: - Timeline Entry

public struct CampusClassEntry: TimelineEntry {
    public let date: Date
    public let disciplineName: String
    public let location: String
    public let timeRange: String
    public let weatherTempC: Int
    public let weatherSymbol: String
    public let colorKey: DepartmentColorKey
    public let status: AttendanceStatus
    public let hasNextClass: Bool

    public static let placeholder = CampusClassEntry(
        date: Date(),
        disciplineName: "Cálculo II",
        location: "ICEB · Sala 19",
        timeRange: "08:00–09:40",
        weatherTempC: 22,
        weatherSymbol: "sun.max.fill",
        colorKey: .mathBlue,
        status: .attending,
        hasNextClass: true
    )
}

// MARK: - Timeline Provider

public struct VidaAcademicaTimelineProvider: TimelineProvider {
    public init() {}

    public func placeholder(in context: Context) -> CampusClassEntry {
        .placeholder
    }

    public func getSnapshot(in context: Context, completion: @escaping (CampusClassEntry) -> Void) {
        completion(loadNextClassEntry())
    }

    public func getTimeline(in context: Context, completion: @escaping (Timeline<CampusClassEntry>) -> Void) {
        let entry = loadNextClassEntry()
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: Date())
            ?? Date().addingTimeInterval(1800)
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }

    private func loadNextClassEntry() -> CampusClassEntry {
        let store = CampusStore()
        if let spotlight = store.currentOrNextClassInfo() {
            return CampusClassEntry(
                date: Date(),
                disciplineName: spotlight.item.title,
                location: spotlight.item.location.isEmpty ? "Campus" : spotlight.item.location,
                timeRange: "\(spotlight.dayLabel) · \(spotlight.item.timeRangeLabel)",
                weatherTempC: spotlight.item.weatherTempC,
                weatherSymbol: "sun.max.fill",
                colorKey: spotlight.item.colorKey,
                status: spotlight.item.attendanceStatus,
                hasNextClass: true
            )
        }
        return .placeholder
    }
}

// MARK: - Widget View (iOS 26 Liquid Glass + iOS 17..18 fallback)

public struct VidaAcademicaWidgetEntryView: View {
    public let entry: CampusClassEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var colorScheme

    public init(entry: CampusClassEntry) {
        self.entry = entry
    }

    public var body: some View {
        let content = Group {
            if family == .systemSmall {
                smallWidgetBody
            } else {
                mediumWidgetBody
            }
        }

        if #available(iOS 17.0, *) {
            content
                .containerBackground(.clear, for: .widget)
        } else {
            content
                .background(CampusTheme.cardBackground(for: colorScheme))
        }
    }

    private var smallWidgetBody: some View {
        let tint = CampusTheme.departmentColor(for: entry.colorKey)

        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Circle()
                    .fill(tint)
                    .frame(width: 8, height: 8)
                Text(entry.timeRange)
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                Spacer()
                Text("\(entry.weatherTempC)°")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.weatherBadgeText(for: colorScheme))
            }

            Spacer(minLength: 2)

            Text(entry.disciplineName)
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                .lineLimit(2)

            Text(entry.location)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                .lineLimit(1)
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .modifier(WidgetGlassModifier(tint: tint))
    }

    private var mediumWidgetBody: some View {
        let tint = CampusTheme.departmentColor(for: entry.colorKey)

        return HStack(spacing: 14) {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(tint)
                .frame(width: 5)
                .padding(.vertical, 6)

            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 8) {
                    Text(entry.status == .attending ? "PRÓXIMA AULA" : entry.status.badgeTitle.uppercased())
                        .font(.system(size: 10, weight: .bold, design: .rounded))
                        .tracking(0.8)
                        .foregroundStyle(tint)

                    Spacer()

                    HStack(spacing: 4) {
                        Image(systemName: entry.weatherSymbol)
                            .font(.system(size: 11, weight: .semibold))
                        Text("\(entry.weatherTempC) °C")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(CampusTheme.weatherBadgeText(for: colorScheme))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(CampusTheme.weatherBadgeBackground(for: colorScheme)))
                }

                Text(entry.disciplineName)
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                    .lineLimit(1)

                HStack(spacing: 10) {
                    Label(entry.timeRange, systemImage: "clock.fill")
                    Label(entry.location, systemImage: "building.2.fill")
                }
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                .lineLimit(1)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .modifier(WidgetGlassModifier(tint: tint))
    }
}

private struct WidgetGlassModifier: ViewModifier {
    let tint: Color

    @ViewBuilder
    func body(content: Content) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            content
                .glassEffect(.regular.tint(tint.opacity(0.14)), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        } else {
            fallback(content)
        }
        #else
        fallback(content)
        #endif
    }

    private func fallback(_ content: Content) -> some View {
        content
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
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
        .description("Próxima aula, sala e clima do campus para estudantes da UFOP.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

#Preview("Widget Medium", as: .systemMedium) {
    VidaAcademicaLiquidGlassWidget()
} timeline: {
    CampusClassEntry.placeholder
}
