import WidgetKit
import SwiftUI

// MARK: - Modelos de Dados do Widget

public struct WidgetScheduleRow: Identifiable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let time: String
    public let colorHex: String

    public init(id: String, title: String, subtitle: String, time: String, colorHex: String) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.time = time
        self.colorHex = colorHex
    }
}

public struct VidaAcademicaEntry: TimelineEntry {
    public let date: Date
    public let headerDay: String
    public let items: [WidgetScheduleRow]

    public init(date: Date, headerDay: String, items: [WidgetScheduleRow]) {
        self.date = date
        self.headerDay = headerDay
        self.items = items
    }
}

// MARK: - Timeline Provider

public struct VidaAcademicaTimelineProvider: TimelineProvider {
    public typealias Entry = VidaAcademicaEntry

    public init() {}

    public func placeholder(in context: Context) -> VidaAcademicaEntry {
        VidaAcademicaEntry(
            date: Date(),
            headerDay: "Hoje · Quarta-feira",
            items: [
                WidgetScheduleRow(
                    id: "sample_1",
                    title: "Cálculo Diferencial e Integral I",
                    subtitle: "Prof. Dr. Ricardo Silva · ICEA 204",
                    time: "08:00–09:40",
                    colorHex: "#5E8BFF"
                ),
                WidgetScheduleRow(
                    id: "sample_2",
                    title: "Estruturas de Dados Avançadas",
                    subtitle: "DECOM Lab 02 · ICEB",
                    time: "10:00–11:40",
                    colorHex: "#6BCB77"
                )
            ]
        )
    }

    public func getSnapshot(in context: Context, completion: @escaping (VidaAcademicaEntry) -> Void) {
        completion(loadCurrentEntry())
    }

    public func getTimeline(in context: Context, completion: @escaping (Timeline<VidaAcademicaEntry>) -> Void) {
        let entry = loadCurrentEntry()
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date().addingTimeInterval(1800)
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        completion(timeline)
    }

    private func loadCurrentEntry() -> VidaAcademicaEntry {
        let now = Date()
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "pt_BR")
        formatter.dateFormat = "EEEE"
        let dayName = formatter.string(from: now).capitalized
        let header = "Hoje · \(dayName)"

        let items = [
            WidgetScheduleRow(
                id: "1",
                title: "Cálculo Diferencial I",
                subtitle: "Prof. Ricardo · ICEA 204",
                time: "08:00–09:40",
                colorHex: "#5E8BFF"
            ),
            WidgetScheduleRow(
                id: "2",
                title: "Estruturas de Dados",
                subtitle: "DECOM Lab 02 · ICEB",
                time: "10:00–11:40",
                colorHex: "#6BCB77"
            )
        ]
        return VidaAcademicaEntry(date: now, headerDay: header, items: items)
    }
}

// MARK: - SwiftUI Liquid Glass View

public struct VidaAcademicaWidgetEntryView: View {
    public let entry: VidaAcademicaTimelineProvider.Entry

    public init(entry: VidaAcademicaTimelineProvider.Entry) {
        self.entry = entry
    }

    public var body: some View {
        ZStack {
            // Fundo Liquid Glass com degradê esmeralda-obsidiana profundo
            LinearGradient(
                colors: [
                    Color(hexString: "#0E1F16").opacity(0.92),
                    Color(hexString: "#183526").opacity(0.85)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            // Camada de refração vítrea ultrafina (.ultraThinMaterial)
            Rectangle()
                .fill(.ultraThinMaterial)

            // Borda especular hairline elegante
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Color.white.opacity(0.18), lineWidth: 0.8)

            // Conteúdo principal
            VStack(alignment: .leading, spacing: 6) {
                // Cabeçalho do Widget: Hoje · <dia> & Marca Vida Acadêmica
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(entry.headerDay)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)

                        Text("Vida Acadêmica")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(Color(hexString: "#6BCB77"))
                    }

                    Spacer()

                    // Emblema sutil do campus
                    HStack(spacing: 3) {
                        Circle().fill(Color(hexString: "#5E8BFF")).frame(width: 5, height: 5)
                        Circle().fill(Color(hexString: "#6BCB77")).frame(width: 5, height: 5)
                        Circle().fill(Color(hexString: "#E9B44C")).frame(width: 5, height: 5)
                    }
                    .padding(.horizontal, 7)
                    .padding(.vertical, 4)
                    .background(
                        Capsule()
                            .fill(Color.white.opacity(0.08))
                            .overlay(Capsule().stroke(Color.white.opacity(0.14), lineWidth: 0.5))
                    )
                }

                if entry.items.isEmpty {
                    Spacer()
                    HStack {
                        Spacer()
                        Text("Nenhuma aula ou compromisso hoje")
                            .font(.system(size: 12))
                            .foregroundColor(Color(hexString: "#8E95A2"))
                        Spacer()
                    }
                    Spacer()
                } else {
                    // Lista de horários do dia (máximo 2 linhas para acomodação limpa no .systemMedium)
                    VStack(spacing: 5) {
                        ForEach(entry.items.prefix(2)) { item in
                            HStack(spacing: 8) {
                                // Barra de cor do departamento / compromisso (4dp de largura)
                                RoundedRectangle(cornerRadius: 2, style: .continuous)
                                    .fill(Color(hexString: item.colorHex))
                                    .frame(width: 4, height: 32)

                                // Título e local/professor
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(item.title)
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)

                                    Text(item.subtitle)
                                        .font(.system(size: 10))
                                        .foregroundColor(Color(hexString: "#A0AAB7"))
                                        .lineLimit(1)
                                }

                                Spacer()

                                // Horário de início e término
                                Text(item.time)
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(Color(hexString: "#CBE6B1"))
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(Color.white.opacity(0.06))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                                            .stroke(Color.white.opacity(0.10), lineWidth: 0.5)
                                    )
                            )
                        }
                    }
                }
            }
            .padding(12)
        }
    }
}

// MARK: - Definição do Widget WidgetKit

public struct VidaAcademicaLiquidGlassWidget: Widget {
    public let kind: String = "VidaAcademicaLiquidGlassWidget"

    public init() {}

    public var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: VidaAcademicaTimelineProvider()) { entry in
            VidaAcademicaWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Vida Acadêmica")
        .description("Horários e compromissos de hoje com acabamento Liquid Glass.")
        .supportedFamilies([.systemMedium])
    }
}
