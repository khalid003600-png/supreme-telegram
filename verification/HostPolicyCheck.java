import com.wolfox.gps.util.WFHostPolicy;
public class HostPolicyCheck {
 public static void main(String[] args) {
  for(int i=1;i<=5;i++) if(WFHostPolicy.permits("com.wolfox.gps.variant"+i)) throw new AssertionError("Module must exclude itself: "+i);
  if(WFHostPolicy.permits("com.wolfox.gps") || WFHostPolicy.permits("com.android.systemui") || WFHostPolicy.permits(null)) throw new AssertionError("Forbidden host admitted");
  if(!WFHostPolicy.permits("org.example.ownedhost")) throw new AssertionError("Owned host rejected");
  System.out.println("PASS: module self-exclusion, OS exclusions, valid host.");
 }
}
