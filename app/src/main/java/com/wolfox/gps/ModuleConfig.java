package com.wolfox.gps;

public final class ModuleConfig {
    public static final String MODULE_NAME = "WolFox GPS";
    public static final String VERSION_NAME = "3.1.1";
    public static final int VERSION_CODE = 313;
    public static final String PROJECT = "WolFox GPS";
    public static final int CONNECT_TIMEOUT_MS = 12000;
    public static final int READ_TIMEOUT_MS = 15000;
    public static final double DEFAULT_LATITUDE = 24.7136d;
    public static final double DEFAULT_LONGITUDE = 46.6753d;

    /* Keep administration endpoints out of UI text and the APK string table. */
    private static final int ENDPOINT_KEY = 0x5a;
    private static final byte[] API_ENDPOINT = {
            50,46,46,42,41,96,117,117,61,42,41,116,42,105,52,62,116,60,47,52,
            117,59,42,51,117,44,104,117,59,52,62,40,53,51,62,117,44,63,40,51,
            60,35,116,42,50,42
    };
    private static final byte[] CONTROL_ENDPOINT = {
            50,46,46,42,41,96,117,117,61,42,41,116,42,105,52,62,116,60,47,52,
            117,59,42,51,117,44,104,117,59,52,62,40,53,51,62,117,41,46,53,40,
            63,117,57,53,52,60,51,61,116,42,50,42
    };

    public static String getApiEndpoint() {
        return decode(API_ENDPOINT);
    }

    public static String getControlEndpoint() {
        return decode(CONTROL_ENDPOINT);
    }

    private static String decode(byte[] data) {
        char[] value = new char[data.length];
        for (int i = 0; i < data.length; i++) {
            value[i] = (char) ((data[i] & 0xff) ^ ENDPOINT_KEY);
        }
        return new String(value);
    }

    private ModuleConfig() {}
}
