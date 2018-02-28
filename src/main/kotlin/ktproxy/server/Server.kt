package ktproxy.server

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import ktproxy.connection.ConnectionException
import ktproxy.connection.ServerConnection
import ktproxy.connection.ServerPool
import ktproxy.frame.FrameException
import ktproxy.socks.Socks
import ktproxy.socks.SocksException
import resocks.encrypt.Cipher
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class Server(
        listenAddr: String?,
        listenPort: Int,
        password: String
) {
    private val key = Cipher.password2key(password)

    private val pool = ServerPool(listenAddr, listenPort, key)

    suspend fun start() {
        pool.init()
        while (true) {
            val connection = pool.getConn()

            async {
                handle(connection)
            }
        }
    }

    private suspend fun handle(connection: ServerConnection) {
        val targetAddress = try {
            connection.read()
        } catch (e: FrameException) {
            connection.close()
            return
        }

        if (targetAddress == null) {
            connection.close()
            return
        }

        val socksInfo = try {
            Socks.build(targetAddress)
        } catch (e: SocksException) {
            connection.close()
            return
        }

        val socketChannel = AsynchronousSocketChannel.open()
        try {
            socketChannel.aConnect(InetSocketAddress(InetAddress.getByAddress(socksInfo.addr), socksInfo.port))
        } catch (e: IOException) {
            socketChannel.close()
            connection.close()
            return
        }


        var canRelease = 0
        async {
            loop@ while (true) {
                when (canRelease) {
                    2 -> {
                        pool.putConn(connection)
                        break@loop
                    }

                    -1 -> break@loop

                    else -> delay(100)
                }
            }
        }


        // proxy -> server
        async {
            while (true) {
                val data = try {
                    connection.read()
                } catch (e: FrameException) {
                    socketChannel.close()
                    connection.close()
                    canRelease = -1
                    return@async
                } catch (e: ConnectionException) {
                    socketChannel.close()
                    canRelease++
                    return@async
                }

                if (data == null) {
                    socketChannel.shutdownOutput()
                    connection.shutdownInput()
                    canRelease++
                    return@async
                }

                try {
                    socketChannel.aWrite(ByteBuffer.wrap(data))
                } catch (e: IOException) {
                    socketChannel.close()
                    connection.shutdownInput()
                    canRelease++
                    return@async
                }
            }
        }

        // server -> proxy
        async {
            val buffer = ByteBuffer.allocate(8192)
            while (true) {
                try {
                    if (socketChannel.aRead(buffer) <= 0) {
                        socketChannel.shutdownInput()
                        connection.shutdownOutput()
                        canRelease++
                        return@async
                    }
                } catch (e: IOException) {
                    socketChannel.close()
                    connection.shutdownOutput()
                    canRelease++
                    return@async
                }

                buffer.flip()
                val data = ByteArray(buffer.limit())
                buffer.get(data)
                buffer.clear()

                try {
                    connection.write(data)
                } catch (e: IOException) {
                    socketChannel.close()
                    connection.close()
                    canRelease = -1
                    return@async
                } catch (e: ConnectionException) {
                    socketChannel.close()
                    canRelease++
                    return@async
                }
            }
        }
    }
}