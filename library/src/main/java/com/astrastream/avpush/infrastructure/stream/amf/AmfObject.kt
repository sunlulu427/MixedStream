package com.astrastream.avpush.infrastructure.stream.amf

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * AMF Object base class - simplified for compatibility
 */
open class AmfObject : AmfData {
    protected val properties = LinkedHashMap<String, AmfData>()
    protected var cachedSize: Int = -1

    companion object {
        /** Byte sequence that marks the end of an AMF object */
        val OBJECT_END_MARKER = byteArrayOf(0x00, 0x00, 0x09)
    }

    override val size: Int
        get() {
            if (cachedSize == -1) {
                cachedSize = 1 // Type byte
                properties.forEach { (key, value) ->
                    cachedSize += AmfString.sizeOf(key, true) + value.size
                }
                cachedSize += OBJECT_END_MARKER.size
            }
            return cachedSize
        }

    override val bytes: ByteArray
        get() {
            val buffer = ByteBuffer.allocate(size)
            buffer.put(AmfType.OBJECT.value)

            properties.forEach { (key, value) ->
                AmfString.writeStringTo(buffer.asOutputStream(), key, true)
                value.writeTo(buffer.asOutputStream())
            }

            buffer.put(OBJECT_END_MARKER)
            return buffer.array()
        }

    // Property accessors
    fun getProperty(key: String): AmfData? = properties[key]

    fun setProperty(key: String, value: AmfData) {
        properties[key] = value
        cachedSize = -1
    }

    fun setProperty(key: String, value: Boolean) {
        setProperty(key, AmfBoolean(value))
    }

    fun setProperty(key: String, value: String) {
        setProperty(key, AmfString(value, false))
    }

    fun setProperty(key: String, value: Int) {
        setProperty(key, AmfNumber(value.toDouble()))
    }

    fun setProperty(key: String, value: Double) {
        setProperty(key, AmfNumber(value))
    }

    @Throws(IOException::class)
    override fun writeTo(out: OutputStream) {
        out.write(AmfType.OBJECT.value.toInt())

        properties.forEach { (key, value) ->
            AmfString.writeStringTo(out, key, true)
            value.writeTo(out)
        }

        out.write(OBJECT_END_MARKER)
    }

    @Throws(IOException::class)
    override fun readFrom(input: InputStream) {
        // Simplified implementation - in real usage would need proper parsing
        cachedSize = 1
    }

    // Extension function to convert ByteBuffer to OutputStream
    private fun ByteBuffer.asOutputStream() = object : OutputStream() {
        override fun write(b: Int) {
            put(b.toByte())
        }

        override fun write(b: ByteArray) {
            put(b)
        }
    }
}

/**
 * AMF Map - extends AmfObject with array size prefix
 */
class AmfMap : AmfObject() {
    override val size: Int
        get() = super.size + 4 // Add 4 bytes for array length

    override val bytes: ByteArray
        get() {
            val buffer = ByteBuffer.allocate(size)
            buffer.put(AmfType.MAP.value)
            buffer.putInt(properties.size)

            properties.forEach { (key, value) ->
                AmfString.writeStringTo(buffer.asOutputStream(), key, true)
                value.writeTo(buffer.asOutputStream())
            }

            buffer.put(OBJECT_END_MARKER)
            return buffer.array()
        }

    @Throws(IOException::class)
    override fun writeTo(out: OutputStream) {
        out.write(AmfType.MAP.value.toInt())

        // Write array size
        AmfUtil.writeUnsignedInt32(out, properties.size)

        properties.forEach { (key, value) ->
            AmfString.writeStringTo(out, key, true)
            value.writeTo(out)
        }

        out.write(OBJECT_END_MARKER)
    }

    // Extension function helper
    private fun ByteBuffer.asOutputStream() = object : OutputStream() {
        override fun write(b: Int) {
            put(b.toByte())
        }

        override fun write(b: ByteArray) {
            put(b)
        }
    }
}