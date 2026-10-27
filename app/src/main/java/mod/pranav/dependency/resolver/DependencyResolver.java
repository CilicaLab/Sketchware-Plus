package mod.pranav.dependency.resolver;

import android.os.Environment;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import com.google.gson.Gson;

import org.cosmic.ide.dependency.resolver.UtilsKt;
import org.cosmic.ide.dependency.resolver.api.Artifact;
import org.cosmic.ide.dependency.resolver.api.EventReciever;
import org.cosmic.ide.dependency.resolver.api.Repository;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuiltInLibraries;
import sketchware.plus.utility.FileUtil;

public class DependencyResolver {

    private static final String DEFAULT_REPOS = "[\n" +
            "    {\"url\": \"https://maven.google.com\", \"name\": \"Google Maven\"},\n" +
            "    {\"url\": \"https://repo1.maven.org/maven2\", \"name\": \"Maven Central\"},\n" +
            "    {\"url\": \"https://jitpack.io\", \"name\": \"JitPack\"},\n" +
            "    {\"url\": \"https://repo.maven.apache.org/maven2\", \"name\": \"Apache Maven\"},\n" +
            "    {\"url\": \"https://jcenter.bintray.com\", \"name\": \"JCenter\"},\n" +
            "    {\"url\": \"https://oss.sonatype.org/content/repositories/releases\", \"name\": \"Sonatype\"},\n" +
            "    {\"url\": \"https://maven.aliyun.com/repository/public\", \"name\": \"Aliyun Public\"},\n" +
            "    {\"url\": \"https://repo.hortonworks.com/content/repositories/releases\", \"name\": \"HortanWorks\"},\n" +
            "    {\"url\": \"https://maven.atlassian.com/content/repositories/atlassian-public\", \"name\": \"Atlassian\"},\n" +
            "    {\"url\": \"https://repo.spring.io/plugins-release\", \"name\": \"Spring Plugins\"},\n" +
            "    {\"url\": \"https://repo.spring.io/libs-milestone\", \"name\": \"Spring Milestone\"}\n" +
            "]";

    private static final String DEX_TMP_DIR = ".dex_tmp";
    private static final String LEGACY_HASH_FILE = ".dex_hash";

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final boolean skipDependencies;
    private final BuildSettings buildSettings;

    private final String downloadPath = FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs";
    private final Path repositoriesJson = Paths.get(
            Environment.getExternalStorageDirectory().getAbsolutePath(),
            ".sketchware",
            "libs",
            "repositories.json"
    );

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Map<String, String> repoUrls = new HashMap<>();
    private final List<File> createdDirs = new ArrayList<>();

    public DependencyResolver(String groupId, String artifactId, String version, boolean skipDependencies, BuildSettings buildSettings) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.skipDependencies = skipDependencies;
        this.buildSettings = buildSettings;

        initRepositories();
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    // ------------------------------------------------------------------
    // Repositories
    // ------------------------------------------------------------------

    private ArrayList<HashMap<String, Object>> readRepoList() throws IOException {
        String text = new String(Files.readAllBytes(repositoriesJson), StandardCharsets.UTF_8);
        return new Gson().fromJson(text, Helper.TYPE_MAP_LIST);
    }

