import SwiftUI

// MARK: - Painel de Detalhes de Feição e Resumo de Salas Livres

internal struct CampusMapDetailSheetView: View {
    let feature: CampusFeatureInfo
    let occupancy: CampusOccupancyStatus?
    let buildingOccupancy: [CampusRoomOccupancyItem]
    let activeFloor: Int32
    let accentColor: Color
    let minTouch: CGFloat
    let highContrast: Bool
    let onFocusRoom: (String) -> Void
    let onSelectFloor: (Int32) -> Void
    let onDismiss: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    private var onPrimaryColor: Color {
        (appearance?.accentColor ?? .red).onPrimary(for: colorScheme)
    }

    private var freeBuildingRooms: [CampusRoomOccupancyItem] {
        buildingOccupancy.filter { $0.status.isFree }
    }

    private var occupiedBuildingRooms: [CampusRoomOccupancyItem] {
        buildingOccupancy.filter { $0.status.isOccupied }
    }

    private var outOfTermBuildingRooms: [CampusRoomOccupancyItem] {
        buildingOccupancy.filter { $0.status == .outOfTerm }
    }

    private var noDataBuildingRooms: [CampusRoomOccupancyItem] {
        buildingOccupancy.filter { $0.status == .noData }
    }

    private var layerTitle: String {
        if let poi = feature.poiType { return poi.title }
        switch feature.layer {
        case .room: return "Sala"
        case .building: return "Prédio"
        case .campusZone: return "Setor do Campus"
        case .parking: return "Estacionamento"
        case .sports: return "Esportes"
        case .greenArea: return "Área Verde"
        case .poi: return "Ponto de Interesse"
        default: return "Local do Campus"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            headerSection

            if let occupancy {
                roomOccupancySection(occupancy)
            } else if !buildingOccupancy.isEmpty {
                buildingOccupancySection
            } else if feature.layer == .building || feature.layer == .campusZone {
                Text("Sem grade cadastrada para este dia/prédio")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            }

            if feature.availableFloors.count > 1 && (feature.layer == .building || feature.layer == .campusZone) {
                floorQuickSwitchRow
            }

            if let notes = feature.notes, !notes.isEmpty {
                Text(notes)
                    .font(.system(size: 12))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: CampusTheme.cardCornerRadius, tint: accentColor, interactive: false, highContrast: highContrast)
    }

    private var headerSection: some View {
        HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(layerTitle)
                        .font(CampusTheme.font(size: 11, weight: .bold))
                        .foregroundStyle(accentColor)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(accentColor.opacity(colorScheme == .dark ? 0.22 : 0.12)))

                    if let code = feature.code, !code.isEmpty, code != feature.name {
                        Text(code)
                            .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(
                                Capsule().fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast))
                            )
                    }

                    if feature.isMock {
                        MapSchematicBadge()
                    }
                }

                Text(feature.name)
                    .font(CampusTheme.font(size: 16, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                if let subtitle = subtitleText, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }
            }

            Spacer(minLength: 4)

            Button {
                onDismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
            .campusHoverLift()
            .accessibilityLabel("Fechar detalhes do local")
        }
    }

    private var subtitleText: String? {
        var parts: [String] = []
        if let category = CampusMapPresentationHelpers.localizeCategory(feature.category) {
            parts.append(category)
        }
        if let floor = feature.floor {
            parts.append("Andar: \(floor == 0 ? "Térreo" : "\(floor + 1)º Pav")")
        }
        if !feature.departments.isEmpty {
            parts.append(feature.departments.joined(separator: " · "))
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    @ViewBuilder
    private func roomOccupancySection(_ status: CampusOccupancyStatus) -> some View {
        switch status {
        case .occupied(let slot):
            let tint = colorScheme == .dark ? Color(hex: 0xF87171) : Color(hex: 0xDC2626)
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Circle().fill(tint).frame(width: 8, height: 8)
                    Text("Ocupada agora")
                        .font(CampusTheme.font(size: 12, weight: .bold))
                        .foregroundStyle(tint)
                    Spacer()
                    Text("\(slot.startTime)–\(slot.endTime)")
                        .font(CampusTheme.tabularFont(size: 12, weight: .bold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }

                Text(slot.label)
                    .font(CampusTheme.font(size: 14, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                if let title = slot.title, !title.isEmpty, title != slot.label {
                    Text(title)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(tint.opacity(colorScheme == .dark ? 0.18 : 0.10))
            )

        case .noRegularClass:
            let freeColor = colorScheme == .dark ? Color(hex: 0x4ADE80) : Color(hex: 0x15803D)
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(freeColor)
                Text("Sem aula regular agora")
                    .font(CampusTheme.font(size: 13, weight: .bold))
                    .foregroundStyle(freeColor)
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(freeColor.opacity(colorScheme == .dark ? 0.18 : 0.10))
            )

        case .noData:
            Label("Sem grade cadastrada para este dia/prédio", systemImage: "calendar.badge.exclamationmark")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

        case .outOfTerm:
            Label("Fora do período letivo / feriado", systemImage: "sun.max.fill")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CampusTheme.weatherBadgeText(for: colorScheme))
        }
    }

    private var buildingOccupancySection: some View {
        let freeColor = colorScheme == .dark ? Color(hex: 0x4ADE80) : Color(hex: 0x15803D)
        let occColor = colorScheme == .dark ? Color(hex: 0xF87171) : Color(hex: 0xDC2626)

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text("\(freeBuildingRooms.count) livre(s)")
                    .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                    .foregroundStyle(freeColor)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(freeColor.opacity(colorScheme == .dark ? 0.20 : 0.12)))

                Text("\(occupiedBuildingRooms.count) ocupada(s)")
                    .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                    .foregroundStyle(occColor)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(occColor.opacity(colorScheme == .dark ? 0.20 : 0.12)))

                if outOfTermBuildingRooms.count == buildingOccupancy.count {
                    Text("Fora do período letivo / feriado")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                } else if noDataBuildingRooms.count == buildingOccupancy.count {
                    Text("Sem grade cadastrada para este dia/prédio")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(buildingOccupancy) { item in
                        let chipColor: Color = {
                            if item.status.isFree { return freeColor }
                            if item.status.isOccupied { return occColor }
                            return CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast)
                        }()
                        Button {
                            onFocusRoom(item.roomId)
                        } label: {
                            HStack(spacing: 4) {
                                Circle()
                                    .fill(chipColor)
                                    .frame(width: 6, height: 6)
                                Text(item.roomCode)
                                    .font(CampusTheme.tabularFont(size: 12, weight: .bold))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                                Text(item.floor == 0 ? "T" : "\(item.floor + 1)º")
                                    .font(CampusTheme.tabularFont(size: 10, weight: .semibold))
                                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 7)
                            .background(
                                Capsule().fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast))
                            )
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(38)
                        .campusHoverLift()
                    }
                }
            }
        }
    }

    private var floorQuickSwitchRow: some View {
        let labelsByFloor = Dictionary(uniqueKeysWithValues: feature.floorLabels.map { ($0.floor, $0.label) })
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(feature.availableFloors, id: \.self) { floor in
                    let isSelected = floor == activeFloor
                    let text = labelsByFloor[floor] ?? (floor == 0 ? "Térreo" : "\(floor)º Pav")
                    Button {
                        onSelectFloor(floor)
                    } label: {
                        Text(text)
                            .font(CampusTheme.font(size: 11, weight: .bold))
                            .foregroundStyle(isSelected ? onPrimaryColor : CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .liquidGlassCapsule(selected: isSelected, accent: accentColor, interactive: true, highContrast: highContrast)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(36)
                }
            }
        }
    }
}

