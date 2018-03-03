package ktproxy.websocket.frame

import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.runBlocking
import ktproxy.coroutineBuffer.CoroutineReadBuffer
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel

fun main(args: Array<String>) = runBlocking {
    val serverSocketChannel = AsynchronousServerSocketChannel.open()
    serverSocketChannel.bind(InetSocketAddress("127.0.0.2", 4667))
    val socketChannel = serverSocketChannel.aAccept()
//    val buffer = ByteBuffer.allocate(8192)
    val buffer = CoroutineReadBuffer(socketChannel)
    val frame = Frame.buildFrame(buffer, FrameType.CLIENT)
    println(String(frame.content))
    println(frame.contentType)
}