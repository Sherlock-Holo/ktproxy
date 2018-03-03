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

    override suspend fun readExactly(length: Int): ByteArray {
        while (bufferContentLength < length) {
            val readLength = socketChannel.aRead(innerBuffer)
            if (readLength > 0) bufferContentLength += readLength
            else {
                throw IOException("unexpected end of stream")
            }
        }

        val byteArray = ByteArray(length)
        innerBuffer.flip()
        innerBuffer.get(byteArray)
        innerBuffer.compact()
        bufferContentLength -= length
        return byteArray
    }

    override suspend fun readLine(): String {
        val sb = StringBuilder()
        while (true) {
            val char = readExactly(1)[0].toChar()
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