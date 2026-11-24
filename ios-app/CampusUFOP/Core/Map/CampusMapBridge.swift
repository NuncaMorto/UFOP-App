import Foundation
import Observation

#if canImport(CampusCoreFFI)
import CampusCoreFFI
#endif

// MARK: - Bridge @Observable com offload FFI, cache derivado e debounce de busca

@MainActor
@Observable
public final class CampusMapBridge {
    public private(set) var campuses: [CampusMapSummaryModel] = []
    public private(set) var activeCampusId: String = "OP_MC"
    public private(set) var activeFloor: Int32 = 0
    public private(set) var timestampIso: String? = nil
    public private(set) var renderBatch: CampusRenderBatch? = nil
    public private(set) var selectedFeature: CampusFeatureInfo? = nil
    public private(set) var selectedOccupancy: CampusOccupancyStatus? = nil
    public private(set) var selectedBuildingOccupancy: [CampusRoomOccupancyItem] = []
    public private(set) var pendingFocus: CampusCameraFocus? = nil
    public private(set) var searchQuery: String = ""
    public private(set) var activePoiFilter: CampusPoiType? = nil
    public private(set) var searchResults: [CampusSearchResult] = []
    public private(set) var isFreeRoomsModeEnabled: Bool = false
    public private(set) var resolutionNotice: String? = nil
    public private(set) var isNativeAvailable: Bool = false
    public private(set) var isLoading: Bool = false
    public private(set) var renderBatchRevision: Int = 0
    public private(set) var geometryRevision: Int = 0
    public private(set) var errorMessage: String? = nil

    public private(set) var freeRoomsOnActiveFloor: [CampusRenderFeature] = []
    public private(set) var floorOccupancySummary: CampusFloorOccupancySummary = CampusFloorOccupancySummary()

    public var onStateDidChange: (() -> Void)? = nil

    private var cachedAvailableFloors: [CampusFloorLabel] = []
    private let adapter = CampusMapNativeAdapter()
    private var loadTask: Task<Void, Never>? = nil
    private var searchDebounceTask: Task<Void, Never>? = nil
    private var activeActionTask: Task<Void, Never>? = nil
    private var mutationGeneration: UInt64 = 0

    public init(timestampIso: String? = nil, initialCampusId: String? = nil) {
        self.timestampIso = timestampIso
        loadBundledEngine(preferredCampusId: initialCampusId)
    }

    public func effectiveTimestampIso() -> String {
        timestampIso ?? Self.currentIsoTimestamp()
    }

    public func loadBundledEngine(preferredCampusId: String? = nil) {
        loadTask?.cancel()
        isLoading = true
        let effectiveTs = effectiveTimestampIso()
        let nativeAdapter = adapter
        notifyStateDidChange()

        loadTask = Task { [weak self] in
            let outcome = await Task.detached(priority: .userInitiated) {
                Result<CampusMapInitialLoadResult?, Error> {
                    try nativeAdapter.loadBundled(
                        preferredCampusId: preferredCampusId,
                        timestampIso: effectiveTs
                    )
                }
            }.value

            guard let self, !Task.isCancelled else { return }
            switch outcome {
            case .success(let loaded?):
                self.isNativeAvailable = true
                self.campuses = loaded.snapshot.campuses
                self.activeCampusId = loaded.snapshot.activeCampusId
                self.activeFloor = loaded.snapshot.defaultFloor
                self.errorMessage = nil
                self.applyRenderBatch(loaded.initialBatch, parentFloorLabels: nil)
            case .success(nil):
                self.isNativeAvailable = false
                self.campuses = []
                self.applyRenderBatch(nil, parentFloorLabels: nil)
                self.errorMessage = nil
            case .failure(let error):
                self.isNativeAvailable = false
                self.errorMessage = "Falha ao inicializar motor vetorial: \(error.localizedDescription)"
            }
            self.isLoading = false
            self.notifyStateDidChange()
        }
    }

    public func selectCampus(_ campusId: String) {
        searchDebounceTask?.cancel()
        activeActionTask?.cancel()
        mutationGeneration &+= 1
        let gen = mutationGeneration
        let nativeAdapter = adapter
        let currentQuery = searchQuery
        let currentFilter = activePoiFilter
        let effectiveTs = effectiveTimestampIso()
        let existingLoadTask = loadTask

        activeActionTask = Task { [weak self] in
            await existingLoadTask?.value
            guard let self, !Task.isCancelled, self.mutationGeneration == gen else { return }

            let outcome = await Task.detached(priority: .userInitiated) {
                Result<CampusSwitchOutcome?, Error> {
                    guard let summary = try nativeAdapter.setActiveCampus(campusId) else { return nil }
                    let batch = try nativeAdapter.getRenderBatch(
                        activeFloor: summary.defaultFloor,
                        timestampIso: effectiveTs
                    )
                    let trimmed = currentQuery.trimmingCharacters(in: .whitespacesAndNewlines)
                    let results: [CampusSearchResult] = (!trimmed.isEmpty || currentFilter != nil)
                        ? nativeAdapter.search(
                            query: trimmed,
                            poiTypeFilter: currentFilter,
                            activeFloor: summary.defaultFloor
                        )
                        : []
                    return CampusSwitchOutcome(summary: summary, batch: batch, searchResults: results)
                }
            }.value

            guard !Task.isCancelled, self.mutationGeneration == gen else { return }
            switch outcome {
            case .success(let payload?):
                self.activeCampusId = payload.summary.campusId
                self.activeFloor = payload.summary.defaultFloor
                self.selectedFeature = nil
                self.selectedOccupancy = nil
                self.selectedBuildingOccupancy = []
                self.resolutionNotice = nil
                self.searchResults = payload.searchResults
                self.applyRenderBatch(payload.batch, parentFloorLabels: nil)
                self.pendingFocus = CampusCameraFocus(
                    campusId: payload.summary.campusId,
                    featureId: payload.summary.campusId,
                    targetFloor: payload.summary.defaultFloor,
                    bounds: payload.summary.bounds
                )
                self.errorMessage = nil
            case .success(nil):
                break
            case .failure(let error):
                self.errorMessage = error.localizedDescription
            }
            self.notifyStateDidChange()
        }
    }

