package com.astrastream.avpush.infrastructure.stream.amf

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * AMF binary data utilities
 *
 * Provides utility functions for reading/writing binary data in AMF format.
 * All functions handle big-endian byte order as per AMF specification.
 */
object AmfUtil {

    // Write operations

    @Throws(IOException::class)
    fun writeUnsignedInt32(out: OutputStream, value: Int) {
        out.write(byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        ))
    }

    @Throws(IOException::class)
    fun writeUnsignedInt24(out: OutputStream, value: Int) {
        out.write(byteArrayOf(
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        ))
    }

    @Throws(IOException::class)
    fun writeUnsignedInt16(out: OutputStream, value: Int) {
        out.write(byteArrayOf(
            (value ushr 8).toByte(),
            value.toByte()
        ))
    }

    @Throws(IOException::class)
    fun writeUnsignedInt32LittleEndian(out: OutputStream, value: Int) {
        out.write(byteArrayOf(
            value.toByte(),
            (value ushr 8).toByte(),
            (value ushr 16).toByte(),
            (value ushr 24).toByte()
        ))
    }

    @Throws(IOException::class)
    fun writeDouble(out: OutputStream, value: Double) {
        val bits = java.lang.Double.doubleToRawLongBits(value)
        out.write(byteArrayOf(
            (bits ushr 56).toByte(),
            (bits ushr 48).toByte(),
            (bits ushr 40).toByte(),
            (bits ushr 32).toByte(),
            (bits ushr 24).toByte(),
            (bits ushr 16).toByte(),
            (bits ushr 8).toByte(),
            bits.toByte()
        ))
    }

    // Read operations

    @Throws(IOException::class)
    fun readUnsignedInt32(input: InputStream): Int {
        return ((input.read() and 0xff) shl 24) or
                ((input.read() and 0xff) shl 16) or
                ((input.read() and 0xff) shl 8) or
                (input.read() and 0xff)
    }

    @Throws(IOException::class)
    fun readUnsignedInt24(input: InputStream): Int {
        return ((input.read() and 0xff) shl 16) or
                ((input.read() and 0xff) shl 8) or
                (input.read() and 0xff)
    }

    @Throws(IOException::class)
    fun readUnsignedInt16(input: InputStream): Int {
        return ((input.read() and 0xff) shl 8) or (input.read() and 0xff)
    }

    @Throws(IOException::class)
    fun readDouble(input: InputStream): Double {
        val bits = ((input.read().toLong() and 0xff) shl 56) or
                ((input.read().toLong() and 0xff) shl 48) or
                ((input.read().toLong() and 0xff) shl 40) or
                ((input.read().toLong() and 0xff) shl 32) or
                ((input.read().toLong() and 0xff) shl 24) or
                ((input.read().toLong() and 0xff) shl 16) or
                ((input.read().toLong() and 0xff) shl 8) or
                (input.read().toLong() and 0xff)
        return java.lang.Double.longBitsToDouble(bits)
    }

    // Byte array conversion operations

    fun toUnsignedInt32(bytes: ByteArray): Int {
        require(bytes.size >= 4) { "Need at least 4 bytes for Int32" }
        return ((bytes[0].toInt() and 0xff) shl 24) or
                ((bytes[1].toInt() and 0xff) shl 16) or
                ((bytes[2].toInt() and 0xff) shl 8) or
                (bytes[3].toInt() and 0xff)
    }

    fun toUnsignedInt32LittleEndian(bytes: ByteArray): Int {
        require(bytes.size >= 4) { "Need at least 4 bytes for Int32" }
        return ((bytes[3].toInt() and 0xff) shl 24) or
                ((bytes[2].toInt() and 0xff) shl 16) or
                ((bytes[1].toInt() and 0xff) shl 8) or
                (bytes[0].toInt() and 0xff)
    }

    fun toUnsignedInt24(bytes: ByteArray): Int {
        require(bytes.size >= 4) { "Need at least 4 bytes for Int24 (offset)" }
        return ((bytes[1].toInt() and 0xff) shl 16) or
                ((bytes[2].toInt() and 0xff) shl 8) or
                (bytes[3].toInt() and 0xff)
    }

    fun toUnsignedInt16(bytes: ByteArray): Int {
        require(bytes.size >= 4) { "Need at least 4 bytes for Int16 (offset)" }
        return ((bytes[2].toInt() and 0xff) shl 8) or (bytes[3].toInt() and 0xff)
    }

    fun unsignedInt32ToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        )
    }

    fun doubleToByteArray(value: Double): ByteArray {
        val bits = java.lang.Double.doubleToRawLongBits(value)
        return byteArrayOf(
            (bits ushr 56).toByte(),
            (bits ushr 48).toByte(),
            (bits ushr 40).toByte(),
            (bits ushr 32).toByte(),
            (bits ushr 24).toByte(),
            (bits ushr 16).toByte(),
            (bits ushr 8).toByte(),
            bits.toByte()
        )
    }

    // Utility operations

    @Throws(IOException::class)
    fun readBytesUntilFull(input: InputStream, targetBuffer: ByteArray) {
        var totalBytesRead = 0
        val targetBytes = targetBuffer.size

        while (totalBytesRead < targetBytes) {
            val bytesRead = input.read(targetBuffer, totalBytesRead, targetBytes - totalBytesRead)
            if (bytesRead == -1) {
                throw IOException("Unexpected EOF reached before read buffer was filled")
            }
            totalBytesRead += bytesRead
        }
    }

    fun toHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    fun toHexString(byte: Byte): String {
        return "%02X".format(byte)
    }
}