package ktproxy.websocket.http

import ktproxy.coroutineBuffer.CoroutineReadBuffer
import ktproxy.websocket.WebsocketException
import java.security.MessageDigest
import java.util.*

class HttpHeader {
    val header: String
    val secWebSocketKey: String?
    private val secWebSocketAccept: String?

    private constructor(isClient: Boolean, value: String, host: String = "github.com") {
        val headerBuilder = StringBuilder()
        header = if (isClient) {
            secWebSocketKey = value
            secWebSocketAccept = null
            headerBuilder.append("GET /chat HTTP/1.1\r\n")
            headerBuilder.append("Host: $host\r\n")
            headerBuilder.append("Upgrade: websocket\r\n")
            headerBuilder.append("Connection: Upgrade\r\n")
            headerBuilder.append("Sec-WebSocket-Key: $value\r\n")
            headerBuilder.append("Sec-WebSocket-Version: 13\r\n\r\n")
            headerBuilder.toString()
        } else {
            secWebSocketKey = null
            secWebSocketAccept = value
            headerBuilder.append("HTTP/1.1 101 Switching Protocols\r\n")
            headerBuilder.append("Upgrade: websocket\r\n")
            headerBuilder.append("Connection: Upgrade\r\n")
            headerBuilder.append("Sec-WebSocket-Accept: $value\r\n\r\n")
            headerBuilder.toString()
        }
    }

    private constructor(header: String) {
        this.header = header
        val pin: Int
        when {
            header.contains("Sec-WebSocket-Key") -> {
                pin = header.indexOf("Sec-WebSocket-Key: ") + "Sec-WebSocket-Key: ".length
                secWebSocketKey = header.substring(pin, pin + 24)
                secWebSocketAccept = null
            }
            header.contains("Sec-WebSocket-Accept: ") -> {
                pin = header.indexOf("Sec-WebSocket-Accept: ") + "Sec-WebSocket-Accept: ".length
                secWebSocketKey = null
                secWebSocketAccept = header.substring(pin, pin + 28)
            }
            else -> throw WebsocketException("secWebSocketKey or secWebSocketAccept not found")
        }
    }

    //    fun getHeaderByteArray() = header.toByteArray()
    val headerByteArray get() = header.toByteArray()

    //    fun getHeaderString() = header
//    val headerString get() = header

    fun checkHttpHeader(): Boolean {
        // server checks client handshake

        val headList = header.substring(0 until header.length - 2).split("\r\n")

        if (!headList[0].startsWith("GET")) return false
        if (!headList.contains("Upgrade: websocket")) return false
        if (!headList.contains("Connection: Upgrade")) return false
        if (!headList.contains("Sec-WebSocket-Version: 13")) return false

        return true
    }

    fun checkHttpHeader(secWebSocketKey: String): Boolean {
        // serverHttpHeader invoke this to check itself on client-end

        val headList = header.substring(0 until header.length - 2).split("\r\n")

        if (headList[0] != "HTTP/1.1 101 Switching Protocols") return false
        if (!headList.contains("Upgrade: websocket")) return false
        if (!headList.contains("Connection: Upgrade")) return false
        if (!headList.contains("Sec-WebSocket-Accept: " + genSecWebSocketAccept(secWebSocketKey))) return false

        return true
    }


    companion object {
        fun buildHttpHeader(secWebSocketKey: String) = HttpHeader(false, genSecWebSocketAccept(secWebSocketKey))

        // offer client http header
        fun buildHttpHeader(): HttpHeader {
            val array = ByteArray(16)
            Random().nextBytes(array)
            return HttpHeader(true, Base64.getEncoder().encodeToString(array))
        }

        fun genSecWebSocketAccept(secWebSocketKey: String): String {
            val sha1 = MessageDigest.getInstance("SHA1")
            sha1.update((secWebSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray())
            return String(Base64.getEncoder().encode(sha1.digest()))
        }

        suspend fun getHttpHeader(readsBuffer: CoroutineReadBuffer): HttpHeader {
            val sb = StringBuilder()
            while (true) {
                val line = readsBuffer.readLine() ?: throw WebsocketException("read http header failed")
                sb.append(line)
                if (line == "\r\n") {
                    return HttpHeader(sb.toString())
                }
            }
        }
    }
}