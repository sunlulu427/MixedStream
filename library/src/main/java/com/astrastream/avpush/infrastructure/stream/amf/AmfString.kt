package com.astrastream.avpush.infrastructure.stream.amf

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * AMF String data type implementation
 *
 * Represents a string value in AMF format. Strings can be used as object keys
 * (without type prefix) or as regular values (with type prefix).
 */
class AmfString @JvmOverloads constructor(
    var value: String = "",
    var isKey: Boolean = false
) : AmfData {

    constructor(isKey: Boolean) : this("", isKey)

    private var cachedSize: Int = -1

    override val size: Int
        get() {
            if (cachedSize == -1) {
                cachedSize = (if (isKey) 0 else 1) + 2 + value.toByteArray().size
            }
            return cachedSize
        }

    override val bytes: ByteArray
        get() {
            val buffer = ByteBuffer.allocate(size)
            if (!isKey) {
                buffer.put(AmfType.STRING.value)
            }
            val valueBytes = value.toByteArray()
            buffer.putShort(valueBytes.size.toShort())
            buffer.put(valueBytes)
            return buffer.array()
        }

    @Throws(IOException::class)
    override fun writeTo(out: OutputStream) {
        val valueBytes = value.toByteArray()

        // Write the STRING data type definition (except if this String is used as a key)
        if (!isKey) {
            out.write(AmfType.STRING.value.toInt())
        }

        // Write 2 bytes indicating string length
        AmfUtil.writeUnsignedInt16(out, valueBytes.size)

        // Write string
        out.write(valueBytes)
    }

    @Throws(IOException::class)
    override fun readFrom(input: InputStream) {
        // Skip data type byte (we assume it's already read)
        val length = AmfUtil.readUnsignedInt16(input)
        cachedSize = 3 + length // 1 + 2 + length

        // Read string value
        val valueBytes = ByteArray(length)
        AmfUtil.readBytesUntilFull(input, valueBytes)
        value = String(valueBytes)
    }

    companion object {
        /**
         * Read a string from input stream
         * @param input Input stream to read from
         * @param isKey Whether this string is used as a key (no type prefix)
         * @return The string value
         */
        @JvmStatic
        @Throws(IOException::class)
        fun readStringFrom(input: InputStream, isKey: Boolean): String {
            if (!isKey) {
                // Read past the data type byte
                input.read()
            }
            val length = AmfUtil.readUnsignedInt16(input)

            // Read string value
            val valueBytes = ByteArray(length)
            AmfUtil.readBytesUntilFull(input, valueBytes)
            return String(valueBytes)
        }

        /**
         * Write a string to output stream
         * @param out Output stream to write to
         * @param string String to write
         * @param isKey Whether this string is used as a key (no type prefix)
         */
        @JvmStatic
        @Throws(IOException::class)
        fun writeStringTo(out: OutputStream, string: String, isKey: Boolean) {
            val valueBytes = string.toByteArray()

            // Write the STRING data type definition (except if this String is used as a key)
            if (!isKey) {
                out.write(AmfType.STRING.value.toInt())
            }

            // Write 2 bytes indicating string length
            AmfUtil.writeUnsignedInt16(out, valueBytes.size)

            // Write string
            out.write(valueBytes)
        }

        /**
         * Calculate the byte size of the resulting AMF string
         * @param string The string value
         * @param isKey Whether this string is used as a key
         * @return The byte size
         */
        @JvmStatic
        fun sizeOf(string: String, isKey: Boolean): Int {
            return (if (isKey) 0 else 1) + 2 + string.toByteArray().size
        }
    }

    override fun toString(): String = "AmfString(value='$value', isKey=$isKey)"
}