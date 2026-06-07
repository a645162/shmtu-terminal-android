package cn.edu.shmtu.terminal.android.data.p2p

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * P2P transport encryption and key derivation, aligned with the Rust `shmtu-p2p` crypto module.
 *
 * Uses AES-256-GCM for payload encryption and PBKDF2-HmacSHA256 for key derivation
 * from the shared pair code. Provides HMAC-SHA256 verification of the derived key
 * across both peers to prevent man-in-the-middle and wrong-code attacks.
 */
object P2PCrypto {

    private const val PBKDF2_ITERATIONS = 600_000
    private const val KEY_LEN_BITS = 256
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val TAG_LEN_BITS = 128
    private const val ALGORITHM = "AES/GCM/NoPadding"

    /**
     * Derive a 256-bit AES key from the pair code and salt using PBKDF2-HmacSHA256.
     * The returned byte array is sensitive and should be zeroed via [ByteArray.fill]
     * when no longer needed.
     */
    fun deriveKey(pairCode: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pairCode.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LEN_BITS)
        try {
            val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Encrypt plaintext with AES-256-GCM. Output format: [12-byte nonce][ciphertext + 16-byte tag].
     * A fresh random nonce is generated for every call.
     */
    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(TAG_LEN_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }

    /**
     * Decrypt data produced by [encrypt]. The first 12 bytes are the nonce,
     * the rest is ciphertext + GCM auth tag.
     */
    fun decrypt(key: ByteArray, data: ByteArray): ByteArray {
        if (data.size < NONCE_LEN + 16) throw IllegalArgumentException("Data too short")
        val nonce = data.copyOfRange(0, NONCE_LEN)
        val ciphertext = data.copyOfRange(NONCE_LEN, data.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(TAG_LEN_BITS, nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Compute a 16-byte verification token as
     * HMAC-SHA256(salt || clientNonce || "p2p-verify", derived_key)[0:16].
     * Used to confirm both peers derived the same key from the same pair code.
     */
    fun generateVerification(salt: ByteArray, clientNonce: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.update(salt)
        mac.update(clientNonce)
        mac.update("p2p-verify".toByteArray(Charsets.UTF_8))
        val result = mac.doFinal()
        return result.copyOfRange(0, 16)
    }

    /** Generate a random 16-byte salt for PBKDF2. */
    fun generateSalt(): ByteArray = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }

    /** Generate a random 8-byte client nonce for the key verification handshake. */
    fun generateClientNonce(): ByteArray = ByteArray(8).also { SecureRandom().nextBytes(it) }

    /**
     * Returns false for control-plane frames (PING/PONG, encryption negotiation)
     * which must be readable without a key. All data-plane frames are encrypted
     * when a key is set.
     */
    fun shouldEncrypt(msgType: Byte): Boolean {
        return msgType.toInt() and 0xFF !in listOf(
            P2PProtocol.TYPE_PING,
            P2PProtocol.TYPE_PONG,
            P2PProtocol.TYPE_ENCRYPTION_NEGOTIATE,
            P2PProtocol.TYPE_ENCRYPTION_CONFIRM,
            P2PProtocol.TYPE_TRANSFER_CHANNEL_OPEN
        )
    }
}
