import SwiftUI
import UniformTypeIdentifiers

// MARK: - Schedule Share & Import Sheet (.ufopgrade / QR UFOPG1)

public struct ScheduleShareImportSheetView: View {
    @Bindable public var store: CampusStore
    public let onClose: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    @State private var exportedJson: String = ""
    @State private var importJson: String = ""
    @State private var statusBanner: String?
    @State private var errorBanner: String?
    @State private var showFileImporter: Bool = false
    @State private var showFileExporter: Bool = false
    @State private var qrSheetMode: GradeQrMode?
    @State private var pendingIncomingPreview: IncomingGradePreview?

    public init(store: CampusStore, onClose: @escaping () -> Void) {
        self.store = store
        self.onClose = onClose
    }

    private var studentDisplayName: String {
        let trimmed = store.studentProfile.fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "Estudante UFOP" : trimmed
    }

    private var suggestedFilename: String {
        UfopGradeCodec.suggestedFilename(forStudentName: studentDisplayName)
    }

    private var currentBundleJson: String {
        store.exportMyGradeBundleJson(semester: store.selectedSemesterFilter)
    }

    public var body: some View {
        let accent = appearance?.accentColor ?? .green
        let hc = appearance?.accessibility.highContrastMode ?? false
        let primary = accent.primary(for: colorScheme)
        let onPrimary = accent.onPrimary(for: colorScheme)
        let minTouch = max(48, appearance?.accessibility.minTouchHeight ?? 48)
        let bundleJson = currentBundleJson

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
                    .minimumTouchTarget(minTouch)
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

                        if let errorBanner {
                            Text(errorBanner)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(Color(hex: 0xDC2626))
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .liquidGlassCard(cornerRadius: 12, tint: Color(hex: 0xDC2626), highContrast: hc)
                        }

                        VStack(alignment: .leading, spacing: 12) {
                            Text("Exportar grade (\(store.selectedSemesterFilter))")
                                .font(.system(size: 15, weight: .bold, design: .rounded))

                            Text("Arquivo \(suggestedFilename) compatível com iOS e Android (.ufopgrade / QR Code).")
                                .font(.system(size: 12))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                            HStack(spacing: 8) {
                                ShareLink(
                                    item: UfopGradeTransferable(
                                        jsonPayload: bundleJson,
                                        suggestedFilename: suggestedFilename,
                                        displayTitle: "Grade de \(studentDisplayName) · \(store.selectedSemesterFilter)"
                                    ),
                                    preview: SharePreview(
                                        suggestedFilename,
                                        icon: Image(systemName: "calendar.badge.plus")
                                    )
                                ) {
                                    Label("Enviar .ufopgrade", systemImage: "square.and.arrow.up")
                                        .font(.system(size: 13, weight: .bold, design: .rounded))
                                        .foregroundStyle(onPrimary)
                                        .frame(maxWidth: .infinity, minHeight: 42)
                                        .liquidGlassCapsule(selected: true, accent: primary, interactive: true, highContrast: hc)
                                }
                                .buttonStyle(.plain)
                                .minimumTouchTarget(minTouch)

                                Button {
                                    qrSheetMode = .myQr
                                } label: {
                                    Label("QR Code", systemImage: "qrcode")
                                        .font(.system(size: 13, weight: .bold, design: .rounded))
                                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                        .frame(maxWidth: .infinity, minHeight: 42)
                                        .liquidGlassCapsule(selected: false, accent: primary, interactive: true, highContrast: hc)
                                }
                                .buttonStyle(.plain)
                                .minimumTouchTarget(minTouch)
                            }

                            HStack(spacing: 8) {
                                Button("Salvar arquivo") {
                                    exportedJson = bundleJson
                                    showFileExporter = true
                                }
                                .buttonStyle(.bordered)
                                .frame(minHeight: 40)

                                Button("Copiar JSON") {
                                    exportedJson = bundleJson
                                    UIPasteboard.general.string = exportedJson
                                    errorBanner = nil
                                    statusBanner = "Grade (.ufopgrade) copiada para a área de transferência!"
                                }
                                .buttonStyle(.bordered)
                                .frame(minHeight: 40)
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
                                Button {
                                    handlePreviewFromInput(importJson, sourceLabel: "Texto colado")
                                } label: {
                                    Label("Importar amigo", systemImage: "person.badge.plus")
                                        .font(.system(size: 13, weight: .bold, design: .rounded))
                                        .foregroundStyle(onPrimary)
                                        .frame(maxWidth: .infinity, minHeight: 42)
                                        .liquidGlassCapsule(selected: true, accent: primary, interactive: true, highContrast: hc)
                                }
                                .buttonStyle(.plain)
                                .minimumTouchTarget(minTouch)

                                Button {
                                    showFileImporter = true
                                } label: {
                                    Label("Abrir arquivo", systemImage: "folder")
                                        .font(.system(size: 13, weight: .bold, design: .rounded))
                                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                        .frame(maxWidth: .infinity, minHeight: 42)
                                        .liquidGlassCapsule(selected: false, accent: primary, interactive: true, highContrast: hc)
                                }
                                .buttonStyle(.plain)
                                .minimumTouchTarget(minTouch)
                            }

                            HStack(spacing: 8) {
                                Button("Ler QR Code") {
                                    qrSheetMode = .scanQr
                                }
                                .buttonStyle(.bordered)

                                Button("Mesclar na minha grade") {
                                    let count = store.importSchedulePayload(json: importJson)
                                    if count > 0 {
                                        errorBanner = nil
                                        statusBanner = "\(count) disciplina(s) importada(s) na sua grade!"
                                    } else {
                                        statusBanner = nil
                                        errorBanner = "Conteúdo inválido ou vazio."
                                    }
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
            contentType: .ufopGrade,
            defaultFilename: suggestedFilename
        ) { result in
            if case .success = result {
                errorBanner = nil
                statusBanner = "Arquivo \(suggestedFilename) exportado!"
            }
        }
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.ufopGrade, .json, .plainText, .data],
            allowsMultipleSelection: false
        ) { result in
            handleFileImport(result)
        }
        .sheet(item: $qrSheetMode) { mode in
            GradeQrCodeSheetView(
                myBundleJson: bundleJson,
                studentDisplayName: studentDisplayName,
                semesterLabel: store.selectedSemesterFilter,
                initialMode: mode,
                onScannedPreview: { preview in
                    qrSheetMode = nil
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                        pendingIncomingPreview = preview
                    }
                },
                onClose: {
                    qrSheetMode = nil
                }
            )
        }
        .sheet(item: $pendingIncomingPreview) { preview in
            IncomingGradeConfirmSheetView(
                preview: preview,
                onConfirm: {
                    let imported = store.importFriendSchedule(from: preview)
                    store.selectedFriendScheduleId = imported.id
                    pendingIncomingPreview = nil
                    errorBanner = nil
                    statusBanner = "Grade de \(imported.friendName) salva! Acesse Disciplinas → Grade para visualizar."
                },
                onCancel: {
                    pendingIncomingPreview = nil
                }
            )
        }
    }

    private func handlePreviewFromInput(_ rawText: String, sourceLabel: String) {
        do {
            let preview = try UfopGradeCodec.parseAndValidatePreview(rawText, sourceLabel: sourceLabel)
            errorBanner = nil
            pendingIncomingPreview = preview
        } catch {
            statusBanner = nil
            errorBanner = error.localizedDescription
        }
    }

    private func handleFileImport(_ result: Result<[URL], Error>) {
        switch result {
        case .failure(let error):
            statusBanner = nil
            errorBanner = error.localizedDescription
        case .success(let urls):
            guard let url = urls.first else { return }
            do {
                let rawText = try UfopGradeCodec.readFileSafely(from: url)
                importJson = rawText
                let label = url.lastPathComponent.isEmpty ? "Arquivo .ufopgrade" : url.lastPathComponent
                handlePreviewFromInput(rawText, sourceLabel: label)
            } catch {
                statusBanner = nil
                errorBanner = error.localizedDescription
            }
        }
    }
}
