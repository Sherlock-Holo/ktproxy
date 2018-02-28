package ktproxy.client

import kotlinx.coroutines.experimental.runBlocking
import ktproxy.config.Config
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
    val config = Config(configFile)

    val poolCapacity = (config.getMap("local")["poolSize"] as Long? ?: 50).toInt()

    val telnetService = config.getMap("reuseCheck")
    val telnet = telnetService["service"] as Boolean
    val telnetPort =
            if (telnet) (telnetService["port"] as Long).toInt()
            else null

    val client = Client(
            config.listenAddr,
            config.listenPort,
            config.proxyAddr,
            config.proxyPort,
            config.password,
            poolCapacity,
            telnetPort
    )

    client.start()
}