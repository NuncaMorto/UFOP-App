import SwiftUI

@main
public struct CampusUFOPApp: App {
    @State private var store = CampusStore()
    @AppStorage("campus.ufop.appearanceMode") private var appearanceModeRaw: String = "system"

    public init() {}

    private var resolvedColorScheme: ColorScheme? {
        switch appearanceModeRaw {
        case "light":
            return .light
        case "dark":
            return .dark
        default:
            return nil
        }
    }

    public var body: some Scene {
        WindowGroup {
            MainTabView()
                .environment(store)
                .preferredColorScheme(resolvedColorScheme)
        }
    }
}
