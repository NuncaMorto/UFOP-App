package br.ufop.campus.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Motor criptográfico offline-first do Vida Acadêmica UFOP.
 *
 * - Estado local (`campus_ufop_state.json`) e arquivos binários internos (`feedback_queue`):
 *   AES-256-GCM autenticado com chave protegida no `AndroidKeyStore` (hardware-backed quando disponível).
 * - Backups portáteis (`.ufopbak`):
 *   Derivação PBKDF2-HMAC-SHA256 (120.000 iterações, salt aleatório de 16 bytes) + AES-256-GCM.
 *
 * Utiliza `java.util.Base64` (disponível desde Android API 26 e em JVM pura), evitando falhas de stub
 * em testes unitários JVM.
 */
object CampusCryptoEngine {

    const val STATE_PREFIX = "UFOPENC1:"
    const val BACKUP_PREFIX = "UFOPBAK1:"
    const val BINARY_PREFIX = "UFOPBIN1:"

    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "ufop_campus_master_state_key_v1"
    private const val AES_ALGORITHM = "AES"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val AES_KEY_SIZE_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8

    private val BINARY_PREFIX_BYTES: ByteArray = BINARY_PREFIX.toByteArray(Charsets.US_ASCII)
    private val secureRandom = SecureRandom()
    private val keyLock = Any()

    @Volatile
    private var testKeyOverride: SecretKey? = null

    @Volatile
    private var androidKeyStoreLoaderForTest: (() -> SecretKey)? = null

    @Volatile
    private var jvmFallbackKey: SecretKey? = null

    /**
     * Injeta chave AES sintética para testes unitários JVM sem AndroidKeyStore.
     * Passe `null` para restaurar o comportamento padrão.
     */
    internal fun setTestKeyOverrideForJvm(key: SecretKey?) {
        synchronized(keyLock) {
            testKeyOverride = key
        }
    }

    /**
     * Simula o provedor `AndroidKeyStore` em testes unitários JVM para validar que falhas reais
     * lançam `SecurityException` sem cair silenciosamente em chave efêmera em memória.
     */
    internal fun setAndroidKeyStoreLoaderForTest(loader: (() -> SecretKey)?) {
        synchronized(keyLock) {
            androidKeyStoreLoaderForTest = loader
        }
    }

    fun isEncryptedStatePayload(rawPayload: String): Boolean {
        return rawPayload.trimStart().startsWith(STATE_PREFIX)
    }

    fun encryptStatePayload(plaintextJson: String): String {
        val key = getOrCreateMasterKey()
        val ivAndCipher = encryptAesGcm(key, plaintextJson.toByteArray(Charsets.UTF_8))
        return STATE_PREFIX + Base64.getEncoder().encodeToString(ivAndCipher)
    }

    fun decryptStatePayloadIfNeeded(rawPayload: String): String {
        val trimmed = rawPayload.trim()
        if (trimmed.startsWith(STATE_PREFIX)) {
            val encoded = trimmed.removePrefix(STATE_PREFIX)
            if (encoded.isEmpty()) {
                throw SecurityException("Payload de estado criptografado está vazio.")
            }
            val ivAndCipher = try {
                Base64.getDecoder().decode(encoded)
            } catch (err: IllegalArgumentException) {
                throw SecurityException("Codificação Base64 inválida no payload de estado.", err)
            }
            val plainBytes = decryptAesGcm(getOrCreateMasterKey(), ivAndCipher)
            return String(plainBytes, Charsets.UTF_8)
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return rawPayload
        }
        throw SecurityException("Formato de estado inválido ou adulterado.")
    }

