package com.astrastream.avpush.infrastructure.stream.amf

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * AMF Number data type implementation
 *
 * Represents a numeric value in AMF format. All numbers in AMF are
 * represented as IEEE 64-bit double precision floating point numbers.
 */
class AmfNumber @JvmOverloads constructor(
    var value: Double = 0.0
) : AmfData {

    override val size: Int = SIZE

    override val bytes: ByteArray
        get() {
            val buffer = ByteBuffer.allocate(SIZE)
            buffer.put(AmfType.NUMBER.value)
            buffer.putDouble(value)
            return buffer.array()
        }

    @Throws(IOException::class)
    override fun writeTo(out: OutputStream) {
        out.write(AmfType.NUMBER.value.toInt())
        AmfUtil.writeDouble(out, value)
    }

    @Throws(IOException::class)
    override fun readFrom(input: InputStream) {
        // Skip data type byte (we assume it's already read)
        value = AmfUtil.readDouble(input)
    }

    companion object {
        /** Size of an AMF number, in bytes (including type bit) */
        const val SIZE = 9

        /**
         * Read a number from input stream
         * @param input Input stream to read from
         * @return The numeric value
         */
        @JvmStatic
        @Throws(IOException::class)
        fun readNumberFrom(input: InputStream): Double {
            // Skip data type byte
            input.read()
            return AmfUtil.readDouble(input)
        }

        /**
         * Write a number to output stream
         * @param out Output stream to write to
         * @param number Number to write
         */
        @JvmStatic
        @Throws(IOException::class)
        fun writeNumberTo(out: OutputStream, number: Double) {
            out.write(AmfType.NUMBER.value.toInt())
            AmfUtil.writeDouble(out, number)
        }
    }

    override fun toString(): String = "AmfNumber(value=$value)"
}