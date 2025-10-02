package com.astrastream.avpush.infrastructure.stream.amf

/**
 * AMF (Action Message Format) data types
 *
 * Defines the standard AMF data types used in RTMP streaming protocol.
 * Each type has a corresponding byte value for wire protocol encoding.
 */
enum class AmfType(val value: Byte) {
    /** Number (encoded as IEEE 64-bit double precision floating point number) */
    NUMBER(0x00),

    /** Boolean (Encoded as a single byte of value 0x00 or 0x01) */
    BOOLEAN(0x01),

    /** String (ASCII encoded) */
    STRING(0x02),

    /** Object - set of key/value pairs */
    OBJECT(0x03),

    /** Null value */
    NULL(0x05),

    /** Undefined value */
    UNDEFINED(0x06),

    /** Map/Dictionary */
    MAP(0x08),

    /** Array */
    ARRAY(0x0A);

    companion object {
        private val lookupMap = values().associateBy { it.value }

        /**
         * Get AmfType by byte value
         * @param value The byte value to look up
         * @return The corresponding AmfType or null if not found
         */
        fun fromByte(value: Byte): AmfType? = lookupMap[value]

        /**
         * Get AmfType by byte value with exception on failure
         * @param value The byte value to look up
         * @return The corresponding AmfType
         * @throws IllegalArgumentException if value is not recognized
         */
        fun fromByteOrThrow(value: Byte): AmfType =
            fromByte(value) ?: throw IllegalArgumentException("Unknown AMF type: 0x${value.toString(16)}")
    }
}