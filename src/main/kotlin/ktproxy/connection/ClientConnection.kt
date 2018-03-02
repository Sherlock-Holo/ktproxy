package ktproxy.connection

import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aWrite
import ktproxy.frame.Frame
import ktproxy.frame.FrameContentType
import ktproxy.frame.FrameException
import ktproxy.frame.FrameType
import resocks.encrypt.Cipher
import resocks.encrypt.CipherModes
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ClientConnection(
        private val addr: String,
        private val port: Int,
        private val key: ByteArray
) : Connection {
    private val readBuffer = ByteBuffer.allocate(8192)

    private lateinit var proxySocketChannel: AsynchronousSocketChannel
    private lateinit var encryptCipher: Cipher
    private lateinit var decryptCipher: Cipher

    private var input = true
    private var output = true

    @Throws(IOException::class)
    override suspend fun write(data: ByteArray): Int {
        return if (!input) -1
        else {
            val cipher = encryptCipher.encrypt(data)
            val frame = Frame(FrameType.CLIENT, FrameContentType.BINARY, cipher)
            proxySocketChannel.aWrite(ByteBuffer.wrap(frame.frameByteArray))
        }
    }

    @Throws(FrameException::class, ConnectionException::class)
    override suspend fun read(): ByteArray? {
        if (!input) throw ConnectionException("connection can't read again")
        else {
            val frame = Frame.buildFrame(proxySocketChannel, readBuffer, FrameType.SERVER)

            return when (frame.contentType) {
                FrameContentType.TEXT -> null

                else -> decryptCipher.decrypt(frame.content)
            }
        }
    }

    @Throws(IOException::class, FrameException::class)
    suspend fun init() {
        proxySocketChannel = AsynchronousSocketChannel.open()
        proxySocketChannel.aConnect(InetSocketAddress(addr, port))

        proxySocketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
        proxySocketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true)

        encryptCipher = Cipher(CipherModes.AES_256_CTR, key)
        val iv = encryptCipher.IVorNonce!!
        val encryptIVFrame = Frame(FrameType.CLIENT, FrameContentType.BINARY, iv)
        proxySocketChannel.aWrite(ByteBuffer.wrap(encryptIVFrame.frameByteArray))

        val decryptIVFrame = Frame.buildFrame(proxySocketChannel, readBuffer, FrameType.SERVER)
        val decryptIV = decryptIVFrame.content
        decryptCipher = Cipher(CipherModes.AES_256_CTR, key, decryptIV)
    }

    fun reset() {
        input = true
        output = true
    }

    fun close() {
        input = false
        output = false
        proxySocketChannel.close()
    }

    suspend fun shutdownOutput() {
        /*try {
            val cipher = encryptCipher.encrypt("fin".toByteArray())
            val frame = Frame(FrameType.CLIENT, FrameContentType.TEXT, cipher)
            proxySocketChannel.aWrite(ByteBuffer.wrap(frame.frameByteArray))
        } finally {
            output = false
        }*/
        output = false

        val cipher = encryptCipher.encrypt("fin".toByteArray())
        val frame = Frame(FrameType.CLIENT, FrameContentType.TEXT, cipher)
        proxySocketChannel.aWrite(ByteBuffer.wrap(frame.frameByteArray))
    }

    fun shutdownInput() {
        input = false
    }

    @Deprecated("will not use destroy")
    suspend fun destroy(destroyIt: Boolean) {
        val cipher = encryptCipher.encrypt("destroy".toByteArray())

        val frame =
                if (destroyIt) Frame(FrameType.CLIENT, FrameContentType.PING, cipher)
                else Frame(FrameType.CLIENT, FrameContentType.PONG, cipher)

        proxySocketChannel.aWrite(ByteBuffer.wrap(frame.frameByteArray))
    }
}