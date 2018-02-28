package ktproxy.server

import kotlinx.coroutines.experimental.runBlocking
import ktproxy.config.buildConfig
import java.io.File

fun main(args: Array<String>) = runBlocking {
    if (args.size != 2) {
        println("ktproxy-server -c/--config config.toml")
        System.exit(1)
    }
    val configFile = File(args[1])
    if (!configFile.exists()) {
        println("config file not exist")
        System.exit(1)
    }
    val config = buildConfig(configFile)

    val server = Server(config.proxyAddr, config.proxyPort, config.password)
    server.start()
}