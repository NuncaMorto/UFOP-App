import SwiftUI

// MARK: - Paleta e Componentes HUD do Mapa 2D Offline

internal struct MapCanvasPalette {
    let background: Color
    let campusGroundFill: Color
    let campusGroundStroke: Color
    let campusZoneFill: Color
    let campusZoneStroke: Color
    let greenAreaFill: Color
    let parkingFill: Color
    let sportsFill: Color
    let walkwayStroke: Color
    let buildingFill: Color
    let buildingStroke: Color
    let roomOccupiedFill: Color
    let roomFreeFill: Color
    let roomNoDataFill: Color
    let roomOutOfTermFill: Color
    let selectedFill: Color
    let roomStroke: Color
    let selectedStroke: Color
    let poiFill: Color
    let labelColor: Color
    let secondaryLabelColor: Color

    static func resolve(for scheme: ColorScheme, highContrast: Bool) -> MapCanvasPalette {
        if scheme == .dark {
            return MapCanvasPalette(
                background: highContrast ? Color(hex: 0x050607) : Color(hex: 0x0E1218),
                campusGroundFill: Color(hex: 0x151A22),
                campusGroundStroke: Color(hex: 0x2F3846),
                campusZoneFill: Color(hex: 0x1B2330),
                campusZoneStroke: Color(hex: 0x3E4C63),
                greenAreaFill: Color(hex: 0x163122),
                parkingFill: Color(hex: 0x242A35),
                sportsFill: Color(hex: 0x1A2E38),
                walkwayStroke: Color(hex: 0x3A4352),
                buildingFill: highContrast ? Color(hex: 0x202630) : Color(hex: 0x252C37),
                buildingStroke: highContrast ? Color.white.opacity(0.85) : Color(hex: 0x526075),
                roomOccupiedFill: Color(hex: 0x7A2727, alpha: 0.72),
                roomFreeFill: Color(hex: 0x1F5C3A, alpha: 0.65),
                roomNoDataFill: Color(hex: 0x353E4D, alpha: 0.75),
                roomOutOfTermFill: Color(hex: 0x2A303B, alpha: 0.72),
                selectedFill: Color(hex: 0x1E3A5F, alpha: 0.72),
                roomStroke: highContrast ? Color.white : Color(hex: 0x94A3B8),
                selectedStroke: Color(hex: 0x60A5FA),
                poiFill: Color(hex: 0xF59E0B),
                labelColor: highContrast ? .white : Color(hex: 0xF1F5F9),
                secondaryLabelColor: highContrast ? Color(hex: 0xE2E8F0) : Color(hex: 0xCBD5E1)
            )
        }
        return MapCanvasPalette(
            background: highContrast ? .white : Color(hex: 0xF5F3EE),
            campusGroundFill: Color(hex: 0xECEAE2),
            campusGroundStroke: Color(hex: 0xC8C3B8),
            campusZoneFill: Color(hex: 0xE4E8EE),
            campusZoneStroke: Color(hex: 0x9AA5B4),
            greenAreaFill: Color(hex: 0xD3E6CE),
            parkingFill: Color(hex: 0xDCDFE4),
            sportsFill: Color(hex: 0xCEE3EB),
            walkwayStroke: Color(hex: 0xB8B5AA),
            buildingFill: highContrast ? Color(hex: 0xEFEFEF) : Color(hex: 0xFFFFFF),
            buildingStroke: highContrast ? .black : Color(hex: 0x64748B),
            roomOccupiedFill: Color(hex: 0xFAD2CF),
            roomFreeFill: Color(hex: 0xCBEAD3),
            roomNoDataFill: Color(hex: 0xE2E8F0),
            roomOutOfTermFill: Color(hex: 0xECEFF3),
            selectedFill: Color(hex: 0xD6E4FF),
            roomStroke: highContrast ? .black : Color(hex: 0x475569),
            selectedStroke: Color(hex: 0x1D4ED8),
            poiFill: Color(hex: 0xD97706),
            labelColor: highContrast ? .black : Color(hex: 0x0F172A),
            secondaryLabelColor: highContrast ? Color(hex: 0x1F2937) : Color(hex: 0x475569)
        )
    }
}

