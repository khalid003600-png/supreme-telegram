import com.wolfox.gps.compat.BeaconPayload;
import java.util.Arrays;
public final class BeaconPayloadCheck {
    public static void main(String[] args) {
        byte[] expected = new byte[] {2,21,0,17,34,51,68,85,102,119,(byte)136,(byte)153,(byte)170,(byte)187,(byte)204,(byte)221,(byte)238,(byte)255,0,1,(byte)255,(byte)255,(byte)197};
        byte[] result = BeaconPayload.encode("00112233-4455-6677-8899-aabbccddeeff", 1, 65535, -59);
        if (!Arrays.equals(result, expected)) throw new AssertionError("iBeacon byte order/length mismatch");
        reject(() -> BeaconPayload.encode("1-1-1-1-1", 1, 1, -59));
        reject(() -> BeaconPayload.encode("00112233-4455-6677-8899-aabbccddeeff", -1, 1, -59));
        reject(() -> BeaconPayload.encode("00112233-4455-6677-8899-aabbccddeeff", 1, 65536, -59));
        reject(() -> BeaconPayload.encode("00112233-4455-6677-8899-aabbccddeeff", 1, 1, 128));
        System.out.println("PASS: exact iBeacon bytes and invalid input rejection");
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid input rejection");
    }
}
