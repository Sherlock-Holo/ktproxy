package ktproxy.socks

import ktproxy.coroutineBuffer.CoroutineReadBuffer
import ktproxy.coroutineBuffer.CoroutineWriteBuffer
import java.io.IOException
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class Socks(
        private val socketChannel: AsynchronousSocketChannel,
        val readBuffer: CoroutineReadBuffer,
        val writeBuffer: CoroutineWriteBuffer
) {

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

        /*buffer.limit(2)
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
        buffer.clear()*/

        val version = try {
            (readBuffer.read() ?: throw SocksException("unexpected stream end")).toInt()
        } catch (e: IOException) {
            throw SocksException("an IO error has occurred")
        }

        val nmethods = try {
            (readBuffer.read() ?: throw SocksException("unexpected stream end")).toInt()
        } catch (e: IOException) {
            throw SocksException("an IO error has occurred")
        }

        if (version != 5) throw SocksException("socks version is $version, not 5")

        /*val methods = ByteArray(nmethods)
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
        buffer.clear()*/

        val methods = try {
            readBuffer.read(nmethods) ?: throw SocksException("unexpected stream end")
        } catch (e: IOException) {
            throw SocksException("an IO error has occurred")
        }

        if (methods.all { it.toInt() and 0xff != 0 }) throw SocksException("socks client does't use no auth mode")

        /*val method = byteArrayOf(5, 0)
        buffer.put(method)
        buffer.flip()
        socketChannel.aWrite(buffer)
        buffer.clear()*/

        try {
            writeBuffer.write(byteArrayOf(5, 0))
        } catch (e: IOException) {
            throw SocksException("send socks auth failed")
        }

        /*buffer.limit(4)
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
        buffer.clear()*/

        val request = try {
            readBuffer.read(4) ?: throw SocksException("unexpected stream end")
        } catch (e: IOException) {
            throw SocksException("an IO error has occurred")
        }

        val requestVersion = request[0].toInt() and 0xff
        val cmd = request[1].toInt() and 0xff
        atyp = request[3].toInt() and 0xff

        if (requestVersion != 5 || cmd != 1) throw SocksException("request error")

        when (atyp) {
            1 -> {
                /*buffer.limit(6)
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
                buffer.clear()*/

                val address = try {
                    readBuffer.read(4) ?: throw SocksException("unexpected stream end")
                } catch (e: IOException) {
                    throw SocksException("an IO error has occurred")
                }
                addr = InetAddress.getByAddress(address)

                port = try {
                    (readBuffer.readShort() ?: throw SocksException("unexpected stream end")).toInt()
                } catch (e: IOException) {
                    throw SocksException("an IO error has occurred")
                }

                targetAddress = ByteArray(1 + 4 + 2)
                targetAddress[0] = atyp.toByte()

                System.arraycopy(address, 0, targetAddress, 1, 4)

                val tmp = ByteArray(2)
                ByteBuffer.wrap(tmp).putShort(port!!.toShort())
                System.arraycopy(tmp, 0, targetAddress, 5, 2)
            }

            4 -> {
                /* buffer.limit(16 + 2)
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
                 buffer.clear()*/

                val address = try {
                    readBuffer.read(16) ?: throw SocksException("unexpected stream end")
                } catch (e: IOException) {
                    throw SocksException("an IO error has occurred")
                }
                addr = InetAddress.getByAddress(address)

                port = try {
                    (readBuffer.readShort() ?: throw SocksException("unexpected stream end")).toInt()
                } catch (e: IOException) {
                    throw SocksException("an IO error has occurred")
                }

                targetAddress = ByteArray(1 + 16 + 2)
                targetAddress[0] = atyp.toByte()

                System.arraycopy(address, 0, targetAddress, 1, 16)

                val tmp = ByteArray(2)
                ByteBuffer.wrap(tmp).putShort(port!!.toShort())
                System.arraycopy(tmp, 0, targetAddress, 17, 2)
            }

            3 -> {
                /*buffer.limit(1)
                if (socketChannel.aRead(buffer) <= 0) {
                    socketChannel.close()
                    throw SocksException("unexpected stream end")
                }

                buffer.flip()
                addrLength = buffer.get().toInt() and 0xff
                buffer.clear()*/

                addrLength = try {
                    (readBuffer.read() ?: throw SocksException("unexpected stream end")).toInt()
                } catch (e: IOException) {
                    throw SocksException("unexpected stream end")
                }

                /*val address = ByteArray(addrLength)
                buffer.limit(addrLength + 2)
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

                addr = InetAddress.getByName(String(address))
                port = buffer.short.toInt()
                buffer.clear()*/

                val address = try {
                    readBuffer.read(addrLength) ?: throw SocksException("unexpected stream end")
                } catch (e: IOException) {
                    throw SocksException("an IO error has occurred")
                }
                addr = InetAddress.getByName(String(address))

                port = try {
                    (readBuffer.readShort() ?: throw SocksException("unexpected stream end")).toInt()
                } catch (e: IOException) {
                    throw SocksException("an IO error has occurred")
                }

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

        val replyHeader = byteArrayOf(5, 0, 0, 1)
        val replyAddress = InetAddress.getByName("127.0.0.1").address
        /*buffer.put(replyAddress)
        buffer.putShort(1080)
        buffer.flip()
        try {
            socketChannel.aWrite(buffer)
        } catch (e: IOException) {
            throw SocksException("an IO error has occurred")
        }*/

        try {
            writeBuffer.write(replyHeader + replyAddress)
            writeBuffer.writeShort(0)
        } catch (e: IOException) {
            throw SocksException("an IO error has occurred")
        }

        isSuccessful = true
    }


    companion object {
        data class SocksInfo(val addr: String, val port: Int)

        fun build(targetAddress: ByteArray): SocksInfo {
            val atyp = targetAddress[0].toInt() and 0xff

            val addr: String
            val port: Int

            when (atyp) {
                1 -> {
                    addr = InetAddress.getByAddress(targetAddress.copyOfRange(1, 5)).hostAddress
                    port = ByteBuffer.wrap(targetAddress.copyOfRange(5, 7)).short.toInt()

                }

                3 -> {
                    val addrLength = targetAddress[1].toInt() and 0xff
                    addr = InetAddress.getByName(String(targetAddress.copyOfRange(2, 2 + addrLength))).hostAddress
                    port = ByteBuffer.wrap(targetAddress.copyOfRange(2 + addrLength, 4 + addrLength)).short.toInt()
                }

                4 -> {
                    addr = InetAddress.getByAddress(targetAddress.copyOfRange(1, 17)).hostAddress
                    port = ByteBuffer.wrap(targetAddress.copyOfRange(17, 19)).short.toInt()
                }

                else -> throw SocksException("atyp error: $atyp, can't not build socks info")
            }
            return SocksInfo(addr, port)
        }
    }
}