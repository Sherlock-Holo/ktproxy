package ktproxy.socks

import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class Socks(private val socketChannel: AsynchronousSocketChannel, val buffer: ByteBuffer) {
    var version: Int? = null
        private set

    var atyp: Int = 0
        private set

    lateinit var addr: InetAddress
        private set

    var addrLength: Int = -1
        private set

    var port: Int? = null
        private set

    lateinit var targetAddress: ByteArray
        private set

    var isSuccessful = false
        private set

    suspend fun init() {
        var length = 0

        buffer.limit(2)
        while (length < 2) {
            try {
                val dataRead = socketChannel.aRead(buffer)
                if (dataRead <= 0) throw SocksException("unexpected stream end")
                length += dataRead
            } catch (e: IOException) {
                throw SocksException("an IO error has occurred")
            }
        }
        length = 0
        buffer.flip()
        version = buffer.get().toInt() and 0xff
        val nmethods = buffer.get().toInt() and 0xff
        buffer.clear()

        if (version != 5) throw SocksException("socks version is $version, not 5")

        /*buffer.limit(1)
        socketChannel.aRead(buffer)
        buffer.flip()
        val nmethods = buffer.get().toInt() and 0xff
        buffer.clear()*/

        val methods = ByteArray(nmethods)
        buffer.limit(nmethods)
        while (length < nmethods) {
            try {
                val dataRead = socketChannel.aRead(buffer)
                if (dataRead <= 0) throw SocksException("unexpected stream end")
                length += dataRead
            } catch (e: IOException) {
                throw SocksException("an IO error has occurred")
            }
        }
        length = 0
        buffer.flip()
        buffer.get(methods)
        buffer.clear()

        if (methods.all { it.toInt() and 0xff != 0 }) throw SocksException("socks client does't use no auth mode")

        val method = byteArrayOf(5, 0)
        buffer.put(method)
        buffer.flip()
        socketChannel.aWrite(buffer)
        buffer.clear()

        buffer.limit(4)
        while (length < 4) {
            try {
                val dataRead = socketChannel.aRead(buffer)
                if (dataRead <= 0) throw SocksException("unexpected stream end")
                length += dataRead
            } catch (e: IOException) {
                throw SocksException("an IO error has occurred")
            }
        }
        length = 0
        buffer.flip()
        val request = ByteArray(4)
        buffer.get(request)
        buffer.clear()

        val requestVersion = request[0].toInt() and 0xff
        val cmd = request[1].toInt() and 0xff
        atyp = request[3].toInt() and 0xff

        if (requestVersion != 5 || cmd != 1) throw SocksException("request error")

        when (atyp) {
            1 -> {
                buffer.limit(6)
                while (length < 6) {
                    try {
                        val dataRead = socketChannel.aRead(buffer)
                        if (dataRead <= 0) throw SocksException("unexpected stream end")
                        length += dataRead
                    } catch (e: IOException) {
                        throw SocksException("an IO error has occurred")
                    }
                }
                buffer.flip()
                val address = ByteArray(4)
                buffer.get(address)

                addr = InetAddress.getByAddress(address)
                port = buffer.short.toInt()
                buffer.clear()

                targetAddress = ByteArray(1 + 4 + 2)
                targetAddress[0] = atyp.toByte()

                System.arraycopy(address, 0, targetAddress, 1, 4)

                val tmp = ByteArray(2)
                ByteBuffer.wrap(tmp).putShort(port!!.toShort())
                System.arraycopy(tmp, 0, targetAddress, 5, 2)
            }

            4 -> {
                buffer.limit(16 + 2)
                while (length < 16 + 2) {
                    try {
                        val dataRead = socketChannel.aRead(buffer)
                        if (dataRead <= 0) throw SocksException("unexpected stream end")
                        length += dataRead
                    } catch (e: IOException) {
                        throw SocksException("an IO error has occurred")
                    }
                }
                buffer.flip()
                val address = ByteArray(16)
                buffer.get(address)

                addr = InetAddress.getByAddress(address)
                port = buffer.short.toInt()
                buffer.clear()

                targetAddress = ByteArray(1 + 16 + 2)
                targetAddress[0] = atyp.toByte()

                System.arraycopy(address, 0, targetAddress, 1, 16)

                val tmp = ByteArray(2)
                ByteBuffer.wrap(tmp).putShort(port!!.toShort())
                System.arraycopy(tmp, 0, targetAddress, 17, 2)
            }

            3 -> {
                buffer.limit(1)
                socketChannel.aRead(buffer)
                buffer.flip()
                addrLength = buffer.get().toInt() and 0xff
                buffer.clear()

                val address = ByteArray(addrLength)
                buffer.limit(addrLength)
                while (length < addrLength + 2) {
                    try {
                        val dataRead = socketChannel.aRead(buffer)
                        if (dataRead <= 0) throw SocksException("unexpected stream end")
                        length += dataRead
                    } catch (e: IOException) {
                        throw SocksException("an IO error has occurred")
                    }
                }
                buffer.flip()
                buffer.get(address)

                addr = InetAddress.getByAddress(address)
                port = buffer.short.toInt()
                buffer.clear()

                targetAddress = ByteArray(1 + 1 + addrLength + 2)
                targetAddress[0] = atyp.toByte()
                targetAddress[1] = addrLength.toByte()

                System.arraycopy(address, 0, targetAddress, 2, addrLength)

                val tmp = ByteArray(2)
                ByteBuffer.wrap(tmp).putShort(port!!.toShort())
                System.arraycopy(tmp, 0, targetAddress, targetAddress.size - 2, 2)
            }

            else -> throw SocksException("unexpected atyp")
        }

        val replyAddress = Inet6Address.getByName("::1").address
        buffer.put(replyAddress)
        buffer.putShort(0)
        buffer.flip()
        try {
            socketChannel.aWrite(buffer)
        } catch (e: IOException) {
            throw SocksException("an IO error has occurred")
        }
        isSuccessful = true
    }


    companion object {
        data class SocksInfo(val addr: ByteArray, val port: Int)

        fun build(targetAddress: ByteArray): SocksInfo {
            val atyp = targetAddress[0].toInt() and 0xff

            val addr: ByteArray
            val port: Int

            when (atyp) {
                1 -> {
                    addr = targetAddress.copyOfRange(1, 5)
                    port = ByteBuffer.wrap(targetAddress.copyOfRange(5, 7)).short.toInt()

                }

                3 -> {
                    val addrLength = targetAddress[1].toInt() and 0xff
                    addr = targetAddress.copyOfRange(2, 2 + addrLength)
                    port = ByteBuffer.wrap(targetAddress.copyOfRange(2 + addrLength, 4 + addrLength)).short.toInt()
                }

                4 -> {
                    addr = targetAddress.copyOfRange(1, 17)
                    port = ByteBuffer.wrap(targetAddress.copyOfRange(17, 19)).short.toInt()
                }

                else -> throw SocksException("atyp error: $atyp, can't not build socks info")
            }
            return SocksInfo(addr, port)
        }
    }
}