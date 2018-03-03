package ktproxy.buffer

import java.nio.channels.AsynchronousSocketChannel

suspend fun AsynchronousSocketChannel.readn(buffer: CoroutineBuffer, size: Int) = buffer.readn0(this, size)

suspend fun AsynchronousSocketChannel.readline(buffer: CoroutineBuffer) = buffer.readLine0(this)