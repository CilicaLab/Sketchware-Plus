package dev.aldi.sayuti.editor.manage;

import static sketchware.plus.utility.FileUtil.formatFileSize;
import static sketchware.plus.utility.FileUtil.getFileSize;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import sketchware.plus.utility.FileUtil;

public class LocalLibrary {
    private final File libraryDir;
    private final String name;
    private final String size;
    private final boolean hasConfig;
    private final boolean hasDex;
    private final boolean hasJar;
    private boolean isSelected;

    public static class DetectionResult {
        public final String packageName;
        public final String source;

        public DetectionResult(String packageName, String source) {
            this.packageName = packageName;
            this.source = source;
        }
    }

    private LocalLibrary(File libraryDir, String name, String size, boolean hasConfig, boolean hasDex, boolean hasJar) {
        this.libraryDir = libraryDir;
        this.name = name;
        this.size = size;
        this.hasConfig = hasConfig;
        this.hasDex = hasDex;
        this.hasJar = hasJar;
    }

    public static LocalLibrary fromFile(File file) {
        boolean configExists = new File(file, "config").exists() && new File(file, "config").length() > 0;
        boolean dexExists = new File(file, "classes.dex").exists();
        boolean jarExists = new File(file, "classes.jar").exists();
        return new LocalLibrary(file, file.getName(), formatFileSize(getFileSize(file)), configExists, dexExists, jarExists);
    }

    public File getLibraryDir() {
        return libraryDir;
    }

    public String getName() {
        return name;
    }

    public String getSize() {
        return size;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean isSelected) {
        this.isSelected = isSelected;
    }

    public boolean hasConfig() {
        return hasConfig;
    }

    public boolean hasDex() {
        return hasDex;
    }

    public boolean hasJar() {
        return hasJar;
    }

    public boolean hasIssues() {
        return !hasConfig || !hasDex;
    }

