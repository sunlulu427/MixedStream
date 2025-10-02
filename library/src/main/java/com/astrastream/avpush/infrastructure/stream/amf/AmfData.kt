package com.astrastream.avpush.infrastructure.stream.amf

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Base interface for all AMF data types
 *
 * Defines the contract for serialization/deserialization of AMF data
 * following the AMF specification for RTMP protocol.
 */
interface AmfData {
    /**
     * Write/Serialize this AMF data instance to the specified OutputStream
     *
     * @param out The output stream to write to
     * @throws IOException if writing fails
     */
    @Throws(IOException::class)
    fun writeTo(out: OutputStream)

    /**
     * Read and parse bytes from the specified input stream to populate this
     * AmfData instance (deserialize)
     *
     * @param input The input stream to read from
     * @throws IOException if reading fails
     */
    @Throws(IOException::class)
    fun readFrom(input: InputStream)

    /**
     * Get the size in bytes required for this object when serialized
     *
     * @return The byte size of this object
     */
    val size: Int

    /**
     * Get the byte array representation of this object
     *
     * @return The bytes of this object
     */
    val bytes: ByteArray
}