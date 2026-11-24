import SwiftUI
import UniformTypeIdentifiers

// MARK: - Schedule Share & Import Sheet (.ufopgrade)

public struct ScheduleShareImportSheetView: View {
    @Bindable public var store: CampusStore
    public let onClose: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    @State private var exportedJson: String = ""
    @State private var importJson: String = ""
    @State private var statusBanner: String?
    @State private var showFileImporter: Bool = false
    @State private var showFileExporter: Bool = false

    public init(store: CampusStore, onClose: @escaping () -> Void) {
        self.store = store
        self.onClose = onClose
    }

    public var body: some View {
        let accent = appearance?.accentColor ?? .green
        let hc = appearance?.accessibility.highContrastMode ?? false
        let primary = accent.primary(for: colorScheme)

        ZStack {
            LiquidGlassBackdrop(accent: accent, highContrast: hc)

            VStack(spacing: 0) {
                HStack {
                    Text("Compartilhar Grade")
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                    Spacer()
                    Button {
                        onClose()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .bold))
                            .frame(width: 38, height: 38)
                            .liquidGlassCapsule(selected: false, accent: primary, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 10)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 14) {
                        if let statusBanner {
                            Text(statusBanner)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(CampusTheme.accessibleTextColor(primary, scheme: colorScheme))
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .liquidGlassCard(cornerRadius: 12, tint: primary, highContrast: hc)
                        }

                        VStack(alignment: .leading, spacing: 10) {
                            Text("Exportar grade (\(store.selectedSemesterFilter))")
                                .font(.system(size: 15, weight: .bold, design: .rounded))

                            HStack(spacing: 8) {
                                Button("Gerar & Copiar JSON") {
                                    exportedJson = store.exportSchedulePayload(semester: store.selectedSemesterFilter)
                                    UIPasteboard.general.string = exportedJson
                                    statusBanner = "Grade copiada para a área de transferência!"
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(primary)

                                Button("Salvar .ufopgrade") {
                                    exportedJson = store.exportSchedulePayload(semester: store.selectedSemesterFilter)
                                    showFileExporter = true
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                        .padding(14)
                        .liquidGlassCard(cornerRadius: 18, tint: primary, highContrast: hc)

                        VStack(alignment: .leading, spacing: 10) {
                            Text("Importar grade de colega")
                                .font(.system(size: 15, weight: .bold, design: .rounded))

                            TextEditor(text: $importJson)
                                .font(.system(size: 12, design: .monospaced))
                                .scrollContentBackground(.hidden)
                                .frame(minHeight: 110)
                                .padding(10)
                                .background(RoundedRectangle(cornerRadius: 10).fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: hc)))

                            HStack(spacing: 8) {
                                Button("Importar JSON") {
                                    let count = store.importSchedulePayload(json: importJson)
                                    statusBanner = count > 0
                                        ? "\(count) disciplina(s) importada(s)!"
                                        : "Conteúdo inválido ou vazio."
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(primary)

                                Button("Abrir arquivo") {
                                    showFileImporter = true
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                        .padding(14)
                        .liquidGlassCard(cornerRadius: 18, tint: primary, highContrast: hc)
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 28)
                }
            }
        }
        .fileExporter(
            isPresented: $showFileExporter,
            document: UfopTextDocument(text: exportedJson),
            contentType: .plainText,
            defaultFilename: "grade-ufop-\(store.selectedSemesterFilter).ufopgrade"
        ) { result in
            if case .success = result {
                statusBanner = "Arquivo .ufopgrade exportado!"
            }
        }
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.data, .plainText, .json],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result, let url = urls.first {
                let accessed = url.startAccessingSecurityScopedResource()
                defer { if accessed { url.stopAccessingSecurityScopedResource() } }
                if let txt = try? String(contentsOf: url, encoding: .utf8) {
                    importJson = txt
                    let count = store.importSchedulePayload(json: txt)
                    statusBanner = count > 0 ? "\(count) disciplina(s) importada(s) do arquivo!" : "Arquivo inválido."
                }
            }
        }
    }
}