    public func selectFloor(_ floor: Int32) {
        guard floor != activeFloor else { return }
        activeFloor = floor
        activeActionTask?.cancel()
        mutationGeneration &+= 1
        let gen = mutationGeneration
        let nativeAdapter = adapter
        let currentQuery = searchQuery
        let currentFilter = activePoiFilter
        let currentSelected = selectedFeature
        let effectiveTs = effectiveTimestampIso()
        let existingLoadTask = loadTask
        notifyStateDidChange()

        activeActionTask = Task { [weak self] in
            await existingLoadTask?.value
            guard let self, !Task.isCancelled, self.mutationGeneration == gen else { return }

            let outcome = await Task.detached(priority: .userInitiated) {
                Result<(CampusRenderBatch?, [CampusSearchResult], [CampusFloorLabel]?), Error> {
                    let batch = try nativeAdapter.getRenderBatch(
                        activeFloor: floor,
                        timestampIso: effectiveTs
                    )
                    let trimmed = currentQuery.trimmingCharacters(in: .whitespacesAndNewlines)
                    let results: [CampusSearchResult] = (!trimmed.isEmpty || currentFilter != nil)
                        ? nativeAdapter.search(
                            query: trimmed,
                            poiTypeFilter: currentFilter,
                            activeFloor: floor
                        )
                        : []
                    let parentFloors = Self.resolveParentFloorLabels(
                        adapter: nativeAdapter,
                        selected: currentSelected
                    )
                    return (batch, results, parentFloors)
                }
            }.value

            guard !Task.isCancelled, self.mutationGeneration == gen else { return }
            switch outcome {
            case .success(let (batch, results, parentFloors)):
                if let batch {
                    self.applyRenderBatch(batch, parentFloorLabels: parentFloors)
                }
                self.searchResults = results
                self.errorMessage = nil
            case .failure(let error):
                self.errorMessage = error.localizedDescription
            }
            self.notifyStateDidChange()
        }
    }

    public func updateTimestamp(_ newTimestampIso: String?) {
        timestampIso = newTimestampIso
        activeActionTask?.cancel()
        mutationGeneration &+= 1
        let gen = mutationGeneration
        let nativeAdapter = adapter
        let floor = activeFloor
        let selected = selectedFeature
        let effectiveTs = effectiveTimestampIso()
        let existingLoadTask = loadTask

        activeActionTask = Task { [weak self] in
            await existingLoadTask?.value
            guard let self, !Task.isCancelled, self.mutationGeneration == gen else { return }

            let outcome = await Task.detached(priority: .userInitiated) {
                Result<(CampusRenderBatch?, CampusOccupancyStatus?, [CampusRoomOccupancyItem], [CampusFloorLabel]?), Error> {
                    let batch = try nativeAdapter.getRenderBatch(
                        activeFloor: floor,
                        timestampIso: effectiveTs
                    )
                    let (occ, buildingOcc) = Self.resolveOccupancy(
                        adapter: nativeAdapter,
                        feature: selected,
                        timestampIso: effectiveTs
                    )
                    let parentFloors = Self.resolveParentFloorLabels(
                        adapter: nativeAdapter,
                        selected: selected
                    )
                    return (batch, occ, buildingOcc, parentFloors)
                }
            }.value

            guard !Task.isCancelled, self.mutationGeneration == gen else { return }
            switch outcome {
            case .success(let (batch, occ, buildingOcc, parentFloors)):
                self.selectedOccupancy = occ
                self.selectedBuildingOccupancy = buildingOcc
                if let batch {
                    self.applyRenderBatch(batch, parentFloorLabels: parentFloors)
                }
                self.errorMessage = nil
            case .failure(let error):
                self.errorMessage = error.localizedDescription
            }
            self.notifyStateDidChange()
        }
    }

