package ktproxy.socks

import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.runBlocking
import ktproxy.coroutineBuffer.CoroutineReadBuffer
import ktproxy.coroutineBuffer.CoroutineWriteBuffer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel

fun main(args: Array<String>) = runBlocking {
    val serverSocketChannel = AsynchronousServerSocketChannel.open()
    serverSocketChannel.bind(InetSocketAddress("127.0.0.2", 4566))
    val client = serverSocketChannel.aAccept()
//    val buffer = ByteBuffer.allocate(8192)
    val readBuffer = CoroutineReadBuffer(client)
    val writeBuffer = CoroutineWriteBuffer(client)

    val socks = Socks(client, readBuffer, writeBuffer)
    socks.init()
    println(socks.isSuccessful)
    val targetaddress = socks.targetAddress
    val socksInfo = Socks.build(targetaddress)
    println(InetAddress.getByName(socksInfo.addr).hostAddress)
    println(socksInfo.port)
}