package com.astrastream.avpush.infrastructure.stream.amf

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * AMF Boolean data type implementation
 */
class AmfBoolean @JvmOverloads constructor(
    var value: Boolean = false
) : AmfData {

    override val size: Int = SIZE

    override val bytes: ByteArray
        get() = byteArrayOf(AmfType.BOOLEAN.value, if (value) 0x01 else 0x00)

    @Throws(IOException::class)
    override fun writeTo(out: OutputStream) {
        out.write(AmfType.BOOLEAN.value.toInt())
        out.write(if (value) 0x01 else 0x00)
    }

    @Throws(IOException::class)
    override fun readFrom(input: InputStream) {
        // Skip data type byte (we assume it's already read)
        value = input.read() == 0x01
    }

    companion object {
        const val SIZE = 2
    }

    override fun toString(): String = "AmfBoolean(value=$value)"
}

/**
 * AMF Null data type implementation
 */
object AmfNull : AmfData {
    const val SIZE = 1

    override val size: Int = SIZE

    override val bytes: ByteArray
        get() = byteArrayOf(AmfType.NULL.value)

    @Throws(IOException::class)
    override fun writeTo(out: OutputStream) {
        out.write(AmfType.NULL.value.toInt())
    }

    @Throws(IOException::class)
    override fun readFrom(input: InputStream) {
        // Skip data type byte (we assume it's already read)
        // No additional data for null
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeNullTo(out: OutputStream) {
        out.write(AmfType.NULL.value.toInt())
    }

    override fun toString(): String = "AmfNull"
}

/**
 * AMF Undefined data type implementation
 */
object AmfUndefined : AmfData {
    const val SIZE = 1

    override val size: Int = SIZE

    override val bytes: ByteArray
        get() = byteArrayOf(AmfType.UNDEFINED.value)

    @Throws(IOException::class)
    override fun writeTo(out: OutputStream) {
        out.write(AmfType.UNDEFINED.value.toInt())
    }

    @Throws(IOException::class)
    override fun readFrom(input: InputStream) {
        // Skip data type byte (we assume it's already read)
        // No additional data for undefined
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeUndefinedTo(out: OutputStream) {
        out.write(AmfType.UNDEFINED.value.toInt())
    }

    override fun toString(): String = "AmfUndefined"
}