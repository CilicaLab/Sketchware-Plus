package sketchware.plus.utility;

import android.content.Context;
import com.google.firebase.FirebaseApp;

public class FirebaseUtil {
    public static boolean isFirebaseInitialized(Context context) {
        try {
            return !FirebaseApp.getApps(context).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
