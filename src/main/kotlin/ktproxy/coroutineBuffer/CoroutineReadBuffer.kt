package ktproxy.coroutineBuffer

import kotlinx.coroutines.experimental.nio.aRead
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class CoroutineReadBuffer(
        private val socketChannel: AsynchronousSocketChannel,
        capacity: Int = 8192,
        direct: Boolean = false
) : CoroutineBuffer() {

    val innerBuffer =
            if (!direct) ByteBuffer.allocate(capacity)
            else ByteBuffer.allocateDirect(capacity)

    private var bufferContentLength = 0

    private val sb = StringBuilder()

    private var readFin = false

    override suspend fun read(length: Int): ByteArray? {
        if (readFin) throw IOException("already read fin")

        while (bufferContentLength < length) {
            val readLength = socketChannel.aRead(innerBuffer)
            if (readLength > 0) {
                bufferContentLength += readLength

            } else {
                readFin = true
                return null
            }
        }

        val byteArray = ByteArray(length)
        innerBuffer.flip()
        innerBuffer.get(byteArray)
        innerBuffer.compact()
        bufferContentLength -= length
        return byteArray
    }

    suspend fun read(): Byte? {
        val data = read(1) ?: return null
        return data[0]
    }

    override suspend fun readLine(): String? {
        sb.delete(0, sb.length)

        while (true) {
            val data = read(1) ?: return if (sb.isEmpty()) null else sb.toString()
            val char = data[0].toChar()

            sb.append(char)
            if (char == '\n') return sb.toString()
        }
    }

    suspend fun readShort(): Short? {
        val data = read(2) ?: return null

        return ByteBuffer.wrap(data).short
    }

    suspend fun readInt(): Int? {
        val data = read(4) ?: return null

        return ByteBuffer.wrap(data).int
    }

    suspend fun readLong(): Long? {
        val data = read(8) ?: return null

        return ByteBuffer.wrap(data).long
    }

    override suspend fun write(data: ByteArray): Int {
        throw OnlyReadable("buffer is only readable")
    }

    override suspend fun writeline(line: String): Int {
        throw OnlyReadable("buffer is only readable")
    }
}