import SwiftUI

internal struct DynamicGridFeedbackBanner: Equatable {
    enum Kind: Equatable {
        case blocked
        case cascade
    }

    let kind: Kind
    let title: String
    let detail: String?
}

internal struct DynamicGridSemanticPalette {
    let completed: Color
    let available: Color
    let locked: Color

    static func resolve(for scheme: ColorScheme) -> DynamicGridSemanticPalette {
        DynamicGridSemanticPalette(
            completed: scheme == .dark ? Color(hex: 0x34D399) : Color(hex: 0x15803D),
            available: scheme == .dark ? Color(hex: 0x60A5FA) : Color(hex: 0x1D4ED8),
            locked: scheme == .dark ? Color(hex: 0xFB7185) : Color(hex: 0xC2410C)
        )
    }
}

// MARK: - Compact Liquid Glass Header Card

internal struct DynamicGridHeaderCard: View {
    let matrix: DynamicCurriculumMatrix
    let summary: DynamicGridSummary
    let autoResolution: DynamicMatrixResolution?
    let isCollapsed: Bool
    let isVerticalOrientation: Bool
    let primaryAccent: Color
    let onPrimaryAccent: Color
    let palette: DynamicGridSemanticPalette
    let highContrast: Bool
    let reduceMotion: Bool
    let minTouch: CGFloat
    let onOpenMatrixPicker: () -> Void
    let onToggleCollapsed: () -> Void
    let onSelectOrientation: (String) -> Void
    let onRequestClear: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if isCollapsed {
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(summary.completedHours)/\(summary.totalGridHours)h (\(summary.completionPercent)%) · \(summary.completedCount)/\(summary.totalCount)")
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            .lineLimit(1)

                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Capsule(style: .continuous)
                                    .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast))

                                Capsule(style: .continuous)
                                    .fill(primaryAccent)
                                    .frame(
                                        width: max(
                                            summary.completedHours > 0 ? 6 : 0,
                                            proxy.size.width * summary.progressFraction
                                        )
                                    )
                            }
                        }
                        .frame(height: 5)
                        .accessibilityHidden(true)
                    }

                    Button {
                        onToggleCollapsed()
                    } label: {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            .frame(width: 40, height: 40)
                            .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: highContrast)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                    .accessibilityLabel("Expandir controles da grade dinâmica")
                }
            } else {
                HStack(spacing: 8) {
                    Button {
                        onOpenMatrixPicker()
                    } label: {
                        HStack(spacing: 6) {
                            VStack(alignment: .leading, spacing: 1) {
                                Text(matrix.courseName)
                                    .font(.system(size: 14, weight: .bold, design: .rounded))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                                    .lineLimit(1)

                                Text(matrix.curriculumLabel)
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                                    .lineLimit(1)
                            }

                            Spacer(minLength: 4)

                            Image(systemName: "chevron.down")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: highContrast)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                    .accessibilityLabel("Selecionar matriz curricular, atual: \(matrix.courseName), \(matrix.curriculumLabel)")

                    Button {
                        onToggleCollapsed()
                    } label: {
                        Image(systemName: "chevron.up")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            .frame(width: 40, height: 40)
                            .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: highContrast)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                    .accessibilityLabel("Recolher controles da grade dinâmica")
                }

                VStack(alignment: .leading, spacing: 5) {
                    Text("\(summary.completedHours)/\(summary.totalGridHours)h (\(summary.completionPercent)%) · \(summary.completedCount)/\(summary.totalCount) disciplinas")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                    GeometryReader { proxy in
                        ZStack(alignment: .leading) {
                            Capsule(style: .continuous)
                                .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast))

                            Capsule(style: .continuous)
                                .fill(primaryAccent)
                                .frame(
                                    width: max(
                                        summary.completedHours > 0 ? 8 : 0,
                                        proxy.size.width * summary.progressFraction
                                    )
                                )
                                .animation(
                                    reduceMotion ? nil : .spring(response: 0.3, dampingFraction: 0.85),
                                    value: summary.completedHours
                                )
                        }
                    }
                    .frame(height: 8)
                    .accessibilityHidden(true)

                    Text("Atividades/TCC/Estágio: \(matrix.requiredActivitiesHours)h · Total do curso: \(matrix.totalCourseHours)h")
                        .font(.system(size: 10.5, weight: .medium))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
                        .lineLimit(1)

                    if let resolution = autoResolution, resolution.matrix.id == matrix.id, let note = resolution.note {
                        Text(note)
                            .font(.system(size: 10.5, weight: .semibold))
                            .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                            .lineLimit(resolution.isCourseMatched ? 2 : 3)
                    }
                }

                LiquidGlassGroup(spacing: 8) {
                    HStack(spacing: 8) {
                        HStack(spacing: 3) {
                            orientationPill(key: "horizontal", label: "Horizontal", icon: "rectangle.split.3x1")
                            orientationPill(key: "vertical", label: "Vertical", icon: "rectangle.split.1x2")
                        }
                        .padding(3)
                        .frame(height: 38)
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: false, highContrast: highContrast)

                        Spacer(minLength: 4)

                        Button {
                            onRequestClear()
                        } label: {
                            Label("Limpar", systemImage: "trash")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundStyle(
                                    summary.completedCount > 0
                                        ? CampusTheme.accessibleTextColor(palette.locked, scheme: colorScheme)
                                        : CampusTheme.mutedText(for: colorScheme, highContrast: highContrast)
                                )
                                .padding(.horizontal, 14)
                                .frame(height: 38)
                                .liquidGlassCapsule(selected: false, accent: palette.locked, interactive: summary.completedCount > 0, highContrast: highContrast)
                        }
                        .buttonStyle(.plain)
                        .disabled(summary.completedCount == 0)
                        .minimumTouchTarget(minTouch)
                        .accessibilityLabel("Limpar disciplinas marcadas na grade")
                    }
                }

                HStack(spacing: 12) {
                    legendDot(label: "Concluída", color: palette.completed, icon: "checkmark.circle.fill")
                    legendDot(label: "Disponível", color: palette.available, icon: "circle")
                    legendDot(label: "Bloqueada", color: palette.locked, icon: "lock.fill")
                    Spacer(minLength: 0)
                }
            }
        }
        .padding(isCollapsed ? 10 : 14)
        .liquidGlassCard(cornerRadius: isCollapsed ? 16 : 20, tint: primaryAccent, highContrast: highContrast)
    }

    private func orientationPill(key: String, label: String, icon: String) -> some View {
        let isSelected = (key == "vertical") ? isVerticalOrientation : !isVerticalOrientation

        return Button {
            onSelectOrientation(key)
        } label: {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 11, weight: .bold))
                Text(label)
                    .font(.system(size: 11.5, weight: .bold, design: .rounded))
            }
            .foregroundStyle(
                isSelected
                    ? onPrimaryAccent
                    : CampusTheme.primaryText(for: colorScheme, highContrast: highContrast)
            )
            .padding(.horizontal, 12)
            .frame(height: 32)
            .background(
                Capsule(style: .continuous)
                    .fill(isSelected ? primaryAccent : Color.clear)
            )
        }
        .buttonStyle(.plain)
        .minimumTouchTarget(minTouch)
        .accessibilityLabel("Modo \(label)")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func legendDot(label: String, color: Color, icon: String) -> some View {
        let accessible = CampusTheme.accessibleTextColor(color, scheme: colorScheme)
        return HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 10.5, weight: .bold))
                .foregroundStyle(accessible)
            Text(label)
                .font(.system(size: 11, weight: .semibold, design: .rounded))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
        }
    }
}

