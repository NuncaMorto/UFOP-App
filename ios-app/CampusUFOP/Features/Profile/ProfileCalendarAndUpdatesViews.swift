import SwiftUI

// MARK: - 2. Calendário Oficial (2026.2, 2027.1, 2027.2)

internal struct ProfileCalendarSectionView: View {
    let calendarEvents: [AcademicCalendarEvent]
    let accentColor: CampusAccentColor
    let primaryAccent: Color
    let onPrimaryAccent: Color
    let highContrast: Bool
    let minTouch: CGFloat
    @Binding var selectedSemester: String
    @Binding var selectedCategory: String

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let semesters = ["2026.2", "2027.1", "2027.2"]
        let semEvents = calendarEvents.filter { $0.semester == selectedSemester }
        let categories = ["Todos"] + Array(Set(semEvents.map(\.category))).sorted()
        let filtered = semEvents.filter {
            selectedCategory == "Todos" || $0.category == selectedCategory
        }

        VStack(alignment: .leading, spacing: 12) {
            Text("Calendários Acadêmicos")
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

            HStack(spacing: 8) {
                ForEach(semesters, id: \.self) { sem in
                    let isSelected = selectedSemester == sem
                    Button {
                        withAnimation(.snappy) {
                            selectedSemester = sem
                            selectedCategory = "Todos"
                        }
                    } label: {
                        Text(sem)
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundStyle(isSelected ? onPrimaryAccent : CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            .frame(maxWidth: .infinity, minHeight: 40)
                            .liquidGlassCapsule(selected: isSelected, accent: primaryAccent, interactive: true, highContrast: highContrast)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(categories, id: \.self) { cat in
                        let isSelected = selectedCategory == cat
                        Button {
                            selectedCategory = cat
                        } label: {
                            Text(cat)
                                .font(.system(size: 12, weight: .semibold, design: .rounded))
                                .foregroundStyle(isSelected ? onPrimaryAccent : CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                                .padding(.horizontal, 12)
                                .frame(minHeight: 34)
                                .liquidGlassCapsule(selected: isSelected, accent: primaryAccent, interactive: true, highContrast: highContrast)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            Text("\(filtered.count) evento(s) em \(selectedSemester)")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))

            VStack(spacing: 10) {
                ForEach(filtered) { ev in
                    HStack(spacing: 12) {
                        VStack(spacing: 1) {
                            Text(ev.dayNumber)
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                            Text(ev.monthShort)
                                .font(.system(size: 9, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                        }
                        .frame(width: 44, height: 42)
                        .background(RoundedRectangle(cornerRadius: 11).fill(accentColor.softTint(for: colorScheme)))

                        VStack(alignment: .leading, spacing: 3) {
                            Text(ev.title)
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            Text(ev.category)
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(12)
                    .liquidGlassCard(cornerRadius: 16, highContrast: highContrast)
                }
            }
        }
    }
}

// MARK: - 4. Atualizações & Histórico de Patches

internal struct ProfileUpdatesSectionView: View {
    let primaryAccent: Color
    let onPrimaryAccent: Color
    let highContrast: Bool
    let minTouch: CGFloat
    let onOpenReleaseNotes: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let greenCheckTint = Color(hex: colorScheme == .dark ? 0x81C784 : 0x2E7D32)

        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Versão Atual")
                        .font(CampusTheme.font(size: 16, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    Text("Vida Acadêmica v\(CampusReleaseNotesData.currentVersionString)")
                        .font(CampusTheme.tabularFont(size: 12.5, weight: .semibold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }

                Spacer()

                Text("v\(CampusReleaseNotesData.currentVersionString)")
                    .font(CampusTheme.tabularFont(size: 12, weight: .bold))
                    .foregroundStyle(onPrimaryAccent)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Capsule(style: .continuous).fill(primaryAccent))
            }

            Button {
                onOpenReleaseNotes()
            } label: {
                Label("Abrir resumo em janela", systemImage: "sparkles")
                    .font(CampusTheme.font(size: 13, weight: .bold))
                    .foregroundStyle(onPrimaryAccent)
                    .frame(maxWidth: .infinity, minHeight: max(44, minTouch))
                    .liquidGlassCapsule(selected: true, accent: primaryAccent, interactive: true, highContrast: highContrast)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 24, tint: nil, highContrast: highContrast)

        Text("HISTÓRICO DE PATCHES")
            .font(CampusTheme.font(size: 11, weight: .bold))
            .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
            .padding(.horizontal, 4)

        VStack(spacing: 10) {
            ForEach(CampusReleaseNotesData.versions) { entry in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("\(entry.version) · \(entry.title)")
                            .font(CampusTheme.tabularFont(size: 14, weight: .bold))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                        Spacer()

                        Text(entry.badge)
                            .font(CampusTheme.font(size: 10, weight: .bold))
                            .foregroundStyle(
                                entry.isLatest
                                    ? primaryAccent
                                    : CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast)
                            )
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(
                                Capsule(style: .continuous).fill(
                                    entry.isLatest
                                        ? primaryAccent.opacity(colorScheme == .dark ? 0.22 : 0.14)
                                        : CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast)
                                )
                            )
                    }

                    ForEach(entry.items, id: \.self) { item in
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 12))
                                .foregroundStyle(greenCheckTint)
                                .padding(.top, 2)

                            Text(item)
                                .font(CampusTheme.font(size: 12.5))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .padding(16)
                .liquidGlassCard(cornerRadius: 24, tint: nil, highContrast: highContrast)
            }
        }
    }
}
