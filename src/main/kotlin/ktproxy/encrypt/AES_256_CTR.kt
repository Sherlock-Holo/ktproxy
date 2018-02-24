package resocks.encrypt

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class AES_256_CTR(key: ByteArray, private var iv: ByteArray? = null) : GeneralCipher {
    private val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    private val skey = SecretKeySpec(key, "AES")

    init {
        if (iv != null) {
            cipher.init(Cipher.DECRYPT_MODE, skey, IvParameterSpec(iv))
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, skey)
            this.iv = cipher.iv
        }
    }

    override fun encrypt(plainText: ByteArray): ByteArray {
        return cipher.update(plainText)
    }

    override fun decrypt(cipherText: ByteArray): ByteArray {
        return cipher.update(cipherText)
    }

    override fun getIVorNonce(): ByteArray? {
        return iv
    }

    override fun encrypt(plainBuffer: ByteBuffer, cipherBuffer: ByteBuffer) {
        cipher.update(plainBuffer, cipherBuffer)
    }

    override fun decrypt(cipherBuffer: ByteBuffer, plainBuffer: ByteBuffer) {
        cipher.update(cipherBuffer, plainBuffer)
    }

    override fun finish() {
        cipher.doFinal()
    }
}