package sketchware.plus.snapshot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SnapshotEntry {

    private String id;
    private String name;
    private long timestamp;
    private String zipFileName;
    private List<String> changedFiles;
    private Map<String, String> fileHashes;

    public SnapshotEntry() {
        this.changedFiles = new ArrayList<>();
        this.fileHashes = new HashMap<>();
    }

    public SnapshotEntry(String id, String name, long timestamp, String zipFileName, List<String> changedFiles, Map<String, String> fileHashes) {
        this.id = id;
        this.name = name;
        this.timestamp = timestamp;
        this.zipFileName = zipFileName;
        this.changedFiles = changedFiles != null ? changedFiles : new ArrayList<>();
        this.fileHashes = fileHashes != null ? fileHashes : new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getZipFileName() {
        return zipFileName;
    }

    public void setZipFileName(String zipFileName) {
        this.zipFileName = zipFileName;
    }

    public List<String> getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(List<String> changedFiles) {
        this.changedFiles = changedFiles;
    }

    public Map<String, String> getFileHashes() {
        return fileHashes;
    }

    public void setFileHashes(Map<String, String> fileHashes) {
        this.fileHashes = fileHashes;
    }

    public JSONObject toJsonObject() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("timestamp", timestamp);
        json.put("zipFileName", zipFileName);

        JSONArray changedArray = new JSONArray();
        if (changedFiles != null) {
            for (String file : changedFiles) {
                changedArray.put(file);
            }
        }
        json.put("changedFiles", changedArray);

        JSONObject hashesObj = new JSONObject();
        if (fileHashes != null) {
            for (Map.Entry<String, String> entry : fileHashes.entrySet()) {
                hashesObj.put(entry.getKey(), entry.getValue());
            }
        }
        json.put("fileHashes", hashesObj);

        return json;
    }

    public static SnapshotEntry fromJsonObject(JSONObject json) {
        if (json == null) return null;
        try {
            SnapshotEntry entry = new SnapshotEntry();
            entry.setId(json.optString("id"));
            entry.setName(json.optString("name"));
            entry.setTimestamp(json.optLong("timestamp"));
            entry.setZipFileName(json.optString("zipFileName"));

            List<String> changedList = new ArrayList<>();
            JSONArray changedArray = json.optJSONArray("changedFiles");
            if (changedArray != null) {
                for (int i = 0; i < changedArray.length(); i++) {
                    changedList.add(changedArray.getString(i));
                }
            }
            entry.setChangedFiles(changedList);

            Map<String, String> hashMap = new HashMap<>();
            JSONObject hashesObj = json.optJSONObject("fileHashes");
            if (hashesObj != null) {
                Iterator<String> keys = hashesObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    hashMap.put(key, hashesObj.optString(key));
                }
            }
            entry.setFileHashes(hashMap);

            return entry;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
