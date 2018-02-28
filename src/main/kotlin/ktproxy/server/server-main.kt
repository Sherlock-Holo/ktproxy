package ktproxy.server

import kotlinx.coroutines.experimental.runBlocking
import ktproxy.server.Server

fun main(args: Array<String>) = runBlocking {
    val server = Server("127.0.0.2", 4567, "test")
    server.start()
}