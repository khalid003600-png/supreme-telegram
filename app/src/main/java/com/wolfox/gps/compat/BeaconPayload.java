package com.wolfox.gps.compat;

import java.nio.ByteBuffer;
import java.util.UUID;

/** iBeacon manufacturer payload, excluding the manufacturer ID supplied to Android. */
public final class BeaconPayload {
    private BeaconPayload() {}
    public static UUID parseUuid(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("UUID غير صالح");
        }
        return UUID.fromString(value);
    }
    public static byte[] encode(String id, int major, int minor, int power) {
        UUID uuid = parseUuid(id);
        if (major < 0 || major > 65535 || minor < 0 || minor > 65535
                || power < -128 || power > 127) throw new IllegalArgumentException("قيمة خارج النطاق");
        return ByteBuffer.allocate(23).put((byte) 0x02).put((byte) 0x15)
                .putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits())
                .putShort((short) major).putShort((short) minor).put((byte) power).array();
    }
}
