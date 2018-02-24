package resocks.encrypt

import java.nio.ByteBuffer
import java.security.MessageDigest

class Cipher(cipherMode: CipherModes, key: ByteArray, iv: ByteArray? = null) {
    private val inner: GeneralCipher
    val IVorNonce: ByteArray?

    init {
        when (cipherMode) {
            CipherModes.AES_256_CTR -> {
                inner = AES_256_CTR(key, iv)
                IVorNonce = inner.getIVorNonce()
            }
        }
    }

    fun encrypt(plainText: ByteArray) = inner.encrypt(plainText)

    fun encrypt(plainBuffer: ByteBuffer, cipherBuffer: ByteBuffer) = inner.encrypt(plainBuffer, cipherBuffer)

    fun decrypt(cipherText: ByteArray) = inner.decrypt(cipherText)

    fun decrypt(cipherBuffer: ByteBuffer, plainBuffer: ByteBuffer) = inner.decrypt(cipherBuffer, plainBuffer)

    fun finish() = inner.finish()

//    fun getIVorNonce() = inner.getIVorNonce()

    companion object {
        fun password2key(passwd: String): ByteArray {
            var keyGen = MessageDigest.getInstance("MD5")
            keyGen.update(passwd.toByteArray())
            var encodeKey = keyGen.digest()
            keyGen = MessageDigest.getInstance("MD5")
            keyGen.update(encodeKey + passwd.toByteArray())
            encodeKey += keyGen.digest()
            return encodeKey
        }
    }
}