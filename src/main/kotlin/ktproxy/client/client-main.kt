package ktproxy.client

import kotlinx.coroutines.experimental.runBlocking
import ktproxy.client.Client

fun main(args: Array<String>) = runBlocking {
    val client = Client("127.0.0.2", 4566, "127.0.0.2", 4567, "test")
    client.start()
}