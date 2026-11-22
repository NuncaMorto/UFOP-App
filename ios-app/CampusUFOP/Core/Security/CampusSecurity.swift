import Foundation
import CryptoKit
import CommonCrypto
import Security
import LocalAuthentication
import PDFKit
import SwiftUI
import UniformTypeIdentifiers
import Observation

// MARK: - Cryptographic Engine (Keychain AES-256-GCM + Portable PBKDF2 Backups)

public enum CampusCryptoError: LocalizedError {
    case invalidStatePayload
    case invalidBackupFormat
    case emptyPassword
    case keyDerivationFailed
    case encryptionFailed
    case decryptionFailed

    public var errorDescription: String? {
        switch self {
        case .invalidStatePayload:
            return "Payload de estado criptografado inválido."
        case .invalidBackupFormat:
            return "Arquivo de backup inválido ou corrompido (.ufopbackup)."
        case .emptyPassword:
            return "Informe uma senha não vazia para o backup."
        case .keyDerivationFailed:
            return "Não foi possível derivar a chave criptográfica."
        case .encryptionFailed:
            return "Falha ao criptografar os dados locais."
        case .decryptionFailed:
            return "Senha incorreta ou arquivo adulterado."
        }
    }
}

public enum CampusCryptoEngine {
    public static let statePrefix = "UFOPENC1:"
    public static let backupPrefix = "UFOPBAK1:"

    private static let keychainService = "br.ufop.campus.security"
    private static let masterKeyAccount = "ufop_campus_master_state_key_v1"
    private static let pbkdf2Iterations: UInt32 = 120_000
    private static let saltLength = 16
    private static let gcmNonceLength = 12
    private static let gcmTagLength = 16
    private static let aesKeyByteCount = 32

    public static func encryptStatePayload(_ plaintextJson: String) throws -> String {
        let key = try getOrCreateMasterKey()
        guard let plainData = plaintextJson.data(using: .utf8) else {
            throw CampusCryptoError.encryptionFailed
        }
        let sealed = try AES.GCM.seal(plainData, using: key)
        guard let combined = sealed.combined else {
            throw CampusCryptoError.encryptionFailed
        }
        return statePrefix + combined.base64EncodedString()
    }

    public static func decryptStatePayloadIfNeeded(_ rawPayload: String) throws -> String {
        let trimmed = rawPayload.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix(statePrefix) {
            let encoded = String(trimmed.dropFirst(statePrefix.count))
            guard !encoded.isEmpty,
                  let combined = Data(base64Encoded: encoded) else {
                throw CampusCryptoError.invalidStatePayload
            }
            let key = try getOrCreateMasterKey()
            let box = try AES.GCM.SealedBox(combined: combined)
            let plainData = try AES.GCM.open(box, using: key)
            guard let text = String(data: plainData, encoding: .utf8) else {
                throw CampusCryptoError.decryptionFailed
            }
            return text
        }
        if trimmed.hasPrefix("{") || trimmed.hasPrefix("[") {
            return rawPayload
        }
        throw CampusCryptoError.invalidStatePayload
    }

    public static func encryptBackupPayload(_ plaintextJson: String, password: String) throws -> String {
        let cleanPassword = password.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanPassword.isEmpty,
              let plainData = plaintextJson.data(using: .utf8) else {
            throw CampusCryptoError.emptyPassword
        }
        let salt = try randomBytes(count: saltLength)
        let derivedKeyData = try derivePbkdf2Key(password: cleanPassword, salt: salt, iterations: pbkdf2Iterations, keyByteCount: aesKeyByteCount)
        let key = SymmetricKey(data: derivedKeyData)
        let sealed = try AES.GCM.seal(plainData, using: key)
        guard let combined = sealed.combined else {
            throw CampusCryptoError.encryptionFailed
        }
        var bundle = Data()
        bundle.append(salt)
        bundle.append(combined)
        return backupPrefix + bundle.base64EncodedString()
    }

