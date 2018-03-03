package ktproxy.client

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import ktproxy.connection.ClientPool
import ktproxy.connection.ConnectionException
import ktproxy.frame.FrameException
import ktproxy.socks.Socks
import resocks.encrypt.Cipher
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.util.logging.Level
import java.util.logging.Logger

class Client(
        listenAddr: String,
        listenPort: Int,
        proxyAddr: String,
        proxyPort: Int,
        password: String,
        poolCapacity: Int,
        private val telnet: Int?
) {
    private val key = Cipher.password2key(password)
    private val listenSocketChannel = AsynchronousServerSocketChannel.open()

    private val pool = ClientPool(proxyAddr, proxyPort, key, poolCapacity)

    private val logger = Logger.getLogger("ktproxy-client logger")
    var loggerLevel: Level
        set(value) {
            logger.level = value
        }
        get() = logger.level

    init {
        listenSocketChannel.bind(InetSocketAddress(listenAddr, listenPort))
    }

    suspend fun start() {
        pool.startCheckReuse(telnet)

        while (true) {
            val socketChannel = listenSocketChannel.aAccept()
            async { handle(socketChannel) }
        }
    }

    private suspend fun handle(socketChannel: AsynchronousSocketChannel) {
        val buffer = ByteBuffer.allocate(8192)
        val socks = Socks(socketChannel, buffer)
        socks.init()
        if (!socks.isSuccessful) {
            socketChannel.close()
            logger.warning("socks init failed")
            return
        }
        logger.info("socks init successful")

        val connection = try {
            pool.getConn()
        } catch (e: IOException) {
            logger.warning("get connection failed: ${e.message}")
            socketChannel.close()
            return
        } catch (e: FrameException) {
            logger.warning("get connection failed: ${e.message}")
            socketChannel.close()
            return
        }
        logger.info("get connection successful")


        val checkQueue = LinkedListChannel<Boolean>()
        async {
            for (i in 0 until 2) {
                if (!checkQueue.receive()) {
                    logger.warning("connection error, discard this connection")
                    break
                }
            }

            pool.putConn(connection)
        }


        try {
            connection.write(socks.targetAddress)
        } catch (e: IOException) {
            logger.warning("send target address failed: ${e.message}")
            connection.close()
            socketChannel.close()
            checkQueue.offer(false)
            return
        }
        logger.info("send target address successful")

        // browser -> proxy
        async {
            buffer.clear()
            while (true) {
                try {
                    if (socketChannel.aRead(buffer) <= 0) {
                        logger.fine("socketChannel read FIN")
                        socketChannel.shutdownInput()
                        try {
                            connection.shutdownOutput()
                        } catch (e: IOException) {
                            logger.warning("connection shutdownOutput failed: ${e.message}")
                            connection.close()
                            checkQueue.offer(false)
                            return@async
                        }

                        checkQueue.offer(true)
                        return@async
                    }
                } catch (e: IOException) {
                    logger.warning("unexpected socketChannel stream end")
                    socketChannel.close()
                    try {
                        connection.shutdownOutput()
                    } catch (e: IOException) {
                        logger.warning("connection shutdownOutput failed: ${e.message}")
                        connection.close()
                        checkQueue.offer(false)
                        return@async
                    }
                    checkQueue.offer(true)
                    return@async
                }

                buffer.flip()
                val data = ByteArray(buffer.limit())
                buffer.get(data)
                buffer.clear()

                try {
                    if (connection.write(data) < 0) {
                        logger.warning("connection can't write")
                        socketChannel.shutdownInput()
                        checkQueue.offer(true)
                        return@async
                    }

                } catch (e: IOException) {
                    logger.warning("connection write data failed")
                    socketChannel.close()
                    connection.close()
                    checkQueue.offer(false)
                    return@async
                }
            }
        }

        // proxy -> browser
        async {
            while (true) {
                val data = try {
                    connection.read()

                } catch (e: ConnectionException) {
                    socketChannel.shutdownOutput()
                    checkQueue.offer(true)
                    return@async

                } catch (e: FrameException) {
                    logger.warning("connection read data failed")
                    socketChannel.close()
                    connection.close()
                    checkQueue.offer(false)
                    return@async
                }

                if (data == null) {
                    logger.info("connection read FIN")
                    socketChannel.shutdownOutput()
                    connection.shutdownInput()
                    checkQueue.offer(true)
                    return@async
                }

                try {
                    socketChannel.aWrite(ByteBuffer.wrap(data))
                } catch (e: IOException) {
                    logger.warning("socketChannel IO error")
                    socketChannel.close()
                    connection.shutdownInput()
                    checkQueue.offer(true)
                    return@async
                }
            }
        }
    }
}