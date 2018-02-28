package ktproxy.test

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import ktproxy.connection.ConnectionException
import ktproxy.connection.ServerConnection
import ktproxy.frame.FrameException
import ktproxy.socks.Socks
import ktproxy.socks.SocksException
import resocks.encrypt.Cipher
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel

class Server(
        listenAddr: String? = null,
        listenPort: Int,
        password: String
) {
    private val key = Cipher.password2key(password)
    private val proxySocketChannel = AsynchronousServerSocketChannel.open()

    init {
        if (listenAddr == null) proxySocketChannel.bind(InetSocketAddress(listenPort))
        else proxySocketChannel.bind(InetSocketAddress(listenAddr, listenPort))
    }

    suspend fun start() {
        while (true) {
            val socketChannel = proxySocketChannel.aAccept()
//            println("accept new connection")
            async {
                handle(ServerConnection(socketChannel, key))
            }
        }
    }

    private suspend fun handle(connection: ServerConnection) {
        try {
            connection.init()
        } catch (e: IOException) {
            connection.close()
            return
        } catch (e: FrameException) {
            connection.close()
            return
        }

        val targetAddress = try {
            connection.read()
        } catch (e: FrameException) {
            connection.close()
            return
        }

        if (targetAddress == null) {
            try {
                connection.shutdownOutput()
            } finally {
                connection.close()
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

        // proxy -> server
        async {
            while (true) {
                val data = try {
                    connection.read()
                } catch (e: FrameException) {
//                    e.printStackTrace()
                    socketChannel.close()
                    connection.close()
                    return@async
                } catch (e: IOException) {
//                    e.printStackTrace()
                    socketChannel.close()
                    connection.close()
                    return@async
                }

                try {
                    socketChannel.aWrite(ByteBuffer.wrap(data))
                } catch (e: IOException) {
//                    e.printStackTrace()
                    socketChannel.close()
                    connection.errorClose()
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
                        return@async
                    }
                } catch (e: IOException) {
//                    e.printStackTrace()
                    socketChannel.close()
                    connection.errorClose()
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
                    connection.close()
                    return@async
                } catch (e: ConnectionException) {
//                    e.printStackTrace()
                    socketChannel.close()
                    connection.close()
                    return@async
                }
            }
        }
    }
}