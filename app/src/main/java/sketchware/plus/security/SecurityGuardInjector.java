package sketchware.plus.security;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.lC;
import a.a.a.yB;
import mod.jbk.build.BuiltInLibraries;

public class SecurityGuardInjector {

    @SuppressWarnings("unused")
    public static String inject(Context context, String scId, String applicationCode) {
        SecurityGuardHandler handler = new SecurityGuardHandler(scId);
        String storedSignature = handler.getFeatureString("expected_signature");
        final String expectedSignature = storedSignature == null ? "" : storedSignature;

        String safeScId = scId != null ? scId.replaceAll("[^a-zA-Z0-9]", "_") : "default";
        String suffix = "_" + safeScId;
        String dieMethodName = "die" + suffix;
        String checkSigMethodName = "checkSignature" + suffix;
        String rootMethodName = "isRooted" + suffix;
        String debugMethodName = "isDebuggerActive" + suffix;
        String emuMethodName = "isEmulator" + suffix;

        List<String> injections = new ArrayList<>();
        List<String> methods = new ArrayList<>();

        if (handler.isFeatureEnabled("package_lock")) {
            HashMap<String, Object> metadata = lC.b(scId);
            String pkgName = metadata != null ? yB.c(metadata, "my_sc_pkg_name") : null;
            if (pkgName != null && !pkgName.isEmpty()) {
                injections.add("        if (!getPackageName().equals(\"" + pkgName + "\")) " + dieMethodName + "();");
            }
        }

        if (handler.isFeatureEnabled("signature_check") && !expectedSignature.isEmpty()) {
            injections.add("        " + checkSigMethodName + "(\"" + expectedSignature + "\");");
            methods.add("\n    private void " + checkSigMethodName + "(String expected) {\n" +
                    "        try {\n" +
                    "            android.content.pm.PackageManager pm = getPackageManager();\n" +
                    "            android.content.pm.Signature[] signatures;\n" +
                    "            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {\n" +
                    "                android.content.pm.PackageInfo info = pm.getPackageInfo(getPackageName(), android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);\n" +
                    "                android.content.pm.SigningInfo signingInfo = info.signingInfo;\n" +
                    "                if (signingInfo.hasMultipleSigners()) {\n" +
                    "                    signatures = signingInfo.getSigningCertificateHistory();\n" +
                    "                } else {\n" +
                    "                    signatures = signingInfo.getApkContentsSigners();\n" +
                    "                }\n" +
                    "            } else {\n" +
                    "                @SuppressWarnings(\"deprecation\")\n" +
                    "                android.content.pm.PackageInfo info = pm.getPackageInfo(getPackageName(), android.content.pm.PackageManager.GET_SIGNATURES);\n" +
                    "                @SuppressWarnings(\"deprecation\")\n" +
                    "                android.content.pm.Signature[] sigs = info.signatures;\n" +
                    "                signatures = sigs;\n" +
                    "            }\n" +
                    "            if (signatures != null) {\n" +
                    "                for (android.content.pm.Signature signature : signatures) {\n" +
                    "                    java.security.MessageDigest md = java.security.MessageDigest.getInstance(\"SHA-256\");\n" +
                    "                    md.update(signature.toByteArray());\n" +
                    "                    byte[] digest = md.digest();\n" +
                    "                    StringBuilder sb = new StringBuilder();\n" +
                    "                    for (byte b : digest) {\n" +
                    "                        String hex = Integer.toHexString(0xFF & b).toUpperCase();\n" +
                    "                        if (hex.length() == 1) sb.append('0');\n" +
                    "                        sb.append(hex);\n" +
                    "                    }\n" +
                    "                    if (sb.toString().equals(expected)) return;\n" +
                    "                }\n" +
                    "            }\n" +
                    "        } catch (Exception e) {\n" +
                    "            android.util.Log.e(\"SecurityGuard\", \"Signature check failed\", e);\n" +
                    "        }\n" +
                    "        " + dieMethodName + "();\n" +
                    "    }\n");
        }

        if (handler.isFeatureEnabled("root_detection")) {
            injections.add("        if (" + rootMethodName + "()) " + dieMethodName + "();");
            methods.add("\n    private boolean " + rootMethodName + "() {\n" +
                    "        String[] binaryPaths = {\"/system/app/Superuser.apk\", \"/sbin/su\", \"/system/bin/su\", \"/system/xbin/su\", \"/data/local/xbin/su\", \"/data/local/bin/su\", \"/system/sd/xbin/su\", \"/system/bin/failsafe/su\", \"/data/local/su\", \"/su/bin/su\"};\n" +
                    "        for (String path : binaryPaths) { try { if (new java.io.File(path).exists()) return true; } catch (Exception ignored) {} }\n" +
                    "        try {\n" +
                    "            String[] apps = {\"com.noshufou.android.su\", \"com.thirdparty.superuser\", \"eu.chainfire.supersu\", \"com.koushikdutta.superuser\", \"com.topjohnwu.magisk\"};\n" +
                    "            for (String pkg : apps) {\n" +
                    "                if (new java.io.File(\"/data/data/\" + pkg).exists() || new java.io.File(\"/data/user/0/\" + pkg).exists()) {\n" +
                    "                    return true;\n" +
                    "                }\n" +
                    "            }\n" +
                    "        } catch (Exception ignored) {}\n" +
                    "        return false;\n" +
                    "    }\n");
        }

        if (handler.isFeatureEnabled("anti_debug")) {
            injections.add("        if (" + debugMethodName + "()) " + dieMethodName + "();");
            methods.add("\n    private boolean " + debugMethodName + "() {\n" +
                    "        if (android.os.Debug.isDebuggerConnected() || android.os.Debug.waitingForDebugger()) return true;\n" +
                    "        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(\"/proc/self/status\"))) {\n" +
                    "            String line;\n" +
                    "            while ((line = br.readLine()) != null) {\n" +
                    "                if (line.startsWith(\"TracerPid:\")) {\n" +
                    "                    String val = line.substring(\"TracerPid:\".length()).trim();\n" +
                    "                    if (Integer.parseInt(val) != 0) return true;\n" +
                    "                    break;\n" +
                    "                }\n" +
                    "            }\n" +
                    "        } catch (Exception ignored) {}\n" +
                    "        return false;\n" +
                    "    }\n");
        }

        if (handler.isFeatureEnabled("emulator_detection")) {
            injections.add("        if (" + emuMethodName + "()) " + dieMethodName + "();");
            methods.add("\n    private boolean " + emuMethodName + "() {\n" +
                    "        return (android.os.Build.BRAND.startsWith(\"generic\") && android.os.Build.DEVICE.startsWith(\"generic\"))\n" +
                    "                || android.os.Build.HARDWARE.contains(\"goldfish\")\n" +
                    "                || android.os.Build.HARDWARE.contains(\"ranchu\")\n" +
                    "                || android.os.Build.MODEL.contains(\"google_sdk\")\n" +
                    "                || android.os.Build.MODEL.contains(\"Emulator\")\n" +
                    "                || android.os.Build.MODEL.contains(\"Android SDK built for x86\")\n" +
                    "                || android.os.Build.MANUFACTURER.contains(\"Genymotion\")\n" +
                    "                || android.os.Build.PRODUCT.contains(\"sdk_google\")\n" +
                    "                || android.os.Build.PRODUCT.contains(\"google_sdk\")\n" +
                    "                || android.os.Build.PRODUCT.contains(\"sdk_x86\")\n" +
                    "                || android.os.Build.PRODUCT.contains(\"vbox86p\")\n" +
                    "                || android.os.Build.PRODUCT.contains(\"emulator\")\n" +
                    "                || android.os.Build.PRODUCT.contains(\"simulator\");\n" +
                    "    }\n");
        }

        if (injections.isEmpty()) {
            return applicationCode;
        }

        methods.add("\n    private void " + dieMethodName + "() {\n" +
                "        android.os.Process.killProcess(android.os.Process.myPid());\n" +
                "    }\n");

        StringBuilder combinedInjections = new StringBuilder();
        for (String s : injections) combinedInjections.append(s).append("\n");

        StringBuilder combinedMethods = new StringBuilder();
        for (String s : methods) combinedMethods.append(s);

        String staticBlock = "\n    static {\n" +
                "        try (java.util.Scanner s = new java.util.Scanner(new java.io.File(\"/proc/self/maps\"))) {\n" +
                "            while (s.hasNextLine()) {\n" +
                "                String line = s.nextLine().toLowerCase();\n" +
                "                if (line.contains(\"xposedbridge\") || line.contains(\"lsposed\") || line.contains(\"lspd\")) {\n" +
                "                    android.os.Process.killProcess(android.os.Process.myPid());\n" +
                "                    break;\n" +
                "                }\n" +
                "            }\n" +
                "        } catch (Exception e) {}\n" +
                "    }\n";

        return insertMethodsAndStaticBlock(applicationCode, staticBlock, combinedInjections.toString(), combinedMethods.toString());
    }