    public func refreshRenderBatch() {
        let nativeAdapter = adapter
        let floor = activeFloor
        let selected = selectedFeature
        let effectiveTs = effectiveTimestampIso()
        let existingLoadTask = loadTask

        Task { [weak self] in
            await existingLoadTask?.value
            guard let self, !Task.isCancelled else { return }

            let outcome = await Task.detached(priority: .userInitiated) {
                Result<(CampusRenderBatch?, [CampusFloorLabel]?), Error> {
                    let batch = try nativeAdapter.getRenderBatch(
                        activeFloor: floor,
                        timestampIso: effectiveTs
                    )
                    let parentFloors = Self.resolveParentFloorLabels(
                        adapter: nativeAdapter,
                        selected: selected
                    )
                    return (batch, parentFloors)
                }
            }.value

            guard !Task.isCancelled else { return }
            switch outcome {
            case .success(let (batch, parentFloors)):
                if let batch {
                    self.applyRenderBatch(batch, parentFloorLabels: parentFloors)
                }
                self.errorMessage = nil
            case .failure(let error):
                self.errorMessage = error.localizedDescription
            }
            self.notifyStateDidChange()
        }
    }

    public func onMapTap(mapX: Float, mapY: Float, zoom: Float) {
        resolutionNotice = nil
        mutationGeneration &+= 1
        let gen = mutationGeneration
        let nativeAdapter = adapter
        let floor = activeFloor
        let effectiveTs = effectiveTimestampIso()
        let existingLoadTask = loadTask

        Task { [weak self] in
            await existingLoadTask?.value
            guard let self, !Task.isCancelled, self.mutationGeneration == gen else { return }

            let outcome = await Task.detached(priority: .userInitiated) { () -> (CampusMapHit?, CampusOccupancyStatus?, [CampusRoomOccupancyItem], [CampusFloorLabel]?) in
                guard let hit = nativeAdapter.hitTest(
                    mapX: mapX,
                    mapY: mapY,
                    activeFloor: floor,
                    zoom: zoom
                ) else {
                    return (nil, nil, [], nil)
                }
                let (occ, buildingOcc) = Self.resolveOccupancy(
                    adapter: nativeAdapter,
                    feature: hit.feature,
                    timestampIso: effectiveTs
                )
                let parentFloors = Self.resolveParentFloorLabels(
                    adapter: nativeAdapter,
                    selected: hit.feature
                )
                return (hit, occ, buildingOcc, parentFloors)
            }.value

            guard !Task.isCancelled, self.mutationGeneration == gen else { return }
            let (hit, occ, buildingOcc, parentFloors) = outcome
            self.selectedFeature = hit?.feature
            self.selectedOccupancy = occ
            self.selectedBuildingOccupancy = buildingOcc
            self.rebuildDerivedCache(parentFloorLabels: parentFloors)
            self.notifyStateDidChange()
        }
    }

    @discardableResult
    public func handleTap(mapX: Float, mapY: Float, zoom: Float) -> CampusMapHit? {
        resolutionNotice = nil
        guard let hit = adapter.hitTest(
            mapX: mapX,
            mapY: mapY,
            activeFloor: activeFloor,
            zoom: zoom
        ) else {
            selectedFeature = nil
            selectedOccupancy = nil
            selectedBuildingOccupancy = []
            rebuildDerivedCache(parentFloorLabels: nil)
            notifyStateDidChange()
            return nil
        }
        selectedFeature = hit.feature
        let (occ, buildingOcc) = Self.resolveOccupancy(
            adapter: adapter,
            feature: hit.feature,
            timestampIso: effectiveTimestampIso()
        )
        selectedOccupancy = occ
        selectedBuildingOccupancy = buildingOcc
        let parentFloors = Self.resolveParentFloorLabels(adapter: adapter, selected: hit.feature)
        rebuildDerivedCache(parentFloorLabels: parentFloors)
        notifyStateDidChange()
        return hit
    }

    public func focusFeature(_ featureId: String) {
        activeActionTask?.cancel()
        mutationGeneration &+= 1
        let gen = mutationGeneration
        let nativeAdapter = adapter
        let currentCampus = activeCampusId
        let currentFloor = activeFloor
        let currentQuery = searchQuery
        let currentFilter = activePoiFilter
        let effectiveTs = effectiveTimestampIso()
        let existingLoadTask = loadTask

        activeActionTask = Task { [weak self] in
            await existingLoadTask?.value
            guard let self, !Task.isCancelled, self.mutationGeneration == gen else { return }

            let outcome = await Task.detached(priority: .userInitiated) {
                Result<CampusFocusOutcome?, Error> {
                    guard let focus = nativeAdapter.getCameraFocus(featureId: featureId) else {
                        return nil
                    }
                    let campusChanged = focus.campusId != currentCampus
                    let floorChanged = focus.targetFloor != currentFloor
                    if campusChanged {
                        _ = try? nativeAdapter.setActiveCampus(focus.campusId)
                    }
                    let batch = try nativeAdapter.getRenderBatch(
                        activeFloor: focus.targetFloor,
                        timestampIso: effectiveTs
                    )
                    let feat = nativeAdapter.getFeature(featureId: focus.featureId)
                    let (occ, buildingOcc) = Self.resolveOccupancy(
                        adapter: nativeAdapter,
                        feature: feat,
                        timestampIso: effectiveTs
                    )
                    let parentFloors = Self.resolveParentFloorLabels(
                        adapter: nativeAdapter,
                        selected: feat
                    )
                    let trimmed = currentQuery.trimmingCharacters(in: .whitespacesAndNewlines)
                    let results: [CampusSearchResult]? = ((campusChanged || floorChanged) && (!trimmed.isEmpty || currentFilter != nil))
                        ? nativeAdapter.search(
                            query: trimmed,
                            poiTypeFilter: currentFilter,
                            activeFloor: focus.targetFloor
                        )
                        : nil
                    return CampusFocusOutcome(
                        focus: focus,
                        batch: batch,
                        feature: feat,
                        occupancy: occ,
                        buildingOccupancy: buildingOcc,
                        parentFloorLabels: parentFloors,
                        refreshedSearchResults: results
                    )
                }
            }.value

            guard !Task.isCancelled, self.mutationGeneration == gen else { return }
            if case .success(let payload?) = outcome {
                self.resolutionNotice = nil
                self.activeCampusId = payload.focus.campusId
                self.activeFloor = payload.focus.targetFloor
                self.selectedFeature = payload.feature
                self.selectedOccupancy = payload.occupancy
                self.selectedBuildingOccupancy = payload.buildingOccupancy
                if let results = payload.refreshedSearchResults {
                    self.searchResults = results
                }
                self.applyRenderBatch(payload.batch, parentFloorLabels: payload.parentFloorLabels)
                self.pendingFocus = payload.focus
                self.notifyStateDidChange()
            }
        }
    }