    fun encryptBackupPayload(plaintextJson: String, password: CharArray): String {
        if (password.isEmpty() || password.all { it.isWhitespace() }) {
            throw IllegalArgumentException("A senha do backup não pode ser vazia.")
        }
        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val derivedKeyBytes = derivePbkdf2KeyBytes(password, salt)
        return try {
            val key = SecretKeySpec(derivedKeyBytes, AES_ALGORITHM)
            val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val cipherAndTag = cipher.doFinal(plaintextJson.toByteArray(Charsets.UTF_8))

            val bundle = ByteArray(salt.size + iv.size + cipherAndTag.size)
            System.arraycopy(salt, 0, bundle, 0, salt.size)
            System.arraycopy(iv, 0, bundle, salt.size, iv.size)
            System.arraycopy(cipherAndTag, 0, bundle, salt.size + iv.size, cipherAndTag.size)

            BACKUP_PREFIX + Base64.getEncoder().encodeToString(bundle)
        } catch (err: GeneralSecurityException) {
            throw SecurityException("Falha ao criptografar backup.", err)
        } finally {
            derivedKeyBytes.fill(0)
        }
    }

    fun decryptBackupPayload(encryptedBundle: String, password: CharArray): String {
        val trimmed = encryptedBundle.trim()
        if (!trimmed.startsWith(BACKUP_PREFIX)) {
            throw SecurityException("Arquivo de backup inválido ou não reconhecido.")
        }
        if (password.isEmpty() || password.all { it.isWhitespace() }) {
            throw SecurityException("Senha do backup obrigatória.")
        }
        val encoded = trimmed.removePrefix(BACKUP_PREFIX)
        if (encoded.isEmpty()) {
            throw SecurityException("Conteúdo de backup vazio.")
        }
        val decoded = try {
            Base64.getDecoder().decode(encoded)
        } catch (err: IllegalArgumentException) {
            throw SecurityException("Conteúdo Base64 inválido no backup.", err)
        }

        val minSize = SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES
        if (decoded.size < minSize) {
            throw SecurityException("Arquivo de backup corrompido ou truncado.")
        }

        val salt = decoded.copyOfRange(0, SALT_LENGTH_BYTES)
        val ivAndCipher = decoded.copyOfRange(SALT_LENGTH_BYTES, decoded.size)
        val derivedKeyBytes = derivePbkdf2KeyBytes(password, salt)
        return try {
            val key = SecretKeySpec(derivedKeyBytes, AES_ALGORITHM)
            val plainBytes = decryptAesGcm(key, ivAndCipher)
            String(plainBytes, Charsets.UTF_8)
        } finally {
            derivedKeyBytes.fill(0)
        }
    }

    fun encryptBytes(plainBytes: ByteArray): ByteArray {
        val ivAndCipher = encryptAesGcm(getOrCreateMasterKey(), plainBytes)
        return ByteArray(BINARY_PREFIX_BYTES.size + ivAndCipher.size).apply {
            System.arraycopy(BINARY_PREFIX_BYTES, 0, this, 0, BINARY_PREFIX_BYTES.size)
            System.arraycopy(ivAndCipher, 0, this, BINARY_PREFIX_BYTES.size, ivAndCipher.size)
        }
    }

    fun decryptBytesIfNeeded(rawBytes: ByteArray): ByteArray {
        if (!hasBinaryPrefix(rawBytes)) {
            return rawBytes
        }
        val payload = rawBytes.copyOfRange(BINARY_PREFIX_BYTES.size, rawBytes.size)
        return decryptAesGcm(getOrCreateMasterKey(), payload)
    }

    private fun hasBinaryPrefix(bytes: ByteArray): Boolean {
        if (bytes.size < BINARY_PREFIX_BYTES.size) return false
        for (i in BINARY_PREFIX_BYTES.indices) {
            if (bytes[i] != BINARY_PREFIX_BYTES[i]) return false
        }
        return true
    }

    private fun derivePbkdf2KeyBytes(password: CharArray, salt: ByteArray): ByteArray {
        val passwordCopy = password.copyOf()
        val spec = PBEKeySpec(passwordCopy, salt, PBKDF2_ITERATIONS, AES_KEY_SIZE_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            factory.generateSecret(spec).encoded
                ?: throw SecurityException("Não foi possível derivar chave PBKDF2.")
        } catch (err: GeneralSecurityException) {
            throw SecurityException("Erro na derivação de chave PBKDF2.", err)
        } finally {
            passwordCopy.fill('\u0000')
            spec.clearPassword()
        }
    }

