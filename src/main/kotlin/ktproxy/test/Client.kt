package ktproxy.test

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
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

class Client(
        listenAddr: String,
        listenPort: Int,
        proxyAddr: String,
        proxyPort: Int,
        password: String
) {
    private val key = Cipher.password2key(password)
    private val listenSocketChannel = AsynchronousServerSocketChannel.open()

    private val pool = ClientPool(proxyAddr, proxyPort, key)

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

        /*val connection = ClientConnection(proxyAddr, proxyPort, key)
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
        }*/


        val connection = try {
            pool.getConn()
        } catch (e: IOException) {
            socketChannel.close()
            return
        } catch (e: FrameException) {
            socketChannel.close()
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



        try {
            connection.write(socks.targetAddress)
        } catch (e: IOException) {
            connection.errorClose()
            socketChannel.close()
            canRelease = -1
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
                        canRelease++
                        return@async
                    }
//                    println("read not 0")
                } catch (e: IOException) {
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
                    socketChannel.close()
                    connection.errorClose()
                    canRelease = -1
                    return@async
                } catch (e: ConnectionException) {
                    socketChannel.close()
                    connection.errorClose()
                    canRelease = -1
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
                    connection.errorClose()
                    canRelease = -1
                    return@async
                } catch (e: FrameException) {
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
                    socketChannel.close()
                    connection.errorClose()
                    canRelease = -1
                    return@async
                }
            }
        }
    }
}