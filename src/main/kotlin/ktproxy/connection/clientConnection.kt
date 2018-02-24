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
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class clientConnection(
        private val addr: String,
        private val port: Int,
        private val key: ByteArray
) : Connection {
    private val readBuffer = ByteBuffer.allocate(8192)

    private var isClosed = false

    private lateinit var proxySocketChannel: AsynchronousSocketChannel
    private lateinit var encryptCipher: Cipher
    private lateinit var decryptCipher: Cipher

    @Throws(IOException::class, ConnectionException::class)
    override suspend fun write(data: ByteArray) {
        if (isClosed) throw ConnectionException("connection is closed")

        val cipher = encryptCipher.encrypt(data)
        val frame = Frame(FrameType.CLIENT, FrameContentType.BINARY, cipher)
        proxySocketChannel.aWrite(ByteBuffer.wrap(frame.frameByteArray))
    }

    @Throws(FrameException::class, ConnectionException::class)
    override suspend fun read(): ByteArray {
        if (isClosed) throw ConnectionException("connection is closed")

        val frame = Frame.buildFrame(proxySocketChannel, readBuffer, FrameType.SERVER)
        return decryptCipher.decrypt(frame.content)
    }

    @Throws(IOException::class, FrameException::class)
    suspend fun init() {
        proxySocketChannel.aConnect(InetSocketAddress(addr, port))

        encryptCipher = Cipher(CipherModes.AES_256_CTR, key)
        val iv = encryptCipher.IVorNonce!!
        val ivFrame = Frame(FrameType.CLIENT, FrameContentType.BINARY, iv)
        proxySocketChannel.aWrite(ByteBuffer.wrap(ivFrame.frameByteArray))

        val frame = Frame.buildFrame(proxySocketChannel, readBuffer, FrameType.SERVER)
        val decryptIV = frame.content
        decryptCipher = Cipher(CipherModes.AES_256_CTR, key, decryptIV)
    }

    fun close() {
        proxySocketChannel.close()
        isClosed = true
    }
}