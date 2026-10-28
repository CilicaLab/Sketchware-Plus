package a.a.a;

import static android.system.OsConstants.S_IRUSR;
import static android.system.OsConstants.S_IWUSR;
import static android.system.OsConstants.S_IXUSR;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.system.Os;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import com.android.sdklib.build.ApkBuilder;
import com.android.sdklib.build.ApkCreationException;
import com.android.sdklib.build.DuplicateFileException;
import com.android.sdklib.build.SealedApkException;
import com.github.megatronking.stringfog.plugin.StringFogClassInjector;
import com.github.megatronking.stringfog.plugin.StringFogMappingPrinter;
import com.iyxan23.zipalignjava.InvalidZipException;
import com.iyxan23.zipalignjava.ZipAlign;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.zip.ZipFile;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import mod.agus.jcoderz.dex.ClassDef;
import mod.agus.jcoderz.dex.Dex;
import mod.agus.jcoderz.dex.FieldId;
import mod.agus.jcoderz.dex.MethodId;
import mod.agus.jcoderz.dex.ProtoId;
import mod.agus.jcoderz.dx.command.dexer.DxContext;
import mod.agus.jcoderz.dx.command.dexer.Main;
import mod.agus.jcoderz.dx.merge.CollisionPolicy;
import mod.agus.jcoderz.dx.merge.DexMerger;
import mod.agus.jcoderz.editor.library.ExtLibSelected;
import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.project.proguard.ProguardHandler;
import mod.hey.studios.util.SystemLogPrinter;
import mod.jbk.build.BuildProgressReceiver;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.build.compiler.dex.DexCompiler;
import mod.jbk.build.compiler.resource.ResourceCompiler;
import mod.jbk.util.LogUtil;
import mod.jbk.util.TestkeySignBridge;
import mod.pranav.build.JarBuilder;
import mod.pranav.build.R8Compiler;
import mod.pranav.viewbinding.ViewBindingBuilder;
import sketchware.plus.SketchApplication;
import sketchware.plus.util.library.BuiltInLibraryManager;
import sketchware.plus.utility.FilePathUtil;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.SketchwareUtil;
import proguard.Configuration;
import proguard.ConfigurationParser;
import proguard.ParseException;
import proguard.ProGuard;
import java.util.LinkedHashSet;

public class ProjectBuilder {
    public static final String TAG = "AppBuilder";
    public static final Object ASSET_LOCK = new Object();

    private final File aapt2Binary;
    private final Context context;
    public BuildSettings build_settings;
    public yq yq;
    public FilePathUtil fpu;
    public ManageLocalLibrary mll;
    public BuiltInLibraryManager builtInLibraryManager;
    public String androidJarPath;
    public ProguardHandler proguard;
    public ProjectSettings settings;
    private BuildProgressReceiver progressReceiver;
    private boolean buildAppBundle = false;
    private ArrayList<File> dexesToAddButNotMerge = new ArrayList<>();

    private static final String DX_HINT =
            "\n\nDx can't dex Java 8+ bytecode (lambdas, invokedynamic, interface default/static methods)."
                    + "\nFix: switch the dexer to D8 in the project's Build Settings.";

    /**
     * Timestamp keeping track of when compiling the project's resources started, needed for stats of how long compiling took.
     */
    private long timestampResourceCompilationStarted;

    public ProjectBuilder(Context context, yq yqVar) {
        /* Detect some bad behaviour of the app */
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        );

        SystemLogPrinter.start();

        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

