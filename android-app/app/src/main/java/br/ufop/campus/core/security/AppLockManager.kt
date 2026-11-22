package br.ufop.campus.core.security

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Gerenciador de bloqueio local do aplicativo (senha + biometria forte vinculada a AndroidKeyStore API 28+).
 *
 * - Hash de senha com PBKDF2-HMAC-SHA256 (120.000 iterações) + salt aleatório de 16 bytes.
 * - Biometria forte (`BIOMETRIC_STRONG`) vinculada a `BiometricPrompt.CryptoObject` com chave AES-256-GCM no `AndroidKeyStore`.
 * - Verificação em tempo constante (`MessageDigest.isEqual`).
 * - Rate-limiting progressivo após 5 tentativas incorretas (30s -> 60s -> 120s -> 240s -> 300s).
 * - Persistência síncrona via `.commit()` em `SharedPreferences` privado dedicado.
 */
object AppLockManager {

    private const val PREFS_NAME = "vida_academica_security_prefs"
    private const val KEY_LOCK_ENABLED = "lock_enabled"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_PASSWORD_HASH = "password_hash_b64"
    private const val KEY_PASSWORD_SALT = "password_salt_b64"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKOUT_UNTIL_MS = "lockout_until_ms"
    private const val KEY_LOCKOUT_LEVEL = "lockout_level"

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val HASH_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val MIN_PASSWORD_LENGTH = 4

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val BIOMETRIC_KEY_ALIAS = "vida_academica_biometric_unlock_v1"
    private const val BIOMETRIC_CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val BIOMETRIC_CHALLENGE_BYTES = 32

    private const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
    private const val INITIAL_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 300_000L

    private val secureRandom = SecureRandom()

    data class AppLockStatus(
        val lockEnabled: Boolean,
        val biometricEnabled: Boolean,
        val biometricHardwareAvailable: Boolean,
        val failedAttempts: Int,
        val lockoutRemainingMs: Long
    )

    sealed class VerifyLockResult {
        data object Success : VerifyLockResult()
        data class Failed(
            val attemptsRemaining: Int,
            val lockoutMs: Long
        ) : VerifyLockResult()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStatus(context: Context): AppLockStatus {
        val sp = prefs(context)
        val lockEnabled = isLockEnabledInternal(sp)
        val biometricHardwareAvailable = isBiometricAvailable(context)
        val biometricEnabled = lockEnabled && sp.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        val failedAttempts = sp.getInt(KEY_FAILED_ATTEMPTS, 0).coerceAtLeast(0)
        val lockoutUntilMs = sp.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        val lockoutRemainingMs = (lockoutUntilMs - System.currentTimeMillis()).coerceAtLeast(0L)
        return AppLockStatus(
            lockEnabled = lockEnabled,
            biometricEnabled = biometricEnabled,
            biometricHardwareAvailable = biometricHardwareAvailable,
            failedAttempts = failedAttempts,
            lockoutRemainingMs = lockoutRemainingMs
        )
    }

    fun isLockEnabled(context: Context): Boolean {
        return isLockEnabledInternal(prefs(context))
    }

