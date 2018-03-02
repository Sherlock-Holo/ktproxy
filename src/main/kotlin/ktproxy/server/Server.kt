package ktproxy.server

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
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel

class Server(
        private val proxyAddr: String?,
        private val proxyPort: Int,
        password: String
) {
    private val key = Cipher.password2key(password)

//    private val pool = ServerPool(proxyAddr, proxyPort, key)

    suspend fun start() {
        /*pool.init()
        while (true) {
            val connection = pool.getConn()

            async {
                handle(connection)
            }
        }*/

        val serverSocketChannel = AsynchronousServerSocketChannel.open()

        if (proxyAddr != null) serverSocketChannel.bind(InetSocketAddress(proxyAddr, proxyPort))
        else serverSocketChannel.bind(InetSocketAddress(proxyPort))

        while (true) {
            val socketChannel = serverSocketChannel.aAccept()
            socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)

            async {
                val connection = ServerConnection(socketChannel, key)
                try {
                    connection.init()
                    handle(connection)
                } finally {
                }
            }
        }


    }

    private suspend fun handle(connection: ServerConnection) {
        while (true) {
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

            // proxy -> server
            val replay1 = async {

                while (true) {
                    val data = try {
                        connection.read()
                    } catch (e: FrameException) {
                        socketChannel.close()
                        connection.close()

                        break
                    } catch (e: ConnectionException) {
                        socketChannel.shutdownOutput()

                        return@async true
                    }

                    if (data == null) {
                        socketChannel.shutdownOutput()
                        connection.shutdownInput()

                        return@async true
                    }

                    try {
                        socketChannel.aWrite(ByteBuffer.wrap(data))
                    } catch (e: IOException) {
                        socketChannel.close()
                        connection.shutdownInput()

                        return@async true
                    }
                }
                return@async false
            }

            // server -> proxy
            val replay2 = async {

                val buffer = ByteBuffer.allocate(8192)
                while (true) {
                    try {
                        if (socketChannel.aRead(buffer) <= 0) {
                            socketChannel.shutdownInput()
                            connection.shutdownOutput()

                            return@async true
                        }
                    } catch (e: IOException) {
                        socketChannel.close()
                        connection.shutdownOutput()

                        return@async true
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

                        break
                    } catch (e: ConnectionException) {
                        socketChannel.shutdownInput()

                        return@async true
                    }
                }
                return@async false
            }

            if (replay1.await() && replay2.await()) {
                try {
                    if (connection.destroy()) {
                        connection.close()
                        break
                    }

                } catch (e: IOException) {
                    connection.close()
                    break
                }
            } else break
        }
    }
}