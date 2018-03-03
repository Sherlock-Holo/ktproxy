package ktproxy.server

import kotlinx.coroutines.experimental.runBlocking
import ktproxy.config.Config
import java.io.File
import java.util.logging.Level

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
    val config = Config(configFile)

    val server = Server(config.proxyAddr, config.proxyPort, config.password)

    if (config.getMap("gernal")["debug"] as Boolean) server.loggerLevel = Level.FINE
    else server.loggerLevel = Level.WARNING

    server.start()
}