// MARK: - Resumo de Salas Livres do Andar Ativo

internal struct CampusMapFreeRoomsSummaryView: View {
    let floorLabel: String
    let summary: CampusFloorOccupancySummary
    let freeRooms: [CampusRenderFeature]
    let accentColor: Color
    let minTouch: CGFloat
    let highContrast: Bool
    let onSelectRoom: (String) -> Void
    let onDismiss: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    private var onPrimaryColor: Color {
        (appearance?.accentColor ?? .red).onPrimary(for: colorScheme)
    }

    var body: some View {
        let freeColor = colorScheme == .dark ? Color(hex: 0x4ADE80) : Color(hex: 0x15803D)

        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "door.left.hand.open")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(freeColor)

                VStack(alignment: .leading, spacing: 1) {
                    Text("Salas livres · \(floorLabel)")
                        .font(CampusTheme.font(size: 14, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                    Text("\(summary.freeCount) livre(s) · \(summary.occupiedCount) ocupada(s)")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }

                Spacer()

                Button {
                    onDismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .accessibilityLabel("Desativar filtro de salas livres")
            }

            if freeRooms.isEmpty {
                let emptyMessage: String = {
                    if summary.outOfTermCount > 0 && summary.occupiedCount == 0 {
                        return "Fora do período letivo / feriado"
                    }
                    if summary.occupiedCount > 0 {
                        return "Todas as salas mapeadas deste andar têm aula agora."
                    }
                    return "Sem grade cadastrada para este dia/prédio"
                }()
                Text(emptyMessage)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(freeRooms) { room in
                            Button {
                                onSelectRoom(room.id)
                            } label: {
                                HStack(spacing: 4) {
                                    Circle().fill(freeColor).frame(width: 6, height: 6)
                                    Text(room.primaryLabel)
                                        .font(CampusTheme.font(size: 12, weight: .bold))
                                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 7)
                                .background(
                                    Capsule().fill(freeColor.opacity(colorScheme == .dark ? 0.22 : 0.14))
                                )
                            }
                            .buttonStyle(.plain)
                            .minimumTouchTarget(38)
                            .campusHoverLift()
                        }
                    }
                }
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: CampusTheme.cardCornerRadius, tint: accentColor, interactive: false, highContrast: highContrast)
    }
}
