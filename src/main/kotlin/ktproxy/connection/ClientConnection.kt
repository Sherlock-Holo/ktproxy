package ktproxy.connection

import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aWrite
import ktproxy.coroutineBuffer.CoroutineReadBuffer
import ktproxy.websocket.frame.Frame
import ktproxy.websocket.frame.FrameContentType
import ktproxy.websocket.frame.FrameException
import ktproxy.websocket.frame.FrameType
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

    private lateinit var proxySocketChannel: AsynchronousSocketChannel
    private lateinit var encryptCipher: Cipher
    private lateinit var decryptCipher: Cipher

    private lateinit var readBuffer: CoroutineReadBuffer

    private var input = true
    private var output = true

    private var readFin = false

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
            val frame = Frame.buildFrame(readBuffer, FrameType.SERVER)

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
        proxySocketChannel = AsynchronousSocketChannel.open()
        proxySocketChannel.aConnect(InetSocketAddress(addr, port))

        readBuffer = CoroutineReadBuffer(proxySocketChannel)

        proxySocketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
        proxySocketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true)

        initIV()
    }

    suspend fun reset() {
        while (!readFin) {
            val frame = Frame.buildFrame(readBuffer, FrameType.SERVER)
            if (frame.contentType == FrameContentType.TEXT) {
//                decryptCipher.decrypt(frame.content)
                readFin = true
            }
        }

        initIV()

        readFin = false
        input = true
        output = true
    }

    fun close() {
        input = false
        output = false
        proxySocketChannel.close()
    }

    suspend fun shutdownOutput() {
        output = false

        val cipher = encryptCipher.encrypt("fin".toByteArray())
        val frame = Frame(FrameType.CLIENT, FrameContentType.TEXT, cipher)
        proxySocketChannel.aWrite(ByteBuffer.wrap(frame.frameByteArray))
    }

    fun shutdownInput() {
        input = false
    }

    /*@Deprecated("will not use destroy")
    suspend fun destroy(destroyIt: Boolean) {
        val cipher = encryptCipher.encrypt("destroy".toByteArray())

        val frame =
                if (destroyIt) Frame(FrameType.CLIENT, FrameContentType.PING, cipher)
                else Frame(FrameType.CLIENT, FrameContentType.PONG, cipher)

        proxySocketChannel.aWrite(ByteBuffer.wrap(frame.frameByteArray))
    }*/

    private suspend fun initIV() {
        encryptCipher = Cipher(CipherModes.AES_256_CTR, key)
        val iv = encryptCipher.IVorNonce!!
        val encryptIVFrame = Frame(FrameType.CLIENT, FrameContentType.BINARY, iv)
        proxySocketChannel.aWrite(ByteBuffer.wrap(encryptIVFrame.frameByteArray))

        val decryptIVFrame = Frame.buildFrame(readBuffer, FrameType.SERVER)
        val decryptIV = decryptIVFrame.content
        decryptCipher = Cipher(CipherModes.AES_256_CTR, key, decryptIV)
    }
}