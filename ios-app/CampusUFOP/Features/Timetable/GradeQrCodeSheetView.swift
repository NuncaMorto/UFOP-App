import SwiftUI
import CoreImage
import CoreImage.CIFilterBuiltins
import AVFoundation
import UIKit

// MARK: - QR Code Generator & Camera Scanner Sheet (UFOPG1:)

public enum GradeQrMode: String, CaseIterable, Identifiable, Sendable {
    case myQr = "Meu QR Code"
    case scanQr = "Ler QR Code"

    public var id: String { rawValue }
}

public struct GradeQrCodeSheetView: View {
    public let myBundleJson: String
    public let studentDisplayName: String
    public let semesterLabel: String
    public let initialMode: GradeQrMode
    public let onScannedPreview: (IncomingGradePreview) -> Void
    public let onClose: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.openURL) private var openURL

    @State private var mode: GradeQrMode
    @State private var copiedFeedback: Bool = false
    @State private var cameraAuthStatus: AVAuthorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
    @State private var isCameraUnavailable: Bool = false
    @State private var scanErrorMessage: String?

    private static let sharedCiContext = CIContext()

    public init(
        myBundleJson: String,
        studentDisplayName: String,
        semesterLabel: String,
        initialMode: GradeQrMode = .myQr,
        onScannedPreview: @escaping (IncomingGradePreview) -> Void,
        onClose: @escaping () -> Void
    ) {
        self.myBundleJson = myBundleJson
        self.studentDisplayName = studentDisplayName
        self.semesterLabel = semesterLabel
        self.initialMode = initialMode
        self.onScannedPreview = onScannedPreview
        self.onClose = onClose
        self._mode = State(initialValue: initialMode)
    }

    public var body: some View {
        let accent = appearance?.accentColor ?? .green
        let primary = accent.primary(for: colorScheme)
        let hc = appearance?.accessibility.highContrastMode ?? false
        let minTouch = max(48, appearance?.accessibility.minTouchHeight ?? 48)

        ZStack {
            LiquidGlassBackdrop(accent: accent, highContrast: hc)

            VStack(spacing: 0) {
                topBar(primary: primary, hc: hc, minTouch: minTouch)

                Picker("Modo do QR Code", selection: $mode) {
                    ForEach(GradeQrMode.allCases) { item in
                        Text(item.rawValue).tag(item)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 20)
                .padding(.bottom, 12)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 16) {
                        if mode == .myQr {
                            myQrSection(primary: primary, hc: hc, minTouch: minTouch)
                        } else {
                            scannerSection(primary: primary, hc: hc, minTouch: minTouch)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 4)
                    .padding(.bottom, 28)
                }
            }
        }
        .preferredColorScheme(appearance?.resolvedColorScheme)
        .onAppear {
            refreshCameraStatusIfNeeded()
        }
        .onChange(of: mode) { _, newMode in
            if newMode == .scanQr {
                scanErrorMessage = nil
                refreshCameraStatusIfNeeded()
            }
        }
        .task(id: copiedFeedback) {
            guard copiedFeedback else { return }
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            copiedFeedback = false
        }
    }

    // MARK: - Top Bar

    private func topBar(primary: Color, hc: Bool, minTouch: CGFloat) -> some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("QR CODE DA GRADE")
                    .font(CampusTheme.font(size: 11, weight: .bold))
                    .tracking(0.9)
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

                Text(mode == .myQr ? "Compartilhar Offline" : "Escanear Colega")
                    .font(CampusTheme.font(size: 22, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
            }

            Spacer()

            Button {
                onClose()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    .frame(width: 38, height: 38)
                    .liquidGlassCapsule(selected: false, accent: primary, highContrast: hc)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
            .accessibilityLabel("Fechar QR Code")
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
        .padding(.bottom, 12)
    }

    // MARK: - My QR Code Section

    private func computeCompactQrState() -> (payload: String?, errorMessage: String?) {
        do {
            let encoded = try UfopGradeCodec.encodeCompactQr(from: myBundleJson)
            return (encoded, nil)
        } catch {
            return (nil, error.localizedDescription)
        }
    }

    @ViewBuilder
    private func myQrSection(primary: Color, hc: Bool, minTouch: CGFloat) -> some View {
        let state = computeCompactQrState()

        if let qrPayload = state.payload {
            if let qrImage = generateQrImage(from: qrPayload) {
                VStack(spacing: 16) {
                    VStack(spacing: 14) {
                        Image(uiImage: qrImage)
                            .interpolation(.none)
                            .resizable()
                            .scaledToFit()
                            .frame(maxWidth: 236, maxHeight: 236)
                            .accessibilityLabel("QR Code da grade horária de \(studentDisplayName)")
                    }
                    .padding(22)
                    .background(
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .fill(Color.white)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .strokeBorder(hc ? CampusTheme.cardBorder(for: colorScheme, highContrast: true) : .clear, lineWidth: hc ? 1.5 : 0)
                    )
                    .shadow(color: CampusTheme.cardShadow(for: colorScheme), radius: 12, x: 0, y: 6)

                    VStack(spacing: 6) {
                        Text(studentDisplayName)
                            .font(CampusTheme.font(size: 18, weight: .bold))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                        Text("Semestre \(semesterLabel)")
                            .font(CampusTheme.tabularFont(size: 13, weight: .semibold))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                        Text("100% offline · Android ↔ iOS")
                            .font(CampusTheme.font(size: 11, weight: .bold))
                            .foregroundStyle(CampusTheme.accessibleTextColor(primary, scheme: colorScheme))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 5)
                            .liquidGlassCapsule(selected: false, accent: primary, interactive: false, highContrast: hc)
                            .padding(.top, 2)
                    }

                    Button {
                        UIPasteboard.general.string = qrPayload
                        withAnimation(.snappy) {
                            copiedFeedback = true
                        }
                    } label: {
                        Label(
                            copiedFeedback ? "Código QR copiado!" : "Copiar código QR",
                            systemImage: copiedFeedback ? "checkmark.circle.fill" : "doc.on.doc"
                        )
                        .font(CampusTheme.font(size: 14, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                        .frame(maxWidth: .infinity, minHeight: minTouch)
                        .liquidGlassCapsule(selected: false, accent: primary, interactive: true, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                }
                .padding(18)
                .frame(maxWidth: .infinity)
                .liquidGlassCard(cornerRadius: 22, tint: primary, highContrast: hc)
            } else {
                errorFallbackCard(
                    title: "QR Code muito denso",
                    message: "Sua grade excedeu o limite visual do QR Code. Use o compartilhamento por arquivo .ufopgrade.",
                    hc: hc
                )
            }
        } else {
            errorFallbackCard(
                title: "Não foi possível gerar o QR Code",
                message: state.errorMessage ?? "Adicione disciplinas antes de gerar o QR Code.",
                hc: hc
            )
        }
    }

    // MARK: - Scanner Section

    @ViewBuilder
    private func scannerSection(primary: Color, hc: Bool, minTouch: CGFloat) -> some View {
        VStack(spacing: 14) {
            if cameraAuthStatus == .authorized && !isCameraUnavailable {
                ZStack {
                    QrScannerViewControllerRepresentable(
                        onDetectString: { rawCode in
                            handleScannedCode(rawCode)
                        },
                        onCameraUnavailable: {
                            isCameraUnavailable = true
                        }
                    )
                    .frame(height: 330)
                    .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))

                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.85), lineWidth: 2.5)
                        .frame(width: 210, height: 210)
                        .shadow(color: Color.black.opacity(0.35), radius: 6)
                }
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .strokeBorder(CampusTheme.cardBorder(for: colorScheme, highContrast: hc), lineWidth: 1)
                )

                Text("Aponte a câmera para o QR Code UFOPG1: de um colega.")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    .multilineTextAlignment(.center)
            } else if cameraAuthStatus == .denied || cameraAuthStatus == .restricted {
                VStack(spacing: 12) {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 32, weight: .light))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

                    Text("Permissão de câmera necessária")
                        .font(CampusTheme.font(size: 16, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                    Text("Autorize o acesso à câmera nos Ajustes para ler o QR Code da grade de seus colegas.")
                        .font(.system(size: 13))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        .multilineTextAlignment(.center)

                    Button {
                        if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
                            openURL(settingsUrl)
                        }
                    } label: {
                        Label("Abrir Ajustes", systemImage: "gear")
                            .font(CampusTheme.font(size: 14, weight: .bold))
                            .foregroundStyle(appearance?.accentColor.onPrimary(for: colorScheme) ?? .white)
                            .frame(maxWidth: .infinity, minHeight: minTouch)
                            .liquidGlassCapsule(selected: true, accent: primary, interactive: true, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                }
                .padding(20)
                .frame(maxWidth: .infinity)
                .liquidGlassCard(cornerRadius: 22, tint: primary, highContrast: hc)
            } else if cameraAuthStatus == .notDetermined {
                VStack(spacing: 12) {
                    ProgressView()
                    Text("Solicitando acesso à câmera...")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                }
                .padding(28)
                .frame(maxWidth: .infinity)
                .liquidGlassCard(cornerRadius: 22, tint: primary, highContrast: hc)
            } else {
                VStack(spacing: 10) {
                    Image(systemName: "video.slash.fill")
                        .font(.system(size: 30, weight: .light))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

                    Text("Câmera indisponível neste aparelho")
                        .font(CampusTheme.font(size: 16, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                    Text("Você ainda pode colar um código UFOPG1: copiado da área de transferência abaixo.")
                        .font(.system(size: 13))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        .multilineTextAlignment(.center)
                }
                .padding(20)
                .frame(maxWidth: .infinity)
                .liquidGlassCard(cornerRadius: 22, tint: primary, highContrast: hc)
            }

            if let scanErrorMessage {
                Text(scanErrorMessage)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xDC2626))
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .liquidGlassCard(cornerRadius: 14, tint: Color(hex: 0xDC2626), highContrast: hc)
            }

            Button {
                pasteClipboardCode()
            } label: {
                Label("Colar código UFOPG1: copiado", systemImage: "doc.on.clipboard")
                    .font(CampusTheme.font(size: 13, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    .frame(maxWidth: .infinity, minHeight: minTouch)
                    .liquidGlassCapsule(selected: false, accent: primary, interactive: true, highContrast: hc)
            }
            .buttonStyle(.plain)
        }
    }

    private func errorFallbackCard(title: String, message: String, hc: Bool) -> some View {
        VStack(spacing: 8) {
            Image(systemName: "qrcode")
                .font(.system(size: 32, weight: .light))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

            Text(title)
                .font(CampusTheme.font(size: 16, weight: .bold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            Text(message)
                .font(.system(size: 13))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                .multilineTextAlignment(.center)
        }
        .padding(22)
        .frame(maxWidth: .infinity)
        .liquidGlassCard(cornerRadius: 22, highContrast: hc)
    }

    // MARK: - Helpers

    private func generateQrImage(from payload: String) -> UIImage? {
        guard !payload.isEmpty else { return nil }
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(payload.utf8)
        filter.correctionLevel = "M"
        guard let outputImage = filter.outputImage else { return nil }
        let transformed = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        guard let cgImage = Self.sharedCiContext.createCGImage(transformed, from: transformed.extent) else {
            return nil
        }
        let targetSize = CGSize(width: cgImage.width, height: cgImage.height)
        let renderer = UIGraphicsImageRenderer(size: targetSize)
        return renderer.image { context in
            context.cgContext.interpolationQuality = .none
            UIImage(cgImage: cgImage).draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }

    private func refreshCameraStatusIfNeeded() {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        cameraAuthStatus = status
        if mode == .scanQr && status == .notDetermined {
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    cameraAuthStatus = granted ? .authorized : .denied
                }
            }
        }
        if status == .authorized {
            let hasDevice = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) != nil
                || AVCaptureDevice.default(for: .video) != nil
            isCameraUnavailable = !hasDevice
        }
    }

    private func handleScannedCode(_ rawCode: String) {
        do {
            let preview = try UfopGradeCodec.parseAndValidatePreview(rawCode, sourceLabel: "QR Code")
            scanErrorMessage = nil
            onClose()
            onScannedPreview(preview)
        } catch {
            scanErrorMessage = "QR Code não reconhecido como grade do Vida Acadêmica."
        }
    }

    private func pasteClipboardCode() {
        guard let text = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines),
              !text.isEmpty else {
            scanErrorMessage = "Nenhum código encontrado na área de transferência."
            return
        }
        do {
            let preview = try UfopGradeCodec.parseAndValidatePreview(text, sourceLabel: "Código colado")
            scanErrorMessage = nil
            onClose()
            onScannedPreview(preview)
        } catch {
            scanErrorMessage = error.localizedDescription
        }
    }
}