    public func updateSearchQuery(_ query: String) {
        searchQuery = query
        searchDebounceTask?.cancel()

        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty || activePoiFilter != nil else {
            searchResults = []
            notifyStateDidChange()
            return
        }

        let nativeAdapter = adapter
        let filter = activePoiFilter
        let floor = activeFloor
        let existingLoadTask = loadTask
        notifyStateDidChange()

        searchDebounceTask = Task { [weak self] in
            do {
                try await Task.sleep(nanoseconds: 135_000_000)
            } catch {
                return
            }
            await existingLoadTask?.value
            guard let self, !Task.isCancelled, self.searchQuery == query else { return }

            let results = await Task.detached(priority: .userInitiated) {
                nativeAdapter.search(
                    query: trimmed,
                    poiTypeFilter: filter,
                    activeFloor: floor
                )
            }.value

            guard !Task.isCancelled, self.searchQuery == query else { return }
            self.searchResults = results
            self.notifyStateDidChange()
        }
    }

    public func togglePoiFilter(_ poiType: CampusPoiType) {
        searchDebounceTask?.cancel()
        if activePoiFilter == poiType {
            activePoiFilter = nil
        } else {
            activePoiFilter = poiType
        }

        let trimmed = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty || activePoiFilter != nil else {
            searchResults = []
            notifyStateDidChange()
            return
        }

        let nativeAdapter = adapter
        let filter = activePoiFilter
        let floor = activeFloor
        let existingLoadTask = loadTask
        notifyStateDidChange()

        searchDebounceTask = Task { [weak self] in
            await existingLoadTask?.value
            guard let self, !Task.isCancelled, self.activePoiFilter == filter else { return }
            let results = await Task.detached(priority: .userInitiated) {
                nativeAdapter.search(
                    query: trimmed,
                    poiTypeFilter: filter,
                    activeFloor: floor
                )
            }.value
            guard !Task.isCancelled, self.activePoiFilter == filter else { return }
            self.searchResults = results
            self.notifyStateDidChange()
        }
    }

    public func clearSearchAndFilters() {
        searchDebounceTask?.cancel()
        searchQuery = ""
        activePoiFilter = nil
        searchResults = []
        notifyStateDidChange()
    }

    public func cancelPendingBackgroundWork() {
        searchDebounceTask?.cancel()
    }

    public func selectSearchResult(_ result: CampusSearchResult) {
        searchDebounceTask?.cancel()
        resolutionNotice = nil
        searchQuery = ""
        searchResults = []
        let fallbackFocus = CampusCameraFocus(
            campusId: result.feature.campusId,
            featureId: result.feature.id,
            targetFloor: result.feature.floor ?? result.feature.defaultFloor ?? activeFloor,
            bounds: result.feature.bounds
        )
        let targetId = result.feature.id
        activeActionTask?.cancel()
        mutationGeneration &+= 1
        let gen = mutationGeneration
        let nativeAdapter = adapter
        let currentCampus = activeCampusId
        let effectiveTs = effectiveTimestampIso()
        let existingLoadTask = loadTask
        notifyStateDidChange()

        activeActionTask = Task { [weak self] in
            await existingLoadTask?.value
            guard let self, !Task.isCancelled, self.mutationGeneration == gen else { return }

            let outcome = await Task.detached(priority: .userInitiated) {
                Result<CampusFocusOutcome, Error> {
                    let focus = nativeAdapter.getCameraFocus(featureId: targetId) ?? fallbackFocus
                    if focus.campusId != currentCampus {
                        _ = try? nativeAdapter.setActiveCampus(focus.campusId)
                    }
                    let batch = try nativeAdapter.getRenderBatch(
                        activeFloor: focus.targetFloor,
                        timestampIso: effectiveTs
                    )
                    let feat = nativeAdapter.getFeature(featureId: focus.featureId) ?? result.feature
                    let (occ, buildingOcc) = Self.resolveOccupancy(
                        adapter: nativeAdapter,
                        feature: feat,
                        timestampIso: effectiveTs
                    )
                    let parentFloors = Self.resolveParentFloorLabels(
                        adapter: nativeAdapter,
                        selected: feat
                    )
                    return CampusFocusOutcome(
                        focus: focus,
                        batch: batch,
                        feature: feat,
                        occupancy: occ,
                        buildingOccupancy: buildingOcc,
                        parentFloorLabels: parentFloors,
                        refreshedSearchResults: nil
                    )
                }
            }.value

            guard !Task.isCancelled, self.mutationGeneration == gen else { return }
            if case .success(let payload) = outcome {
                self.activeCampusId = payload.focus.campusId
                self.activeFloor = payload.focus.targetFloor
                self.selectedFeature = payload.feature
                self.selectedOccupancy = payload.occupancy
                self.selectedBuildingOccupancy = payload.buildingOccupancy
                self.applyRenderBatch(payload.batch, parentFloorLabels: payload.parentFloorLabels)
                self.pendingFocus = payload.focus
                self.notifyStateDidChange()
            }
        }
    }

