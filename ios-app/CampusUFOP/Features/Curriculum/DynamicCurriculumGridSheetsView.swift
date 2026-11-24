import SwiftUI

internal struct DynamicMatrixPickerSheet: View {
    let availableMatrices: [DynamicCurriculumMatrix]
    let selectedMatrixId: String
    let autoMatchedMatrixId: String?
    let primaryAccent: Color
    let onPrimaryAccent: Color
    let onSelect: (DynamicCurriculumMatrix) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?

    @State private var searchQuery: String = ""

    internal init(
        availableMatrices: [DynamicCurriculumMatrix],
        selectedMatrixId: String,
        autoMatchedMatrixId: String?,
        primaryAccent: Color,
        onPrimaryAccent: Color,
        onSelect: @escaping (DynamicCurriculumMatrix) -> Void
    ) {
        self.availableMatrices = availableMatrices
        self.selectedMatrixId = selectedMatrixId
        self.autoMatchedMatrixId = autoMatchedMatrixId
        self.primaryAccent = primaryAccent
        self.onPrimaryAccent = onPrimaryAccent
        self.onSelect = onSelect
    }

    private var hc: Bool { appearance?.accessibility.highContrastMode ?? false }
    private var minTouch: CGFloat { appearance?.accessibility.minTouchHeight ?? 48 }

    private var filteredMatrices: [DynamicCurriculumMatrix] {
        DynamicCurriculumCatalog.searchMatrices(availableMatrices, query: searchQuery)
    }

    private var versionCountByCode: [String: Int] {
        var counts: [String: Int] = [:]
        for item in availableMatrices where !item.courseCode.isEmpty {
            counts[item.courseCode.uppercased(), default: 0] += 1
        }
        return counts
    }

    var body: some View {
        NavigationStack {
            List {
                if filteredMatrices.isEmpty {
                    ContentUnavailableView(
                        "Nenhum curso encontrado",
                        systemImage: "magnifyingglass",
                        description: Text("Tente buscar pelo nome, código ou campus do curso.")
                    )
                } else {
                    ForEach(filteredMatrices) { item in
                        let isSelected = item.id == selectedMatrixId
                        let isStudentCourse = item.id == autoMatchedMatrixId
                        let showValidity = (versionCountByCode[item.courseCode.uppercased()] ?? 0) > 1
                        let titleText = item.courseCode.isEmpty ? item.courseName : "\(item.courseCode) · \(item.courseName)"
                        let metaParts = [
                            item.campus.isEmpty ? nil : item.campus,
                            item.curriculumLabel,
                            showValidity ? "Vigência \(item.validityLabel())" : nil
                        ].compactMap { $0 }.joined(separator: " · ")

                        Button {
                            onSelect(item)
                        } label: {
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack(spacing: 6) {
                                        Text(titleText)
                                            .font(.system(size: 14.5, weight: .bold, design: .rounded))
                                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                            .lineLimit(2)

                                        if isStudentCourse {
                                            Text("Seu curso")
                                                .font(.system(size: 10, weight: .bold, design: .rounded))
                                                .foregroundStyle(onPrimaryAccent)
                                                .padding(.horizontal, 7)
                                                .padding(.vertical, 2)
                                                .background(Capsule(style: .continuous).fill(primaryAccent))
                                        }
                                    }

                                    Text(metaParts)
                                        .font(.system(size: 12, weight: .medium))
                                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                        .lineLimit(2)

                                    Text("\(item.courses.count) disciplinas · \(item.totalGridHours)h na grade · \(item.totalCourseHours)h totais")
                                        .font(.system(size: 11, weight: .semibold, design: .rounded))
                                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                                }

                                Spacer(minLength: 8)

                                if isSelected {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.system(size: 18, weight: .bold))
                                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                                }
                            }
                            .padding(.vertical, 4)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(minTouch)
                    }
                }
            }
            .navigationTitle("Cursos (\(availableMatrices.count))")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(
                text: $searchQuery,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "Buscar curso, código ou campus..."
            )
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fechar") {
                        dismiss()
                    }
                }
            }
        }
    }
}