// MARK: - AVCaptureSession QR Scanner UIViewControllerRepresentable

private struct QrScannerViewControllerRepresentable: UIViewControllerRepresentable {
    let onDetectString: (String) -> Void
    let onCameraUnavailable: () -> Void

    func makeUIViewController(context: Context) -> QrScannerViewController {
        let controller = QrScannerViewController()
        controller.onDetectString = onDetectString
        controller.onCameraUnavailable = onCameraUnavailable
        return controller
    }

    func updateUIViewController(_ uiViewController: QrScannerViewController, context: Context) {
        uiViewController.onDetectString = onDetectString
        uiViewController.onCameraUnavailable = onCameraUnavailable
    }

    static func dismantleUIViewController(_ uiViewController: QrScannerViewController, coordinator: ()) {
        uiViewController.stopCaptureSession()
    }
}

private final class QrScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onDetectString: ((String) -> Void)?
    var onCameraUnavailable: (() -> Void)?

    private let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "io.github.nuncamorto.vidaacademica.qrscanner")
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var isConfigured = false
    private var isVisible = false
    private var didConsumeValidCode = false
    private var lastRejectedTimestamp: Date = .distantPast

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.bounds
        view.layer.addSublayer(layer)
        previewLayer = layer

        configureCaptureSession()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        isVisible = true
        sessionQueue.async { [weak self] in
            guard let self, self.isConfigured, !self.session.isRunning else { return }
            self.session.startRunning()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        isVisible = false
        stopCaptureSession()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    func stopCaptureSession() {
        sessionQueue.async { [weak self] in
            guard let self, self.session.isRunning else { return }
            self.session.stopRunning()
        }
    }

    private func configureCaptureSession() {
        sessionQueue.async { [weak self] in
            guard let self, !self.isConfigured else { return }

            let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back)
                ?? AVCaptureDevice.default(for: .video)

            guard let videoDevice = device,
                  let input = try? AVCaptureDeviceInput(device: videoDevice) else {
                DispatchQueue.main.async {
                    self.onCameraUnavailable?()
                }
                return
            }

            let output = AVCaptureMetadataOutput()
            self.session.beginConfiguration()

            if self.session.canAddInput(input) {
                self.session.addInput(input)
            } else {
                self.session.commitConfiguration()
                DispatchQueue.main.async {
                    self.onCameraUnavailable?()
                }
                return
            }

            if self.session.canAddOutput(output) {
                self.session.addOutput(output)
                output.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
                if output.availableMetadataObjectTypes.contains(.qr) {
                    output.metadataObjectTypes = [.qr]
                }
            } else {
                self.session.commitConfiguration()
                DispatchQueue.main.async {
                    self.onCameraUnavailable?()
                }
                return
            }

            self.session.commitConfiguration()
            self.isConfigured = true

            DispatchQueue.main.async { [weak self] in
                guard let self, self.isVisible else { return }
                self.sessionQueue.async { [weak self] in
                    guard let self, !self.session.isRunning else { return }
                    self.session.startRunning()
                }
            }
        }
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard !didConsumeValidCode,
              let codeObject = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let rawValue = codeObject.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines),
              !rawValue.isEmpty else {
            return
        }

        if let _ = try? UfopGradeCodec.parseAndValidatePreview(rawValue, sourceLabel: "QR Code") {
            didConsumeValidCode = true
            stopCaptureSession()
            onDetectString?(rawValue)
        } else if Date().timeIntervalSince(lastRejectedTimestamp) >= 1.5 {
            lastRejectedTimestamp = Date()
            onDetectString?(rawValue)
        }
    }
}
