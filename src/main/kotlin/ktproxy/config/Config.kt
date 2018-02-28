package ktproxy.config

import com.moandjiezana.toml.Toml
import java.io.File

class Config(file: File) {
    private val toml = Toml().read(file)

    val listenAddr: String
    val listenPort: Int
    val proxyAddr: String
    val proxyPort: Int
    val password: String

    init {
        listenAddr = toml.getString("local.listenAddr")
        listenPort = toml.getLong("local.listenPort").toInt()
        proxyAddr = toml.getString("proxy.proxyAddr")
        proxyPort = toml.getLong("proxy.proxyPort").toInt()
        password = toml.getString("gernal.password")
    }

    fun getMap(title: String) = toml.getTable(title).toMap()
}