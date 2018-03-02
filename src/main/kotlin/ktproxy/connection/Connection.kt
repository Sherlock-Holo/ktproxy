package ktproxy.connection

interface Connection {
    suspend fun write(data: ByteArray): Int

    suspend fun read(): ByteArray?
}