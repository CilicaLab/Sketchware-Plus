package sketchware.plus.security;

import android.content.Context;
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

    public static String inject(Context context, String scId, String applicationCode) {
        SecurityGuardHandler handler = new SecurityGuardHandler(scId);
        String expectedSignature = handler.getFeatureString("expected_signature");

        if (handler.isFeatureEnabled("auto_signature_sync")) {
            String detected = getActiveSigningHash();
            if (detected != null) {
                expectedSignature = detected;
                handler.setFeatureValue("expected_signature", detected);
            }
        }
        
        List<String> injections = new ArrayList<>();
        List<String> methods = new ArrayList<>();

        if (handler.isFeatureEnabled("package_lock")) {
             HashMap<String, Object> metadata = lC.b(scId);
             String pkgName = yB.c(metadata, "my_sc_pkg_name");
             injections.add("        if (!getPackageName().equals(\"" + pkgName + "\")) { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(1); }");
        }
        
        if (handler.isFeatureEnabled("signature_check") && !expectedSignature.isEmpty()) {
            injections.add("        checkSignature(\"" + expectedSignature + "\");");
            methods.add("\n    private void checkSignature(String expected) {\n" +
                    "        try {\n" +
                    "            android.content.pm.PackageManager pm = getPackageManager();\n" +
                    "            String pmClass = pm.getClass().getName();\n" +
                    "            if (pmClass.contains(\"proxy\") || pmClass.contains(\"Proxy\") || pmClass.contains(\"Hook\")) throw new Exception();\n" +
                    "\n" +
                    "            android.content.pm.PackageInfo info = pm.getPackageInfo(getPackageName(), 64);\n" +
                    "            for (android.content.pm.Signature signature : info.signatures) {\n" +
                    "                java.security.MessageDigest md = java.security.MessageDigest.getInstance(\"SHA-256\");\n" +
                    "                md.update(signature.toByteArray());\n" +
                    "                byte[] digest = md.digest();\n" +
                    "                StringBuilder sb = new StringBuilder();\n" +
                    "                for (byte b : digest) { String hex = Integer.toHexString(0xFF & b).toUpperCase(); if (hex.length() == 1) sb.append('0'); sb.append(hex); }\n" +
                    "                if (sb.toString().equals(expected)) return;\n" +
                    "            }\n" +
                    "        } catch (Exception ignored) {}\n" +
                    "        android.os.Process.killProcess(android.os.Process.myPid());\n" +
                    "        System.exit(1);\n" +
                    "    }\n");
        }
        
        if (handler.isFeatureEnabled("root_detection")) {
            injections.add("        if (isRooted()) { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(1); }");
            methods.add("\n    private boolean isRooted() {\n" +
                    "        String[] binaryPaths = {\"/system/app/Superuser.apk\", \"/sbin/su\", \"/system/bin/su\", \"/system/xbin/su\", \"/data/local/xbin/su\", \"/data/local/bin/su\", \"/system/sd/xbin/su\", \"/system/bin/failsafe/su\", \"/data/local/su\", \"/su/bin/su\",};\n" +
                    "        for (String path : binaryPaths) { if (new java.io.File(path).exists()) return true; }\n" +
                    "        try {\n" +
                    "            String[] apps = {\"com.noshufou.android.su\", \"com.thirdparty.superuser\", \"eu.chainfire.supersu\", \"com.koushikdutta.superuser\", \"com.topjohnwu.magisk\", \"com.zacharee1.systemuituner\"};\n" +
                    "            for (String pkg : apps) { try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception ignored) {} }\n" +
                    "        } catch (Exception ignored) {}\n" +
                    "        String buildTags = android.os.Build.TAGS;\n" +
                    "        if (buildTags != null && buildTags.contains(\"test-keys\")) return true;\n" +
                    "        return false;\n" +
                    "    }\n");
        }

        if (handler.isFeatureEnabled("anti_debug")) {
            injections.add("        if (isDebuggerActive()) { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(1); }");
            methods.add("\n    private boolean isDebuggerActive() {\n" +
                    "        if (android.os.Debug.isDebuggerConnected() || android.os.Debug.waitingForDebugger()) return true;\n" +
                    "        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) return true;\n" +
                    "        try {\n" +
                    "            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(\"/proc/self/status\"));\n" +
                    "            String line;\n" +
                    "            while ((line = br.readLine()) != null) {\n" +
                    "                if (line.startsWith(\"TracerPid:\")) {\n" +
                    "                    if (!line.endsWith(\"0\")) return true;\n" +
                    "                    break;\n" +
                    "                }\n" +
                    "            }\n" +
                    "            br.close();\n" +
                    "        } catch (Exception ignored) {}\n" +
                    "        return false;\n" +
                    "    }\n");
        }

        if (handler.isFeatureEnabled("emulator_detection")) {
            injections.add("        if (isEmulator()) { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(1); }");
            methods.add("\n    private boolean isEmulator() {\n" +
                    "        return (android.os.Build.BRAND.startsWith(\"generic\") && android.os.Build.DEVICE.startsWith(\"generic\"))\n" +
                    "                || android.os.Build.FINGERPRINT.startsWith(\"generic\")\n" +
                    "                || android.os.Build.FINGERPRINT.startsWith(\"unknown\")\n" +
                    "                || android.os.Build.HARDWARE.contains(\"goldfish\")\n" +
                    "                || android.os.Build.HARDWARE.contains(\"ranchu\")\n" +
                    "                || android.os.Build.MODEL.contains(\"google_sdk\")\n" +
                    "                || android.os.Build.MODEL.contains(\"Emulator\")\n" +
                    "                || android.os.Build.MODEL.contains(\"Android SDK built for x86\")\n" +
                    "                || android.os.Build.MANUFACTURER.contains(\"Genymotion\")\n" +
                    "                || android.os.Build.PRODUCT.contains(\"sdk_google\")\n" +
                    "                || android.os.Build.PRODUCT.contains(\"google_sdk\")\n" +
                    "                || android.os.Build.PRODUCT.contains(\"sdk\")\n" +
                    "                || android.os.Build.PRODUCT.contains(\"sdk_x86\")\n" +
                    "                || android.os.Build.PRODUCT.contains(\"vbox86p\")\n" +
                    "                || android.os.Build.PRODUCT.contains(\"emulator\")\n" +
                    "                || android.os.Build.PRODUCT.contains(\"simulator\");\n" +
                    "    }\n");
        }

        if (!injections.isEmpty()) {
            StringBuilder combinedInjections = new StringBuilder();
            for (String s : injections) combinedInjections.append(s).append("\n");
            
            StringBuilder combinedMethods = new StringBuilder();
            for (String s : methods) combinedMethods.append(s);

            // Static protection against early hooking
            String staticBlock = "\n    static {\n" +
                    "        try { java.io.File f = new java.io.File(\"/proc/self/maps\");\n" +
                    "        if (f.exists()) { java.util.Scanner s = new java.util.Scanner(f);\n" +
                    "        while (s.hasNextLine()) { if (s.nextLine().contains(\"XposedBridge.jar\")) android.os.Process.killProcess(android.os.Process.myPid()); } } } catch (Exception e) {}\n" +
                    "    }\n";
            
            int firstBrace = applicationCode.indexOf("{");
            if (firstBrace != -1) {
                applicationCode = applicationCode.substring(0, firstBrace + 1) + staticBlock + applicationCode.substring(firstBrace + 1);
            }

            String anchor = "mApplicationContext = getApplicationContext();";
            if (applicationCode.contains(anchor)) {
                applicationCode = applicationCode.replace(anchor, anchor + "\n" + combinedInjections.toString());
            } else {
                applicationCode = applicationCode.replace("super.onCreate();", "super.onCreate();\n" + combinedInjections.toString());
            }
            
            int lastBrace = applicationCode.lastIndexOf("}");
            if (lastBrace != -1) {
                applicationCode = applicationCode.substring(0, lastBrace) + combinedMethods.toString() + "\n}";
            }
        }
        
        return applicationCode;
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
            e.printStackTrace();
        }
        return null;
    }
}
