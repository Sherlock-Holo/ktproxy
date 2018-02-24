package ktproxy.socks

import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel

fun main(args: Array<String>) = runBlocking {
    val serverSocketChannel = AsynchronousServerSocketChannel.open()
    serverSocketChannel.bind(InetSocketAddress("127.0.0.2", 4567))
    val client = serverSocketChannel.aAccept()
    val buffer = ByteBuffer.allocate(8192)
    val socks = Socks(client, buffer)
    socks.init()
    println(socks.isSuccessful)
    val targetaddress = socks.targetAddress
    val socksInfo = Socks.build(targetaddress)
    println(InetAddress.getByAddress(socksInfo.addr).hostAddress)
    println(socksInfo.port)
}