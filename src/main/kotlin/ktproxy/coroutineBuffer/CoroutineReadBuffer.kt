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

    private val innerBuffer =
            if (!direct) ByteBuffer.allocate(capacity)
            else ByteBuffer.allocateDirect(capacity)

    private var bufferContentLength = 0

    private val sb = StringBuilder()

    private var readFin = false

    override suspend fun readExactly(length: Int): ByteArray? {
        if (readFin) throw IOException("already read fin")

        var haveReadData = false
        while (bufferContentLength < length) {
            val readLength = socketChannel.aRead(innerBuffer)
            if (readLength > 0) {
                bufferContentLength += readLength
                haveReadData = true

            } else {
                readFin = true

                if (haveReadData) break
                else return null
            }
        }

        val byteArray = ByteArray(length)
        innerBuffer.flip()
        innerBuffer.get(byteArray)
        innerBuffer.compact()
        bufferContentLength -= length
        return byteArray
    }

    override suspend fun readLine(): String? {
        sb.delete(0, sb.length)

        while (true) {
            val data = readExactly(1) ?: return if (sb.isEmpty()) null else sb.toString()
            val char = data[0].toChar()

            sb.append(char)
            if (char == '\n') return sb.toString()
        }
    }

    override suspend fun write(data: ByteArray): Int {
        throw OnlyReadable("buffer is only readable")
    }

    override suspend fun writeline(line: String): Int {
        throw OnlyReadable("buffer is only readable")
    }
}