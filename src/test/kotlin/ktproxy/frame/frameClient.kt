package ktproxy.frame

import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aWrite
import kotlinx.coroutines.experimental.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

fun main(args: Array<String>) = runBlocking<Unit> {
    val socketChannel = AsynchronousSocketChannel.open()
    socketChannel.aConnect(InetSocketAddress("127.0.0.2", 4667))
    val buffer = ByteBuffer.allocate(8192)
    val frame = Frame(FrameType.CLIENT, FrameContentType.PING, "sherlock".toByteArray())
    buffer.put(frame.frameByteArray)

    buffer.flip()
    socketChannel.aWrite(buffer)
}