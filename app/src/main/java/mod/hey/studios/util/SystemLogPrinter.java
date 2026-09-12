package mod.hey.studios.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import kellinwood.logging.LogManager;
import kellinwood.logging.Logger;
import mod.jbk.util.LogUtil;
import sketchware.plus.utility.FileUtil;

public class SystemLogPrinter {

    private static final String PATH = FileUtil.getExternalStorageDir().concat("/.sketchware/debug.txt");
    private static PrintStream ps;
    private static final StringBuilder capturedLogs = new StringBuilder();

    public static void start() {
        start(PATH);
    }

    public static void start(String path) {
        synchronized (capturedLogs) {
            capturedLogs.setLength(0);
        }
        // Remove logging in Kellinwood's zipsigner
        LogManager.setLoggerFactory(category -> new Logger() {
            @Override
            public boolean isErrorEnabled() {
                return false;
            }

            @Override
            public void error(String message) {
            }

            @Override
            public void error(String message, Throwable t) {
            }

            @Override
            public boolean isWarnEnabled() {
                return false;
            }

            @Override
            public void warn(String message) {
            }

            @Override
            public void warn(String message, Throwable t) {
            }

            @Override
            public boolean isInfoEnabled() {
                return false;
            }

            @Override
            public void info(String message) {
            }

            @Override
            public void info(String message, Throwable t) {
            }

            @Override
            public boolean isDebugEnabled() {
                return false;
            }

            @Override
            public void debug(String message) {
            }

            @Override
            public void debug(String message, Throwable t) {
            }
        });

        // Reset the log file
        FileUtil.writeFile(path, "");

        try {
            // Use FileOutputStream instead of FileWriter
            ps = new PrintStream(new FileOutputStream(path, true), true) {
                @Override
                public void write(int b) {
                    super.write(b);
                    synchronized (capturedLogs) {
                        capturedLogs.append((char) b);
                    }
                }

                @Override
                public void write(byte[] buf, int off, int len) {
                    super.write(buf, off, len);
                    synchronized (capturedLogs) {
                        capturedLogs.append(new String(buf, off, len));
                    }
                }
            };
            System.setOut(ps);
            System.setErr(ps);
        } catch (IOException e) {
            LogUtil.e("SystemLogPrinter", "IOException while creating PrintStream to " + path, e);
        }
    }

    public static String getCapturedLogs() {
        synchronized (capturedLogs) {
            return capturedLogs.toString();
        }
    }

    public static void stop() {
        if (ps != null) {
            ps.close();
            ps = null;
        }
    }
}
