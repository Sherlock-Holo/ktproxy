package ktproxy.connection

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aWrite
import ktproxy.websocket.frame.FrameException
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel

class ClientPool(private val proxyAddr: String, private val proxyPort: Int, private val key: ByteArray, val poolCapacity: Int) {
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
        var connection: ClientConnection?

        while (true) {
            connection = getConn0()
            if (connection == null) break
            else {
                try {
                    connection.reset()
                    return connection
                } catch (e: FrameException) {
                } catch (e: IOException) {
                }
            }

        }

        connection = ClientConnection(proxyAddr, proxyPort, key)
        connection.init()
        return connection
    }

    private suspend fun getConn0(): ClientConnection? {
        lock.receive()
        return try {
            if (!pool.isEmpty()) pool.removeAt(0)
            else null

        } finally {
            lock.offer(2018)
        }
    }

    fun putConn(connection: ClientConnection) {
        async {
            if (poolSize <= poolCapacity) {
                reuseTime++
                poolSize++
                
                pool.add(connection)

            } else {
                connection.close()
            }
        }
    }

    suspend fun startCheckReuse(port: Int?) {
        if (port != null) {
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
}