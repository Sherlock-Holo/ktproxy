package ktproxy.coroutineBuffer

abstract class CoroutineBuffer {
    abstract suspend fun read(length: Int): ByteArray?

    abstract suspend fun readLine(): String?

    abstract suspend fun write(data: ByteArray): Int

    abstract suspend fun writeline(line: String): Int
}