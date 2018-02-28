package ktproxy.config

import com.moandjiezana.toml.Toml
import java.io.File

data class ProxyConfig(
        val listenAddr: String,
        val listenPort: Int,
        val proxyAddr: String,
        val proxyPort: Int,
        val password: String
)

fun buildConfig(file: File): ProxyConfig {
    val toml = Toml().read(file)

    return ProxyConfig(
            toml.getString("local.listenAddr"),
            toml.getLong("local.listenPort").toInt(),
            toml.getString("proxy.proxyAddr"),
            toml.getLong("proxy.proxyPort").toInt(),
            toml.getString("password")
    )
}