    fun isBiometricAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false
        }
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val biometricManagerResult = runCatching {
                val manager = appContext.getSystemService(BiometricManager::class.java)
                if (manager != null) {
                    val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    } else {
                        @Suppress("DEPRECATION")
                        manager.canAuthenticate()
                    }
                    status == BiometricManager.BIOMETRIC_SUCCESS
                } else {
                    null
                }
            }.getOrNull()
            if (biometricManagerResult != null) {
                return biometricManagerResult
            }
        }

        return runCatching {
            val pm = appContext.packageManager
            val hasBiometricFeature = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    (pm.hasSystemFeature(PackageManager.FEATURE_FACE) ||
                        pm.hasSystemFeature(PackageManager.FEATURE_IRIS)))
            if (!hasBiometricFeature) {
                return@runCatching false
            }
            // Em API 28, verifica enrollment quando a permissão legada estiver presente;
            // caso contrário, confia na presença de hardware biométrico e deixa o BiometricPrompt validar.
            runCatching {
                @Suppress("DEPRECATION")
                val fm = appContext.getSystemService(android.hardware.fingerprint.FingerprintManager::class.java)
                if (fm != null) {
                    fm.isHardwareDetected && fm.hasEnrolledFingerprints()
                } else {
                    true
                }
            }.getOrDefault(true)
        }.getOrDefault(false)
    }

    fun enableLock(
        context: Context,
        password: String,
        allowBiometric: Boolean
    ): Result<Unit> {
        if (password.trim().length < MIN_PASSWORD_LENGTH) {
            return Result.failure(
                IllegalArgumentException("A senha deve ter pelo menos $MIN_PASSWORD_LENGTH caracteres.")
            )
        }
        return runCatching {
            val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
            val hash = hashPassword(password, salt)
            val saved = prefs(context).edit()
                .putBoolean(KEY_LOCK_ENABLED, true)
                .putBoolean(KEY_BIOMETRIC_ENABLED, allowBiometric)
                .putString(KEY_PASSWORD_SALT, Base64.getEncoder().encodeToString(salt))
                .putString(KEY_PASSWORD_HASH, Base64.getEncoder().encodeToString(hash))
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
                .putInt(KEY_LOCKOUT_LEVEL, 0)
                .commit()
            hash.fill(0)
            if (!saved) {
                throw IllegalStateException("Não foi possível salvar a configuração de bloqueio.")
            }
        }
    }

    fun setBiometricEnabled(
        context: Context,
        currentPasswordOrNull: String? = null,
        enabled: Boolean
    ): Result<Unit> {
        if (!isLockEnabled(context)) {
            return Result.failure(
                IllegalStateException("Ative o bloqueio por senha antes de configurar a biometria.")
            )
        }
        if (currentPasswordOrNull != null) {
            when (val verify = verifyPassword(context, currentPasswordOrNull)) {
                is VerifyLockResult.Success -> Unit
                is VerifyLockResult.Failed -> {
                    val msg = if (verify.lockoutMs > 0L) {
                        "Muitas tentativas incorretas. Aguarde ${(verify.lockoutMs + 999L) / 1000L}s."
                    } else {
                        "Senha incorreta."
                    }
                    return Result.failure(SecurityException(msg))
                }
            }
        }
        if (enabled && !isBiometricAvailable(context)) {
            return Result.failure(
                IllegalStateException("Biometria forte não disponível ou não cadastrada neste dispositivo.")
            )
        }
        val saved = prefs(context).edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .commit()
        return if (saved) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Não foi possível atualizar a preferência de biometria."))
        }
    }

    fun disableLock(
        context: Context,
        currentPassword: String
    ): VerifyLockResult {
        if (!isLockEnabled(context)) {
            return VerifyLockResult.Success
        }
        val verify = verifyPassword(context, currentPassword)
        if (verify is VerifyLockResult.Success) {
            clearAllLockData(context)
        }
        return verify
    }

    fun verifyPassword(
        context: Context,
        candidatePassword: String
    ): VerifyLockResult {
        val sp = prefs(context)
        if (!isLockEnabledInternal(sp)) {
            return VerifyLockResult.Success
        }

        val now = System.currentTimeMillis()
        val lockoutUntil = sp.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        if (now < lockoutUntil) {
            return VerifyLockResult.Failed(
                attemptsRemaining = 0,
                lockoutMs = lockoutUntil - now
            )
        }

        val saltB64 = sp.getString(KEY_PASSWORD_SALT, null)
        val expectedHashB64 = sp.getString(KEY_PASSWORD_HASH, null)
        if (saltB64.isNullOrBlank() || expectedHashB64.isNullOrBlank()) {
            return VerifyLockResult.Failed(attemptsRemaining = 0, lockoutMs = 0L)
        }

        val salt = runCatching { Base64.getDecoder().decode(saltB64) }.getOrNull()
        val expectedHash = runCatching { Base64.getDecoder().decode(expectedHashB64) }.getOrNull()
        if (salt == null || expectedHash == null) {
            return VerifyLockResult.Failed(attemptsRemaining = 0, lockoutMs = 0L)
        }

        val candidateHash = hashPassword(candidatePassword, salt)
        val matches = try {
            MessageDigest.isEqual(expectedHash, candidateHash)
        } finally {
            candidateHash.fill(0)
        }

        if (matches) {
            resetFailedAttempts(sp)
            return VerifyLockResult.Success
        }

        val newFailedAttempts = sp.getInt(KEY_FAILED_ATTEMPTS, 0).coerceAtLeast(0) + 1
        if (newFailedAttempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            val newLevel = sp.getInt(KEY_LOCKOUT_LEVEL, 0).coerceAtLeast(0) + 1
            val shift = (newLevel - 1).coerceIn(0, 4)
            val cooldownMs = (INITIAL_LOCKOUT_MS shl shift).coerceAtMost(MAX_LOCKOUT_MS)
            sp.edit()
                .putInt(KEY_FAILED_ATTEMPTS, newFailedAttempts)
                .putInt(KEY_LOCKOUT_LEVEL, newLevel)
                .putLong(KEY_LOCKOUT_UNTIL_MS, now + cooldownMs)
                .commit()
            return VerifyLockResult.Failed(
                attemptsRemaining = 0,
                lockoutMs = cooldownMs
            )
        }

        val remaining = (MAX_ATTEMPTS_BEFORE_LOCKOUT - newFailedAttempts).coerceAtLeast(0)
        sp.edit()
            .putInt(KEY_FAILED_ATTEMPTS, newFailedAttempts)
            .commit()
        return VerifyLockResult.Failed(
            attemptsRemaining = remaining,
            lockoutMs = 0L
        )
    }

    fun clearAllLockData(context: Context) {
        prefs(context).edit().clear().commit()
        runCatching { deleteBiometricKey() }
    }

    fun launchBiometricPrompt(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onFailure("Biometria requer Android 9 ou superior.")
            return
        }
        if (!isBiometricAvailable(activity)) {
            onFailure("Biometria forte não disponível ou não configurada neste aparelho.")
            return
        }

        val challenge = ByteArray(BIOMETRIC_CHALLENGE_BYTES).also { secureRandom.nextBytes(it) }
        runCatching {
            val cipher = createBiometricUnlockCipher()
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)
            val executor = activity.mainExecutor
            val builder = BiometricPrompt.Builder(activity)
                .setTitle("Desbloquear Vida Acadêmica")
                .setSubtitle("Confirme sua identidade para acessar seus dados")
                .setNegativeButton("Usar senha", executor) { _, _ ->
                    challenge.fill(0)
                    onCancel()
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            }

            val prompt = builder.build()
            val cancellationSignal = CancellationSignal()
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    val authenticatedCipher = result?.cryptoObject?.cipher
                    if (authenticatedCipher == null) {
                        challenge.fill(0)
                        onFailure("Falha na validação criptográfica da biometria. Use sua senha.")
                        return
                    }
                    val sealedProof = runCatching { authenticatedCipher.doFinal(challenge) }.getOrNull()
                    challenge.fill(0)
                    if (sealedProof == null || sealedProof.isEmpty()) {
                        onFailure("Não foi possível validar a chave biométrica. Use sua senha.")
                        return
                    }
                    sealedProof.fill(0)
                    resetFailedAttempts(prefs(activity))
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    challenge.fill(0)
                    val isCancel = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                    if (isCancel) {
                        onCancel()
                    } else {
                        val message = errString?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                            ?: "Não foi possível autenticar por biometria."
                        onFailure(message)
                    }
                }

                override fun onAuthenticationFailed() {
                    // O prompt nativo do sistema já orienta o usuário a tentar novamente.
                }
            }

            prompt.authenticate(cryptoObject, cancellationSignal, executor, callback)
        }.onFailure {
            challenge.fill(0)
            onFailure("Não foi possível abrir o leitor biométrico com validação criptográfica. Use sua senha.")
        }
    }

    private fun deleteBiometricKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
        }
    }

    private fun getOrCreateBiometricUnlockKey(forceRecreate: Boolean = false): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (forceRecreate && keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
        } else if (!forceRecreate && keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            val existing = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val specBuilder = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            specBuilder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            specBuilder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }

    private fun createBiometricUnlockCipher(): Cipher {
        val cipher = Cipher.getInstance(BIOMETRIC_CIPHER_TRANSFORMATION)
        return try {
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateBiometricUnlockKey(forceRecreate = false))
            cipher
        } catch (_: KeyPermanentlyInvalidatedException) {
            val refreshed = Cipher.getInstance(BIOMETRIC_CIPHER_TRANSFORMATION)
            refreshed.init(Cipher.ENCRYPT_MODE, getOrCreateBiometricUnlockKey(forceRecreate = true))
            refreshed
        }
    }

    private fun isLockEnabledInternal(sp: SharedPreferences): Boolean {
        return sp.getBoolean(KEY_LOCK_ENABLED, false) &&
            !sp.getString(KEY_PASSWORD_HASH, null).isNullOrBlank() &&
            !sp.getString(KEY_PASSWORD_SALT, null).isNullOrBlank()
    }

    private fun resetFailedAttempts(sp: SharedPreferences) {
        sp.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .putInt(KEY_LOCKOUT_LEVEL, 0)
            .commit()
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, HASH_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            factory.generateSecret(spec).encoded
                ?: throw IllegalStateException("Falha ao gerar hash da senha.")
        } finally {
            chars.fill('\u0000')
            spec.clearPassword()
        }
    }
}
