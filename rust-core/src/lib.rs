pub mod db;
pub mod domain;
pub mod ffi;
pub mod models;
pub mod repository;

#[cfg(test)]
mod tests;

pub use ffi::{
    CampusEngine, CampusError, CampusMapEngine, calculate_max_absences, validate_overlay,
};
pub use models::{
    AbsenceHistoryEntry, AcademicCalendarEvent, AcademicProfileSummary, AgendaDayOverview,
    AgendaTimelineItem, AssessmentItem, AssessmentWithCourse, AttendanceStatus, CameraFocus,
    CampusMapSummary, CampusSearchResults, ClassCheckInDetail, CourseScheduleSlotItem, DayPillItem,
    DepartmentColorOption, DisciplineDetail, DisciplineSummaryCard, FeatureInfo, FloorLabel,
    IndoorStatus, MapHit, MapLayer, OccupancyStatus, OverlayValidationReport, PoiType, Rect2D,
    RenderBatch, RenderFeature, RenderGeomKind, RenderOccupancyKind, Resolution, RoomOccupancyItem,
    SearchResult, SlotInfo, TimelineItemKind, TimetableCell, TimetableDayCell, TimetableRow,
    UfopCampusInfo, UfopCurriculumTemplate, WeeklyTimetable,
};

uniffi::setup_scaffolding!();
