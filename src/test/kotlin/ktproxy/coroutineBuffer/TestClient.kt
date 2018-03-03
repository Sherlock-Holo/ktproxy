package ktproxy.coroutineBuffer

import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.runBlocking
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousSocketChannel

fun main(args: Array<String>) = runBlocking {
    val socketChannel = AsynchronousSocketChannel.open()
    socketChannel.aConnect(InetSocketAddress("127.0.0.2", 4399))
    val buffer = CoroutineReadBuffer(socketChannel)

    println(buffer.readLine())
    println(String(buffer.read(7)!!))
}