    public static func decryptBackupPayload(_ encryptedBundle: String, password: String) throws -> String {
        let trimmed = encryptedBundle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix(backupPrefix) else {
            throw CampusCryptoError.invalidBackupFormat
        }
        let cleanPassword = password.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanPassword.isEmpty else {
            throw CampusCryptoError.emptyPassword
        }
        let encoded = String(trimmed.dropFirst(backupPrefix.count))
        guard let decoded = Data(base64Encoded: encoded),
              decoded.count >= saltLength + gcmNonceLength + gcmTagLength else {
            throw CampusCryptoError.invalidBackupFormat
        }
        let salt = decoded.subdata(in: 0..<saltLength)
        let combined = decoded.subdata(in: saltLength..<decoded.count)
        let derivedKeyData = try derivePbkdf2Key(password: cleanPassword, salt: salt, iterations: pbkdf2Iterations, keyByteCount: aesKeyByteCount)
        let key = SymmetricKey(data: derivedKeyData)
        do {
            let box = try AES.GCM.SealedBox(combined: combined)
            let plainData = try AES.GCM.open(box, using: key)
            guard let json = String(data: plainData, encoding: .utf8) else {
                throw CampusCryptoError.decryptionFailed
            }
            return json
        } catch {
            throw CampusCryptoError.decryptionFailed
        }
    }

    // MARK: Keychain Master Key & PBKDF2

    private static func getOrCreateMasterKey() throws -> SymmetricKey {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: masterKeyAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecSuccess, let keyData = item as? Data, keyData.count == aesKeyByteCount {
            return SymmetricKey(data: keyData)
        }

        let newKeyData = try randomBytes(count: aesKeyByteCount)
        let addQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: masterKeyAccount,
            kSecValueData as String: newKeyData,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        SecItemDelete(addQuery as CFDictionary)
        let addStatus = SecItemAdd(addQuery as CFDictionary, nil)
        guard addStatus == errSecSuccess || addStatus == errSecDuplicateItem else {
            throw CampusCryptoError.encryptionFailed
        }
        return SymmetricKey(data: newKeyData)
    }

    public static func randomBytes(count: Int) throws -> Data {
        var bytes = Data(count: count)
        let status = bytes.withUnsafeMutableBytes { buffer in
            SecRandomCopyBytes(kSecRandomDefault, count, buffer.baseAddress!)
        }
        guard status == errSecSuccess else {
            throw CampusCryptoError.keyDerivationFailed
        }
        return bytes
    }

    public static func derivePbkdf2Key(
        password: String,
        salt: Data,
        iterations: UInt32 = 120_000,
        keyByteCount: Int = 32
    ) throws -> Data {
        let passwordBytes = Array(password.utf8).map { Int8(bitPattern: $0) }
        var derived = Data(count: keyByteCount)
        let status = derived.withUnsafeMutableBytes { derivedBuf in
            salt.withUnsafeBytes { saltBuf in
                CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2),
                    passwordBytes,
                    passwordBytes.count,
                    saltBuf.bindMemory(to: UInt8.self).baseAddress,
                    salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                    iterations,
                    derivedBuf.bindMemory(to: UInt8.self).baseAddress,
                    keyByteCount
                )
            }
        }
        guard status == kCCSuccess else {
            throw CampusCryptoError.keyDerivationFailed
        }
        return derived
    }
}

// MARK: - Ephemeral PDF Extractor (Zero Residual Student PDFs)

public enum SecurePdfError: LocalizedError {
    case unreadableDocument
    case emptyText

    public var errorDescription: String? {
        switch self {
        case .unreadableDocument:
            return "Não foi possível abrir o arquivo PDF selecionado."
        case .emptyText:
            return "Nenhum texto legível encontrado neste PDF (verifique se não é apenas imagem escaneada)."
        }
    }
}

public enum SecurePdfProcessor {
    private static let tempPrefix = "ufop_pdf_tmp_"

