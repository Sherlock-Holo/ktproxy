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

class ClientConnection(
        private val addr: String,
        private val port: Int,
        private val key: ByteArray
) : Connection {
    private val readBuffer = ByteBuffer.allocate(8192)

    private var isClosed = false

    private lateinit var proxySocketChannel: AsynchronousSocketChannel
    private lateinit var encryptCipher: Cipher
    private lateinit var decryptCipher: Cipher

    @Throws(ConnectionException::class, IOException::class)
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
        val plain = decryptCipher.decrypt(frame.content)
        if (plain.contentEquals("fin".toByteArray())) {
            isClosed = true
            throw ConnectionException("connection is closed")
        }

        return plain
    }

    @Throws(IOException::class, FrameException::class)
    suspend fun init() {
        proxySocketChannel = AsynchronousSocketChannel.open()
        proxySocketChannel.aConnect(InetSocketAddress(addr, port))

        encryptCipher = Cipher(CipherModes.AES_256_CTR, key)
        val iv = encryptCipher.IVorNonce!!
        val encryptIVFrame = Frame(FrameType.CLIENT, FrameContentType.BINARY, iv)
        proxySocketChannel.aWrite(ByteBuffer.wrap(encryptIVFrame.frameByteArray))
        println("send encrypt iv")

        val decryptIVFrame = Frame.buildFrame(proxySocketChannel, readBuffer, FrameType.SERVER)
        println("get decrypt iv")
        val decryptIV = decryptIVFrame.content
        decryptCipher = Cipher(CipherModes.AES_256_CTR, key, decryptIV)
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