    public func toggleFreeRoomsMode() {
        setFreeRoomsMode(!isFreeRoomsModeEnabled)
    }

    public func setFreeRoomsMode(_ enabled: Bool) {
        isFreeRoomsModeEnabled = enabled
        notifyStateDidChange()
        if enabled && (timestampIso == nil || renderBatch == nil) {
            refreshRenderBatch()
        }
    }

    public func clearResolutionNotice() {
        resolutionNotice = nil
        notifyStateDidChange()
    }

    public func resolveMeetingAsync(
        courseCode: String,
        section: String,
        weekday: UInt32,
        startTime: String,
        fallbackBuildingRaw: String
    ) {
        activeActionTask?.cancel()
        mutationGeneration &+= 1
        let gen = mutationGeneration
        let nativeAdapter = adapter
        let currentCampus = activeCampusId
        let effectiveTs = effectiveTimestampIso()
        let existingLoadTask = loadTask

        activeActionTask = Task { [weak self] in
            await existingLoadTask?.value
            guard let self, !Task.isCancelled, self.mutationGeneration == gen else { return }

            let outcome = await Task.detached(priority: .userInitiated) { () -> (CampusMeetingResolution, CampusFocusOutcome?) in
                let res = nativeAdapter.resolveMeeting(
                    courseCode: courseCode,
                    section: section,
                    weekday: weekday,
                    startTime: startTime,
                    fallbackBuildingRaw: fallbackBuildingRaw
                )
                switch res {
                case .exactRoom(_, _, let roomId, _, let focus):
                    if focus.campusId != currentCampus {
                        _ = try? nativeAdapter.setActiveCampus(focus.campusId)
                    }
                    let batch = try? nativeAdapter.getRenderBatch(
                        activeFloor: focus.targetFloor,
                        timestampIso: effectiveTs
                    )
                    let feat = nativeAdapter.getFeature(featureId: roomId)
                    let (occ, buildingOcc) = Self.resolveOccupancy(
                        adapter: nativeAdapter,
                        feature: feat,
                        timestampIso: effectiveTs
                    )
                    let parentFloors = Self.resolveParentFloorLabels(
                        adapter: nativeAdapter,
                        selected: feat
                    )
                    return (
                        res,
                        CampusFocusOutcome(
                            focus: focus,
                            batch: batch,
                            feature: feat,
                            occupancy: occ,
                            buildingOccupancy: buildingOcc,
                            parentFloorLabels: parentFloors,
                            refreshedSearchResults: nil
                        )
                    )
                case .buildingFallback(_, let buildingId, let focus):
                    if focus.campusId != currentCampus {
                        _ = try? nativeAdapter.setActiveCampus(focus.campusId)
                    }
                    let batch = try? nativeAdapter.getRenderBatch(
                        activeFloor: focus.targetFloor,
                        timestampIso: effectiveTs
                    )
                    let feat = nativeAdapter.getFeature(featureId: buildingId)
                    let (occ, buildingOcc) = Self.resolveOccupancy(
                        adapter: nativeAdapter,
                        feature: feat,
                        timestampIso: effectiveTs
                    )
                    let parentFloors = Self.resolveParentFloorLabels(
                        adapter: nativeAdapter,
                        selected: feat
                    )
                    return (
                        res,
                        CampusFocusOutcome(
                            focus: focus,
                            batch: batch,
                            feature: feat,
                            occupancy: occ,
                            buildingOccupancy: buildingOcc,
                            parentFloorLabels: parentFloors,
                            refreshedSearchResults: nil
                        )
                    )
                case .unmapped:
                    return (res, nil)
                }
            }.value

            guard !Task.isCancelled, self.mutationGeneration == gen else { return }
            let (res, payload) = outcome
            switch res {
            case .exactRoom:
                self.resolutionNotice = nil
                if let payload {
                    self.activeCampusId = payload.focus.campusId
                    self.activeFloor = payload.focus.targetFloor
                    self.selectedFeature = payload.feature
                    self.selectedOccupancy = payload.occupancy
                    self.selectedBuildingOccupancy = payload.buildingOccupancy
                    self.applyRenderBatch(payload.batch, parentFloorLabels: payload.parentFloorLabels)
                    self.pendingFocus = payload.focus
                }
            case .buildingFallback:
                self.resolutionNotice = "Sala específica sem mapa interno — exibindo o prédio."
                if let payload {
                    self.activeCampusId = payload.focus.campusId
                    self.activeFloor = payload.focus.targetFloor
                    self.selectedFeature = payload.feature
                    self.selectedOccupancy = payload.occupancy
                    self.selectedBuildingOccupancy = payload.buildingOccupancy
                    self.applyRenderBatch(payload.batch, parentFloorLabels: payload.parentFloorLabels)
                    self.pendingFocus = payload.focus
                }
            case .unmapped:
                self.resolutionNotice = "Local não mapeado neste campus. Use a busca para encontrar o prédio."
            }
            self.notifyStateDidChange()
        }
    }