    /// Lê o PDF do URL escolhido pelo aluno, extrai o texto em memória e exclui imediatamente
    /// qualquer arquivo temporário usado na importação.
    public static func extractTextAndDeleteTemp(from url: URL) throws -> String {
        purgeResidualTemporaryPdfs()

        let needsSecurityAccess = url.startAccessingSecurityScopedResource()
        defer {
            if needsSecurityAccess {
                url.stopAccessingSecurityScopedResource()
            }
            purgeResidualTemporaryPdfs()
        }

        let pdfData = try Data(contentsOf: url, options: .mappedIfSafe)
        var textOutput = ""

        let tempUrl = FileManager.default.temporaryDirectory
            .appendingPathComponent("\(tempPrefix)\(UUID().uuidString).pdf")

        defer {
            if FileManager.default.fileExists(atPath: tempUrl.path) {
                try? Data(repeating: 0, count: 128).write(to: tempUrl, options: .atomic)
                try? FileManager.default.removeItem(at: tempUrl)
            }
            purgeResidualTemporaryPdfs()
        }

        if let doc = PDFDocument(data: pdfData) {
            textOutput = extractAllPagesText(from: doc)
        } else {
            try pdfData.write(to: tempUrl, options: .atomic)
            if let doc = PDFDocument(url: tempUrl) {
                textOutput = extractAllPagesText(from: doc)
            } else {
                throw SecurePdfError.unreadableDocument
            }
        }

        let trimmed = textOutput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw SecurePdfError.emptyText
        }
        return trimmed
    }

    private static func extractAllPagesText(from document: PDFDocument) -> String {
        var pages: [String] = []
        for idx in 0..<document.pageCount {
            if let pageText = document.page(at: idx)?.string, !pageText.isEmpty {
                pages.append(pageText)
            }
        }
        return pages.joined(separator: "\n")
    }

    public static func purgeResidualTemporaryPdfs() {
        let fm = FileManager.default
        let tmpDir = fm.temporaryDirectory
        guard let contents = try? fm.contentsOfDirectory(at: tmpDir, includingPropertiesForKeys: nil) else {
            return
        }
        for item in contents where item.lastPathComponent.hasPrefix(tempPrefix) || item.pathExtension.lowercased() == "pdf" {
            try? fm.removeItem(at: item)
        }
    }
}

// MARK: - App Lock Manager (PIN/Password + Face ID / Touch ID + Progressive Lockout)

@Observable
public final class AppLockManager {
    private static let keyLockEnabled = "campus.ufop.lockEnabled"
    private static let keyBiometricEnabled = "campus.ufop.biometricEnabled"
    private static let keyPasswordSaltB64 = "campus.ufop.lockSaltB64"
    private static let keyPasswordHashB64 = "campus.ufop.lockHashB64"
    private static let keyFailedAttempts = "campus.ufop.lockFailedAttempts"
    private static let keyLockoutUntilEpoch = "campus.ufop.lockoutUntilEpoch"
    private static let keyLockoutLevel = "campus.ufop.lockoutLevel"

    private static let maxAttemptsBeforeLockout = 5
    private static let initialLockoutSec: TimeInterval = 30
    private static let maxLockoutSec: TimeInterval = 300

    public var isLockEnabled: Bool
    public var isBiometricEnabled: Bool
    public var isUnlocked: Bool
    public var failedAttempts: Int
    public var lockoutRemainingSeconds: Int

    public init() {
        let defaults = UserDefaults.standard
        let salt = defaults.string(forKey: Self.keyPasswordSaltB64) ?? ""
        let hash = defaults.string(forKey: Self.keyPasswordHashB64) ?? ""
        let enabled = defaults.bool(forKey: Self.keyLockEnabled) && !salt.isEmpty && !hash.isEmpty
        self.isLockEnabled = enabled
        self.isBiometricEnabled = enabled && defaults.bool(forKey: Self.keyBiometricEnabled)
        self.isUnlocked = !enabled
        self.failedAttempts = max(0, defaults.integer(forKey: Self.keyFailedAttempts))
        let until = defaults.double(forKey: Self.keyLockoutUntilEpoch)
        self.lockoutRemainingSeconds = max(0, Int(ceil(until - Date().timeIntervalSince1970)))
    }

