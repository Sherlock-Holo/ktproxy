package ktproxy.test

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
//    private val proxySocketChannel = AsynchronousServerSocketChannel.open()

    private val pool = ServerPool(listenAddr, listenPort, key)

    /*init {
        if (listenAddr == null) proxySocketChannel.bind(InetSocketAddress(listenPort))
        else proxySocketChannel.bind(InetSocketAddress(listenAddr, listenPort))
    }*/

    suspend fun start() {
        pool.init()
        while (true) {
//            val socketChannel = proxySocketChannel.aAccept()

            val connection = pool.getConn()

            async {
                handle(connection)
            }
        }
    }

    private suspend fun handle(connection: ServerConnection) {
        /*try {
            connection.init()
        } catch (e: IOException) {
            connection.close()
            return
        } catch (e: FrameException) {
            connection.close()
            return
        }*/

        val targetAddress = try {
            connection.read()
        } catch (e: FrameException) {
            connection.errorClose()
            return
        }

        if (targetAddress == null) {
            try {
                connection.shutdownOutput()
            } finally {
                connection.errorClose()
                return
            }
        }

        val socksInfo = try {
            Socks.build(targetAddress!!)
        } catch (e: SocksException) {
            connection.errorClose()
            return
        }

        val socketChannel = AsynchronousSocketChannel.open()
        try {
            socketChannel.aConnect(InetSocketAddress(InetAddress.getByAddress(socksInfo.addr), socksInfo.port))
        } catch (e: IOException) {
            e.printStackTrace()
            socketChannel.close()
            connection.errorClose()
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
//                    e.printStackTrace()
                    socketChannel.close()
                    connection.errorClose()
                    canRelease = -1
                    return@async
                } catch (e: IOException) {
//                    e.printStackTrace()
                    socketChannel.close()
                    connection.errorClose()
                    canRelease = -1
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
//                    e.printStackTrace()
                    socketChannel.close()
                    connection.errorClose()
                    canRelease = -1
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
//                    e.printStackTrace()
                    socketChannel.close()
                    connection.errorClose()
                    canRelease = -1
                    return@async
                }

                buffer.flip()
                val data = ByteArray(buffer.limit())
                buffer.get(data)
                buffer.clear()

                try {
                    connection.write(data)
                } catch (e: IOException) {
//                    e.printStackTrace()
                    socketChannel.close()
                    connection.errorClose()
                    canRelease = -1
                    return@async
                } catch (e: ConnectionException) {
//                    e.printStackTrace()
                    socketChannel.close()
                    connection.errorClose()
                    canRelease = -1
                    return@async
                }
            }
        }
    }
}