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
            async { handle(socketChannel) }
        }
    }

    private suspend fun handle(socketChannel: AsynchronousSocketChannel) {
        val buffer = ByteBuffer.allocate(8192)
        val socks = Socks(socketChannel, buffer)
        socks.init()
        if (!socks.isSuccessful) {
            socketChannel.close()
            return
        }

        val connection = ClientConnection(proxyAddr, proxyPort, key)
        try {
            connection.init()
        } catch (e: IOException) {
            socketChannel.close()
            connection.close()
            e.printStackTrace()
            return
        } catch (e: FrameException) {
            socketChannel.close()
            connection.close()
            e.printStackTrace()
            return
        }

        try {
            connection.write(socks.targetAddress)
        } catch (e: IOException) {
            connection.close()
            socketChannel.close()
            return
        }

        // browser -> proxy
        async {
            buffer.clear()
            while (true) {
                try {
                    if (socketChannel.aRead(buffer) <= 0) {
                        socketChannel.shutdownInput()
                        connection.shutdownOutput()
                        return@async
                    }
//                    println("read not 0")
                } catch (e: IOException) {
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

                if (data == null) {
                    socketChannel.shutdownOutput()
                    connection.shutdownInput()
                    return@async
                }

                try {
                    socketChannel.aWrite(ByteBuffer.wrap(data))
                } catch (e: IOException) {
                    socketChannel.close()
                    connection.errorClose()
                    return@async
                }
            }
        }
    }
}