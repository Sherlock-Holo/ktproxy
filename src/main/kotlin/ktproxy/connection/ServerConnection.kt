package ktproxy.connection

import kotlinx.coroutines.experimental.nio.aWrite
import ktproxy.frame.Frame
import ktproxy.frame.FrameContentType
import ktproxy.frame.FrameException
import ktproxy.frame.FrameType
import resocks.encrypt.Cipher
import resocks.encrypt.CipherModes
import java.io.IOException
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ServerConnection(
        private val proxySocketChannel: AsynchronousSocketChannel,
        private val key: ByteArray
) : Connection {
    init {
        proxySocketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
        proxySocketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
    }

    private val readBuffer = ByteBuffer.allocate(8192)

    private lateinit var encryptCipher: Cipher
    private lateinit var decryptCipher: Cipher

    private var input = true
    private var output = true

    private var readFin = false

    @Throws(IOException::class, ConnectionException::class)
    override suspend fun write(data: ByteArray): Int {
        return if (!output) -1
        else {
            val cipher = encryptCipher.encrypt(data)
            val frame = Frame(FrameType.SERVER, FrameContentType.BINARY, cipher)
            proxySocketChannel.aWrite(ByteBuffer.wrap(frame.frameByteArray))
        }
    }

    @Throws(FrameException::class, ConnectionException::class)
    override suspend fun read(): ByteArray? {
        if (!input) throw ConnectionException("connection can't read again")
        else {
            val frame = Frame.buildFrame(proxySocketChannel, readBuffer, FrameType.CLIENT)

            return when (frame.contentType) {
                FrameContentType.TEXT -> {
                    readFin = true
                    null
                }

                else -> decryptCipher.decrypt(frame.content)
            }
        }

    }

    @Throws(IOException::class, FrameException::class)
    suspend fun init() {
        initIV()
    }

    suspend fun reset() {
        while (!readFin) {
            val frame = Frame.buildFrame(proxySocketChannel, readBuffer, FrameType.CLIENT)
            if (frame.contentType == FrameContentType.TEXT) {
                decryptCipher.decrypt(frame.content)
                readFin = true
            }
        }

        readFin = false
        input = true
        output = true

        initIV()
    }

    fun close() {
        input = false
        output = false
        proxySocketChannel.close()
    }

    suspend fun shutdownOutput() {
        try {
            val cipher = encryptCipher.encrypt("fin".toByteArray())
            val frame = Frame(FrameType.SERVER, FrameContentType.TEXT, cipher)
            proxySocketChannel.aWrite(ByteBuffer.wrap(frame.frameByteArray))
        } finally {
            output = false
        }
    }

    fun shutdownInput() {
        input = false
    }

    @Deprecated("will not use destroy")
    suspend fun destroy(): Boolean {
        val frame = Frame.buildFrame(proxySocketChannel, readBuffer, FrameType.CLIENT)
        val plain = decryptCipher.decrypt(frame.content)
        return when {
            plain.contentEquals("destroy".toByteArray()) -> true

            else -> false
        }
    }

    private suspend fun initIV() {
        encryptCipher = Cipher(CipherModes.AES_256_CTR, key)
        val encryptIV = encryptCipher.IVorNonce!!
        val encryptIVFrame = Frame(FrameType.SERVER, FrameContentType.BINARY, encryptIV)
        proxySocketChannel.aWrite(ByteBuffer.wrap(encryptIVFrame.frameByteArray))

        val decryptIVFrame = Frame.buildFrame(proxySocketChannel, readBuffer, FrameType.CLIENT)
        val decryptIV = decryptIVFrame.content
        decryptCipher = Cipher(CipherModes.AES_256_CTR, key, decryptIV)
    }
}