// MARK: - Inline Feedback Banner

internal struct DynamicGridFeedbackBannerView: View {
    let banner: DynamicGridFeedbackBanner
    let lockedBaseColor: Color
    let highContrast: Bool
    let onDismiss: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let tint = banner.kind == .blocked ? lockedBaseColor : Color(hex: colorScheme == .dark ? 0xFBBF24 : 0xB45309)
        let accessibleTint = CampusTheme.accessibleTextColor(tint, scheme: colorScheme)

        HStack(alignment: .top, spacing: 10) {
            Image(systemName: banner.kind == .blocked ? "lock.fill" : "arrow.uturn.backward.circle.fill")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(accessibleTint)
                .padding(.top, 1)

            VStack(alignment: .leading, spacing: 2) {
                Text(banner.title)
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    .fixedSize(horizontal: false, vertical: true)

                if let detail = banner.detail, !detail.isEmpty {
                    Text(detail)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            Spacer(minLength: 4)

            Button {
                onDismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
                    .frame(width: 28, height: 28)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Fechar aviso")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .liquidGlassCard(cornerRadius: 16, tint: tint, highContrast: highContrast)
    }
}

// MARK: - 2D Scrollable Grid (Horizontal & Vertical Modes)

internal struct DynamicGridContentView: View {
    let matrix: DynamicCurriculumMatrix
    let completedSet: Set<String>
    let isVerticalOrientation: Bool
    let isRegular: Bool
    let cardWidth: CGFloat
    let cardHeight: CGFloat
    let gridSpacing: CGFloat
    let primaryAccent: Color
    let palette: DynamicGridSemanticPalette
    let highContrast: Bool
    let onTapCourse: (DynamicCourseNode) -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        if isVerticalOrientation {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(1...max(1, matrix.totalPeriods), id: \.self) { period in
                    let periodCourses = matrix.courses(inPeriod: period)
                    let doneCount = periodCourses.filter { completedSet.contains($0.code) }.count
                    let periodHours = periodCourses.reduce(0) { $0 + $1.workloadHours }

                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 8) {
                            Text("\(period)º Período")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                            Text("\(doneCount)/\(periodCourses.count) · \(periodHours)h")
                                .font(.system(size: 11, weight: .semibold, design: .rounded))
                                .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: false, highContrast: highContrast)

                        HStack(alignment: .top, spacing: gridSpacing) {
                            ForEach(periodCourses) { course in
                                DynamicGridCourseCard(
                                    course: course,
                                    matrix: matrix,
                                    completedSet: completedSet,
                                    cardWidth: cardWidth,
                                    cardHeight: cardHeight,
                                    palette: palette,
                                    highContrast: highContrast,
                                    onTap: { onTapCourse(course) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            HStack(alignment: .top, spacing: isRegular ? 14 : 10) {
                ForEach(1...max(1, matrix.totalPeriods), id: \.self) { period in
                    let periodCourses = matrix.courses(inPeriod: period)
                    let doneCount = periodCourses.filter { completedSet.contains($0.code) }.count

                    VStack(alignment: .leading, spacing: gridSpacing) {
                        HStack {
                            Text("\(period)º Per.")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            Spacer(minLength: 4)
                            Text("\(doneCount)/\(periodCourses.count)")
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                        }
                        .padding(.horizontal, 12)
                        .frame(width: cardWidth, height: 34)
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: false, highContrast: highContrast)

                        VStack(spacing: gridSpacing) {
                            ForEach(periodCourses) { course in
                                DynamicGridCourseCard(
                                    course: course,
                                    matrix: matrix,
                                    completedSet: completedSet,
                                    cardWidth: cardWidth,
                                    cardHeight: cardHeight,
                                    palette: palette,
                                    highContrast: highContrast,
                                    onTap: { onTapCourse(course) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Course Node Card

internal struct DynamicGridCourseCard: View {
    let course: DynamicCourseNode
    let matrix: DynamicCurriculumMatrix
    let completedSet: Set<String>
    let cardWidth: CGFloat
    let cardHeight: CGFloat
    let palette: DynamicGridSemanticPalette
    let highContrast: Bool
    let onTap: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let status = DynamicGridEngine.status(for: course, matrix: matrix, completedCodes: completedSet)
        let missing = DynamicGridEngine.missingPrerequisites(for: course, completedCodes: completedSet)

        let stateColor: Color = {
            switch status {
            case .completed: return palette.completed
            case .available: return palette.available
            case .locked: return palette.locked
            }
        }()

        let accessibleStateColor = CampusTheme.accessibleTextColor(stateColor, scheme: colorScheme)

        let statusIcon: String = {
            switch status {
            case .completed: return "checkmark.circle.fill"
            case .available: return "circle"
            case .locked: return "lock.fill"
            }
        }()

        let statusLabel: String = {
            switch status {
            case .completed: return "Concluída"
            case .available: return "Disponível"
            case .locked: return "Bloqueada"
            }
        }()

        let cardSurface: Color = {
            if highContrast {
                return CampusTheme.cardBackground(for: colorScheme, highContrast: true)
            }
            switch status {
            case .completed:
                return stateColor.opacity(colorScheme == .dark ? 0.22 : 0.13)
            case .available:
                return CampusTheme.cardBackground(for: colorScheme).opacity(colorScheme == .dark ? 0.85 : 0.92)
            case .locked:
                return stateColor.opacity(colorScheme == .dark ? 0.14 : 0.08)
            }
        }()

        let borderStroke: Color = {
            if highContrast {
                return stateColor
            }
            switch status {
            case .completed:
                return stateColor.opacity(colorScheme == .dark ? 0.78 : 0.65)
            case .available:
                return stateColor.opacity(colorScheme == .dark ? 0.50 : 0.38)
            case .locked:
                return stateColor.opacity(colorScheme == .dark ? 0.44 : 0.34)
            }
        }()

        Button {
            onTap()
        } label: {
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 4) {
                    Text(course.code)
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(accessibleStateColor)
                        .lineLimit(1)

                    Spacer(minLength: 2)

                    Image(systemName: statusIcon)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(accessibleStateColor)
                }

                Text(course.name)
                    .font(.system(size: 11.5, weight: .semibold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    .lineLimit(3)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Spacer(minLength: 0)

                HStack(spacing: 4) {
                    Text("\(course.workloadHours)h")
                        .font(.system(size: 10.5, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

                    Spacer(minLength: 2)

                    if course.isElective {
                        Text("Eletiva")
                            .font(.system(size: 9.5, weight: .bold, design: .rounded))
                            .foregroundStyle(accessibleStateColor)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(stateColor.opacity(colorScheme == .dark ? 0.24 : 0.14))
                            )
                    } else if status == .locked && !missing.isEmpty {
                        Text("\(missing.count) pré")
                            .font(.system(size: 9.5, weight: .bold, design: .rounded))
                            .foregroundStyle(accessibleStateColor)
                    }
                }
            }
            .padding(10)
            .frame(width: cardWidth, height: cardHeight, alignment: .topLeading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(cardSurface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(borderStroke, lineWidth: highContrast ? 2 : (status == .completed ? 1.5 : 1))
            )
        }
        .buttonStyle(.plain)
        .campusHoverLift()
        .accessibilityLabel(
            "\(course.code), \(course.name), \(course.period)º período, \(course.workloadHours) horas\(course.isElective ? ", eletiva" : ""), \(statusLabel)"
        )
        .accessibilityHint(
            status == .locked && !missing.isEmpty
                ? "Bloqueada. Conclua primeiro: \(missing.joined(separator: ", "))."
                : (status == .completed ? "Toque para desmarcar." : "Toque para marcar como concluída.")
        )
    }
}