            LogUtil.d(TAG, "Running Sketchware Plus " + info.versionName + " (" + info.versionCode + ")");

            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);

            long fileSizeInBytes = new File(applicationInfo.sourceDir).length();
            LogUtil.d(TAG, "base.apk's size is " + Formatter.formatFileSize(context, fileSizeInBytes) + " (" + fileSizeInBytes + " B)");
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e(TAG, "Somehow failed to get package info about us!", e);
        }

        aapt2Binary = new File(context.getCacheDir(), "aapt2");
        build_settings = new BuildSettings(yqVar.sc_id);
        this.context = context;
        yq = yqVar;
        fpu = new FilePathUtil();
        mll = new ManageLocalLibrary(yqVar.sc_id);
        builtInLibraryManager = new BuiltInLibraryManager(yqVar.sc_id);
        File defaultAndroidJar = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "android.jar");
        androidJarPath = build_settings.getValue(BuildSettings.SETTING_ANDROID_JAR_PATH, defaultAndroidJar.getAbsolutePath());
        proguard = new ProguardHandler(yqVar.sc_id);
        settings = new ProjectSettings(yqVar.sc_id);
    }

    public ProjectBuilder(BuildProgressReceiver buildAsyncTask, Context context, yq yqVar) {
        this(context, yqVar);
        progressReceiver = buildAsyncTask;
    }

    /**
     * Checks if a file on local storage differs from a file in assets, and if so,
     * replaces the file on local storage with the one in assets. Includes retry logic,
     * thread synchronization, and zip/archive integrity validation.
     *
     * @param fileInAssets The file in assets relative to assets/ in the APK
     * @param targetFile   The file on local storage
     * @return If the file in assets has been extracted / replaced
     */
    public static boolean hasFileChanged(String fileInAssets, String targetFile) {
        synchronized (ASSET_LOCK) {
            File compareToFile = new File(targetFile);
            oB fileUtil = new oB();
            int maxRetries = 3;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    long lengthOfFileInAssets = fileUtil.a(SketchApplication.getContext(), fileInAssets);

                    boolean cacheValid = compareToFile.exists()
                            && compareToFile.length() > 0
                            && (lengthOfFileInAssets <= 0 || compareToFile.length() == lengthOfFileInAssets);

                    if (cacheValid && isZipOrJar(targetFile)) {
                        cacheValid = isValidZipArchive(compareToFile);
                        if (!cacheValid) {
                            LogUtil.w(TAG, "Cached file is corrupted zip/jar for asset filename: " + fileInAssets + " at target: " + targetFile + ". Will re-extract.");
                        }
                    }

                    if (cacheValid) {
                        return false;
                    }

                    if (compareToFile.exists()) {
                        fileUtil.a(compareToFile);
                    }

                    LogUtil.d(TAG, "Extracting asset filename: " + fileInAssets + " -> " + targetFile + " (attempt " + attempt + "/" + maxRetries + ")");

                    fileUtil.a(SketchApplication.getContext(), fileInAssets, targetFile);

                    boolean extractionSuccessful = compareToFile.exists() && compareToFile.length() > 0;
                    if (extractionSuccessful && lengthOfFileInAssets > 0) {
                        extractionSuccessful = (compareToFile.length() == lengthOfFileInAssets);
                    }
                    if (extractionSuccessful && isZipOrJar(targetFile)) {
                        extractionSuccessful = isValidZipArchive(compareToFile);
                    }

                    if (!extractionSuccessful) {
                        fileUtil.a(compareToFile);
                        throw new IOException("Extracted file failed integrity check for asset filename: " + fileInAssets + " (target: " + targetFile + ")");
                    }

                    return true;
                } catch (Throwable e) {
                    LogUtil.w(TAG, "Failed attempt " + attempt + "/" + maxRetries + " reading/extracting asset filename: " + fileInAssets + " (target: " + targetFile + ")", e);
                    if (compareToFile.exists()) {
                        fileUtil.a(compareToFile);
                    }
                    if (attempt == maxRetries) {
                        LogUtil.e(TAG, "CRITICAL: Asset extraction failed permanently for asset filename: " + fileInAssets + " (target: " + targetFile + ")", e);
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }
                        throw new RuntimeException("Failed to extract asset filename '" + fileInAssets + "' to '" + targetFile + "' after " + maxRetries + " attempts", e);
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            return true;
        }
    }

    private static boolean isZipOrJar(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        return lower.endsWith(".zip") || lower.endsWith(".jar");
    }

    public static boolean isValidZipArchive(File file) {
        if (file == null || !file.exists() || file.length() == 0) {
            return false;
        }
        try (ZipFile zipFile = new ZipFile(file)) {
            return zipFile.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Compile resources and log time needed.
     *
     * @throws Exception Thrown when anything goes wrong while compiling resources
     */
    public void compileResources() throws Exception {
        timestampResourceCompilationStarted = System.currentTimeMillis();
        ResourceCompiler compiler = new ResourceCompiler(
                this,
                aapt2Binary,
                buildAppBundle,
                progressReceiver);
        compiler.compile();
        LogUtil.d(TAG, "Compiling resources took " + (System.currentTimeMillis() - timestampResourceCompilationStarted) + " ms");
    }

    public void generateViewBinding() throws IOException, SAXException {
        if (settings.getValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, ProjectSettings.SETTING_GENERIC_VALUE_FALSE)
                .equals(ProjectSettings.SETTING_GENERIC_VALUE_FALSE)) {
            return;
        }
        File outputDirectory = new File(yq.javaFilesPath + File.separator + yq.packageName.replace(".", File.separator) + File.separator + "databinding");
        outputDirectory.mkdirs();

        List<File> layouts = FileUtil.listFiles(yq.layoutFilesPath, "xml").stream()
                .map(File::new)
                .collect(Collectors.toList());

        ViewBindingBuilder builder = new ViewBindingBuilder(layouts, outputDirectory, yq.packageName);

        builder.generateBindings();
    }

    public boolean isD8Enabled() {
        return build_settings.getValue(
                BuildSettings.SETTING_DEXER,
                BuildSettings.SETTING_DEXER_DX
        ).equals(BuildSettings.SETTING_DEXER_D8);
    }

    public String getDxRunningText() {
        return (isD8Enabled() ? "D8" : "Dx") + " is running...";
    }

    /**
     * Compile Java classes into DEX file(s)
     *
     * @throws Exception Thrown if the compiler had any problems compiling
     */
    public void createDexFilesFromClasses() throws Exception {
        FileUtil.makeDir(yq.binDirectoryPath + File.separator + "dex");
        if (proguard.isShrinkingEnabled() && proguard.isR8Enabled()) return;

        if (isD8Enabled()) {
            long savedTimeMillis = System.currentTimeMillis();
            try {
                DexCompiler.compileDexFiles(this);
                LogUtil.d(TAG, "D8 took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            } catch (Exception e) {
                LogUtil.e(TAG, "D8 failed to process .class files", e);
                throw e;
            }
        } else {
            long savedTimeMillis = System.currentTimeMillis();
            List<String> args = Arrays.asList(
                    "--debug",
                    "--verbose",
                    "--multi-dex",
                    "--output=" + yq.binDirectoryPath + File.separator + "dex",
                    proguard.isShrinkingEnabled() ? yq.proguardClassesPath : yq.compiledClassesPath
            );

            java.io.PrintStream oldErr = System.err;
            java.io.ByteArrayOutputStream errBuf = new java.io.ByteArrayOutputStream();
            String dxErr;
            int result;

            try {
                LogUtil.d(TAG, "Running Dx with these arguments: " + args);
                System.setErr(new java.io.PrintStream(errBuf, true));

                Main.clearInternTables();
                Main.Arguments arguments = new Main.Arguments();
                Method parseMethod = Main.Arguments.class.getDeclaredMethod("parse", String[].class);
                parseMethod.setAccessible(true);
                parseMethod.invoke(arguments, (Object) args.toArray(new String[0]));

                result = Main.run(arguments);
            } catch (Exception e) {
                LogUtil.e(TAG, "Dx failed to process .class files", e);
                throw new By("Dx crashed: " + e + DX_HINT);
            } finally {
                System.setErr(oldErr);
                dxErr = errBuf.toString();
            }

            if (result != 0) {
                LogUtil.e(TAG, "Dx returned " + result + ":\n" + dxErr);
                throw new By("Dx failed (code " + result + "):\n" + dxErr + DX_HINT);
            }
            LogUtil.d(TAG, "Dx took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
        }

        /* Check that bin/dex/classes.dex exists and is non-empty */
        File mainProjectDex = new File(yq.binDirectoryPath + File.separator + "dex" + File.separator + "classes.dex");
        if (!mainProjectDex.exists() || mainProjectDex.length() == 0) {
            File dexDirForCheck = new File(yq.binDirectoryPath + File.separator + "dex");
            File[] filesInBinDex = dexDirForCheck.listFiles();
            StringBuilder fileListStr = new StringBuilder();
            if (filesInBinDex != null && filesInBinDex.length > 0) {
                for (File f : filesInBinDex) {
                    fileListStr.append("\n - ").append(f.getName()).append(" (").append(f.length()).append(" bytes)");
                }
            } else {
                fileListStr.append(" (directory is empty)");
            }
            throw new By("Dexing failed to produce bin/dex/classes.dex or output was 0 bytes. Files in bin/dex:"
                    + fileListStr + (isD8Enabled() ? "" : DX_HINT));
        }

        /* Log status after dexing */
        String packagePath = yq.packageName.replace('.', File.separatorChar);
        File coreManagerClassInPkg = new File(yq.compiledClassesPath + File.separator + packagePath + File.separator + "CoreManager.class");
        File compiledClassesDir = new File(yq.compiledClassesPath);
        List<File> classFiles = compiledClassesDir.exists() ? FileUtil.listFilesRecursively(compiledClassesDir, ".class") : java.util.Collections.emptyList();

        File dexDir = new File(yq.binDirectoryPath + File.separator + "dex");
        File[] dexFiles = dexDir.listFiles((dir, name) -> name.endsWith(".dex"));

        StringBuilder dexLog = new StringBuilder("Dexing output summary:\n");
        dexLog.append(" - bin/classes/").append(packagePath).append("/CoreManager.class exists: ").append(coreManagerClassInPkg.exists()).append(" (").append(coreManagerClassInPkg.getAbsolutePath()).append(")\n");
        dexLog.append(" - Total .class count in bin/classes: ").append(classFiles.size()).append("\n");
        dexLog.append(" - Files in bin/dex:\n");
        if (dexFiles != null && dexFiles.length > 0) {
            for (File dexFile : dexFiles) {
                dexLog.append("   * ").append(dexFile.getName()).append(" (").append(dexFile.length()).append(" bytes)\n");
            }
        } else {
            dexLog.append("   * (none)\n");
        }
        LogUtil.d(TAG, dexLog.toString());
    }

    /**
     * @return The Java source level passed to ECJ. Always at least 1.8, because ECJ rejects
     * a source/compliance level lower than the target level (1.8).
     */
    private String getJavaSourceLevel() {
        String javaVersion = build_settings.getValue(BuildSettings.SETTING_JAVA_VERSION,
                BuildSettings.SETTING_JAVA_VERSION_1_7);

        if (!isD8Enabled()) {
            // Dx can't handle lambdas or Java 8 bytecode, so stay at 1.7
            return "1.7";
        }
        if (javaVersion.equals("1.7") || javaVersion.equals("1.6") || javaVersion.equals("1.5")) {
            return "1.8";
        }
        return javaVersion;
    }

    private String getJavaTargetLevel() {
        return isD8Enabled() ? "1.8" : "1.7";
    }

    public String getClasspath() {
        StringBuilder classpath = new StringBuilder();

        /*
         * Add yq#u (.sketchware/mysc/xxx/bin/classes) if it exists
         * since there might be compiled Kotlin files for ecj to use classpath as.
         */
        KotlinCompilerBridge.maybeAddKotlinFilesToClasspath(classpath, yq);

        /* Add android.jar */
        classpath.append(androidJarPath);

        /* Add HTTP legacy files if wanted */
        if (!build_settings.getValue(BuildSettings.SETTING_NO_HTTP_LEGACY,
                BuildSettings.SETTING_GENERIC_VALUE_FALSE).equals(BuildSettings.SETTING_GENERIC_VALUE_TRUE)) {
            classpath.append(":").append(BuiltInLibraries.getLibraryClassesJarPathString(BuiltInLibraries.HTTP_LEGACY_ANDROID));
        }

        /* Include MultiDex library */
        classpath.append(":").append(BuiltInLibraries.getLibraryClassesJarPathString(BuiltInLibraries.ANDROIDX_MULTIDEX));

        /*
         * Add lambda helper classes.
         * The source level is always >= 1.8 now (see getJavaSourceLevel()), so lambdas can always be used.
         */
        classpath.append(":").append(new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "core-lambda-stubs.jar").getAbsolutePath());

        /* Add used built-in libraries to the classpath */
        for (Jp library : builtInLibraryManager.getLibraries()) {
            classpath.append(":").append(BuiltInLibraries.getLibraryClassesJarPathString(library.getName()));
        }

        /* Add local libraries to the classpath */
        classpath.append(mll.getJarLocalLibrary());

        /* Append user's custom classpath */
        if (!build_settings.getValue(BuildSettings.SETTING_CLASSPATH, "").isEmpty()) {
            classpath.append(":").append(build_settings.getValue(BuildSettings.SETTING_CLASSPATH, ""));
        }

        /* Add JARs from project's classpath */
        String path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + yq.sc_id + "/files/classpath/";
        ArrayList<String> jars = FileUtil.listFiles(path, "jar");
        classpath.append(":").append(TextUtils.join(":", jars));

        return classpath.toString();
    }

    /**
     * @return Similar to {@link ProjectBuilder#getClasspath()}, but doesn't return some local libraries' JARs if ProGuard full mode is enabled
     */
    public String getProguardClasspath() {
        Collection<String> localLibraryJarsWithFullModeOn = new LinkedList<>();

        for (HashMap<String, Object> localLibrary : mll.list) {
            Object nameObject = localLibrary.get("name");
            Object jarPathObject = localLibrary.get("jarPath");

            if (nameObject instanceof String name && jarPathObject instanceof String jarPath) {

                if (localLibrary.containsKey("jarPath") && proguard.libIsProguardFMEnabled(name)) {
                    localLibraryJarsWithFullModeOn.add(jarPath);
                }
            }
        }

        String normalClasspath = getClasspath();
        StringBuilder classpath = new StringBuilder();
        normalClasspathLoop:
        for (String classpathPart : normalClasspath.split(":")) {
            for (String jarPathToExclude : localLibraryJarsWithFullModeOn) {
                if (classpathPart.equals(jarPathToExclude)) {
                    localLibraryJarsWithFullModeOn.remove(jarPathToExclude);
                    continue normalClasspathLoop;
                }
            }

            if (!classpathPart.equals(yq.compiledClassesPath)) {
                classpath.append(classpathPart).append(':');
            }
        }

        // remove trailing delimiter
        classpath.deleteCharAt(classpath.length() - 1);

        return classpath.toString();
    }

    /**
     * Dexes libraries.
     *
     * @return List of result DEX files which were merged or couldn't be merged with others.
     * @throws Exception Thrown if merging had problems
     */
    private Collection<File> dexLibraries(File outputDirectory, List<File> dexes) throws Exception {
        int lastDexNumber = 1;
        String nextMergedDexFilename;
        Collection<File> resultDexFiles = new LinkedList<>();
        LinkedList<Dex> dexObjects = new LinkedList<>();
        Iterator<File> toMergeIterator = dexes.iterator();

        List<FieldId> mergedDexFields;
        List<MethodId> mergedDexMethods;
        List<ProtoId> mergedDexProtos;
        List<Integer> mergedDexTypes;

        {
            // Closable gets closed automatically
            Dex firstDex = new Dex(new FileInputStream(toMergeIterator.next()));
            dexObjects.add(firstDex);
            mergedDexFields = new LinkedList<>(firstDex.fieldIds());
            mergedDexMethods = new LinkedList<>(firstDex.methodIds());
            mergedDexProtos = new LinkedList<>(firstDex.protoIds());
            mergedDexTypes = new LinkedList<>(firstDex.typeIds());
        }

        while (toMergeIterator.hasNext()) {
            File dexFile = toMergeIterator.next();
            nextMergedDexFilename = lastDexNumber == 1 ? "classes.dex" : "classes" + lastDexNumber + ".dex";

            // Closable gets closed automatically
            Dex dex = new Dex(new FileInputStream(dexFile));

            boolean canMerge = true;
            List<FieldId> newDexFieldIds = new LinkedList<>();
            List<MethodId> newDexMethodIds = new LinkedList<>();
            List<ProtoId> newDexProtoIds = new LinkedList<>();
            List<Integer> newDexTypeIds = new LinkedList<>();

            bruh:
            {
                for (FieldId fieldId : dex.fieldIds()) {
                    if (!mergedDexFields.contains(fieldId)) {
                        if (mergedDexFields.size() + newDexFieldIds.size() + 1 > 0xffff) {
                            LogUtil.d(TAG, "Can't merge DEX file to " + nextMergedDexFilename +
                                    " because it has too many new field IDs. "
                                    + nextMergedDexFilename + " will have " + mergedDexFields.size() + " field IDs");
                            canMerge = false;
                            break bruh;
                        } else {
                            newDexFieldIds.add(fieldId);
                        }
                    }
                }

                for (MethodId methodId : dex.methodIds()) {
                    if (!newDexMethodIds.contains(methodId)) {
                        if (mergedDexMethods.size() + newDexMethodIds.size() + 1 > 0xffff) {
                            LogUtil.d(TAG, "Can't merge DEX file to " + nextMergedDexFilename +
                                    " because it has too many new method IDs. "
                                    + nextMergedDexFilename + " will have " + mergedDexMethods.size() + " method IDs");
                            canMerge = false;
                            break bruh;
                        } else {
                            newDexMethodIds.add(methodId);
                        }
                    }
                }

                for (ProtoId protoId : dex.protoIds()) {
                    if (!newDexProtoIds.contains(protoId)) {
                        if (mergedDexProtos.size() + newDexProtoIds.size() + 1 > 0xffff) {
                            LogUtil.d(TAG, "Can't merge DEX file to " + nextMergedDexFilename +
                                    " because it has too many new proto IDs. "
                                    + nextMergedDexFilename + " will have " + mergedDexProtos.size() + " proto IDs");
                            canMerge = false;
                            break bruh;
                        } else {
                            newDexProtoIds.add(protoId);
                        }
                    }
                }

                for (Integer typeId : dex.typeIds()) {
                    if (!newDexTypeIds.contains(typeId)) {
                        if (mergedDexTypes.size() + newDexProtoIds.size() + 1 > 0xffff) {
                            LogUtil.d(TAG, "Can't merge DEX file to " + nextMergedDexFilename +
                                    " because it has too many new type IDs. "
                                    + nextMergedDexFilename + " will have " + mergedDexTypes.size() + " type IDs");
                            canMerge = false;
                            break bruh;
                        } else {
                            newDexTypeIds.add(typeId);
                        }
                    }
                }
            }

            if (canMerge) {
                LogUtil.d(TAG, "Merging DEX #" + dexes.indexOf(dexFile) + " as well to " + nextMergedDexFilename);
                dexObjects.add(dex);
                mergedDexFields.addAll(newDexFieldIds);
                mergedDexMethods.addAll(newDexMethodIds);
                mergedDexProtos.addAll(newDexProtoIds);
                mergedDexTypes.addAll(newDexTypeIds);
            } else {
                File target = new File(outputDirectory, nextMergedDexFilename);
                mergeDexes(target, dexObjects);
                resultDexFiles.add(target);
                dexObjects.clear();
                dexObjects.add(dex);

                mergedDexFields = new ArrayList<>(dex.fieldIds());
                mergedDexMethods = new ArrayList<>(dex.methodIds());
                mergedDexProtos = new ArrayList<>(dex.protoIds());
                mergedDexTypes = new ArrayList<>(dex.typeIds());
                lastDexNumber++;
            }
        }
        if (!dexObjects.isEmpty()) {
            File file = new File(outputDirectory, lastDexNumber == 1 ? "classes.dex" : "classes" + lastDexNumber + ".dex");
            mergeDexes(file, dexObjects);
            resultDexFiles.add(file);
        }

        return resultDexFiles;
    }

    /**
     * Get package names of in-use libraries which have resources, separated by <code>:</code>.
     */
    public String getLibraryPackageNames() {
        StringBuilder extraPackages = new StringBuilder();
        for (Jp library : builtInLibraryManager.getLibraries()) {
            if (library.hasResources()) {
                extraPackages.append(library.getPackageName()).append(":");
            }
        }
        return extraPackages + mll.getPackageNameLocalLibrary();
    }




    /**
     * Run Eclipse Compiler to compile Java files.
     */
    public void compileJavaCode() throws zy, IOException {
        long savedTimeMillis = System.currentTimeMillis();

        /*
         * Delete the package-less R.java BEFORE collecting source files.
         * Otherwise it gets added to the compile list, deleted afterwards, and ECJ
         * fails with "File .../gen/R.java is missing".
         */
        File rJavaFileWithoutPackage = new File(yq.rJavaDirectoryPath, "R.java");
        if (rJavaFileWithoutPackage.exists() && !rJavaFileWithoutPackage.delete()) {
            LogUtil.w(TAG, "Failed to delete file " + rJavaFileWithoutPackage.getAbsolutePath());
        }

        class EclipseOutputStream extends OutputStream {
            private final StringBuffer mBuffer = new StringBuffer();

            @Override
            public void write(int b) {
                mBuffer.append((char) b);
            }

            public String getOut() {
                return mBuffer.toString();
            }
        }

        try (EclipseOutputStream outOutputStream = new EclipseOutputStream();
             PrintWriter outWriter = new PrintWriter(outOutputStream);
             EclipseOutputStream errOutputStream = new EclipseOutputStream();
             PrintWriter errWriter = new PrintWriter(errOutputStream)) {

            /* Source directories, in order */
            String pathJava = fpu.getPathJava(yq.sc_id);
            String pathBroadcast = fpu.getPathBroadcast(yq.sc_id);
            String pathService = fpu.getPathService(yq.sc_id);

            List<String> sourceDirectories = new ArrayList<>();
            for (String dir : new String[]{
                    yq.javaFilesPath,
                    yq.rJavaDirectoryPath,
                    pathJava,
                    pathBroadcast,
                    pathService
            }) {
                if (dir != null && FileUtil.isExistFile(dir)) {
                    sourceDirectories.add(dir);
                }
            }

            /* Collect .java files, skipping duplicates and files that no longer exist */
            LinkedHashSet<File> javaFilesToCompile = new LinkedHashSet<>();
            for (String dir : sourceDirectories) {
                for (File javaFile : FileUtil.listFilesRecursively(new File(dir), ".java")) {
                    if (javaFile.exists()) {
                        javaFilesToCompile.add(javaFile);
                    }
                }
            }

            ArrayList<String> args = new ArrayList<>();
            args.add("-source");
            args.add(getJavaSourceLevel());
            args.add("-target");
            args.add(getJavaTargetLevel());
            args.add("-nowarn");
            if (!build_settings.getValue(BuildSettings.SETTING_NO_WARNINGS,
                    BuildSettings.SETTING_GENERIC_VALUE_TRUE).equals(BuildSettings.SETTING_GENERIC_VALUE_TRUE)) {
                args.add("-deprecation");
            }
            args.add("-d");
            args.add(yq.compiledClassesPath);

            args.add("-sourcepath");
            args.add(TextUtils.join(File.pathSeparator, sourceDirectories));

            args.add("-cp");
            args.add(getClasspath());
            args.add("-proc:none");

            for (File javaFile : javaFilesToCompile) {
                args.add(javaFile.getAbsolutePath());
            }

            /* Start compiling */
            org.eclipse.jdt.internal.compiler.batch.Main main =
                    new org.eclipse.jdt.internal.compiler.batch.Main(outWriter, errWriter, false, null, null);
            LogUtil.d(TAG, "Running Eclipse compiler with these arguments: " + args);
            main.compile(args.toArray(new String[0]));
            outWriter.flush();
            errWriter.flush();

            String errOut = errOutputStream.getOut();
            String stdOut = outOutputStream.getOut();
            LogUtil.d(TAG, "System.out of Eclipse compiler: " + stdOut);
            if (!errOut.trim().isEmpty()) {
                LogUtil.e(TAG, "Eclipse compiler stderr:\n" + errOut);
            }

            /*
             * ECJ can reject its arguments (e.g. incompatible source/target level) without
             * reporting any compile errors, so also verify that it actually produced .class files.
             */
            File compiledClassesDir = new File(yq.compiledClassesPath);
            boolean producedNoClasses = !compiledClassesDir.exists()
                    || FileUtil.listFilesRecursively(compiledClassesDir, ".class").isEmpty();

            if (main.globalErrorsCount > 0
                    || producedNoClasses
                    || (!errOut.trim().isEmpty() && errOut.toLowerCase().contains("error"))) {
                LogUtil.e(TAG, "Failed to compile Java files. Errors count: " + main.globalErrorsCount
                        + ", produced no classes: " + producedNoClasses);
                String message;
                if (!errOut.trim().isEmpty()) {
                    message = errOut;
                } else if (producedNoClasses) {
                    message = "Java compiler produced no .class files. Compiler output:\n" + stdOut;
                } else {
                    message = "Java compilation failed with global errors count: " + main.globalErrorsCount;
                }
                throw new zy(message);
            } else {
                LogUtil.d(TAG, "Compiling Java files took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            }
        }
    }

    public void buildApk() throws By {
        List<File> dexFilesForApk = new ArrayList<>();
        String firstDexPath;

        if (!dexesToAddButNotMerge.isEmpty()) {
            File projectMainDex = new File(yq.binDirectoryPath + File.separator + "dex" + File.separator + "classes.dex");
            if (projectMainDex.exists() && projectMainDex.length() > 0) {
                firstDexPath = projectMainDex.getAbsolutePath();
                dexesToAddButNotMerge.removeIf(file -> file.getAbsolutePath().equals(projectMainDex.getAbsolutePath()));
            } else {
                File binDexDir = new File(yq.binDirectoryPath + File.separator + "dex");
                File[] filesInBinDex = binDexDir.listFiles();
                StringBuilder binDexList = new StringBuilder();
                if (filesInBinDex != null && filesInBinDex.length > 0) {
                    for (File f : filesInBinDex) {
                        binDexList.append("\n - ").append(f.getName()).append(" (").append(f.length()).append(" bytes)");
                    }
                } else {
                    binDexList.append(" (directory is empty)");
                }
                throw new By("Build failed: Main project DEX file (" + projectMainDex.getAbsolutePath()
                        + ") is missing or 0 bytes. Files in bin/dex:" + binDexList);
            }
        } else {
            firstDexPath = yq.classesDexPath;
            File mergedMainDex = new File(firstDexPath);
            if (!mergedMainDex.exists() || mergedMainDex.length() == 0) {
                throw new By("Build failed: Merged main DEX file (" + firstDexPath + ") is missing or 0 bytes.");
            }
        }

        File firstDexFile = new File(firstDexPath);
        dexFilesForApk.add(firstDexFile);

        try {
            ApkBuilder apkBuilder = new ApkBuilder(new File(yq.unsignedUnalignedApkPath), new File(yq.resourcesApkPath), firstDexFile, null, null, System.out);

            for (Jp library : builtInLibraryManager.getLibraries()) {
                apkBuilder.addResourcesFromJar(BuiltInLibraries.getLibraryClassesJarPath(library.getName()));
            }

            for (String jarPath : mll.getJarLocalLibrary().split(":")) {
                if (!jarPath.trim().isEmpty()) {
                    apkBuilder.addResourcesFromJar(new File(jarPath));
                }
            }

            /* Add project's native libraries */
            File nativeLibrariesDirectory = new File(fpu.getPathNativelibs(yq.sc_id));
            if (nativeLibrariesDirectory.exists()) {
                apkBuilder.addNativeLibraries(nativeLibrariesDirectory);
            }

            /* Add Local libraries' native libraries */
            for (String nativeLibraryDirectory : mll.getNativeLibs()) {
                apkBuilder.addNativeLibraries(new File(nativeLibraryDirectory));
            }

            int dexNumber = 2;
            if (!dexesToAddButNotMerge.isEmpty()) {
                for (File dexFile : dexesToAddButNotMerge) {
                    if (!dexFile.getAbsolutePath().equals(firstDexFile.getAbsolutePath())) {
                        apkBuilder.addFile(dexFile, "classes" + dexNumber + ".dex");
                        dexFilesForApk.add(dexFile);
                        dexNumber++;
                    }
                }
            } else {
                while (true) {
                    File extraDex = new File(yq.binDirectoryPath, "classes" + dexNumber + ".dex");
                    if (!extraDex.exists()) {
                        break;
                    }
                    apkBuilder.addFile(extraDex, "classes" + dexNumber + ".dex");
                    dexFilesForApk.add(extraDex);
                    dexNumber++;
                }
            }

            verifyManifestClassesInDex(dexFilesForApk);

            apkBuilder.setDebugMode(false);
            apkBuilder.sealApk();
        } catch (ApkCreationException | SealedApkException e) {
            throw new By(e.getMessage());
        } catch (DuplicateFileException e) {
            String message = "Duplicate files from two libraries detected \r\n";
            message += "File1: " + e.getFile1() + " \r\n";
            message += "File2: " + e.getFile2() + " \r\n";
            message += "Archive path: " + e.getArchivePath();
            throw new By(message);
        }
        LogUtil.d(TAG, "Time passed since starting to compile resources until building the unsigned APK: " +
                (System.currentTimeMillis() - timestampResourceCompilationStarted) + " ms");
    }

    public void verifyManifestClassesInDex(List<File> dexFiles) throws By {
        File manifestFile = new File(yq.androidManifestPath);
        if (!manifestFile.exists()) return;

        List<String> requiredClasses = new ArrayList<>();
        try {
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
            org.w3c.dom.Document doc = db.parse(manifestFile);
            doc.getDocumentElement().normalize();

            String pkg = doc.getDocumentElement().getAttribute("package");
            if (pkg.isEmpty()) {
                pkg = yq.packageName;
            }

            List<String> tagNames = List.of("application", "activity", "service", "receiver");
            for (String tagName : tagNames) {
                org.w3c.dom.NodeList nodes = doc.getElementsByTagName(tagName);
                for (int i = 0; i < nodes.getLength(); i++) {
                    org.w3c.dom.Element elem = (org.w3c.dom.Element) nodes.item(i);
                    String androidName = elem.getAttribute("android:name");
                    if (androidName.isEmpty()) {
                        androidName = elem.getAttribute("name");
                    }
                    if (!androidName.isEmpty()) {
                        String fqcn;
                        if (androidName.startsWith(".")) {
                            fqcn = pkg + androidName;
                        } else if (!androidName.contains(".")) {
                            fqcn = pkg + "." + androidName;
                        } else {
                            fqcn = androidName;
                        }
                        if (!requiredClasses.contains(fqcn)) {
                            requiredClasses.add(fqcn);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.w(TAG, "Failed to parse manifest for DEX validation: " + e.getMessage());
            return;
        }

        java.util.Set<String> definedClasses = new java.util.HashSet<>();
        List<String> dexSummaryList = new ArrayList<>();

        for (File dexFile : dexFiles) {
            if (!dexFile.exists()) continue;
            int classCount = 0;
            try {
                Dex dex = new Dex(dexFile);
                List<String> typeNames = dex.typeNames();
                for (ClassDef classDef : dex.classDefs()) {
                    classCount++;
                    int typeIndex = classDef.getTypeIndex();
                    if (typeIndex >= 0 && typeIndex < typeNames.size()) {
                        String typeDescriptor = typeNames.get(typeIndex);
                        if (typeDescriptor.startsWith("L") && typeDescriptor.endsWith(";")) {
                            String fqcn = typeDescriptor.substring(1, typeDescriptor.length() - 1).replace('/', '.');
                            definedClasses.add(fqcn);
                        }
                    }
                }
            } catch (Exception e) {
                LogUtil.w(TAG, "Failed to inspect DEX file " + dexFile.getAbsolutePath() + ": " + e.getMessage());
            }

            dexSummaryList.add(dexFile.getName() + " (path: " + dexFile.getAbsolutePath() + ", size: " + dexFile.length() + " bytes, class count: " + classCount + ")");
        }

        List<String> missingClasses = new ArrayList<>();
        for (String reqClass : requiredClasses) {
            if (!definedClasses.contains(reqClass)) {
                missingClasses.add(reqClass);
            }
        }

        if (!missingClasses.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("Build failed: Manifest-referenced class(es) missing from DEX files in APK:\n");
            for (String missing : missingClasses) {
                errorMsg.append(" - ").append(missing).append("\n");
            }
            errorMsg.append("DEX files included in APK:\n");
            for (String summary : dexSummaryList) {
                errorMsg.append(" - ").append(summary).append("\n");
            }
            LogUtil.e(TAG, errorMsg.toString());
            throw new By(errorMsg.toString());
        } else {
            LogUtil.d(TAG, "Manifest classes verified in DEX files successfully. " + requiredClasses.size() + " manifest class(es) checked across " + dexFiles.size() + " DEX file(s).");
        }
    }

    /**
     * Either merges DEX files to as few as possible, or adds list of DEX files to add to the APK to
     * {@link #dexesToAddButNotMerge}.
     * <p>
     * Will merge DEX files if either the project's minSdkVersion is lower than 21, or if {@link jq#isDebugBuild}
     * of {@link yq#N} in {@link #yq} is false.
     *
     * @throws Exception Thrown if merging failed
     */
    public void getDexFilesReady() throws Exception {
        verifyManifestClassesExist();
        long savedTimeMillis = System.currentTimeMillis();
        ArrayList<File> dexes = new ArrayList<>();

        /* Add AndroidX MultiDex library */
        dexes.add(BuiltInLibraries.getLibraryDexFile(BuiltInLibraries.ANDROIDX_MULTIDEX));

        /* Add HTTP legacy files if wanted */
        if (!build_settings.getValue(BuildSettings.SETTING_NO_HTTP_LEGACY, ProjectSettings.SETTING_GENERIC_VALUE_FALSE)
                .equals(ProjectSettings.SETTING_GENERIC_VALUE_TRUE)) {
            dexes.add(BuiltInLibraries.getLibraryDexFile(BuiltInLibraries.HTTP_LEGACY_ANDROID));
        }

        /* Add used built-in libraries' DEX files */
        for (Jp builtInLibrary : builtInLibraryManager.getLibraries()) {
            dexes.add(BuiltInLibraries.getLibraryDexFile(builtInLibrary.getName()));
        }

        /* Add local libraries' main DEX files */
        ArrayList<HashMap<String, Object>> list = mll.list;
        for (int i1 = 0, listSize = list.size(); i1 < listSize; i1++) {
            HashMap<String, Object> localLibrary = list.get(i1);
            Object localLibraryName = localLibrary.get("name");

            if (localLibraryName instanceof String) {
                Object localLibraryDexPath = localLibrary.get("dexPath");

                if (localLibraryDexPath instanceof String) {
                    if (!proguard.libIsProguardFMEnabled((String) localLibraryName)) {
                        dexes.add(new File((String) localLibraryDexPath));
                        /* Add library's extra DEX files */
                        File localLibraryDirectory = new File((String) localLibraryDexPath).getParentFile();

                        if (localLibraryDirectory != null) {
                            File[] localLibraryFiles = localLibraryDirectory.listFiles();

                            if (localLibraryFiles != null) {
                                for (File localLibraryFile : localLibraryFiles) {
                                    String filename = localLibraryFile.getName();

                                    if (!filename.equals("classes.dex")
                                            && filename.startsWith("classes") && filename.endsWith(".dex")) {
                                        dexes.add(localLibraryFile);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    SketchwareUtil.toastError("Invalid DEX file path of enabled Local library #" + i1, Toast.LENGTH_LONG);
                }
            } else {
                SketchwareUtil.toastError("Invalid name of enabled Local library #" + i1, Toast.LENGTH_LONG);
            }
        }

        File binDexDir = new File(yq.binDirectoryPath + File.separator + "dex");
        File[] binDexFiles = binDexDir.listFiles();
        StringBuilder binDexLog = new StringBuilder("Contents of bin/dex before collecting dexes:\n");
        if (binDexFiles != null && binDexFiles.length > 0) {
            for (File binDexFile : binDexFiles) {
                binDexLog.append(" - ").append(binDexFile.getName()).append(" (").append(binDexFile.length()).append(" bytes)\n");
            }
        } else {
            binDexLog.append(" - (directory is empty or does not exist: ").append(binDexDir.getAbsolutePath()).append(")\n");
        }
        LogUtil.d(TAG, binDexLog.toString());

        for (String file : FileUtil.listFiles(yq.binDirectoryPath + File.separator + "dex", "dex")) {
            dexes.add(new File(file));
        }

        LogUtil.d(TAG, "Will merge these " + dexes.size() + " DEX files to classes.dex: " + dexes);

        if (settings.getMinSdkVersion() < 21 || !yq.N.isDebugBuild) {
            dexLibraries(new File(yq.binDirectoryPath), dexes);
            LogUtil.d(TAG, "Merging DEX files took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
        } else {
            dexesToAddButNotMerge = dexes;
            LogUtil.d(TAG, "Skipped merging DEX files due to debug build with minSdkVersion >= 21");
        }
    }

    /**
     * Extracts AAPT2 binaries (if they need to be extracted).
     *
     * @throws By If anything goes wrong while extracting
     */
    public void maybeExtractAapt2() throws By {
        var abi = Build.SUPPORTED_ABIS[0];
        try {
            if (hasFileChanged("aapt/aapt2-" + abi, aapt2Binary.getAbsolutePath())) {
                Os.chmod(aapt2Binary.getAbsolutePath(), S_IRUSR | S_IWUSR | S_IXUSR);
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "Failed to extract AAPT2 binaries", e);
            // noinspection ConstantValue: the bytecode's lying
            throw new By(
                    e instanceof FileNotFoundException fileNotFoundException ?
                            "Looks like the device's architecture (" + abi + ") isn't supported.\n"
                                    + Log.getStackTraceString(fileNotFoundException)
                            : "Couldn't extract AAPT2 binaries! Message: " + e.getMessage()
            );
        }
    }

    /**
     * Checks if we need to extract any library/dependency from assets to filesDir,
     * and extracts them, if needed. Also initializes used built-in libraries.
     */
    public void buildBuiltInLibraryInformation() {
        if (yq.N.g) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_APPCOMPAT);
            builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_COORDINATORLAYOUT);
            builtInLibraryManager.addLibrary(BuiltInLibraries.MATERIAL);
        }
        if (yq.N.isFirebaseEnabled) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.FIREBASE_COMMON);
        }
        if (yq.N.isFirebaseAuthUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.FIREBASE_AUTH);
        }
        if (yq.N.isFirebaseDatabaseUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.FIREBASE_DATABASE);
        }
        if (yq.N.isFirebaseStorageUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.FIREBASE_STORAGE);
        }
        if (yq.N.isMapUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.PLAY_SERVICES_MAPS);
        }
        if (yq.N.isAdMobEnabled) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.PLAY_SERVICES_ADS);
        }
        if (yq.N.isGsonUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.GSON);
        }
        if (yq.N.isGlideUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.GLIDE);
        }
        if (yq.N.isHttp3Used) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.OKHTTP_ANDROID);
        }

        KotlinCompilerBridge.maybeAddKotlinBuiltInLibraryDependenciesIfPossible(this, builtInLibraryManager);

        ExtLibSelected.addUsedDependencies(yq.N.x, builtInLibraryManager);
    }

    public BuiltInLibraryManager getBuiltInLibraryManager() {
        return builtInLibraryManager;
    }

    /**
     * Sign the debug APK file with testkey.
     * <p>
     * This method uses apksigner, but kellinwood's zipsigner as fallback.
     */
    public void signDebugApk() throws GeneralSecurityException, IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        TestkeySignBridge.signWithTestkey(yq.unsignedUnalignedApkPath, yq.finalToInstallApkPath);
    }

    private void mergeDexes(File target, List<Dex> dexes) throws IOException {
        DexMerger merger = new DexMerger(dexes.toArray(new Dex[0]), CollisionPolicy.KEEP_FIRST, new DxContext());
        merger.merge().writeTo(target);
    }

    /**
     * Adds all built-in libraries' ProGuard rules to {@code args}, if any.
     *
     * @param args List of arguments to add built-in libraries' ProGuard roles to.
     */
    private void proguardAddLibConfigs(List<String> args) {
        for (Jp library : builtInLibraryManager.getLibraries()) {
            File config = BuiltInLibraries.getLibraryProguardConfiguration(library.getName());
            if (config.exists()) {
                args.add("-include");
                args.add(config.getAbsolutePath());
            }
        }
    }

    /**
     * Generates default ProGuard R.java rules and adds them to {@code args}.
     *
     * @param args List of arguments to add R.java rules to.
     */
    private void proguardAddRjavaRules(List<String> args) {
        FileUtil.writeFile(yq.proguardAutoGeneratedExclusions, getRJavaRules());
        args.add("-include");
        args.add(yq.proguardAutoGeneratedExclusions);
    }

    private String getRJavaRules() {
        StringBuilder sb = new StringBuilder("# R.java rules");
        for (Jp jp : builtInLibraryManager.getLibraries()) {
            if (jp.hasResources() && !jp.getPackageName().isEmpty()) {
                sb.append("\n");
                sb.append("-keep class ");
                sb.append(jp.getPackageName());
                sb.append(".** { *; }");
            }
        }
        for (HashMap<String, Object> hashMap : mll.list) {
            String obj = hashMap.get("name").toString();
            if (hashMap.containsKey("packageName") && !proguard.libIsProguardFMEnabled(obj)) {
                sb.append("\n");
                sb.append("-keep class ");
                sb.append(hashMap.get("packageName").toString());
                sb.append(".** { *; }");
            }
        }
        sb.append("\n");
        sb.append("-keep class ").append(yq.packageName).append(".R { *; }").append('\n');
        return sb.toString();
    }

    public void runR8() throws IOException {
        long savedTimeMillis = System.currentTimeMillis();

        ArrayList<String> config = new ArrayList<>();
        config.add(ProguardHandler.ANDROID_PROGUARD_RULES_PATH);
        config.add(yq.proguardAaptRules);
        config.add(proguard.getCustomProguardRules());
        var rules = new ArrayList<>(Arrays.asList(getRJavaRules().split("\n")));
        for (Jp library : builtInLibraryManager.getLibraries()) {
            File f = BuiltInLibraries.getLibraryProguardConfiguration(library.getName());
            if (f.exists()) {
                config.add(f.getAbsolutePath());
            }
        }
        config.addAll(mll.getPgRules());
        ArrayList<String> jars = new ArrayList<>();
        jars.add(yq.compiledClassesPath + ".jar");

        for (HashMap<String, Object> hashMap : mll.list) {
            String obj = hashMap.get("name").toString();
            if (hashMap.containsKey("jarPath") && proguard.libIsProguardFMEnabled(obj)) {
                jars.add(hashMap.get("jarPath").toString());
            }
        }
        try {
            JarBuilder.INSTANCE.generateJar(new File(yq.compiledClassesPath));
            new R8Compiler(rules, config.toArray(new String[0]), getProguardClasspath().split(":"), jars.toArray(new String[0]), settings.getMinSdkVersion(), yq).compile();
        } catch (Exception e) {
            throw new IOException(e);
        }
        LogUtil.d(TAG, "R8 took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
    }

    public void runProguard() throws IOException {
        long savedTimeMillis = System.currentTimeMillis();

        ArrayList<String> args = new ArrayList<>();

        /* Include global ProGuard rules */
        args.add("-include");
        args.add(ProguardHandler.ANDROID_PROGUARD_RULES_PATH);

        /* Include ProGuard rules generated by AAPT2 */
        args.add("-include");
        args.add(yq.proguardAaptRules);

        /* Include custom ProGuard rules */
        args.add("-include");
        args.add(proguard.getCustomProguardRules());

        proguardAddLibConfigs(args);
        proguardAddRjavaRules(args);

        /* Include local libraries' ProGuard rules */
        for (String rule : mll.getPgRules()) {
            args.add("-include");
            args.add(rule);
        }

        /* Include compiled Java classes (?) IT SAYS -in*jar*s, so why include .class es? */
        args.add("-injars");
        args.add(yq.compiledClassesPath);

        for (HashMap<String, Object> hashMap : mll.list) {
            String obj = hashMap.get("name").toString();
            if (hashMap.containsKey("jarPath") && proguard.libIsProguardFMEnabled(obj)) {
                args.add("-injars");
                args.add(hashMap.get("jarPath").toString());
            }
        }
        args.add("-libraryjars");
        args.add(getProguardClasspath());
        args.add("-outjars");
        args.add(yq.proguardClassesPath);
        if (proguard.isDebugFilesEnabled()) {
            args.add("-printseeds");
            args.add(yq.proguardSeedsPath);
            args.add("-printusage");
            args.add(yq.proguardUsagePath);
            args.add("-printmapping");
            args.add(yq.proguardMappingPath);
        }
        LogUtil.d(TAG, "About to run ProGuard with these arguments: " + args);

        Configuration configuration = new Configuration();

        try {
            ConfigurationParser parser = new ConfigurationParser(args.toArray(new String[0]), System.getProperties());
            try {
                parser.parse(configuration);
            } finally {
                parser.close();
            }
        } catch (ParseException e) {
            throw new IOException(e);
        }

        try {
            new ProGuard(configuration).execute();
        } catch (Exception e) {
            throw new IOException(e);
        }

        LogUtil.d(TAG, "ProGuard took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
    }

    public void runStringfog() {
        try {
            StringFogMappingPrinter stringFogMappingPrinter = new StringFogMappingPrinter(new File(yq.binDirectoryPath,
                    "stringFogMapping.txt"));
            StringFogClassInjector stringFogClassInjector = new StringFogClassInjector(new String[0],
                    "UTF-8",
                    "com.github.megatronking.stringfog.xor.StringFogImpl",
                    "com.github.megatronking.stringfog.xor.StringFogImpl",
                    stringFogMappingPrinter);
            stringFogMappingPrinter.startMappingOutput();
            stringFogMappingPrinter.ouputInfo("UTF-8", "com.github.megatronking.stringfog.xor.StringFogImpl");
            stringFogClassInjector.doFog2ClassInDir(new File(yq.compiledClassesPath));
            KB.a(context, "stringfog/stringfog.zip", yq.compiledClassesPath);
        } catch (Exception e) {
            LogUtil.e("StringFog", "Failed to run StringFog", e);
        }
    }

    public void runZipalign(String inPath, String outPath) throws By {
        LogUtil.d(TAG, "About to zipalign " + inPath + " to " + outPath);
        long savedTimeMillis = System.currentTimeMillis();

        try (RandomAccessFile in = new RandomAccessFile(inPath, "r");
             FileOutputStream out = new FileOutputStream(outPath)) {
            ZipAlign.alignZip(in, out);
        } catch (IOException e) {
            throw new By("Couldn't run zipalign on " + inPath + " with output path " + outPath + ": " + Log.getStackTraceString(e));
        } catch (InvalidZipException e) {
            throw new By("Failed to zipalign due to the given zip being invalid: " + Log.getStackTraceString(e));
        }

        LogUtil.d(TAG, "zipalign took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
    }

    /**
     * Fails the build early if a class the manifest points at (application, activity,
     * service, receiver) was never compiled, instead of shipping an APK that crashes on launch.
     */
    public void verifyManifestClassesExist() throws By {
        File manifestFile = new File(yq.androidManifestPath);
        if (!manifestFile.exists()) return;

        try {
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
            org.w3c.dom.Document doc = db.parse(manifestFile);
            doc.getDocumentElement().normalize();

            String pkg = doc.getDocumentElement().getAttribute("package");
            if (pkg.isEmpty()) {
                pkg = yq.packageName;
            }

            List<String> tagNames = List.of("application", "activity", "service", "receiver");
            for (String tagName : tagNames) {
                org.w3c.dom.NodeList nodes = doc.getElementsByTagName(tagName);
                for (int i = 0; i < nodes.getLength(); i++) {
                    org.w3c.dom.Element elem = (org.w3c.dom.Element) nodes.item(i);
                    String androidName = elem.getAttribute("android:name");
                    if (androidName.isEmpty()) {
                        androidName = elem.getAttribute("name");
                    }
                    if (!androidName.isEmpty()) {
                        String fqcn;
                        if (androidName.startsWith(".")) {
                            fqcn = pkg + androidName;
                        } else if (!androidName.contains(".")) {
                            fqcn = pkg + "." + androidName;
                        } else {
                            fqcn = androidName;
                        }

                        /* Only check the project's own classes, not library ones */
                        if (fqcn.startsWith(pkg) || fqcn.startsWith(yq.packageName)) {
                            File classFile = new File(yq.compiledClassesPath, fqcn.replace('.', '/') + ".class");
                            if (!classFile.exists()) {
                                throw new By("Build failed: Manifest-referenced class '" + fqcn + "' (" + tagName + " name=" + androidName + ") was not found in compiled classes (" + classFile.getAbsolutePath() + "). Check if the source file failed to compile or was excluded.");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof By) {
                throw (By) e;
            }
            LogUtil.w(TAG, "Failed to verify manifest classes: " + e.getMessage());
        }
    }

    public void setBuildAppBundle(boolean buildAppBundle) {
        this.buildAppBundle = buildAppBundle;
    }
}