    public var biometricAvailable: Bool {
        let context = LAContext()
        var error: NSError?
        return context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    }

    public var biometricLabel: String {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            return "Biometria"
        }
        switch context.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .opticID: return "Optic ID"
        default: return "Biometria"
        }
    }

    public func refreshLockoutCountdown() {
        let until = UserDefaults.standard.double(forKey: Self.keyLockoutUntilEpoch)
        lockoutRemainingSeconds = max(0, Int(ceil(until - Date().timeIntervalSince1970)))
    }

    public func lockNowIfEnabled() {
        if isLockEnabled {
            isUnlocked = false
            refreshLockoutCountdown()
        }
    }

    @discardableResult
    public func enableLock(password: String, allowBiometric: Bool) -> String? {
        let clean = password.trimmingCharacters(in: .whitespacesAndNewlines)
        guard clean.count >= 4 else {
            return "A senha deve ter pelo menos 4 caracteres."
        }
        guard let salt = try? CampusCryptoEngine.randomBytes(count: 16),
              let hash = try? CampusCryptoEngine.derivePbkdf2Key(password: clean, salt: salt) else {
            return "Não foi possível gerar a chave de bloqueio."
        }
        let defaults = UserDefaults.standard
        defaults.set(true, forKey: Self.keyLockEnabled)
        defaults.set(allowBiometric && biometricAvailable, forKey: Self.keyBiometricEnabled)
        defaults.set(salt.base64EncodedString(), forKey: Self.keyPasswordSaltB64)
        defaults.set(hash.base64EncodedString(), forKey: Self.keyPasswordHashB64)
        defaults.set(0, forKey: Self.keyFailedAttempts)
        defaults.set(0.0, forKey: Self.keyLockoutUntilEpoch)
        defaults.set(0, forKey: Self.keyLockoutLevel)

        isLockEnabled = true
        isBiometricEnabled = allowBiometric && biometricAvailable
        failedAttempts = 0
        lockoutRemainingSeconds = 0
        isUnlocked = true
        return nil
    }

    @discardableResult
    public func setBiometricEnabled(_ enabled: Bool) -> String? {
        guard isLockEnabled else {
            return "Ative o bloqueio por senha antes de habilitar a biometria."
        }
        if enabled && !biometricAvailable {
            return "Face ID / Touch ID indisponível ou não configurado neste aparelho."
        }
        UserDefaults.standard.set(enabled, forKey: Self.keyBiometricEnabled)
        isBiometricEnabled = enabled
        return nil
    }

    @discardableResult
    public func disableLock(currentPassword: String) -> String? {
        guard isLockEnabled else { return nil }
        if let verifyError = verifyPassword(currentPassword) {
            return verifyError
        }
        clearAllLockData()
        return nil
    }

    @discardableResult
    public func verifyPassword(_ candidate: String) -> String? {
        guard isLockEnabled else {
            isUnlocked = true
            return nil
        }

        refreshLockoutCountdown()
        if lockoutRemainingSeconds > 0 {
            return "Muitas tentativas incorretas. Aguarde \(lockoutRemainingSeconds)s."
        }

        let defaults = UserDefaults.standard
        guard let saltB64 = defaults.string(forKey: Self.keyPasswordSaltB64),
              let expectedB64 = defaults.string(forKey: Self.keyPasswordHashB64),
              let salt = Data(base64Encoded: saltB64),
              let expectedHash = Data(base64Encoded: expectedB64),
              let candidateHash = try? CampusCryptoEngine.derivePbkdf2Key(password: candidate.trimmingCharacters(in: .whitespacesAndNewlines), salt: salt) else {
            return "Erro ao verificar a senha."
        }

        if constantTimeEqual(expectedHash, candidateHash) {
            defaults.set(0, forKey: Self.keyFailedAttempts)
            defaults.set(0.0, forKey: Self.keyLockoutUntilEpoch)
            defaults.set(0, forKey: Self.keyLockoutLevel)
            failedAttempts = 0
            lockoutRemainingSeconds = 0
            isUnlocked = true
            return nil
        }

        let newAttempts = failedAttempts + 1
        failedAttempts = newAttempts
        defaults.set(newAttempts, forKey: Self.keyFailedAttempts)

        if newAttempts >= Self.maxAttemptsBeforeLockout {
            let level = defaults.integer(forKey: Self.keyLockoutLevel) + 1
            let shift = min(4, max(0, level - 1))
            let cooldown = min(Self.maxLockoutSec, Self.initialLockoutSec * pow(2.0, Double(shift)))
            let until = Date().timeIntervalSince1970 + cooldown
            defaults.set(level, forKey: Self.keyLockoutLevel)
            defaults.set(until, forKey: Self.keyLockoutUntilEpoch)
            lockoutRemainingSeconds = Int(ceil(cooldown))
            return "Muitas tentativas incorretas. Bloqueado por \(lockoutRemainingSeconds)s."
        }

        let remaining = Self.maxAttemptsBeforeLockout - newAttempts
        return "Senha incorreta (\(remaining) tentativa(s) restante(s))."
    }

    public func authenticateWithBiometrics(completion: @escaping (Bool, String?) -> Void) {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            DispatchQueue.main.async {
                completion(false, error?.localizedDescription ?? "Biometria indisponível.")
            }
            return
        }
        context.localizedFallbackTitle = "Usar senha do app"
        context.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: "Confirmar identidade para acessar o Vida Acadêmica"
        ) { [weak self] success, evalError in
            DispatchQueue.main.async {
                if success {
                    self?.isUnlocked = true
                    self?.failedAttempts = 0
                    self?.lockoutRemainingSeconds = 0
                    UserDefaults.standard.set(0, forKey: Self.keyFailedAttempts)
                    UserDefaults.standard.set(0.0, forKey: Self.keyLockoutUntilEpoch)
                    UserDefaults.standard.set(0, forKey: Self.keyLockoutLevel)
                    completion(true, nil)
                } else {
                    completion(false, evalError?.localizedDescription)
                }
            }
        }
    }

    public func clearAllLockData() {
        let defaults = UserDefaults.standard
        defaults.removeObject(forKey: Self.keyLockEnabled)
        defaults.removeObject(forKey: Self.keyBiometricEnabled)
        defaults.removeObject(forKey: Self.keyPasswordSaltB64)
        defaults.removeObject(forKey: Self.keyPasswordHashB64)
        defaults.removeObject(forKey: Self.keyFailedAttempts)
        defaults.removeObject(forKey: Self.keyLockoutUntilEpoch)
        defaults.removeObject(forKey: Self.keyLockoutLevel)
        isLockEnabled = false
        isBiometricEnabled = false
        failedAttempts = 0
        lockoutRemainingSeconds = 0
        isUnlocked = true
    }

    private func constantTimeEqual(_ lhs: Data, _ rhs: Data) -> Bool {
        guard lhs.count == rhs.count else { return false }
        var diff: UInt8 = 0
        for idx in 0..<lhs.count {
            diff |= lhs[idx] ^ rhs[idx]
        }
        return diff == 0
    }
}

// MARK: - SwiftUI FileDocument for Exporting .ufopbackup / .ufopgrade

public struct UfopTextDocument: FileDocument {
    public static var readableContentTypes: [UTType] { [.plainText, .data] }

    public var text: String

    public init(text: String) {
        self.text = text
    }

    public init(configuration: ReadConfiguration) throws {
        if let data = configuration.file.regularFileContents,
           let str = String(data: data, encoding: .utf8) {
            self.text = str
        } else {
            self.text = ""
        }
    }

    public func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
