package ktproxy.coroutineBuffer

abstract class CoroutineBuffer {
    abstract suspend fun readExactly(length: Int): ByteArray

    abstract suspend fun readLine(): String

    abstract suspend fun write(data: ByteArray): Int

    abstract suspend fun writeline(line: String): Int
}