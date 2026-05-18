package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object LocalDateAsStringSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateAsString", PrimitiveKind.STRING)

    /**
     * Encode a local date as its ISO-8601 string representation.
     *
     * @param encoder The kotlinx.serialization encoder receiving the string.
     * @param value The local date to serialize.
     */
    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    /**
     * Decode a local date from its ISO-8601 string representation.
     *
     * @param decoder The kotlinx.serialization decoder providing the string.
     */
    override fun deserialize(decoder: Decoder): LocalDate {
        return LocalDate.parse(decoder.decodeString())
    }
}

object LocalTimeAsStringSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalTimeAsString", PrimitiveKind.STRING)

    /**
     * Encode a local time as its ISO-8601 string representation.
     *
     * @param encoder The kotlinx.serialization encoder receiving the string.
     * @param value The local time to serialize.
     */
    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.toString())
    }

    /**
     * Decode a local time from its ISO-8601 string representation.
     *
     * @param decoder The kotlinx.serialization decoder providing the string.
     */
    override fun deserialize(decoder: Decoder): LocalTime {
        return LocalTime.parse(decoder.decodeString())
    }
}

object ZoneIdAsStringSerializer : KSerializer<ZoneId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ZoneIdAsString", PrimitiveKind.STRING)

    /**
     * Encode a time zone by its stable zone id.
     *
     * @param encoder The kotlinx.serialization encoder receiving the zone id string.
     * @param value The zone id to serialize.
     */
    override fun serialize(encoder: Encoder, value: ZoneId) {
        encoder.encodeString(value.id)
    }

    /**
     * Decode a time zone from its stable zone id.
     *
     * @param decoder The kotlinx.serialization decoder providing the zone id string.
     */
    override fun deserialize(decoder: Decoder): ZoneId {
        return ZoneId.of(decoder.decodeString())
    }
}
