package ktproxy.coroutineBuffer

import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class CoroutineWriteBuffer(
        private val socketChannel: AsynchronousSocketChannel,
        capacity: Int = 8192,
        direct: Boolean = false
) : CoroutineBuffer() {

    private val innerBuffer =
            if (!direct) ByteBuffer.allocate(capacity)
            else ByteBuffer.allocateDirect(capacity)

    override suspend fun read(length: Int): ByteArray {
        throw OnlyWritable("buffer is only writable")
    }

    override suspend fun readLine(): String {
        throw OnlyWritable("buffer is only writable")
    }

    override suspend fun write(data: ByteArray): Int {
        innerBuffer.clear()
        innerBuffer.put(data)
        innerBuffer.flip()
        return socketChannel.aWrite(innerBuffer)
    }

    suspend fun writeShort(n: Short): Int {
        val data = ByteArray(2)
        ByteBuffer.wrap(data).putShort(n)
        return write(data)
    }

    suspend fun writeInt(n: Int): Int {
        val data = ByteArray(4)
        ByteBuffer.wrap(data).putInt(n)
        return write(data)
    }

    suspend fun writeLong(n: Long): Int {
        val data = ByteArray(8)
        ByteBuffer.wrap(data).putLong(n)
        return write(data)
    }

    override suspend fun writeline(line: String) = write(line.toByteArray())
}