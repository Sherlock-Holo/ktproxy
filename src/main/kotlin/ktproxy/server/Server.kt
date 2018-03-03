package ktproxy.server

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aRead
import ktproxy.connection.ConnectionException
import ktproxy.connection.ServerConnection
import ktproxy.coroutineBuffer.CoroutineWriteBuffer
import ktproxy.socks.Socks
import ktproxy.socks.SocksException
import ktproxy.websocket.frame.FrameException
import resocks.encrypt.Cipher
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.util.logging.Level
import java.util.logging.Logger

class Server(
        private val proxyAddr: String?,
        private val proxyPort: Int,
        password: String
) {
    private val key = Cipher.password2key(password)

    private val logger = Logger.getLogger("ktproxy-server logger")
    var loggerLevel: Level
        set(value) {
            logger.level = value
        }
        get() = logger.level

    suspend fun start() {
        val serverSocketChannel = AsynchronousServerSocketChannel.open()

        if (proxyAddr != null) serverSocketChannel.bind(InetSocketAddress(proxyAddr, proxyPort))
        else serverSocketChannel.bind(InetSocketAddress(proxyPort))

        while (true) {
            val socketChannel = serverSocketChannel.aAccept()
            socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
            logger.fine("accept new connection")

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
        var reuse = false
        while (true) {
            if (reuse) {
                try {
                    connection.reset()
                } catch (e: FrameException) {
                    logger.warning("reset failed: ${e.message}")
                    connection.close()
                    return
                }
                logger.info("reset successful")
            }

            val targetAddress = try {
                connection.read()
            } catch (e: FrameException) {
                logger.warning("read target address failed: ${e.message}")
                connection.close()
                return
            }

            if (targetAddress == null) {
                logger.warning("read target address failed")
                connection.close()
                return
            }
            logger.info("read target address successful")

            val socksInfo = try {
                Socks.build(targetAddress)
            } catch (e: SocksException) {
                logger.warning("reuse: $reuse, build socks info failed: ${e.message}")
                connection.close()
                return
            }
            logger.info("reuse: $reuse, get socks info")

            val socketChannel = AsynchronousSocketChannel.open()
            try {
                socketChannel.aConnect(InetSocketAddress(InetAddress.getByName(socksInfo.addr), socksInfo.port))
            } catch (e: IOException) {
                logger.warning("connect to target failed")
                socketChannel.close()
                connection.close()
                return
            }
            logger.info("connect to target successful")

            val writeBuffer = CoroutineWriteBuffer(socketChannel)

            // proxy -> server
            val replay1 = async {

                while (true) {
                    val data = try {
                        connection.read()
                    } catch (e: FrameException) {
                        logger.warning("connection read data failed: ${e.message}")
                        socketChannel.close()
                        connection.close()

                        return@async

                        // only connection close will throw this exception
                    } catch (e: ConnectionException) {
                        logger.warning(e.message)
                        socketChannel.shutdownOutput()

                        return@async
                    }

                    if (data == null) {
                        logger.info("read FIN")
                        socketChannel.shutdownOutput()
                        connection.shutdownInput()

                        return@async
                    }

                    try {
//                        socketChannel.aWrite(ByteBuffer.wrap(data))
                        writeBuffer.write(data)
                    } catch (e: IOException) {
                        logger.warning("socketChannel write data failed: ${e.message}")
                        socketChannel.close()
                        connection.shutdownInput()

                        return@async
                    }
                }
            }

            // server -> proxy
            val replay2 = async {

                val buffer = ByteBuffer.allocate(8192)
                while (true) {
                    try {
                        if (socketChannel.aRead(buffer) <= 0) {
                            logger.fine("socketChannel read FIN")
                            socketChannel.shutdownInput()
                            try {
                                connection.shutdownOutput()

                            } catch (e: IOException) {
                                logger.warning("connection shutdownOutput failed: ${e.message}")
                                socketChannel.close()
                                connection.close()

                            } finally {
                                return@async
                            }
                        }

                    } catch (e: IOException) {
                        logger.warning("socketChannel read data failed: ${e.message}")
                        socketChannel.close()
                        try {
                            connection.shutdownOutput()

                        } catch (e: IOException) {
                            logger.warning("connection shutdownOutput failed: ${e.message}")
                            socketChannel.close()
                            connection.close()

                        } finally {
                            return@async
                        }
                    }

                    buffer.flip()
                    val data = ByteArray(buffer.limit())
                    buffer.get(data)
                    buffer.clear()

                    try {
                        if (connection.write(data) < 0) {
                            try {
                                connection.shutdownOutput()

                            } catch (e: IOException) {
                                logger.warning("connection shutdownOutput failed: ${e.message}")
                                socketChannel.close()
                                connection.close()

                            } finally {
                                return@async
                            }
                        }

                    } catch (e: IOException) {
                        logger.warning("connection write data failed: ${e.message}")
                        socketChannel.close()
                        connection.close()

                        return@async
                    }
                }
            }

            replay1.await()
            replay2.await()
            reuse = true
        }
    }
}