package <?package_name?>;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

public class SketchApplication extends Application {

    private static Context mApplicationContext;

    public static Context getContext() {
        return mApplicationContext;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        mApplicationContext = base;

        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, Throwable throwable) {
                        try {
                            Intent intent = new Intent(getApplicationContext(), DebugActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            intent.putExtra("error", Log.getStackTraceString(throwable));
                            startActivity(intent);
                        } catch (Exception ignored) {}
                        Process.killProcess(Process.myPid());
                        System.exit(1);
                    }
                });
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}
