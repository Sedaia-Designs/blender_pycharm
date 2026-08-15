package com.sakurasedaia.blenderdevelopment.core

import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils
import java.security.SecureRandom
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.util.Base64

/**
 * A utility class for generating, encoding, and decoding cryptographic keys.
 *
 * This class provides methods to create a random key, encode it into a Base64 URL-safe string,
 * and decode it back into a byte array. Keys are expected to have a fixed size defined by
 * `KEY_SIZE_BYTES`.
 */
internal object BlenderAuthentication {
  private const val KEY_SIZE_BYTES = 32
  private val secureRandom = SecureRandom()

  /**
   * Generates a cryptographic key as a random sequence of bytes.
   *
   * @return A byte array of length equal to `KEY_SIZE_BYTES`, securely generated using a
   *         cryptographically strong random number generator.
   */
  fun create(): ByteArray =
    ByteArray(KEY_SIZE_BYTES).also(secureRandom::nextBytes)

  /**
   * Encodes the provided cryptographic key into a Base64 URL-safe string.
   *
   * @param key The cryptographic key to encode. It must be a byte array of length `KEY_SIZE_BYTES`.
   * @return The Base64 URL-safe encoded string representation of the key.
   * @throws InvalidKeyException If the length of the provided key is not equal to `KEY_SIZE_BYTES`.
   */
  fun encode(key: ByteArray): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(requireValid(key))
  }

  /**
   * Notarizes a given message using the HMAC-SHA-256 algorithm and a specified key.
   *
   * @param key The secret key used for HMAC signing. Must be a valid key with the required size.
   * @param message The message to be authenticated.
   * @return The HMAC-SHA-256 hexadecimal string of the authenticated message.
   */
  fun notarizeMessage(key: ByteArray, message: String): String {
    val stringToBytes: ByteArray = message.toByteArray(charset=Charsets.UTF_8)

    return HmacUtils(HmacAlgorithms.HMAC_SHA_256, requireValid(key)).hmacHex(stringToBytes)
  }

  /**
   * Verifies if a given post's signature is authentic by comparing it to a computed HMAC signature.
   *
   * @param signature The HMAC-SHA-256 hexadecimal string representing the signed data to verify.
   * @param body The content of the post to be authenticated.
   * @param key The secret key used for HMAC signing, provided as a byte array.
   * @return `true` if the provided signature matches the computed signature; `false` otherwise.
   */
  fun isPostAuthentic(signature: String, body: String, key: ByteArray): Boolean {
    val receivedSignature = try {
      signature.hexToByteArray()
    }
    catch (_: IllegalArgumentException) {
      return false
    }

    if (receivedSignature.size != KEY_SIZE_BYTES) {
      return false
    }

    val expectedSignature = notarizeMessage(key, body).hexToByteArray()

    return MessageDigest.isEqual(
      expectedSignature,
      receivedSignature
    )
  }

  /**
   * Decodes a Base64 URL-safe encoded string back into its corresponding cryptographic key.
   *
   * @param encodedKey The Base64 URL-safe encoded string representation of the cryptographic key.
   *                   It is expected to decode into a byte array of length `KEY_SIZE_BYTES`.
   * @return A byte array representing the decoded cryptographic key.
   * @throws IllegalArgumentException If the provided string is not a valid Base64 URL-safe encoded string.
   * @throws InvalidKeyException If the decoded key does not have a length of `KEY_SIZE_BYTES`.
   */
  fun decode(encodedKey: String): ByteArray {
    val decodedKey = Base64.getUrlDecoder().decode(encodedKey)

    return requireValid(decodedKey)
  }

  fun requireValid(key: ByteArray): ByteArray {
    if (key.size != KEY_SIZE_BYTES) {
      throw ErrorTypes.KEY_SIZE_MISMATCH.createException(
        KEY_SIZE_BYTES,
        key.size
      )
    }

    return key
  }

  fun requireValid(encodedKey: String): String {
    val decodedKey = Base64.getUrlDecoder().decode(encodedKey)

    try {
      requireValid(decodedKey)
    } catch (e: InvalidKeyException) {
      throw e
    }
    return encodedKey
  }
}
