import SwiftUI

// MARK: - Busca, Seletor de Campus e Filtros Rápidos do Mapa

internal struct CampusMapSearchAndFiltersView: View {
    @Bindable var bridge: CampusMapBridge
    let accentColor: Color
    let minTouch: CGFloat
    let highContrast: Bool
    @Binding var isResultsExpanded: Bool

    @Environment(\.colorScheme) private var colorScheme
    @FocusState private var isSearchFieldFocused: Bool

    private var hasActiveQueryOrFilter: Bool {
        !bridge.searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || bridge.activePoiFilter != nil
    }

    private var activeCampusDisplayName: String {
        if let found = bridge.campuses.first(where: { $0.campusId == bridge.activeCampusId }) {
            return found.shortDisplayName
        }
        switch bridge.activeCampusId {
        case "JM_ICEA": return "ICEA"
        case "MA_ICHS": return "ICHS"
        case "MA_ICSA": return "ICSA"
        default: return "Morro do Cruzeiro"
        }
    }

    private var campusOptions: [(id: String, title: String)] {
        if !bridge.campuses.isEmpty {
            return bridge.campuses.map { ($0.campusId, $0.shortDisplayName) }
        }
        return [
            ("OP_MC", "Morro do Cruzeiro"),
            ("JM_ICEA", "ICEA"),
            ("MA_ICHS", "ICHS"),
            ("MA_ICSA", "ICSA")
        ]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            searchAndCampusRow

            filterChipsRow

            if isResultsExpanded && hasActiveQueryOrFilter {
                searchResultsCard
            }
        }
    }

    // MARK: - Barra de Busca + Seletor de Campus

    private var searchAndCampusRow: some View {
        HStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))

                TextField(
                    "Buscar sala, prédio ou serviço...",
                    text: Binding(
                        get: { bridge.searchQuery },
                        set: { newText in
                            bridge.updateSearchQuery(newText)
                            isResultsExpanded = !newText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                || bridge.activePoiFilter != nil
                        }
                    )
                )
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .focused($isSearchFieldFocused)
                .onSubmit {
                    if !bridge.searchResults.isEmpty {
                        isResultsExpanded = true
                    }
                }

                if hasActiveQueryOrFilter {
                    Button {
                        bridge.clearSearchAndFilters()
                        isResultsExpanded = false
                        isSearchFieldFocused = false
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 16))
                            .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
                    }
                    .buttonStyle(.plain)
                    .frame(minWidth: 32, minHeight: 32)
                    .contentShape(Rectangle())
                    .accessibilityLabel("Limpar busca e filtros")
                }
            }
            .padding(.horizontal, 14)
            .frame(minHeight: max(46, minTouch))
            .liquidGlassCapsule(selected: false, accent: accentColor, interactive: false, highContrast: highContrast)

            Menu {
                ForEach(campusOptions, id: \.id) { campus in
                    Button {
                        bridge.selectCampus(campus.id)
                        isSearchFieldFocused = false
                    } label: {
                        if campus.id == bridge.activeCampusId {
                            Label(campus.title, systemImage: "checkmark")
                        } else {
                            Text(campus.title)
                        }
                    }
                }
            } label: {
                HStack(spacing: 5) {
                    Image(systemName: "building.columns.fill")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(accentColor)

                    Text(activeCampusDisplayName)
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                        .lineLimit(1)

                    Image(systemName: "chevron.down")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }
                .padding(.horizontal, 12)
                .frame(minHeight: max(46, minTouch))
                .liquidGlassCapsule(selected: false, accent: accentColor, interactive: true, highContrast: highContrast)
            }
            .campusHoverLift()
            .accessibilityLabel("Selecionar campus: \(activeCampusDisplayName)")
        }
    }

    // MARK: - Chips de POI + Salas Livres

    private var filterChipsRow: some View {
        let freeCount = bridge.floorOccupancySummary.freeCount

        return LiquidGlassGroup(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Button {
                        bridge.toggleFreeRoomsMode()
                    } label: {
                        let selected = bridge.isFreeRoomsModeEnabled
                        HStack(spacing: 5) {
                            Image(systemName: selected ? "checkmark.circle.fill" : "door.left.hand.open")
                                .font(.system(size: 12, weight: .bold))
                            Text(freeCount > 0 ? "Salas livres (\(freeCount))" : "Salas livres")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .lineLimit(1)
                        }
                        .foregroundStyle(
                            selected
                                ? accentColor
                                : CampusTheme.primaryText(for: colorScheme, highContrast: highContrast)
                        )
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .minimumTouchTarget(minTouch)
                        .liquidGlassCapsule(selected: selected, accent: accentColor, interactive: true, highContrast: highContrast)
                    }
                    .buttonStyle(.plain)
                    .campusHoverLift()
                    .accessibilityLabel("Alternar modo salas livres no andar atual")

                    ForEach(CampusPoiType.quickFilterCases) { poiType in
                        let selected = bridge.activePoiFilter == poiType
                        Button {
                            let willActivate = bridge.activePoiFilter != poiType
                            bridge.togglePoiFilter(poiType)
                            isSearchFieldFocused = false
                            isResultsExpanded = willActivate || !bridge.searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        } label: {
                            HStack(spacing: 5) {
                                Image(systemName: poiType.systemImage)
                                    .font(.system(size: 11, weight: .bold))
                                Text(poiType.title)
                                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                                    .lineLimit(1)
                            }
                            .foregroundStyle(
                                selected
                                    ? accentColor
                                    : CampusTheme.primaryText(for: colorScheme, highContrast: highContrast)
                            )
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .minimumTouchTarget(minTouch)
                            .liquidGlassCapsule(selected: selected, accent: accentColor, interactive: true, highContrast: highContrast)
                        }
                        .buttonStyle(.plain)
                        .campusHoverLift()
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    // MARK: - Resultados Clicáveis

    private var searchResultsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(
                    bridge.searchResults.isEmpty
                        ? "Nenhum resultado"
                        : "\(bridge.searchResults.count) resultado(s) no mapa"
                )
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

                Spacer()

                Button {
                    isResultsExpanded = false
                    isSearchFieldFocused = false
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
                }
                .buttonStyle(.plain)
                .frame(minWidth: 32, minHeight: 32)
                .contentShape(Rectangle())
                .accessibilityLabel("Recolher resultados")
            }

            if bridge.searchResults.isEmpty {
                Text("Tente buscar pelo código do prédio (ex.: ICEB, BSA, EM), número da sala ou categoria.")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            } else {
                ScrollView(.vertical, showsIndicators: true) {
                    LazyVStack(spacing: 6) {
                        ForEach(bridge.searchResults) { result in
                            Button {
                                bridge.selectSearchResult(result)
                                isResultsExpanded = false
                                isSearchFieldFocused = false
                            } label: {
                                searchResultRow(result)
                            }
                            .buttonStyle(.plain)
                            .campusHoverLift()
                        }
                    }
                }
                .frame(maxHeight: 230)
            }
        }
        .padding(12)
        .liquidGlassCard(cornerRadius: 18, tint: accentColor, interactive: false, highContrast: highContrast)
    }

    private func searchResultRow(_ result: CampusSearchResult) -> some View {
        let feat = result.feature
        let iconName: String = {
            if let poi = feat.poiType { return poi.systemImage }
            switch feat.layer {
            case .room: return "door.left.hand.closed"
            case .building: return "building.2.fill"
            case .campusZone: return "map.fill"
            case .parking: return "parkingsign.circle.fill"
            default: return "mappin.circle.fill"
            }
        }()

        let subtitleParts: [String] = {
            var parts: [String] = []
            if let floor = feat.floor {
                parts.append(floor == 0 ? "Térreo" : "\(floor + 1)º Pav")
            }
            if let bId = feat.buildingId, !bId.isEmpty, bId != feat.id {
                parts.append(bId)
            }
            if let cat = feat.category, !cat.isEmpty {
                parts.append(localizedCategory(cat))
            }
            return parts
        }()

        return HStack(spacing: 10) {
            Image(systemName: iconName)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(accentColor)
                .frame(width: 28, height: 28)
                .background(
                    Circle()
                        .fill(accentColor.opacity(colorScheme == .dark ? 0.22 : 0.12))
                )

            VStack(alignment: .leading, spacing: 2) {
                Text(feat.name)
                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    .lineLimit(1)

                if !subtitleParts.isEmpty {
                    Text(subtitleParts.joined(separator: " · "))
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 6)

            if feat.isMock {
                Text("Esquemático")
                    .font(.system(size: 10, weight: .semibold, design: .rounded))
                    .foregroundStyle(CampusTheme.weatherBadgeText(for: colorScheme))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(CampusTheme.weatherBadgeBackground(for: colorScheme)))
            }

            Image(systemName: "chevron.right")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .frame(minHeight: minTouch)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast).opacity(0.65))
        )
    }

    private func localizedCategory(_ raw: String) -> String {
        switch raw.lowercased() {
        case "classroom": return "Sala de aula"
        case "lab": return "Laboratório"
        case "library": return "Biblioteca"
        case "dining": return "Alimentação"
        case "toilet": return "Banheiro"
        case "faculty_office": return "Secretaria/Docentes"
        case "entrance": return "Acesso"
        case "parking": return "Estacionamento"
        case "academic", "academic_zone": return "Acadêmico"
        default: return raw.capitalized
        }
    }
}
