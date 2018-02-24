package ktproxy.test

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import ktproxy.connection.ClientConnection
import ktproxy.connection.ConnectionException
import ktproxy.frame.FrameException
import ktproxy.socks.Socks
import resocks.encrypt.Cipher
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel

class Client(
        listenAddr: String,
        listenPort: Int,
        private val proxyAddr: String,
        private val proxyPort: Int,
        password: String
) {
    private val key = Cipher.password2key(password)
    private val listenSocketChannel = AsynchronousServerSocketChannel.open()

    init {
        listenSocketChannel.bind(InetSocketAddress(listenAddr, listenPort))
    }

    suspend fun start() {
        while (true) {
            val socketChannel = listenSocketChannel.aAccept()
            println("accept new connection")
            async { handle(socketChannel) }
        }
    }

    private suspend fun handle(socketChannel: AsynchronousSocketChannel) {
        val buffer = ByteBuffer.allocate(8192)
        val socks = Socks(socketChannel, buffer)
        socks.init()
        if (!socks.isSuccessful) {
            println("socks handshake failed")
            socketChannel.close()
            return
        }
        println("socks handshake successful")

        val connection = ClientConnection(proxyAddr, proxyPort, key)
        try {
            connection.init()
        } catch (e: IOException) {
            socketChannel.close()
            connection.close()
            println("connection init failed")
            return
        } catch (e: FrameException) {
            socketChannel.close()
            connection.close()
            println("connection init failed")
            return
        }
        println("connection init successful")

        try {
            connection.write(socks.targetAddress)
        } catch (e: IOException) {
            connection.close()
            socketChannel.close()
            return
        }
        println("sand targetaddress successful")

        // browser -> proxy
        async {
            while (true) {
                try {
                    if (socketChannel.aRead(buffer) <= 0) {
                        socketChannel.close()
                        connection.close()
                        return@async
                    }
                } catch (e: IOException) {
                    socketChannel.close()
                    connection.close()
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
                    return@async
                } catch (e: ConnectionException) {
                    socketChannel.close()
                    connection.close()
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
                    socketChannel.close()
                    connection.close()
                    return@async
                } catch (e: FrameException) {
                    socketChannel.close()
                    connection.close()
                    return@async
                }

                try {
                    socketChannel.aWrite(ByteBuffer.wrap(data))
                } catch (e: IOException) {
                    socketChannel.close()
                    connection.close()
                    return@async
                }
            }
        }
    }
}