internal struct MapHudOverlayView: View {
    let floors: [CampusFloorLabel]
    let activeFloor: Int32
    let selectedFeature: CampusFeatureInfo?
    let selectedOccupancy: CampusOccupancyStatus?
    let isNativeAvailable: Bool
    let errorMessage: String?
    let accentColor: Color
    let minTouch: CGFloat
    let highContrast: Bool
    let onSelectFloor: (Int32) -> Void
    let onClearSelection: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            HStack(alignment: .top) {
                if !floors.isEmpty {
                    MapFloorSelectorBar(
                        floors: floors,
                        activeFloor: activeFloor,
                        accentColor: accentColor,
                        minTouch: minTouch,
                        highContrast: highContrast,
                        onSelectFloor: onSelectFloor
                    )
                }
                Spacer()
            }
            .padding(.horizontal, 12)

            Spacer()

            if let selected = selectedFeature {
                HStack {
                    MapSelectedFeatureCard(
                        feature: selected,
                        occupancy: selectedOccupancy,
                        accentColor: accentColor,
                        minTouch: minTouch,
                        highContrast: highContrast,
                        onDismiss: onClearSelection
                    )
                    .frame(maxWidth: 460)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 12)
            } else if !isNativeAvailable {
                HStack {
                    MapUnavailablePlaceholderCard(
                        errorMessage: errorMessage,
                        highContrast: highContrast
                    )
                    .frame(maxWidth: 460)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 12)
            }
        }
        .padding(.top, 12)
    }
}

internal struct MapFloorSelectorBar: View {
    let floors: [CampusFloorLabel]
    let activeFloor: Int32
    let accentColor: Color
    let minTouch: CGFloat
    let highContrast: Bool
    let onSelectFloor: (Int32) -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(floors) { item in
                    let isSelected = item.floor == activeFloor
                    Button {
                        onSelectFloor(item.floor)
                    } label: {
                        Text(item.label)
                            .font(.system(size: 13, weight: isSelected ? .bold : .medium, design: .rounded))
                            .foregroundStyle(
                                isSelected
                                    ? accentColor
                                    : CampusTheme.primaryText(for: colorScheme, highContrast: highContrast)
                            )
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .minimumTouchTarget(minTouch)
                            .liquidGlassCapsule(
                                selected: isSelected,
                                accent: accentColor,
                                interactive: true,
                                highContrast: highContrast
                            )
                    }
                    .buttonStyle(.plain)
                    .campusHoverLift()
                }
            }
        }
    }
}

internal struct MapSelectedFeatureCard: View {
    let feature: CampusFeatureInfo
    let occupancy: CampusOccupancyStatus?
    let accentColor: Color
    let minTouch: CGFloat
    let highContrast: Bool
    let onDismiss: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .center, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(feature.name)
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    if let floor = feature.floor {
                        Text("Andar: \(floor == 0 ? "Térreo / 1º Pav" : "\(floor + 1)º Pav")")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    }
                }
                Spacer()
                if feature.isMock {
                    MapSchematicBadge()
                }
                Button {
                    onDismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .campusHoverLift()
                .accessibilityLabel("Fechar detalhes do local")
            }

            if let occupancy {
                Text(occupancy.displayTitle)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(accentColor)
            } else if let notes = feature.notes, !notes.isEmpty {
                Text(notes)
                    .font(.system(size: 12))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 18, tint: accentColor, interactive: false, highContrast: highContrast)
    }
}

internal struct MapSchematicBadge: View {
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Text("Layout esquemático")
            .font(.system(size: 11, weight: .semibold, design: .rounded))
            .foregroundStyle(CampusTheme.weatherBadgeText(for: colorScheme))
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(
                Capsule(style: .continuous)
                    .fill(CampusTheme.weatherBadgeBackground(for: colorScheme))
            )
    }
}

internal struct MapUnavailablePlaceholderCard: View {
    let errorMessage: String?
    let highContrast: Bool

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Text(errorMessage ?? "Motor vetorial offline pronto para integração nativa (XCFramework).")
            .font(.system(size: 12, weight: .medium))
            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .liquidGlassCard(cornerRadius: 16, interactive: false, highContrast: highContrast)
    }
}