    @discardableResult
    public func resolveMeeting(
        courseCode: String,
        section: String,
        weekday: UInt32,
        startTime: String,
        fallbackBuildingRaw: String
    ) -> CampusMeetingResolution {
        let res = adapter.resolveMeeting(
            courseCode: courseCode,
            section: section,
            weekday: weekday,
            startTime: startTime,
            fallbackBuildingRaw: fallbackBuildingRaw
        )
        let effectiveTs = effectiveTimestampIso()
        switch res {
        case .exactRoom(_, _, let roomId, _, let focus):
            resolutionNotice = nil
            applyCameraFocusSync(focus, timestampIso: effectiveTs)
            let feat = adapter.getFeature(featureId: roomId)
            selectedFeature = feat
            let (occ, buildingOcc) = Self.resolveOccupancy(
                adapter: adapter,
                feature: feat,
                timestampIso: effectiveTs
            )
            selectedOccupancy = occ
            selectedBuildingOccupancy = buildingOcc
            let parentFloors = Self.resolveParentFloorLabels(adapter: adapter, selected: feat)
            rebuildDerivedCache(parentFloorLabels: parentFloors)
        case .buildingFallback(_, let buildingId, let focus):
            resolutionNotice = "Sala específica sem mapa interno — exibindo o prédio."
            applyCameraFocusSync(focus, timestampIso: effectiveTs)
            let feat = adapter.getFeature(featureId: buildingId)
            selectedFeature = feat
            let (occ, buildingOcc) = Self.resolveOccupancy(
                adapter: adapter,
                feature: feat,
                timestampIso: effectiveTs
            )
            selectedOccupancy = occ
            selectedBuildingOccupancy = buildingOcc
            let parentFloors = Self.resolveParentFloorLabels(adapter: adapter, selected: feat)
            rebuildDerivedCache(parentFloorLabels: parentFloors)
        case .unmapped:
            resolutionNotice = "Local não mapeado neste campus. Use a busca para encontrar o prédio."
        }
        notifyStateDidChange()
        return res
    }

    public func consumePendingFocus() {
        pendingFocus = nil
        notifyStateDidChange()
    }

    public func clearSelection() {
        selectedFeature = nil
        selectedOccupancy = nil
        selectedBuildingOccupancy = []
        rebuildDerivedCache(parentFloorLabels: nil)
        notifyStateDidChange()
    }

    public func availableFloorsForUi() -> [CampusFloorLabel] {
        cachedAvailableFloors
    }

    private func notifyStateDidChange() {
        onStateDidChange?()
    }

    private func applyCameraFocusSync(_ focus: CampusCameraFocus, timestampIso: String) {
        let campusChanged = focus.campusId != activeCampusId
        if campusChanged {
            _ = try? adapter.setActiveCampus(focus.campusId)
            activeCampusId = focus.campusId
        }
        activeFloor = focus.targetFloor
        let batch = try? adapter.getRenderBatch(activeFloor: activeFloor, timestampIso: timestampIso)
        if let batch {
            applyRenderBatch(batch, parentFloorLabels: nil)
        }
        pendingFocus = focus
    }

    private func applyRenderBatch(_ batch: CampusRenderBatch?, parentFloorLabels: [CampusFloorLabel]?) {
        if let batch, !Self.hasSameBatchGeometry(previous: renderBatch, next: batch) {
            geometryRevision &+= 1
        } else if batch == nil && renderBatch != nil {
            geometryRevision &+= 1
        }
        renderBatch = batch
        renderBatchRevision &+= 1
        rebuildDerivedCache(parentFloorLabels: parentFloorLabels)
    }

