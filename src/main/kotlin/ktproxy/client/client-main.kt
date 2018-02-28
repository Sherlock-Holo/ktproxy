package ktproxy.client

import kotlinx.coroutines.experimental.runBlocking
import ktproxy.config.buildConfig
import java.io.File

fun main(args: Array<String>) = runBlocking {
    if (args.size != 2) {
        println("ktproxy-client -c/--config config.toml")
        System.exit(1)
    }
    val configFile = File(args[1])
    if (!configFile.exists()) {
        println("config file not exist")
        System.exit(1)
    }
    val config = buildConfig(configFile)

    val client = Client(config.listenAddr, config.listenPort, config.proxyAddr, config.proxyPort, config.password)
    client.start()
}