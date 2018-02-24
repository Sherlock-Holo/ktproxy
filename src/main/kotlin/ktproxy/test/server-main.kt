package ktproxy.test

import kotlinx.coroutines.experimental.runBlocking

fun main(args: Array<String>) = runBlocking {
    val server = Server("127.0.0.2", 4567, "test")
    server.start()
}