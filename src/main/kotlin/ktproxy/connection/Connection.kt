package ktproxy.connection

interface Connection {
    suspend fun write(data: ByteArray)

    suspend fun read(): ByteArray
}