    private fun isAndroidKeyStoreProviderPresent(): Boolean {
        if (Security.getProvider(ANDROID_KEYSTORE_PROVIDER) != null) {
            return true
        }
        return runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER)
            true
        }.getOrDefault(false)
    }

    private fun getOrCreateMasterKey(): SecretKey {
        testKeyOverride?.let { return it }
        synchronized(keyLock) {
            testKeyOverride?.let { return it }
            val testLoader = androidKeyStoreLoaderForTest
            if (testLoader != null) {
                return try {
                    testLoader()
                } catch (err: SecurityException) {
                    throw err
                } catch (err: Throwable) {
                    throw SecurityException(
                        "Falha ao acessar a chave mestra no AndroidKeyStore.",
                        err
                    )
                }
            }
            if (isAndroidKeyStoreProviderPresent()) {
                return try {
                    getOrCreateAndroidKeyStoreKey()
                } catch (err: SecurityException) {
                    throw err
                } catch (err: Throwable) {
                    throw SecurityException(
                        "Falha ao acessar ou gerar a chave mestra no AndroidKeyStore.",
                        err
                    )
                }
            }
            // Fallback em memória permitido exclusivamente fora do Android (testes unitários JVM).
            val existingFallback = jvmFallbackKey
            if (existingFallback != null) {
                return existingFallback
            }
            val generator = KeyGenerator.getInstance(AES_ALGORITHM)
            generator.init(AES_KEY_SIZE_BITS, secureRandom)
            val generated = generator.generateKey()
            jvmFallbackKey = generated
            return generated
        }
    }

    private fun getOrCreateAndroidKeyStoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER)
        keyStore.load(null)
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val existing = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
            return existing ?: throw SecurityException(
                "A chave mestra no AndroidKeyStore existe, mas não pôde ser recuperada."
            )
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(AES_KEY_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptAesGcm(key: SecretKey, plainBytes: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val iv: ByteArray
            if (key.encoded == null) {
                // Chave não-exportável do AndroidKeyStore exige IV gerado internamente pelo Keymaster/TEE.
                cipher.init(Cipher.ENCRYPT_MODE, key)
                iv = cipher.iv
                    ?: throw SecurityException("AndroidKeyStore não retornou IV para AES-GCM.")
                if (iv.size != GCM_IV_LENGTH_BYTES) {
                    throw SecurityException("Tamanho de IV inesperado (${iv.size} bytes).")
                }
            } else {
                iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
            val cipherAndTag = cipher.doFinal(plainBytes)
            ByteArray(iv.size + cipherAndTag.size).apply {
                System.arraycopy(iv, 0, this, 0, iv.size)
                System.arraycopy(cipherAndTag, 0, this, iv.size, cipherAndTag.size)
            }
        } catch (err: SecurityException) {
            throw err
        } catch (err: GeneralSecurityException) {
            throw SecurityException("Falha ao criptografar payload com AES-256-GCM.", err)
        }
    }

    private fun decryptAesGcm(key: SecretKey, ivAndCipher: ByteArray): ByteArray {
        if (ivAndCipher.size < GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES) {
            throw SecurityException("Payload criptografado truncado ou inválido.")
        }
        val iv = ivAndCipher.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val cipherAndTag = ivAndCipher.copyOfRange(GCM_IV_LENGTH_BYTES, ivAndCipher.size)
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(cipherAndTag)
        } catch (err: SecurityException) {
            throw err
        } catch (err: GeneralSecurityException) {
            throw SecurityException(
                "Falha ao descriptografar dados: senha incorreta ou conteúdo adulterado.",
                err
            )
        }
    }
}