    public DetectionResult getDetectedPackageInfo() {
        // 1. AndroidManifest.xml
        File manifest = new File(libraryDir, "AndroidManifest.xml");
        if (manifest.exists() && manifest.isFile()) {
            try {
                String content = FileUtil.readFile(manifest.getAbsolutePath());
                Matcher matcher = Pattern.compile("<manifest.*package=\"(.*?)\"", Pattern.DOTALL).matcher(content);
                if (matcher.find()) {
                    String group = matcher.group(1);
                    if (group != null) {
                        String pkg = group.trim();
                        if (!pkg.isEmpty()) {
                            return new DetectionResult(pkg, "AndroidManifest.xml");
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. classes.jar
        File jarFile = new File(libraryDir, "classes.jar");
        if (jarFile.exists() && jarFile.isFile()) {
            String jarPkg = detectPackageFromJar(jarFile);
            if (!jarPkg.isEmpty()) {
                return new DetectionResult(jarPkg, "classes.jar");
            }
        }

        // 3. classes.dex
        File dexFile = new File(libraryDir, "classes.dex");
        if (dexFile.exists() && dexFile.isFile()) {
            String dexPkg = detectPackageFromDex(dexFile);
            if (!dexPkg.isEmpty()) {
                return new DetectionResult(dexPkg, "classes.dex");
            }
        }

        // 4. Folder Name Fallback
        String folderName = libraryDir.getName();
        String cleanedName = folderName.replaceAll("-[0-9].*$", "")
                .replaceAll("\\.(aar|jar)$", "")
                .trim();
        if (cleanedName.contains(".")) {
            return new DetectionResult(cleanedName, "Library folder name");
        }

        return new DetectionResult("", "None");
    }

    private static String detectPackageFromJar(File jarFile) {
        try (ZipFile zipFile = new ZipFile(jarFile)) {
            String rClassPackage = null;
            String buildConfigPackage = null;
            Map<String, Integer> packageCounts = new HashMap<>();

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class") && !name.contains("$")) {
                    int lastSlash = name.lastIndexOf('/');
                    if (lastSlash > 0) {
                        String pkgPath = name.substring(0, lastSlash);
                        String className = name.substring(lastSlash + 1);
                        String pkgName = pkgPath.replace('/', '.');

                        if (className.equals("R.class")) {
                            rClassPackage = pkgName;
                        } else if (className.equals("BuildConfig.class")) {
                            buildConfigPackage = pkgName;
                        }

                        packageCounts.put(pkgName, packageCounts.getOrDefault(pkgName, 0) + 1);
                    }
                }
            }

            if (rClassPackage != null && !rClassPackage.isEmpty()) {
                return rClassPackage;
            }
            if (buildConfigPackage != null && !buildConfigPackage.isEmpty()) {
                return buildConfigPackage;
            }

            String topPackage = "";
            int maxCount = 0;
            for (Map.Entry<String, Integer> entry : packageCounts.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    topPackage = entry.getKey();
                }
            }
            return topPackage;
        } catch (Exception ignored) {}
        return "";
    }

    private static String detectPackageFromDex(File dexFile) {
        try {
            byte[] bytes = Files.readAllBytes(dexFile.toPath());
            if (bytes.length > 0) {
                String content = new String(bytes, StandardCharsets.ISO_8859_1);

                Matcher rMatcher = Pattern.compile("L([a-zA-Z0-9_$]+/(?:[a-zA-Z0-9_$]+/)+)R;").matcher(content);
                if (rMatcher.find()) {
                    String group = rMatcher.group(1);
                    if (group != null) {
                        return group.replace('/', '.').replaceAll("\\.$", "");
                    }
                }

                Matcher bcMatcher = Pattern.compile("L([a-zA-Z0-9_$]+/(?:[a-zA-Z0-9_$]+/)+)BuildConfig;").matcher(content);
                if (bcMatcher.find()) {
                    String group = bcMatcher.group(1);
                    if (group != null) {
                        return group.replace('/', '.').replaceAll("\\.$", "");
                    }
                }

                Matcher classMatcher = Pattern.compile("L([a-zA-Z0-9_]{2,}/(?:[a-zA-Z0-9_]{2,}/)+)[A-Z][a-zA-Z0-9_$]*;").matcher(content);
                Map<String, Integer> counts = new HashMap<>();
                int count = 0;
                while (classMatcher.find() && count < 200) {
                    String group = classMatcher.group(1);
                    if (group != null) {
                        String pkg = group.replace('/', '.').replaceAll("\\.$", "");
                        if (!pkg.startsWith("java.") && !pkg.startsWith("android.") && !pkg.startsWith("kotlin.")) {
                            counts.put(pkg, counts.getOrDefault(pkg, 0) + 1);
                        }
                    }
                    count++;
                }

                String bestPkg = "";
                int max = 0;
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    if (entry.getValue() > max) {
                        max = entry.getValue();
                        bestPkg = entry.getKey();
                    }
                }
                if (!bestPkg.isEmpty()) {
                    return bestPkg;
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    public String getIssuesDescription() {
        if (!hasConfig && !hasDex) {
            String desc = "This library is missing both the 'config' file (package name) and 'classes.dex' (compiled bytecode).\n\n" +
                   "• Missing 'config': Automatic package imports and resource compilation will fail.\n" +
                   "• Missing 'classes.dex': Library code will not be included in the APK, causing runtime crashes (ClassNotFoundException).";
            if (hasJar) {
                desc += "\n\n(A 'classes.jar' file was detected, so 'classes.dex' can be generated automatically!)";
            }
            return desc;
        } else if (!hasConfig) {
            return "This library is missing the 'config' file containing its package name.\n\n" +
                   "Without 'config', automatic package imports and resource compilation for this library will not work properly.";
        } else if (!hasDex) {
            String desc = "This library is missing 'classes.dex' (compiled bytecode).\n\n" +
                   "Without 'classes.dex', classes from this library will not be bundled into the APK and will cause runtime crashes (ClassNotFoundException) when accessed.";
            if (hasJar) {
                desc += "\n\n(A 'classes.jar' file was detected, so 'classes.dex' can be generated automatically!)";
            }
            return desc;
        }
        return "No issues detected.";
    }
}
