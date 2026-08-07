package com.radley.applock.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for the backup PIN.
 *
 * The PIN is never stored, only a hash of it, so there is nothing to encrypt at rest and no
 * need for a Keystore-wrapped secret. A 4-digit PIN only has 10 000 possible values, so the
 * cost factor is doing the real work here: 120 000 iterations makes an offline sweep of the
 * whole keyspace slow even with the hash and salt in hand.
 */
object PinHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val PREFIX = "v1"

    private val random = SecureRandom()

    /** Encodes as `v1:<base64 salt>:<base64 hash>`; the prefix leaves room to change scheme. */
    fun hash(pin: String, salt: ByteArray = newSalt()): String {
        val encoder = Base64.getEncoder()
        return listOf(
            PREFIX,
            encoder.encodeToString(salt),
            encoder.encodeToString(derive(pin, salt)),
        ).joinToString(":")
    }

    /**
     * Returns false for anything that is not a valid match, including malformed or corrupt
     * stored values — a garbled record must fail closed rather than throw somewhere up the
     * call stack where it might be caught and treated as success.
     */
    fun verify(pin: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        val parts = stored.split(":")
        if (parts.size != 3 || parts[0] != PREFIX) return false

        val decoder = Base64.getDecoder()
        val salt: ByteArray
        val expected: ByteArray
        try {
            salt = decoder.decode(parts[1])
            expected = decoder.decode(parts[2])
        } catch (e: IllegalArgumentException) {
            return false
        }
        if (salt.isEmpty() || expected.isEmpty()) return false

        // Length-constant comparison; a plain == on ByteArray would compare references anyway.
        return MessageDigest.isEqual(derive(pin, salt), expected)
    }

    private fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
