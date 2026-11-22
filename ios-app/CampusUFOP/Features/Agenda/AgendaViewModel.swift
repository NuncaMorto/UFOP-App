import Foundation
import Observation

@Observable
public final class AgendaViewModel {
    public let store: CampusStore
    public var activeCheckInDetail: ClassCheckInDetail?
    public var isAddCommitmentPresented: Bool = false

    public init(store: CampusStore) {
        self.store = store
    }

    public var overview: AgendaDayOverview {
        store.currentAgendaOverview
    }

    public var activeCampus: UfopCampusInfo {
        store.activeCampus
    }

    public var campuses: [UfopCampusInfo] {
        store.campuses
    }

    public func selectDay(_ chip: AgendaDayChip) {
        store.selectAgendaDate(chip.dateIso)
    }

    public func openCheckInSheet(for item: AgendaTimelineItem) {
        guard item.isClassCheckInEnabled,
              let detail = store.getClassCheckInDetail(agendaItemId: item.id) else {
            return
        }
        activeCheckInDetail = detail
    }

    public func saveCheckIn(
        agendaItemId: String,
        status: AttendanceStatus,
        colorKey: DepartmentColorKey
    ) {
        store.saveClassCheckIn(
            agendaItemId: agendaItemId,
            status: status,
            colorKey: colorKey
        )
        activeCheckInDetail = nil
    }

    public func dismissCheckInSheet() {
        activeCheckInDetail = nil
    }

    public func openAddCommitmentSheet() {
        isAddCommitmentPresented = true
    }

    public func dismissAddCommitmentSheet() {
        isAddCommitmentPresented = false
    }

    public func addCommitment(
        title: String,
        description: String,
        startTime: String,
        endTime: String,
        location: String,
        weatherTempC: Int? = nil
    ) {
        _ = store.createCommitment(
            title: title,
            description: description,
            isoDate: store.selectedDateIso,
            startTime: startTime,
            endTime: endTime,
            location: location,
            weatherTempC: weatherTempC
        )
        isAddCommitmentPresented = false
    }

    public func deleteCommitment(id: String) {
        store.deleteCommitment(id: id)
    }

    public func setActiveCampus(_ campusId: String) {
        _ = store.setActiveCampus(campusId: campusId)
    }
}
