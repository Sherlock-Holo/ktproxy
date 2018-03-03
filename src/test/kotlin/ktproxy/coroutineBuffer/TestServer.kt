package ktproxy.coroutineBuffer

import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.runBlocking
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel

fun main(args: Array<String>) = runBlocking<Unit> {
    val serverSocketChannel = AsynchronousServerSocketChannel.open()
    serverSocketChannel.bind(InetSocketAddress(4399))
    val socketChannel = serverSocketChannel.aAccept()
    val buffer = CoroutineWriteBuffer(socketChannel)

    buffer.writeline("sherlock holo\n")

    buffer.write("121 121".toByteArray())
}