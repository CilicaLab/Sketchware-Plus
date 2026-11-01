package mod.jbk.util;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import sketchware.plus.utility.FileUtil;

public class LogUtil {
    private static boolean loggingEnabled = true;
    private static boolean logToLogcatToo = false;

    public static void setLoggingEnabled(boolean enabled) {
        loggingEnabled = enabled;
    }

    public static void setLogToLogcatToo(boolean log) {
        logToLogcatToo = log;
    }

    public static void d(String tag, String message) {
        d(tag, message, null);
    }

    public static void d(String tag, String message, Throwable throwable) {
        LogUtil.println('D', tag, message, throwable);
    }

    public static void w(String tag, String message) {
        LogUtil.w(tag, message, null);
    }

    public static void w(String tag, String message, Throwable throwable) {
        LogUtil.println('W', tag, message, throwable);
    }

    public static void e(String tag, String message) {
        LogUtil.e(tag, message, null);
    }

    public static void e(String tag, String message, Throwable throwable) {
        LogUtil.println('E', tag, message, throwable);
    }

    private static void println(char type, String tag, String message, Throwable throwable) {
        if (loggingEnabled) {
            StringBuilder toLog = new StringBuilder(getDateAndTime(System.currentTimeMillis()));
            toLog.append(" ");
            toLog.append(type);
            toLog.append('/');
            toLog.append(tag);
            toLog.append(": ");
            toLog.append(message);

            if (throwable != null) {
                toLog.append('\n');
                toLog.append(Log.getStackTraceString(throwable));
            }

            System.out.println(toLog);

            // Log errors and warnings directly to data.log
            if (type == 'E' || type == 'W' || throwable != null) {
                logToDataLogFile(toLog.toString());
            }
        }

        if (logToLogcatToo) {
            switch (type) {
                case 'D':
                    Log.d(tag, message, throwable);
                    break;

                case 'W':
                    Log.w(tag, message, throwable);
                    break;

                case 'E':
                    Log.e(tag, message, throwable);
                    break;
            }
        }
    }

    private static void logToDataLogFile(String formattedLog) {
        try {
            String baseDir = FileUtil.getExternalStorageDir();
            if (baseDir != null && !baseDir.isEmpty()) {
                String[] targets = new String[]{
                        baseDir + File.separator + ".sketchware" + File.separator + "data" + File.separator + "data.log",
                        baseDir + File.separator + ".sketchware" + File.separator + "mysc" + File.separator + "data.log"
                };

                for (String path : targets) {
                    try {
                        File file = new File(path);
                        if (file.getParentFile() != null && !file.getParentFile().exists()) {
                            file.getParentFile().mkdirs();
                        }
                        if (!file.exists()) {
                            file.createNewFile();
                        }
                        FileWriter writer = new FileWriter(file, true);
                        writer.write(formattedLog + "\n");
                        writer.close();
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private static String getDateAndTime(long millis) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
        return simpleDateFormat.format(new Date(millis));
    }

    public static void log(String tag, String prefixIfOneLiner, String logMessageIfMultipleLines, ArrayList<String> list) {
        String toLog = list.toString();
        if (toLog.length() > 1023) {
            ArrayList<String> loggableStrings = new ArrayList<>();
            int beginIndex = 0;
            while (beginIndex < (toLog.length() - (toLog.length() % 1024))) {
                loggableStrings.add(toLog.substring(beginIndex, beginIndex + 1024));
                beginIndex += 1024;
            }
            loggableStrings.add(toLog.substring(beginIndex));

            Log.d(tag, logMessageIfMultipleLines);
            for (String loggableString : loggableStrings) {
                Log.d(tag, loggableString);
            }
        } else {
            Log.d(tag, prefixIfOneLiner + list);
        }
    }
}
