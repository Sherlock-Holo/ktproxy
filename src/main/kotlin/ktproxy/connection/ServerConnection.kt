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

    private var isClosed = false

    private lateinit var encryptCipher: Cipher
    private lateinit var decryptCipher: Cipher

    @Throws(IOException::class, ConnectionException::class)
    override suspend fun write(data: ByteArray) {
        if (isClosed) throw ConnectionException("connection is closed")

        val cipher = encryptCipher.encrypt(data)
        val frame = Frame(FrameType.SERVER, FrameContentType.BINARY, cipher)
        proxySocketChannel.aWrite(ByteBuffer.wrap(frame.frameByteArray))
    }

    @Throws(FrameException::class, ConnectionException::class)
    override suspend fun read(): ByteArray {
        if (isClosed) throw ConnectionException("connection is closed")

        val frame = Frame.buildFrame(proxySocketChannel, readBuffer, FrameType.CLIENT)
        val plain = decryptCipher.decrypt(frame.content)
        if (plain.contentEquals("fin".toByteArray())) {
            isClosed = true
            throw ConnectionException("connection is closed")
        }

        return plain
    }

    @Throws(IOException::class, FrameException::class)
    suspend fun init() {
        encryptCipher = Cipher(CipherModes.AES_256_CTR, key)
        val iv = encryptCipher.IVorNonce!!
        val encryptIVFrame = Frame(FrameType.CLIENT, FrameContentType.BINARY, iv)
        proxySocketChannel.aWrite(ByteBuffer.wrap(encryptIVFrame.frameByteArray))

        val frame = Frame.buildFrame(proxySocketChannel, readBuffer, FrameType.SERVER)
        val decryptIVFrame = frame.content
        decryptCipher = Cipher(CipherModes.AES_256_CTR, key, decryptIVFrame)
    }

    suspend fun close() {
        if (!isClosed) {
            try {
                write("fin".toByteArray())
            } finally {
                proxySocketChannel.close()
                isClosed = true
            }

        } else return
    }
}