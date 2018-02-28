package ktproxy.connection

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.nio.aAccept
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel

class ServerPool(private val proxyAddr: String?, private val proxyPort: Int, private val key: ByteArray) {
    private val pool = LinkedListChannel<ServerConnection>()

    suspend fun init() {
        val serverSocketChannel = AsynchronousServerSocketChannel.open()

        if (proxyAddr != null) serverSocketChannel.bind(InetSocketAddress(proxyAddr, proxyPort))
        else serverSocketChannel.bind(InetSocketAddress(proxyPort))

        async {
            while (true) {
                val socketChannel = serverSocketChannel.aAccept()
                val connection = ServerConnection(socketChannel, key)
                try {
                    connection.init()
                    pool.offer(connection)
                } finally {
                }
            }
        }
    }

    suspend fun getConn(): ServerConnection {
        val connection = pool.receive()
        connection.shutdownStatus = 0
        return connection
    }

    fun putConn(connection: ServerConnection) = pool.offer(connection)
}