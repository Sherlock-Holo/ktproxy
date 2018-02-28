package ktproxy.connection

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aWrite
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel

class ServerPool(private val proxyAddr: String?, private val proxyPort: Int, private val key: ByteArray) {
    private val pool = LinkedListChannel<ServerConnection>()

    private var reuseTime = 0

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

    fun putConn(connection: ServerConnection): Boolean {
        reuseTime++
        return pool.offer(connection)
    }

    suspend fun startCheckReuse(port: Int) {
        async {
            val serverSocketChannel = AsynchronousServerSocketChannel.open()
            serverSocketChannel.bind(InetSocketAddress("127.0.0.1", port))
            while (true) {
                val telnet = serverSocketChannel.aAccept()
                async {
                    telnet.aWrite(ByteBuffer.wrap("reuse: $reuseTime".toByteArray()))
                    telnet.close()
                }
            }
        }
    }
}