    private func rebuildDerivedCache(parentFloorLabels: [CampusFloorLabel]?) {
        if let batch = renderBatch {
            freeRoomsOnActiveFloor = batch.items
                .filter { $0.layer == .room && $0.occupancy == .noRegularClass }
                .sorted { lhs, rhs in
                    lhs.primaryLabel.localizedCaseInsensitiveCompare(rhs.primaryLabel) == .orderedAscending
                }

            var freeCount = 0
            var occupiedCount = 0
            var noDataCount = 0
            var outOfTermCount = 0
            for item in batch.items where item.layer == .room {
                switch item.occupancy {
                case .noRegularClass:
                    freeCount += 1
                case .occupied:
                    occupiedCount += 1
                case .noData:
                    noDataCount += 1
                case .outOfTerm:
                    outOfTermCount += 1
                case .none:
                    break
                }
            }
            floorOccupancySummary = CampusFloorOccupancySummary(
                freeCount: freeCount,
                occupiedCount: occupiedCount,
                noDataCount: noDataCount,
                outOfTermCount: outOfTermCount
            )
        } else {
            freeRoomsOnActiveFloor = []
            floorOccupancySummary = CampusFloorOccupancySummary()
        }

        if let feat = selectedFeature, !feat.floorLabels.isEmpty {
            cachedAvailableFloors = feat.floorLabels
        } else if let parentFloorLabels, !parentFloorLabels.isEmpty {
            cachedAvailableFloors = parentFloorLabels
        } else {
            cachedAvailableFloors = renderBatch?.floorLabels ?? []
        }
    }

    private static func hasSameBatchGeometry(
        previous: CampusRenderBatch?,
        next: CampusRenderBatch
    ) -> Bool {
        guard let previous else { return false }
        guard previous.campusId == next.campusId,
              previous.activeFloor == next.activeFloor,
              previous.coords.count == next.coords.count,
              previous.ringStarts.count == next.ringStarts.count,
              previous.ringCounts.count == next.ringCounts.count,
              previous.items.count == next.items.count else {
            return false
        }
        for idx in previous.items.indices {
            let a = previous.items[idx]
            let b = next.items[idx]
            if a.id != b.id ||
                a.layer != b.layer ||
                a.geomKind != b.geomKind ||
                a.ringStartIdx != b.ringStartIdx ||
                a.ringCount != b.ringCount ||
                a.isMock != b.isMock ||
                a.primaryLabel != b.primaryLabel ||
                a.secondaryLabel != b.secondaryLabel {
                return false
            }
        }
        return true
    }

    private nonisolated static func resolveParentFloorLabels(
        adapter: CampusMapNativeAdapter,
        selected: CampusFeatureInfo?
    ) -> [CampusFloorLabel]? {
        guard let selected, selected.floorLabels.isEmpty,
              let parentId = selected.buildingId ?? selected.parentId else {
            return nil
        }
        let parent = adapter.getFeature(featureId: parentId)
        guard let labels = parent?.floorLabels, !labels.isEmpty else { return nil }
        return labels
    }

    private nonisolated static func resolveOccupancy(
        adapter: CampusMapNativeAdapter,
        feature: CampusFeatureInfo?,
        timestampIso: String
    ) -> (CampusOccupancyStatus?, [CampusRoomOccupancyItem]) {
        guard let feature else { return (nil, []) }
        if feature.layer == .room {
            let occ = adapter.getRoomOccupancy(roomId: feature.id, timestampIso: timestampIso)
            return (occ, [])
        }
        if feature.layer == .building || feature.layer == .campusZone {
            let buildingOcc = (try? adapter.listBuildingOccupancy(
                buildingId: feature.id,
                timestampIso: timestampIso
            )) ?? []
            return (nil, buildingOcc)
        }
        return (nil, [])
    }

    private static func currentIsoTimestamp() -> String {
        let formatter = ISO8601DateFormatter()
        formatter.timeZone = TimeZone(identifier: "America/Sao_Paulo")
        return formatter.string(from: Date())
    }
}

// MARK: - Payloads Sendable e Adaptador FFI Thread-Safe

private struct CampusMapBundledSnapshot: Sendable {
    let campuses: [CampusMapSummaryModel]
    let activeCampusId: String
    let defaultFloor: Int32
}

private struct CampusMapInitialLoadResult: Sendable {
    let snapshot: CampusMapBundledSnapshot
    let initialBatch: CampusRenderBatch?
}

private struct CampusSwitchOutcome: Sendable {
    let summary: CampusMapSummaryModel
    let batch: CampusRenderBatch?
    let searchResults: [CampusSearchResult]
}

private struct CampusFocusOutcome: Sendable {
    let focus: CampusCameraFocus
    let batch: CampusRenderBatch?
    let feature: CampusFeatureInfo?
    let occupancy: CampusOccupancyStatus?
    let buildingOccupancy: [CampusRoomOccupancyItem]
    let parentFloorLabels: [CampusFloorLabel]?
    let refreshedSearchResults: [CampusSearchResult]?
}

private final class CampusMapNativeAdapter: @unchecked Sendable {
    private let lock = NSLock()

    #if canImport(CampusCoreFFI)
    private var nativeEngine: CampusMapEngine? = nil

