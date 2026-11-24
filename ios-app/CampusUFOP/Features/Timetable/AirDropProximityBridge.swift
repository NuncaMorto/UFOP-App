import SwiftUI
#if os(iOS)
import UIKit
import LinkPresentation
#endif

// MARK: - AirDrop Proximity / NameDrop Bridge for .ufopgrade Sharing

#if os(iOS)
public struct AirDropProximityBridge: UIViewControllerRepresentable {
    public let isActive: Bool
    public let jsonPayload: String
    public let filename: String
    public let shareTitle: String

    public init(
        isActive: Bool,
        jsonPayload: String,
        filename: String,
        shareTitle: String
    ) {
        self.isActive = isActive
        self.jsonPayload = jsonPayload
        self.filename = filename
        self.shareTitle = shareTitle
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    public func makeUIViewController(context: Context) -> AirDropBridgeViewController {
        let controller = AirDropBridgeViewController()
        controller.view.backgroundColor = .clear
        controller.view.isUserInteractionEnabled = false
        return controller
    }

    public func updateUIViewController(_ uiViewController: AirDropBridgeViewController, context: Context) {
        let coordinator = context.coordinator
        let trimmedPayload = jsonPayload.trimmingCharacters(in: .whitespacesAndNewlines)
        let shouldActivate = isActive && !trimmedPayload.isEmpty

        if coordinator.lastIsActive == shouldActivate
            && coordinator.lastPayload == trimmedPayload
            && coordinator.lastFilename == filename
            && coordinator.lastTitle == shareTitle {
            if shouldActivate, let config = coordinator.currentConfiguration {
                if uiViewController.bridgeConfiguration == nil {
                    uiViewController.bridgeConfiguration = config
                } else {
                    uiViewController.parent?.activityItemsConfiguration = config
                }
            }
            return
        }

        coordinator.lastIsActive = shouldActivate
        coordinator.lastPayload = trimmedPayload
        coordinator.lastFilename = filename
        coordinator.lastTitle = shareTitle

        guard shouldActivate else {
            coordinator.currentConfiguration = nil
            uiViewController.bridgeConfiguration = nil
            return
        }

        let cleanName = (filename as NSString).lastPathComponent
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedFilename: String = {
            let base = cleanName.isEmpty ? "grade_estudante_ufop.ufopgrade" : cleanName
            return base.lowercased().hasSuffix(".ufopgrade") ? base : "\(base).ufopgrade"
        }()

        let fileUrl = FileManager.default.temporaryDirectory
            .appendingPathComponent(resolvedFilename)

        guard let data = trimmedPayload.data(using: .utf8) else {
            return
        }
        try? data.write(to: fileUrl, options: .atomic)

        let config = UIActivityItemsConfiguration(objects: [fileUrl as NSURL])
        let resolvedTitle = shareTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? resolvedFilename
            : shareTitle
        config.metadataProvider = { key in
            guard key == .linkPresentationMetadata else { return nil }
            let metadata = LPLinkMetadata()
            metadata.title = resolvedTitle
            metadata.originalURL = fileUrl
            metadata.url = fileUrl
            return metadata
        }

        coordinator.currentConfiguration = config
        uiViewController.bridgeConfiguration = config
    }

    public static func dismantleUIViewController(
        _ uiViewController: AirDropBridgeViewController,
        coordinator: Coordinator
    ) {
        coordinator.currentConfiguration = nil
        uiViewController.bridgeConfiguration = nil
    }

    public final class Coordinator {
        var lastIsActive: Bool?
        var lastPayload: String?
        var lastFilename: String?
        var lastTitle: String?
        var currentConfiguration: UIActivityItemsConfiguration?
    }
}

public final class AirDropBridgeViewController: UIViewController {
    var bridgeConfiguration: UIActivityItemsConfiguration? {
        didSet {
            activityItemsConfiguration = bridgeConfiguration
            parent?.activityItemsConfiguration = bridgeConfiguration
        }
    }

    public override func didMove(toParent parent: UIViewController?) {
        super.didMove(toParent: parent)
        if let bridgeConfiguration {
            parent?.activityItemsConfiguration = bridgeConfiguration
        }
    }
}
#else
public struct AirDropProximityBridge: View {
    public let isActive: Bool
    public let jsonPayload: String
    public let filename: String
    public let shareTitle: String

    public init(
        isActive: Bool,
        jsonPayload: String,
        filename: String,
        shareTitle: String
    ) {
        self.isActive = isActive
        self.jsonPayload = jsonPayload
        self.filename = filename
        self.shareTitle = shareTitle
    }

    public var body: some View {
        EmptyView()
    }
}
#endif
