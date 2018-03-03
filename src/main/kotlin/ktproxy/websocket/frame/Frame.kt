package ktproxy.websocket.frame

import kotlinx.coroutines.experimental.nio.aRead
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.*
import kotlin.experimental.xor

class Frame(
        val frameType: FrameType,
        val contentType: FrameContentType,
        val content: ByteArray,
        private var maskKey: ByteArray? = null
) {
    var opcode = 1 shl 7
    val frameByteArray: ByteArray

    init {
        opcode = when (contentType) {
            FrameContentType.BINARY -> opcode or 0x2

            FrameContentType.TEXT -> opcode or 0x1

            FrameContentType.PING -> opcode or 0x9

            FrameContentType.PONG -> opcode or 0xA

            FrameContentType.CLOSE -> opcode or 0x8
        }

        val initPayloadLength: Int
        val payloadLength: Int

        when (frameType) {
            FrameType.CLIENT -> {
                if (maskKey == null) {
                    maskKey = ByteArray(4)
                    Random().nextBytes(maskKey)
                }

                val maskData = mask(maskKey!!, content)

                val frameBuffer: ByteBuffer

                when {
                    content.size <= 125 -> {
                        initPayloadLength = 1 shl 7 or content.size
                        payloadLength = content.size

                        frameByteArray = ByteArray(2 + 4 + payloadLength)

                        frameBuffer = ByteBuffer.wrap(frameByteArray)
                        frameBuffer.put(opcode.toByte())
                        frameBuffer.put(initPayloadLength.toByte())
                    }
                    content.size <= 65535 -> {
                        val tmp = ByteArray(2)
                        initPayloadLength = 1 shl 7 or 126
                        payloadLength = ByteBuffer.wrap(tmp).putShort(content.size.toShort()).flip().short.toInt()

                        frameByteArray = ByteArray(2 + 2 + 4 + payloadLength)

                        frameBuffer = ByteBuffer.wrap(frameByteArray)
                        frameBuffer.put(opcode.toByte())
                        frameBuffer.put(initPayloadLength.toByte())
                        frameBuffer.putShort(payloadLength.toShort())
                    }
                    else -> {
                        val tmp = ByteArray(8)
                        initPayloadLength = 1 shl 7 or 127
                        payloadLength = ByteBuffer.wrap(tmp).putLong(content.size.toLong()).flip().long.toInt()

                        frameByteArray = ByteArray(2 + 8 + 4 + payloadLength)

                        frameBuffer = ByteBuffer.wrap(frameByteArray)
                        frameBuffer.put(opcode.toByte())
                        frameBuffer.put(initPayloadLength.toByte())
                        frameBuffer.putLong(payloadLength.toLong())
                    }
                }

                frameBuffer.put(maskKey)
                frameBuffer.put(maskData)
            }

            FrameType.SERVER -> {
                maskKey = null
                val frameBuffer: ByteBuffer

                when {
                    content.size <= 125 -> {
                        initPayloadLength = content.size
                        payloadLength = initPayloadLength

                        frameByteArray = ByteArray(2 + payloadLength)

                        frameBuffer = ByteBuffer.wrap(frameByteArray)
                        frameBuffer.put(opcode.toByte())
                        frameBuffer.put(initPayloadLength.toByte())
//                        frameBuffer.put(content)
                    }
                    content.size <= 65535 -> {
                        val tmp = ByteArray(2)
                        initPayloadLength = 126
                        payloadLength = ByteBuffer.wrap(tmp).putShort(content.size.toShort()).flip().short.toInt()

                        frameByteArray = ByteArray(2 + 2 + payloadLength)

                        frameBuffer = ByteBuffer.wrap(frameByteArray)
                        frameBuffer.put(opcode.toByte())
                        frameBuffer.put(initPayloadLength.toByte())
                        frameBuffer.putShort(payloadLength.toShort())
//                        frameBuffer.put(content)
                    }
                    else -> {
                        val tmp = ByteArray(8)
                        initPayloadLength = 127
                        payloadLength = ByteBuffer.wrap(tmp).putLong(content.size.toLong()).flip().long.toInt()

                        frameByteArray = ByteArray(2 + 8 + payloadLength)

                        frameBuffer = ByteBuffer.wrap(frameByteArray)
                        frameBuffer.put(opcode.toByte())
                        frameBuffer.put(initPayloadLength.toByte())
                        frameBuffer.putLong(payloadLength.toLong())
//                        frameBuffer.put(content)
                    }
                }
                frameBuffer.put(content)
            }
        }
    }


    companion object {

        @Throws(IOException::class, FrameException::class)
        suspend fun buildFrame(socketChannel: AsynchronousSocketChannel, buffer: ByteBuffer, frameType: FrameType): Frame {
            var length = 0
            val contentType: FrameContentType

            buffer.limit(2)
            val frameHeader = ByteArray(2)
            while (length < 2) {
                val dataRead = socketChannel.aRead(buffer)
                if (dataRead <= 0) throw FrameException("unexpected stream end")
                length += dataRead
            }
            length = 0
            buffer.flip()
            buffer.get(frameHeader)
            buffer.clear()

            when (frameHeader[0].toInt() and 0xff) {
            // text frame
                1 shl 7 or 0x1 -> contentType = FrameContentType.TEXT

            // binary frame
                1 shl 7 or 0x2 -> contentType = FrameContentType.BINARY

            // ping frame
                1 shl 7 or 0x9 -> contentType = FrameContentType.PING

            // pong frame
                1 shl 7 or 0xA -> contentType = FrameContentType.PONG

            // close frame
                1 shl 7 or 0x8 -> contentType = FrameContentType.CLOSE

                else -> throw FrameException("unexpected frame content type")
            }

            val initPayloadLength = when (frameType) {
                FrameType.CLIENT -> frameHeader[1].toInt() and 0xff and 0x7F
                FrameType.SERVER -> frameHeader[1].toInt() and 0xff
            }

            val payloadLength = when {
                initPayloadLength <= 125 -> {
                    initPayloadLength
                }

                initPayloadLength == 126 -> {
                    buffer.limit(2)
                    while (length < 2) {
                        val dataRead = socketChannel.aRead(buffer)
                        if (dataRead <= 0) throw FrameException("unexpected stream end")
                        length += dataRead
                    }
                    length = 0
                    buffer.flip()
                    val result = buffer.short.toInt()
                    buffer.clear()
                    result
                }

                initPayloadLength == 127 -> {
//                    ByteBuffer.wrap(readsBuffer.readExactly(8)).long.toInt()
                    buffer.limit(8)
                    while (length < 8) {
                        val dataRead = socketChannel.aRead(buffer)
                        if (dataRead <= 0) throw FrameException("unexpected stream end")
                        length += dataRead
                    }
                    length = 0
                    buffer.flip()
                    val result = buffer.long.toInt()
                    buffer.clear()
                    result
                }

                else -> throw FrameException("unexpected init payload length: $initPayloadLength")
            }

            when (frameType) {
                FrameType.CLIENT -> {
                    buffer.limit(4 + payloadLength)
                    val maskKey = ByteArray(4)
                    val data = ByteArray(payloadLength)
                    while (length < 4 + payloadLength) {
                        val dataRead = socketChannel.aRead(buffer)
                        if (dataRead <= 0) throw FrameException("unexpected stream end")
                        length += dataRead
                    }
                    buffer.flip()
                    buffer.get(maskKey)
                    buffer.get(data)
                    buffer.clear()

                    return Frame(frameType, contentType, mask(maskKey, data), maskKey)
                }

                FrameType.SERVER -> {
                    buffer.limit(payloadLength)
                    val data = ByteArray(payloadLength)
                    while (length < payloadLength) {
                        val dataRead = socketChannel.aRead(buffer)
                        if (dataRead <= 0) throw FrameException("unexpected stream end")
                        length += dataRead
                    }
                    buffer.flip()
                    buffer.get(data)
                    buffer.clear()

                    return Frame(frameType, contentType, data)
                }
            }
        }


        private fun mask(maskKey: ByteArray, data: ByteArray): ByteArray {
            val maskedData = ByteArray(data.size)
            for (i in 0 until data.size) {
                maskedData[i] = data[i] xor maskKey[i % 4]
            }
            return maskedData
        }
    }
}