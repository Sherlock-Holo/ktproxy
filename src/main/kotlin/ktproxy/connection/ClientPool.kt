package ktproxy.connection

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aWrite
import ktproxy.frame.FrameException
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel

class ClientPool(private val proxyAddr: String, private val proxyPort: Int, private val key: ByteArray) {
    private val lock = LinkedListChannel<Int>()
    private val pool = ArrayList<ClientConnection>()

    private var reuseTime = 0

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
                reuseTime++
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

    suspend fun startCheckReuse(port: Int) {
        async {
            val serverSocketChannel = AsynchronousServerSocketChannel.open()
            serverSocketChannel.bind(InetSocketAddress("127.0.0.1", port))
            while (true) {
                val telnet = serverSocketChannel.aAccept()
                async {
                    telnet.aWrite(ByteBuffer.wrap("reuse: $reuseTime\n".toByteArray()))
                    telnet.close()
                }
            }
        }
    }
}