    public static String detectAndPersistSigningHash(Context context, String scId) {
        @SuppressWarnings("unused")
        Context ctx = context;
        SecurityGuardHandler handler = new SecurityGuardHandler(scId);
        if (handler.isFeatureEnabled("auto_signature_sync")) {
            // TODO: Check if custom keystore API is configured; if custom keystore is used, do not overwrite expected_signature.
            String detected = getActiveSigningHash();
            if (detected != null) {
                handler.setFeatureValue("expected_signature", detected);
                return detected;
            }
        }
        return null;
    }

    private static String getActiveSigningHash() {
        try {
            File testkeyCert = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "testkey/testkey.x509.pem");
            if (testkeyCert.exists()) {
                try (FileInputStream fis = new FileInputStream(testkeyCert)) {
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
                    return SecurityUtils.getCertificateHash(cert);
                }
            }
        } catch (Exception e) {
            Log.e("SecurityGuardInjector", "Failed to get active signing hash", e);
        }
        return null;
    }

    private static int findClassOpeningBrace(String code) {
        int classIdx = code.indexOf("class ");
        if (classIdx == -1) return code.indexOf("{");
        return code.indexOf("{", classIdx);
    }

    private static String insertMethodsAndStaticBlock(String code, String staticBlock, String injections, String methods) {
        int classOpen = findClassOpeningBrace(code);
        String modified = code;
        if (classOpen != -1) {
            modified = modified.substring(0, classOpen + 1) + staticBlock + modified.substring(classOpen + 1);
        }

        String anchor = "mApplicationContext = getApplicationContext();";
        int anchorIdx = modified.indexOf(anchor);
        if (anchorIdx != -1) {
            modified = modified.substring(0, anchorIdx) + anchor + "\n" + injections + modified.substring(anchorIdx + anchor.length());
        } else {
            int onCreateIdx = modified.indexOf("super.onCreate();");
            if (onCreateIdx != -1) {
                modified = modified.substring(0, onCreateIdx) + "super.onCreate();\n" + injections + modified.substring(onCreateIdx + "super.onCreate();".length());
            } else {
                Log.w("SecurityGuardInjector", "Neither mApplicationContext anchor nor super.onCreate() found. Skipping security injections.");
                return modified;
            }
        }

        int lastBrace = findFinalClosingBrace(modified);
        if (lastBrace != -1) {
            modified = modified.substring(0, lastBrace) + methods + "\n" + modified.substring(lastBrace);
        }
        return modified;
    }

    private static int findFinalClosingBrace(String code) {
        int braceCount = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int lastBrace = -1;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = (i + 1 < code.length()) ? code.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (c == '\\') {
                    i++;
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                continue;
            }

            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    lastBrace = i;
                }
            }
        }
        return lastBrace;
    }
}