    func loadBundled(
        preferredCampusId: String?,
        timestampIso: String
    ) throws -> CampusMapInitialLoadResult? {
        lock.lock()
        defer { lock.unlock() }

        let engine = try CampusMapEngine.newBundled()
        nativeEngine = engine
        var activeId = engine.getActiveCampusId()
        var activeFloorOverride: Int32? = nil

        if let preferredCampusId,
           !preferredCampusId.isEmpty,
           preferredCampusId != activeId,
           let switched = try? engine.setActiveCampus(campusId: preferredCampusId).toBridgeModel() {
            activeId = switched.campusId
            activeFloorOverride = switched.defaultFloor
        }

        let loadedCampuses = engine.listCampuses().map { $0.toBridgeModel() }
        let defaultFloor = activeFloorOverride
            ?? loadedCampuses.first { $0.campusId == activeId }?.defaultFloor
            ?? 0
        let batch = try engine.getRenderBatch(
            activeFloor: defaultFloor,
            timestampOpt: timestampIso
        ).toBridgeModel()

        return CampusMapInitialLoadResult(
            snapshot: CampusMapBundledSnapshot(
                campuses: loadedCampuses,
                activeCampusId: activeId,
                defaultFloor: defaultFloor
            ),
            initialBatch: batch
        )
    }

    func setActiveCampus(_ campusId: String) throws -> CampusMapSummaryModel? {
        lock.lock()
        defer { lock.unlock() }
        guard let engine = nativeEngine else { return nil }
        return try engine.setActiveCampus(campusId: campusId).toBridgeModel()
    }

    func getRenderBatch(activeFloor: Int32, timestampIso: String?) throws -> CampusRenderBatch? {
        lock.lock()
        defer { lock.unlock() }
        guard let engine = nativeEngine else { return nil }
        return try engine.getRenderBatch(
            activeFloor: activeFloor,
            timestampOpt: timestampIso
        ).toBridgeModel()
    }

    func hitTest(mapX: Float, mapY: Float, activeFloor: Int32, zoom: Float) -> CampusMapHit? {
        lock.lock()
        defer { lock.unlock() }
        guard let engine = nativeEngine else { return nil }
        return engine.hitTest(
            mapX: mapX,
            mapY: mapY,
            activeFloor: activeFloor,
            zoom: zoom
        )?.toBridgeModel()
    }

    func getCameraFocus(featureId: String) -> CampusCameraFocus? {
        lock.lock()
        defer { lock.unlock() }
        return nativeEngine?.getCameraFocus(featureId: featureId)?.toBridgeModel()
    }

    func getFeature(featureId: String) -> CampusFeatureInfo? {
        lock.lock()
        defer { lock.unlock() }
        return nativeEngine?.getFeature(featureId: featureId)?.toBridgeModel()
    }

    func search(query: String, poiTypeFilter: CampusPoiType?, activeFloor: Int32) -> [CampusSearchResult] {
        lock.lock()
        defer { lock.unlock() }
        guard let engine = nativeEngine else { return [] }
        return engine.search(
            query: query,
            poiTypeFilter: poiTypeFilter?.toFfi(),
            activeFloor: activeFloor
        ).map { $0.toBridgeModel() }
    }

    func resolveMeeting(
        courseCode: String,
        section: String,
        weekday: UInt32,
        startTime: String,
        fallbackBuildingRaw: String
    ) -> CampusMeetingResolution {
        lock.lock()
        defer { lock.unlock() }
        guard let engine = nativeEngine else { return .unmapped }
        return engine.resolveMeeting(
            courseCode: courseCode,
            section: section,
            weekday: weekday,
            startTime: startTime,
            fallbackBuildingRaw: fallbackBuildingRaw
        ).toBridgeModel()
    }

    func getRoomOccupancy(roomId: String, timestampIso: String) -> CampusOccupancyStatus? {
        lock.lock()
        defer { lock.unlock() }
        guard let engine = nativeEngine else { return nil }
        return try? engine.getRoomOccupancy(
            roomId: roomId,
            timestampEpochOrIso: timestampIso
        ).toBridgeModel()
    }

    func listBuildingOccupancy(buildingId: String, timestampIso: String) throws -> [CampusRoomOccupancyItem] {
        lock.lock()
        defer { lock.unlock() }
        guard let engine = nativeEngine else { return [] }
        return try engine.listBuildingOccupancy(
            buildingId: buildingId,
            timestampEpochOrIso: timestampIso
        ).map { $0.toBridgeModel() }
    }
    #else
    func loadBundled(
        preferredCampusId: String?,
        timestampIso: String
    ) throws -> CampusMapInitialLoadResult? {
        nil
    }
    func setActiveCampus(_ campusId: String) throws -> CampusMapSummaryModel? { nil }
    func getRenderBatch(activeFloor: Int32, timestampIso: String?) throws -> CampusRenderBatch? { nil }
    func hitTest(mapX: Float, mapY: Float, activeFloor: Int32, zoom: Float) -> CampusMapHit? { nil }
    func getCameraFocus(featureId: String) -> CampusCameraFocus? { nil }
    func getFeature(featureId: String) -> CampusFeatureInfo? { nil }
    func search(query: String, poiTypeFilter: CampusPoiType?, activeFloor: Int32) -> [CampusSearchResult] { [] }
    func resolveMeeting(
        courseCode: String,
        section: String,
        weekday: UInt32,
        startTime: String,
        fallbackBuildingRaw: String
    ) -> CampusMeetingResolution {
        .unmapped
    }
    func getRoomOccupancy(roomId: String, timestampIso: String) -> CampusOccupancyStatus? { nil }
    func listBuildingOccupancy(buildingId: String, timestampIso: String) throws -> [CampusRoomOccupancyItem] { [] }
    #endif
}
