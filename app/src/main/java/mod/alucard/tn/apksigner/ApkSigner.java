package mod.alucard.tn.apksigner;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.apksigner.ApkSignerTool;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import mod.jbk.build.BuiltInLibraries;

public class ApkSigner {

    private static final File EXTRACTED_TESTKEY_FILES_DIRECTORY = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "testkey");

    /**
     * Sign an APK with testkey.
     *
     * @param inputPath  The APK file to sign
     * @param outputPath File to output the signed APK to
     * @param callback   Callback for System.out during signing. May be null
     */
    public void signWithTestKey(@NonNull String inputPath, @NonNull String outputPath, @Nullable LogCallback callback) {
        try (LogWriter logger = new LogWriter(callback)) {
            long savedTimeMillis = System.currentTimeMillis();
            PrintStream oldOut = System.out;

            List<String> args = Arrays.asList(
                    "sign",
                    "--in",
                    inputPath,
                    "--out",
                    outputPath,
                    "--key",
                    new File(EXTRACTED_TESTKEY_FILES_DIRECTORY, "testkey.pk8").getAbsolutePath(),
                    "--cert",
                    new File(EXTRACTED_TESTKEY_FILES_DIRECTORY, "testkey.x509.pem").getAbsolutePath()
            );

            logger.write("Signing an APK file with these arguments: " + args);

            /* If the signing has a callback, we need to change System.out to our logger */
            if (callback != null) {
                try (PrintStream stream = new PrintStream(logger)) {
                    System.setOut(stream);
                }
            }

            try {
                ApkSignerTool.main(args.toArray(new String[0]));
            } catch (Exception e) {
                LogCallback.errorCount.incrementAndGet();
                logger.write("An error occurred while trying to sign the APK file " + inputPath +
                        " and outputting it to " + outputPath + ": " + e.getMessage() + "\n" +
                        "Stack trace: " + Log.getStackTraceString(e));
            }

            logger.write("Signing an APK file took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");

            if (callback != null) {
                System.setOut(oldOut);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void signWithKeyStore(@NonNull String inputFilePath, @NonNull String outputFilePath,
                                 @NonNull String keyStorePath, @NonNull String keyStorePassword,
                                 @NonNull String keyStoreKeyAlias, @NonNull String keyPassword, @Nullable LogCallback callback) {
        signWithCustomKeyStore(inputFilePath, outputFilePath, keyStorePath, keyStorePassword, keyStoreKeyAlias, keyPassword, true, true, true, callback);
    }

    public void signWithCustomKeyStore(@NonNull String inputFilePath, @NonNull String outputFilePath,
                                        @NonNull String keyStorePath, @NonNull String keyStorePassword,
                                        @NonNull String keyStoreKeyAlias, @NonNull String keyPassword,
                                        boolean v1SigningEnabled, boolean v2SigningEnabled, boolean v3SigningEnabled,
                                        @Nullable LogCallback callback) {
        try (LogWriter logger = new LogWriter(callback)) {
            long savedTimeMillis = System.currentTimeMillis();
            PrintStream oldOut = System.out;

            // Register BouncyCastle and JKS KeyStore provider for Android JVM
            try {
                java.security.Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
                if (java.security.Security.getProvider("JKS") == null) {
                    java.security.Security.addProvider(new java.security.Provider("JKS", 1.0, "JKS Provider") {
                        {
                            put("KeyStore.JKS", "kellinwood.security.zipsigner.optional.JKS");
                        }
                    });
                }
            } catch (Exception ignored) {}

            String ksType = "JKS";
            String lowerPath = keyStorePath.toLowerCase();
            if (lowerPath.endsWith(".p12") || lowerPath.endsWith(".pfx") || lowerPath.endsWith(".pkcs12")) {
                ksType = "PKCS12";
            } else if (lowerPath.endsWith(".bks")) {
                ksType = "BKS";
            }

            String effectiveKeyPass = (keyPassword != null && !keyPassword.isEmpty()) ? keyPassword : keyStorePassword;

            List<String> args = Arrays.asList(
                    "sign",
                    "--in",
                    inputFilePath,
                    "--out",
                    outputFilePath,
                    "--ks",
                    keyStorePath,
                    "--ks-pass",
                    "pass:" + keyStorePassword,
                    "--ks-key-alias",
                    keyStoreKeyAlias,
                    "--key-pass",
                    "pass:" + effectiveKeyPass,
                    "--ks-type",
                    ksType,
                    "--v1-signing-enabled",
                    String.valueOf(v1SigningEnabled),
                    "--v2-signing-enabled",
                    String.valueOf(v2SigningEnabled),
                    "--v3-signing-enabled",
                    String.valueOf(v3SigningEnabled),
                    "--v4-signing-enabled",
                    "false"
            );

            logger.write("Signing APK with custom keystore (" + ksType + ", v1=" + v1SigningEnabled + ", v2=" + v2SigningEnabled + ", v3=" + v3SigningEnabled + ")...");

            if (callback != null) {
                try (PrintStream stream = new PrintStream(logger)) {
                    System.setOut(stream);
                }
            }

            try {
                ApkSignerTool.main(args.toArray(new String[0]));
            } catch (Exception e) {
                LogCallback.errorCount.incrementAndGet();
                logger.write("An error occurred while trying to sign the APK file " + inputFilePath +
                        " and outputting it to " + outputFilePath + ": " + e.getMessage() + "\n" +
                        "Stack trace: " + Log.getStackTraceString(e));
                throw e;
            } finally {
                if (callback != null) {
                    System.setOut(oldOut);
                }
            }

            logger.write("Signing an APK file took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
        } catch (Exception e) {
            Log.e("ApkSigner", "Error signing APK with custom keystore: " + e.getMessage(), e);
            throw new RuntimeException("APK signing failed: " + e.getMessage(), e);
        }
    }

    public interface LogCallback {
        AtomicInteger errorCount = new AtomicInteger(0);

        void onNewLineLogged(String line);
    }

    private static class LogWriter extends OutputStream {

        private final LogCallback mCallback;
        private String mCache = "";

        private LogWriter(LogCallback callback) {
            mCallback = callback;
        }

        @Override
        public void write(int b) {
            if (isLoggingDisabled()) return;

            mCache += (char) b;

            if (((char) b) == '\n') {
                mCallback.onNewLineLogged(mCache);
                mCache = "";
            }
        }

        private void write(String s) {
            if (isLoggingDisabled()) return;

            for (byte b : s.getBytes()) {
                write(b);
            }
        }

        private boolean isLoggingDisabled() {
            return mCallback == null;
        }
    }
}
