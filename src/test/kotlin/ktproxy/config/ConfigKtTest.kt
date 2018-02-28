package ktproxy.config

import org.junit.Assert.*
import java.io.File

fun main(args: Array<String>) {
    val config = buildConfig(File("/home/sherlock/git/ktproxy/src/main/kotlin/ktproxy/config/config.toml"))
    assertEquals("127.0.0.2", config.listenAddr)
    assertEquals(4566, config.listenPort)
    assertEquals("127.0.0.2", config.proxyAddr)
    assertEquals(4567, config.proxyPort)
    assertEquals("test", config.password)
}