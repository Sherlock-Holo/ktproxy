package ktproxy.frame

import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aWrite
import kotlinx.coroutines.experimental.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel

fun main(args: Array<String>) = runBlocking<Unit> {
    val serverSocketChannel = AsynchronousServerSocketChannel.open()
    serverSocketChannel.bind(InetSocketAddress("127.0.0.2", 4567))
    val socketChannel = serverSocketChannel.aAccept()
    val buffer = ByteBuffer.allocate(8192)
    val frame = Frame.buildFrame(socketChannel, buffer, FrameType.CLIENT)
    println(String(frame.content))
//    buffer.put(Frame(FrameType.SERVER, FrameContentType.BINARY, File("/tmp/randomfile").readBytes()).frameByteArray)
    buffer.put(Frame(FrameType.SERVER, FrameContentType.BINARY, "holo".toByteArray()).frameByteArray)
    buffer.flip()
    socketChannel.aWrite(buffer)
}