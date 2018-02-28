package ktproxy.connection

import kotlinx.coroutines.experimental.channels.LinkedListChannel
import ktproxy.frame.FrameException
import java.io.IOException

class ClientPool(private val proxyAddr: String, private val proxyPort: Int, private val key: ByteArray) {
    private val lock = LinkedListChannel<Int>()
    private val pool = ArrayList<ClientConnection>()

    init {
        lock.offer(2018)
    }

    @Throws(IOException::class, FrameException::class)
    suspend fun getConn(): ClientConnection {
        lock.receive()
        try {
            if (!pool.isEmpty()) {
                val connection = pool.removeAt(0)
                connection.shutdownStatus = 0
                return connection
            }
        } finally {
            lock.offer(2018)
        }

        val connection = ClientConnection(proxyAddr, proxyPort, key)
        connection.init()
        return connection
    }

    fun putConn(connection: ClientConnection) = pool.add(connection)
}