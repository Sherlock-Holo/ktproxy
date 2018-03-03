package ktproxy.buffer

import kotlinx.coroutines.experimental.nio.aRead
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class CoroutineBuffer(capacity: Int = 8192) {
    private val innerBuffer = ByteBuffer.allocate(capacity)
    private var bufferContentLength = 0

    suspend fun readn0(socketChannel: AsynchronousSocketChannel, length: Int): ByteArray {
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

    suspend fun readLine0(socketChannel: AsynchronousSocketChannel): String {
        val sb = StringBuilder()
        while (true) {
            val char = readn0(socketChannel, 1)[0].toChar()
            sb.append(char)
            if (char == '\n') return sb.toString()
        }
    }
}