package sketchware.plus.snapshot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import a.a.a.jC;

public class SnapshotManager {

    private static final String PREF_NAME = "sketchware_plus_snapshots_prefs";
    private static final String KEY_ENABLED_PREFIX = "super_snapshot_enabled_";

    private static final int MAX_SNAPSHOTS = 30;
    private static final long DEBOUNCE_MS = 5000L;
    private static final long MIN_FREE_SPACE_BYTES = 20 * 1024 * 1024L; // 20 MB

    private static SnapshotManager instance;

    private final Context appContext;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private final ConcurrentHashMap<String, ReentrantLock> projectLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSnapshotTimes = new HashMap<>();

    public interface SnapshotCallback {
        void onSuccess();
        void onError(String message);
    }

    private SnapshotManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized SnapshotManager getInstance(Context context) {
        if (instance == null) {
            instance = new SnapshotManager(context);
        }
        return instance;
    }

    private ReentrantLock getLock(String scId) {
        return projectLocks.computeIfAbsent(scId, k -> new ReentrantLock());
    }

    // --- Toggle Settings ---

    public boolean isSnapshotEnabled(String scId) {
        if (scId == null) return false;
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ENABLED_PREFIX + scId, false);
    }

    public void setSnapshotEnabled(String scId, boolean enabled) {
        if (scId == null) return;
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ENABLED_PREFIX + scId, enabled).apply();
    }

    // --- Directory Helpers ---

    public File getSnapshotsDir(String scId) {
        File base = new File(Environment.getExternalStorageDirectory(), ".sketchware/snapshots/" + scId);
        if (!base.exists()) {
            base.mkdirs();
        }
        return base;
    }

    public File getIndexFile(String scId) {
        return new File(getSnapshotsDir(scId), "index.json");
    }

    private List<File> getProjectDirectories(String scId) {
        List<File> dirs = new ArrayList<>();
        dirs.add(new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + scId));
        dirs.add(new File(Environment.getExternalStorageDirectory(), ".sketchware/mysc/list/" + scId));
        dirs.add(new File(Environment.getExternalStorageDirectory(), ".sketchware/resources/images/" + scId));
        dirs.add(new File(Environment.getExternalStorageDirectory(), ".sketchware/resources/fonts/" + scId));
        dirs.add(new File(Environment.getExternalStorageDirectory(), ".sketchware/resources/sounds/" + scId));
        return dirs;
    }

    // --- Core Save Hook ---

    public void onProjectSaved(String scId) {
        if (scId == null || !isSnapshotEnabled(scId)) {
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (lastSnapshotTimes) {
            Long lastTime = lastSnapshotTimes.get(scId);
            if (lastTime != null && (now - lastTime) < DEBOUNCE_MS) {
                return; // Debounced
            }
            lastSnapshotTimes.put(scId, now);
        }

        executor.execute(() -> createSnapshotInternal(scId, null, false));
    }

    public void createManualSnapshot(String scId, String customName, SnapshotCallback callback) {
        executor.execute(() -> {
            boolean success = createSnapshotInternal(scId, customName, true);
            if (callback != null) {
                mainHandler.post(() -> {
                    if (success) callback.onSuccess();
                    else callback.onError("Failed to create snapshot");
                });
            }
        });
    }

    private boolean createSnapshotInternal(String scId, String customName, boolean force) {
        ReentrantLock lock = getLock(scId);
        lock.lock();
        try {
            File snapDir = getSnapshotsDir(scId);
            if (snapDir.getFreeSpace() < MIN_FREE_SPACE_BYTES) {
                return false; // Low storage
            }

            Map<String, File> fileMap = collectProjectFiles(scId);
            Map<String, String> currentHashes = computeHashes(fileMap);

            List<SnapshotEntry> entries = loadIndex(scId);
            SnapshotEntry lastEntry = entries.isEmpty() ? null : entries.get(entries.size() - 1);

            List<String> changedFiles = new ArrayList<>();
            Map<String, String> lastHashes = lastEntry != null ? lastEntry.getFileHashes() : new HashMap<>();

            for (Map.Entry<String, String> entry : currentHashes.entrySet()) {
                String relPath = entry.getKey();
                String hash = entry.getValue();
                if (!lastHashes.containsKey(relPath) || !hash.equals(lastHashes.get(relPath))) {
                    changedFiles.add(relPath);
                }
            }

            for (String relPath : lastHashes.keySet()) {
                if (!currentHashes.containsKey(relPath)) {
                    changedFiles.add("[deleted] " + relPath);
                }
            }

            if (!force && changedFiles.isEmpty() && lastEntry != null) {
                return true; // No changes
            }

            String name = customName != null ? customName : autoGenerateName(changedFiles);
            long timestamp = System.currentTimeMillis();
            String snapId = UUID.randomUUID().toString().substring(0, 8);
            String zipFileName = "snapshot_" + timestamp + "_" + snapId + ".zip";
            File zipFile = new File(snapDir, zipFileName);

            boolean zipped = createZip(fileMap, zipFile);
            if (!zipped) {
                return false;
            }

            SnapshotEntry newEntry = new SnapshotEntry(
                    snapId,
                    name,
                    timestamp,
                    zipFileName,
                    changedFiles,
                    currentHashes
            );

            entries.add(newEntry);
            pruneOldSnapshots(scId, entries);
            saveIndex(scId, entries);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            lock.unlock();
        }
    }

    private String autoGenerateName(List<String> changedFiles) {
        String dateStr = new SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(new Date());

        if (changedFiles.isEmpty()) {
            return "Project Snapshot (" + dateStr + ")";
        }

        if (changedFiles.size() == 1) {
            String f = changedFiles.get(0);
            if (f.endsWith(".java") || f.endsWith(".kt")) {
                return "Edited code: " + getFileName(f) + " (" + dateStr + ")";
            } else if (f.endsWith(".xml")) {
                return "Edited layout: " + getFileName(f) + " (" + dateStr + ")";
            } else if (f.contains("images/") || f.contains("fonts/") || f.contains("sounds/")) {
                return "Updated resource: " + getFileName(f) + " (" + dateStr + ")";
            } else if (f.contains("project")) {
                return "Updated project config (" + dateStr + ")";
            }
        }

        boolean hasCode = false;
        boolean hasLayout = false;
        boolean hasResource = false;

        for (String f : changedFiles) {
            if (f.endsWith(".java") || f.endsWith(".kt")) hasCode = true;
            if (f.endsWith(".xml")) hasLayout = true;
            if (f.contains("images/") || f.contains("fonts/") || f.contains("sounds/")) hasResource = true;
        }

        if (hasCode && !hasLayout && !hasResource) {
            return "Edited logic & code (" + dateStr + ")";
        } else if (hasLayout && !hasCode && !hasResource) {
            return "Edited layout XML (" + dateStr + ")";
        } else if (hasResource && !hasCode && !hasLayout) {
            return "Updated assets/resources (" + dateStr + ")";
        }

        return "Changed " + changedFiles.size() + " file(s) (" + dateStr + ")";
    }

    private String getFileName(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    // --- Collect & Hash Files ---

    private Map<String, File> collectProjectFiles(String scId) {
        Map<String, File> map = new HashMap<>();
        File storageDir = Environment.getExternalStorageDirectory();

        File dataDir = new File(storageDir, ".sketchware/data/" + scId);
        if (dataDir.exists()) {
            collectFilesRecursive(dataDir, "data", map);
        }

        File myscDir = new File(storageDir, ".sketchware/mysc/list/" + scId);
        if (myscDir.exists()) {
            collectFilesRecursive(myscDir, "mysc", map);
        }

        File imgDir = new File(storageDir, ".sketchware/resources/images/" + scId);
        if (imgDir.exists()) {
            collectFilesRecursive(imgDir, "resources/images", map);
        }

        File fontDir = new File(storageDir, ".sketchware/resources/fonts/" + scId);
        if (fontDir.exists()) {
            collectFilesRecursive(fontDir, "resources/fonts", map);
        }

        File soundDir = new File(storageDir, ".sketchware/resources/sounds/" + scId);
        if (soundDir.exists()) {
            collectFilesRecursive(soundDir, "resources/sounds", map);
        }

        return map;
    }

    private void collectFilesRecursive(File file, String currentPath, Map<String, File> map) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    collectFilesRecursive(child, currentPath + "/" + child.getName(), map);
                }
            }
        } else if (file.isFile()) {
            map.put(currentPath, file);
        }
    }

    private Map<String, String> computeHashes(Map<String, File> fileMap) {
        Map<String, String> hashes = new HashMap<>();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];

            for (Map.Entry<String, File> entry : fileMap.entrySet()) {
                File file = entry.getValue();
                md.reset();
                try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        md.update(buffer, 0, read);
                    }
                    byte[] digest = md.digest();
                    StringBuilder sb = new StringBuilder();
                    for (byte b : digest) {
                        sb.append(String.format("%02x", b));
                    }
                    hashes.put(entry.getKey(), sb.toString());
                } catch (Exception e) {
                    // Ignore unreadable file
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return hashes;
    }

    // --- Zip & Unzip ---

    private boolean createZip(Map<String, File> fileMap, File destZip) {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destZip)))) {
            byte[] buffer = new byte[8192];
            for (Map.Entry<String, File> entry : fileMap.entrySet()) {
                String relPath = entry.getKey();
                File file = entry.getValue();
                if (!file.exists()) continue;

                ZipEntry ze = new ZipEntry(relPath);
                zos.putNextEntry(ze);

                try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        zos.write(buffer, 0, read);
                    }
                }
                zos.closeEntry();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean unzipToDirectory(File zipFile, File targetDir) {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            byte[] buffer = new byte[8192];
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                File outFile = new File(targetDir, ze.getName());
                if (ze.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile))) {
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, read);
                        }
                    }
                }
                zis.closeEntry();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- Restore ---

    public void restoreSnapshot(String scId, SnapshotEntry targetEntry, SnapshotCallback callback) {
        executor.execute(() -> {
            ReentrantLock lock = getLock(scId);
            lock.lock();
            try {
                // Safety snapshot first
                createSnapshotInternal(scId, "Before restore (" + targetEntry.getName() + ")", true);

                File snapDir = getSnapshotsDir(scId);
                File targetZip = new File(snapDir, targetEntry.getZipFileName());

                if (!targetZip.exists()) {
                    notifyError(callback, "Snapshot archive file is missing.");
                    return;
                }

                File tempExtractDir = new File(snapDir, "temp_restore_" + System.currentTimeMillis());
                if (tempExtractDir.exists()) {
                    deleteDirRecursive(tempExtractDir);
                }
                tempExtractDir.mkdirs();

                boolean unzipped = unzipToDirectory(targetZip, tempExtractDir);
                if (!unzipped) {
                    deleteDirRecursive(tempExtractDir);
                    notifyError(callback, "Failed to extract snapshot archive.");
                    return;
                }

                // Restore files from temp directory back to system locations
                boolean restored = applyRestoredFiles(scId, tempExtractDir);
                deleteDirRecursive(tempExtractDir);

                if (restored) {
                    if (callback != null) {
                        mainHandler.post(callback::onSuccess);
                    }
                } else {
                    notifyError(callback, "Error applying restored snapshot files.");
                }

            } catch (Exception e) {
                e.printStackTrace();
                notifyError(callback, "Restore exception: " + e.getMessage());
            } finally {
                lock.unlock();
            }
        });
    }

    private boolean applyRestoredFiles(String scId, File tempExtractDir) {
        try {
            File storageDir = Environment.getExternalStorageDirectory();

            File dataTemp = new File(tempExtractDir, "data");
            if (dataTemp.exists()) {
                File targetData = new File(storageDir, ".sketchware/data/" + scId);
                deleteDirRecursive(targetData);
                copyDirRecursive(dataTemp, targetData);
            }

            File myscTemp = new File(tempExtractDir, "mysc");
            if (myscTemp.exists()) {
                File targetMysc = new File(storageDir, ".sketchware/mysc/list/" + scId);
                deleteDirRecursive(targetMysc);
                copyDirRecursive(myscTemp, targetMysc);
            }

            File resTemp = new File(tempExtractDir, "resources");
            if (resTemp.exists()) {
                File[] resTypes = resTemp.listFiles();
                if (resTypes != null) {
                    for (File typeDir : resTypes) {
                        File targetRes = new File(storageDir, ".sketchware/resources/" + typeDir.getName() + "/" + scId);
                        deleteDirRecursive(targetRes);
                        copyDirRecursive(typeDir, targetRes);
                    }
                }
            }

            // Invalidate Sketchware's in-memory project cache so it re-reads from disk
            jC.a();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void notifyError(SnapshotCallback callback, String msg) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(msg));
        }
    }

    // --- Index & Pruning ---

    public List<SnapshotEntry> getSnapshots(String scId) {
        ReentrantLock lock = getLock(scId);
        lock.lock();
        try {
            List<SnapshotEntry> list = loadIndex(scId);
            Collections.reverse(list); // Newest first
            return list;
        } finally {
            lock.unlock();
        }
    }

    public void deleteSnapshot(String scId, String snapshotId, SnapshotCallback callback) {
        executor.execute(() -> {
            ReentrantLock lock = getLock(scId);
            lock.lock();
            try {
                List<SnapshotEntry> list = loadIndex(scId);
                SnapshotEntry target = null;
                for (SnapshotEntry entry : list) {
                    if (entry.getId().equals(snapshotId)) {
                        target = entry;
                        break;
                    }
                }

                if (target != null) {
                    list.remove(target);
                    File zipFile = new File(getSnapshotsDir(scId), target.getZipFileName());
                    if (zipFile.exists()) {
                        zipFile.delete();
                    }
                    saveIndex(scId, list);
                }

                if (callback != null) {
                    mainHandler.post(callback::onSuccess);
                }
            } catch (Exception e) {
                e.printStackTrace();
                notifyError(callback, "Failed to delete snapshot.");
            } finally {
                lock.unlock();
            }
        });
    }

    public void clearSnapshots(String scId, SnapshotCallback callback) {
        executor.execute(() -> {
            ReentrantLock lock = getLock(scId);
            lock.lock();
            try {
                File snapDir = getSnapshotsDir(scId);
                deleteDirRecursive(snapDir);
                snapDir.mkdirs();

                if (callback != null) {
                    mainHandler.post(callback::onSuccess);
                }
            } catch (Exception e) {
                e.printStackTrace();
                notifyError(callback, "Failed to clear snapshots.");
            } finally {
                lock.unlock();
            }
        });
    }

    public long getStorageSize(String scId) {
        File dir = getSnapshotsDir(scId);
        return getFolderSize(dir);
    }

    public void onProjectDeleted(String scId) {
        executor.execute(() -> {
            ReentrantLock lock = getLock(scId);
            lock.lock();
            try {
                File snapDir = getSnapshotsDir(scId);
                deleteDirRecursive(snapDir);
                setSnapshotEnabled(scId, false);
            } finally {
                lock.unlock();
            }
        });
    }

    private void pruneOldSnapshots(String scId, List<SnapshotEntry> entries) {
        while (entries.size() > MAX_SNAPSHOTS) {
            SnapshotEntry oldest = entries.remove(0);
            File zip = new File(getSnapshotsDir(scId), oldest.getZipFileName());
            if (zip.exists()) {
                zip.delete();
            }
        }
    }

    private List<SnapshotEntry> loadIndex(String scId) {
        List<SnapshotEntry> list = new ArrayList<>();
        File indexFile = getIndexFile(scId);
        if (!indexFile.exists()) {
            return list;
        }

        try (InputStream is = new FileInputStream(indexFile)) {
            byte[] bytes = new byte[(int) indexFile.length()];
            is.read(bytes);
            String jsonStr = new String(bytes, "UTF-8");
            JSONArray array = new JSONArray(jsonStr);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                SnapshotEntry entry = SnapshotEntry.fromJsonObject(obj);
                if (entry != null) {
                    list.add(entry);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // Corrupted index -> reset or salvage
        }
        return list;
    }

    private void saveIndex(String scId, List<SnapshotEntry> entries) {
        try {
            JSONArray array = new JSONArray();
            for (SnapshotEntry entry : entries) {
                array.put(entry.toJsonObject());
            }

            File indexFile = getIndexFile(scId);
            try (FileOutputStream fos = new FileOutputStream(indexFile)) {
                fos.write(array.toString(2).getBytes("UTF-8"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- File Utility Helpers ---

    private long getFolderSize(File file) {
        long size = 0;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    size += getFolderSize(child);
                }
            }
        } else if (file.isFile()) {
            size += file.length();
        }
        return size;
    }

    private void deleteDirRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirRecursive(child);
                }
            }
        }
        file.delete();
    }

    private void copyDirRecursive(File src, File dest) throws Exception {
        if (src.isDirectory()) {
            if (!dest.exists()) dest.mkdirs();
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyDirRecursive(child, new File(dest, child.getName()));
                }
            }
        } else if (src.isFile()) {
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            try (InputStream is = new FileInputStream(src);
                 FileOutputStream os = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }
        }
    }
}
