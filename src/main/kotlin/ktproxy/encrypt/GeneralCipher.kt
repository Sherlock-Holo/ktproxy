package resocks.encrypt

import java.nio.ByteBuffer

interface GeneralCipher {
    // maybe some encrypt module doesn't use IV or Nonce
    fun getIVorNonce(): ByteArray? = null

    fun encrypt(plainText: ByteArray): ByteArray

    fun decrypt(cipherText: ByteArray): ByteArray

    fun encrypt(plainBuffer: ByteBuffer, cipherBuffer: ByteBuffer)

    fun decrypt(cipherBuffer: ByteBuffer, plainBuffer: ByteBuffer)

    fun finish()
}