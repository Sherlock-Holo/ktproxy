package ktproxy.connection

import kotlinx.coroutines.experimental.nio.aWrite
import ktproxy.frame.Frame
import ktproxy.frame.FrameContentType
import ktproxy.frame.FrameException
import ktproxy.frame.FrameType
import resocks.encrypt.Cipher
import resocks.encrypt.CipherModes
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ServerConnection(
        private val proxySocketChannel: AsynchronousSocketChannel,
        private val key: ByteArray
) : Connection {
    private val readBuffer = ByteBuffer.allocate(8192)

    private lateinit var encryptCipher: Cipher
    private lateinit var decryptCipher: Cipher

    /*shutdownInput is 1, shutdownOutput is 2, close is 3*/
    var shutdownStatus = 0

    @Throws(IOException::class, ConnectionException::class)
    override suspend fun write(data: ByteArray) {
        when (shutdownStatus) {
            2 -> throw ConnectionException("connection can't write again")

            3 -> throw ConnectionException("connection is closed")

            else -> {
                val cipher = encryptCipher.encrypt(data)
                val frame = Frame(FrameType.SERVER, FrameContentType.BINARY, cipher)
                proxySocketChannel.aWrite(ByteBuffer.wrap(frame.frameByteArray))
            }
        }
    }

    @Throws(FrameException::class, ConnectionException::class)
    override suspend fun read(): ByteArray? {
        when (shutdownStatus) {
            1 -> throw ConnectionException("connection can't read again")

            3 -> throw ConnectionException("connection is closed")

            else -> {
                val frame = Frame.buildFrame(proxySocketChannel, readBuffer, FrameType.CLIENT)
                val plain = decryptCipher.decrypt(frame.content)
                return when {
                    plain.contentEquals("fin".toByteArray()) -> null

                    plain.contentEquals("rst".toByteArray()) -> throw ConnectionException("connection reset by peer")

                    else -> plain
                }

            }
        }

    }

    @Throws(IOException::class, FrameException::class)
    suspend fun init() {
        encryptCipher = Cipher(CipherModes.AES_256_CTR, key)
        val encryptIV = encryptCipher.IVorNonce!!
        val encryptIVFrame = Frame(FrameType.SERVER, FrameContentType.BINARY, encryptIV)
        proxySocketChannel.aWrite(ByteBuffer.wrap(encryptIVFrame.frameByteArray))

        val decryptIVFrame = Frame.buildFrame(proxySocketChannel, readBuffer, FrameType.CLIENT)
        val decryptIV = decryptIVFrame.content
        decryptCipher = Cipher(CipherModes.AES_256_CTR, key, decryptIV)
    }

    private fun close() {
        shutdownStatus = 3
        proxySocketChannel.close()
    }

    suspend fun shutdownOutput() {
        when (shutdownStatus) {
            2 -> return

            3 -> {
//                proxySocketChannel.close()
            }

            else -> {
                try {
                    write("fin".toByteArray())
                } finally {
                    shutdownStatus += 2
                }
            }
        }
    }

    fun shutdownInput() {
        when (shutdownStatus) {
            1 -> return

            3 -> {
//                proxySocketChannel.close()
            }

            else -> {
                shutdownStatus += 1
            }
        }
    }

    suspend fun errorClose() {
        try {
            write("rst".toByteArray())
        } finally {
            close()
        }
    }
}