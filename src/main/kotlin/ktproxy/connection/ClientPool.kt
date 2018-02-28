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

    var poolSize = 0
        private set

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
                poolSize--
                return connection
            }
        } finally {
            lock.offer(2018)
        }

        val connection = ClientConnection(proxyAddr, proxyPort, key)
        connection.init()
        return connection
    }

    suspend fun putConn(connection: ClientConnection) {
        async {
            if (poolSize <= 30) {
                try {
                    connection.destroy(false)
                } catch (e: IOException) {
                    connection.close()
                    return@async
                }
                reuseTime++
                poolSize++
                pool.add(connection)

            } else {
                try {
                    connection.destroy(true)
                } finally {
                    connection.close()
                }
            }
        }
    }

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