    private void initRepositories() {
        try {
            if (Files.notExists(repositoriesJson)) {
                if (repositoriesJson.getParent() != null) {
                    Files.createDirectories(repositoriesJson.getParent());
                }
                Files.write(repositoriesJson, DEFAULT_REPOS.getBytes(StandardCharsets.UTF_8));
            } else {
                String existing = new String(Files.readAllBytes(repositoriesJson), StandardCharsets.UTF_8);
                if (!existing.contains("maven.google.com") || !existing.contains("repo1.maven.org")) {
                    Files.write(repositoriesJson, DEFAULT_REPOS.getBytes(StandardCharsets.UTF_8));
                }
            }

            ArrayList<HashMap<String, Object>> repoList;
            try {
                repoList = readRepoList();
            } catch (Exception parseError) {
                // broken json on disk, reset to defaults
                Files.write(repositoriesJson, DEFAULT_REPOS.getBytes(StandardCharsets.UTF_8));
                repoList = readRepoList();
            }

            UtilsKt.getRepositories().clear();
            repoUrls.clear();

            if (repoList != null) {
                for (HashMap<String, Object> map : repoList) {
                    String url = (String) map.get("url");
                    String name = (String) map.get("name");
                    if (url == null) continue;

                    final String repoName = name != null ? name : "Unknown";
                    final String finalUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
                    repoUrls.put(repoName, finalUrl);

                    UtilsKt.getRepositories().add(new Repository() {
                        @Override
                        public String getName() {
                            return repoName;
                        }

                        @Override
                        public String getURL() {
                            EventReciever receiver = UtilsKt.getEventReciever();
                            if (receiver instanceof DependencyResolverCallback) {
                                ((DependencyResolverCallback) receiver).onSearchingRepository(repoName);
                            }
                            return finalUrl;
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // Callback
    // ------------------------------------------------------------------

    public static class DependencyResolverCallback extends EventReciever {
        @Override
        public void artifactFound(Artifact artifact) {}
        @Override
        public void onArtifactNotFound(Artifact artifact) {}
        @Override
        public void onFetchingLatestVersion(Artifact artifact) {}
        @Override
        public void onFetchedLatestVersion(Artifact artifact, String version) {}
        @Override
        public void onResolving(Artifact artifact, Artifact dependency) {}
        @Override
        public void onResolutionComplete(Artifact artifact) {}
        @Override
        public void onSkippingResolution(Artifact artifact) {}
        @Override
        public void onVersionNotFound(Artifact artifact) {}
        @Override
        public void onDependenciesNotFound(Artifact artifact) {}
        @Override
        public void onInvalidScope(Artifact artifact, String scope) {}
        @Override
        public void onInvalidPOM(Artifact artifact) {}
        @Override
        public void onDownloadStart(Artifact artifact) {}
        @Override
        public void onDownloadEnd(Artifact artifact) {}
        @Override
        public void onDownloadError(Artifact artifact, Throwable error) {}
        public void onDownloadProgress(Artifact dep, long bytesRead, long totalBytes) {}
        public void unzipping(Artifact artifact) {}
        public void dexing(Artifact artifact) {}
        /** total == -1 means unknown (indeterminate), e.g. while merging. */
        public void onDexingProgress(Artifact dep, int processed, int total) {}
        public void onTaskCompleted(List<String> artifacts) {}
        public void dexingFailed(Artifact dependency, Exception e) {}
        public void invalidPackaging(Artifact artifact) {}
        public void onSearchingRepository(String repoName) {}
    }

    // ------------------------------------------------------------------
    // Main flow
    // ------------------------------------------------------------------

    public void resolveDependency(DependencyResolverCallback callback) {
        boolean success = false;
        try {
            success = doResolve(callback);
        } finally {
            if (!success) {
                // cancelled or failed: remove folders created by this run
                for (File dir : createdDirs) {
                    deleteRecursive(dir);
                }
            }
            createdDirs.clear();
        }
    }

    private boolean doResolve(DependencyResolverCallback callback) {
        UtilsKt.setEventReciever(callback);
        Artifact dummyArtifact = new Artifact(groupId, artifactId, version, null, "jar");

        if (isCancelled()) return false;

        Artifact mainDep;
        try {
            mainDep = UtilsKt.getArtifact(groupId, artifactId, version);
        } catch (Throwable t) {
            if (!isCancelled()) callback.onArtifactNotFound(dummyArtifact);
            return false;
        }

        if (mainDep == null || isCancelled()) {
            if (!isCancelled()) callback.onArtifactNotFound(dummyArtifact);
            return false;
        }

        if (!"jar".equals(mainDep.getExtension()) && !"aar".equals(mainDep.getExtension())) {
            callback.invalidPackaging(mainDep);
            return false;
        }

        List<Path> libraryJars = new ArrayList<>();
        libraryJars.add(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.toPath().resolve("core-lambda-stubs.jar"));
        String androidJarPath = buildSettings.getValue(
                BuildSettings.SETTING_ANDROID_JAR_PATH,
                new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "android.jar").getAbsolutePath()
        );
        libraryJars.add(Paths.get(androidJarPath));

        List<Path> classpath = new ArrayList<>();
        String cpSetting = buildSettings.getValue(BuildSettings.SETTING_CLASSPATH, "");
        for (String cp : cpSetting.split(":")) {
            if (!cp.isEmpty()) classpath.add(Paths.get(cp));
        }

        // artifacts that need dexing, in order, keyed by folder name (dedupes)
        Map<String, Artifact> toProcess = new LinkedHashMap<>();
        Map<String, Path> jars = new HashMap<>();

        // ---- download + prepare main ----
        try {
            Path jar = fetchAndPrepare(mainDep, callback);
            if (isCancelled()) return false;
            toProcess.put(folderName(mainDep), mainDep);
            if (jar != null) {
                jars.put(folderName(mainDep), jar);
                classpath.add(jar);
            }
        } catch (Exception e) {
            if (!isCancelled()) callback.onDownloadError(mainDep, e);
            return false;
        }

        // ---- download + prepare dependencies ----
        if (!skipDependencies) {
            final Artifact finalMain = mainDep;
            try {
                kotlinx.coroutines.BuildersKt.runBlocking(
                        kotlinx.coroutines.Dispatchers.getIO(),
                        (scope, continuation) -> finalMain.resolveDependencyTree(null, null, continuation)
                );
            } catch (Throwable e) {
                if (!isCancelled()) callback.onDownloadError(mainDep, e);
                return false;
            }

            if (isCancelled()) return false;

            List<Artifact> allDeps;
            try {
                allDeps = new ArrayList<>(kotlinx.coroutines.BuildersKt.runBlocking(
                        kotlinx.coroutines.Dispatchers.getIO(),
                        (scope, continuation) -> finalMain.getAllDependencies(continuation)
                ));
            } catch (Throwable e) {
                allDeps = new ArrayList<>();
            }

            for (Artifact dep : allDeps) {
                if (isCancelled()) return false;

                if (!"jar".equals(dep.getExtension()) && !"aar".equals(dep.getExtension())) {
                    callback.invalidPackaging(dep);
                    continue;
                }
                if (dep.getVersion() == null || dep.getVersion().isEmpty()) {
                    callback.onVersionNotFound(dep);
                    continue;
                }

                String key = folderName(dep);
                if (toProcess.containsKey(key)) continue;

                try {
                    Path jar = fetchAndPrepare(dep, callback);
                    if (isCancelled()) return false;
                    toProcess.put(key, dep);
                    if (jar == null) {
                        callback.onDependenciesNotFound(dep);
                    } else {
                        jars.put(key, jar);
                        classpath.add(jar);
                    }
                } catch (Exception e) {
                    if (!isCancelled()) callback.onDownloadError(dep, e);
                    return false;
                }
            }
        }

        // ---- dex everything with the full classpath ----
        for (Map.Entry<String, Artifact> entry : toProcess.entrySet()) {
            if (isCancelled()) return false;

            Artifact dep = entry.getValue();
            Path jar = jars.get(entry.getKey());
            if (jar == null) continue; // resource-only lib, nothing to dex

            callback.dexing(dep);
            try {
                List<Path> classpathForDep = new ArrayList<>(classpath);
                classpathForDep.remove(jar);
                compileJar(dep, jar, classpathForDep, libraryJars, callback);
                if (isCancelled()) return false;
                callback.onResolutionComplete(dep);
            } catch (Exception e) {
                if (!isCancelled()) callback.dexingFailed(dep, e);
                return false;
            }
        }

        if (isCancelled()) return false;

        if (skipDependencies) {
            callback.onSkippingResolution(mainDep);
        }

        List<String> completed = new ArrayList<>(toProcess.keySet());
        callback.onTaskCompleted(completed);
        return true;
    }

    private String folderName(Artifact a) {
        return a.getArtifactId() + "-v" + a.getVersion();
    }

    private File prepareLibDir(Artifact a) {
        File dir = new File(downloadPath, folderName(a));
        if (!dir.exists()) {
            createdDirs.add(dir);
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Downloads the artifact, unzips AARs, writes config/proguard files.
     * Returns the path to classes.jar, or null if the lib has none.
     */
    private Path fetchAndPrepare(Artifact a, DependencyResolverCallback callback) throws Exception {
        File dir = prepareLibDir(a);
        String ext = a.getExtension();
        File archive = new File(dir, "classes." + ext);

        downloadArtifactFile(a, archive, callback);
        if (isCancelled()) return null;

        Path dirPath = dir.toPath();

        if ("aar".equals(ext)) {
            callback.unzipping(a);
            unzip(archive.toPath());
            Files.deleteIfExists(archive.toPath());

            String packageName = findPackageName(dir.getAbsolutePath(), a.getGroupId());
            Files.write(dirPath.resolve("config"), packageName.getBytes(StandardCharsets.UTF_8));

            Path pgPath = dirPath.resolve("proguard.txt");
            Path rulesPath = dirPath.resolve("rules.pro");
            if (!Files.exists(pgPath) && Files.exists(rulesPath)) {
                Files.copy(rulesPath, pgPath);
            }
        } else {
            try {
                Path configPath = dirPath.resolve("config");
                if (Files.notExists(configPath)) {
                    Files.write(configPath, a.getGroupId().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {}
        }

        Path jar = dirPath.resolve("classes.jar");
        return Files.exists(jar) ? jar : null;
    }

    // ------------------------------------------------------------------
    // Download
    // ------------------------------------------------------------------

    private void downloadArtifactFile(Artifact artifact, File destFile, DependencyResolverCallback callback) throws IOException {
        if (destFile.getParentFile() != null && !destFile.getParentFile().exists()) {
            destFile.getParentFile().mkdirs();
        }

        String repoUrl = null;
        if (artifact.getRepository() != null) {
            // map lookup, does NOT trigger onSearchingRepository
            repoUrl = repoUrls.get(artifact.getRepository().getName());
        }

        if (repoUrl != null) {
            if (!repoUrl.endsWith("/")) repoUrl += "/";
            String path = artifact.getGroupId().replace('.', '/') + "/" +
                    artifact.getArtifactId() + "/" +
                    artifact.getVersion() + "/" +
                    artifact.getArtifactId() + "-" + artifact.getVersion() + "." + artifact.getExtension();

            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(repoUrl + path).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.connect();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    long totalBytes = connection.getContentLengthLong();
                    callback.onDownloadStart(artifact);

                    try (InputStream input = new BufferedInputStream(connection.getInputStream());
                         OutputStream output = new BufferedOutputStream(new FileOutputStream(destFile))) {

                        byte[] buffer = new byte[16384];
                        long bytesReadTotal = 0;
                        long lastReportTime = 0;
                        long lastReportBytes = 0;
                        int read;

                        while ((read = input.read(buffer)) != -1) {
                            if (isCancelled()) {
                                throw new IOException("Download cancelled");
                            }
                            output.write(buffer, 0, read);
                            bytesReadTotal += read;

                            long now = System.currentTimeMillis();
                            boolean report = now - lastReportTime >= 100;
                            if (!report && totalBytes > 0) {
                                report = (bytesReadTotal - lastReportBytes) * 100 / totalBytes >= 1;
                            }
                            if (report) {
                                lastReportTime = now;
                                lastReportBytes = bytesReadTotal;
                                callback.onDownloadProgress(artifact, bytesReadTotal, totalBytes);
                            }
                        }
                        output.flush();
                        callback.onDownloadProgress(artifact, bytesReadTotal, totalBytes > 0 ? totalBytes : bytesReadTotal);
                    }
                    callback.onDownloadEnd(artifact);
                    return;
                }
            } catch (IOException | RuntimeException e) {
                if (destFile.exists()) destFile.delete();
                if (isCancelled()) {
                    throw new IOException("Download cancelled", e);
                }
                // otherwise fall through to the default downloader
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        if (isCancelled()) throw new IOException("Download cancelled");

        callback.onDownloadStart(artifact);
        try {
            artifact.downloadTo(destFile);
        } catch (Exception e) {
            if (destFile.exists()) destFile.delete();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        }
        if (isCancelled()) {
            if (destFile.exists()) destFile.delete();
            throw new IOException("Download cancelled");
        }
        callback.onDownloadEnd(artifact);
    }

    // ------------------------------------------------------------------
    // Package / unzip
    // ------------------------------------------------------------------

    private String findPackageName(String path, String defaultValue) {
        File manifest = new File(path, "AndroidManifest.xml");
        if (!manifest.isFile()) return defaultValue;
        try {
            String content = FileUtil.readFile(manifest.getAbsolutePath());
            Matcher m = Pattern.compile("<manifest.*package=\"(.*?)\"", Pattern.DOTALL).matcher(content);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return defaultValue;
    }

    private void unzip(Path path) throws IOException {
        Path baseDir = path.getParent().toAbsolutePath().normalize();
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                if (isCancelled()) throw new IOException("Unzip cancelled");

                ZipEntry entry = entries.nextElement();
                Path dest = baseDir.resolve(entry.getName()).normalize();
                if (!dest.startsWith(baseDir)) {
                    throw new IOException("Blocked unsafe zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    if (dest.getParent() != null) Files.createDirectories(dest.getParent());
                    try (InputStream input = zipFile.getInputStream(entry);
                         OutputStream output = Files.newOutputStream(dest)) {
                        byte[] buffer = new byte[16384];
                        int len;
                        while ((len = input.read(buffer)) != -1) {
                            if (isCancelled()) throw new IOException("Unzip cancelled");
                            output.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Dexing (real progress)
    // ------------------------------------------------------------------

    private static class DexCancelledException extends RuntimeException {
        DexCancelledException() {
            super("Dexing cancelled");
        }
    }

    /**
     * Receives one callback per dexed class from D8. That is the real progress source.
     * Each class is written to tmpDir and counted.
     */
    private class DexProgressConsumer implements DexFilePerClassFileConsumer {
        private final File tmpDir;
        private final Artifact artifact;
        private final int total;
        private final DependencyResolverCallback callback;
        private final AtomicInteger processed = new AtomicInteger();
        private long lastReport = 0;

        DexProgressConsumer(File tmpDir, Artifact artifact, int total, DependencyResolverCallback callback) {
            this.tmpDir = tmpDir;
            this.artifact = artifact;
            this.total = total;
            this.callback = callback;
        }

        @Override
        public void accept(String primaryClassDescriptor, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
            if (isCancelled()) throw new DexCancelledException();

            String name = primaryClassDescriptor;
            if (name.startsWith("L") && name.endsWith(";")) {
                name = name.substring(1, name.length() - 1);
            }

            try {
                File out = new File(tmpDir, name + ".dex");
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    os.write(data.getBuffer(), data.getOffset(), data.getLength());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            int done = Math.min(processed.incrementAndGet(), total);
            boolean report;
            long now = System.currentTimeMillis();
            synchronized (this) {
                report = now - lastReport >= 100 || done >= total;
                if (report) lastReport = now;
            }
            if (report) callback.onDexingProgress(artifact, done, total);
        }

        @Override
        public void finished(DiagnosticsHandler handler) {}
    }

    private int countClasses(File jarFile) {
        int count = 0;
        try (ZipFile zip = new ZipFile(jarFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory()
                        && name.endsWith(".class")
                        && !name.startsWith("META-INF/")
                        && !name.equals("module-info.class")) {
                    count++;
                }
            }
        } catch (Exception ignored) {}
        return count;
    }

    private void compileJar(Artifact artifact, Path jarFile, List<Path> classpath, List<Path> libraryJars, DependencyResolverCallback callback) throws Exception {
        if (isCancelled()) return;

        File jarObj = jarFile.toFile();
        File outDir = jarObj.getParentFile();
        File tmpDir = new File(outDir, DEX_TMP_DIR);
        ExecutorService executor = null;

        try {
            // stale cache file from older versions
            new File(outDir, LEGACY_HASH_FILE).delete();

            final int total = countClasses(jarObj);
            if (total == 0) {
                callback.onDexingProgress(artifact, 1, 1);
                return;
            }

            callback.onDexingProgress(artifact, 0, total);

            deleteRecursive(tmpDir);
            tmpDir.mkdirs();
            deleteOldDexFiles(outDir);

            int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
            executor = Executors.newFixedThreadPool(threads);

            // phase 1: dex class by class, real progress
            D8.run(
                    D8Command.builder()
                            .setIntermediate(true)
                            .setMode(CompilationMode.RELEASE)
                            .addProgramFiles(jarFile)
                            .addLibraryFiles(libraryJars)
                            .addClasspathFiles(classpath)
                            .setProgramConsumer(new DexProgressConsumer(tmpDir, artifact, total, callback))
                            .build(),
                    executor
            );

            if (isCancelled()) return;

            // phase 2: merge into classes.dex, unknown steps so indeterminate
            callback.onDexingProgress(artifact, total, -1);

            List<Path> dexFiles;
            try (Stream<Path> walk = Files.walk(tmpDir.toPath())) {
                dexFiles = walk.filter(p -> p.toString().endsWith(".dex")).collect(Collectors.toList());
            }
            if (dexFiles.isEmpty()) return;

            D8.run(
                    D8Command.builder()
                            .setIntermediate(true)
                            .setMode(CompilationMode.RELEASE)
                            .addProgramFiles(dexFiles)
                            .setOutput(outDir.toPath(), OutputMode.DexIndexed)
                            .build(),
                    executor
            );

            if (isCancelled()) return;

            callback.onDexingProgress(artifact, total, total);

        } catch (OutOfMemoryError oom) {
            System.gc();
            throw new Exception("Out of memory while dexing " + artifact.getArtifactId() + ". Close background apps and try again.", oom);
        } catch (Exception e) {
            if (isCancelled()) return; // D8 wraps our cancel exception, ignore
            throw e;
        } finally {
            if (executor != null) executor.shutdownNow();
            deleteRecursive(tmpDir);
            new File(outDir, LEGACY_HASH_FILE).delete();
        }
    }

    private void deleteOldDexFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            if (f.isFile() && n.startsWith("classes") && n.endsWith(".dex")) {
                f.delete();
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursive(child);
        }
        file.delete();
    }
}