package sketchware.plus.store.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class StoreItem implements Serializable {
    public static final String TYPE_BLOCK = "BLOCK";
    public static final String TYPE_COMPONENT = "COMPONENT";

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("author")
    private String author;

    @SerializedName("author_id")
    private String authorId;

    @SerializedName("description")
    private String description;

    @SerializedName("type")
    private String type; // BLOCK or COMPONENT

    @SerializedName("tags")
    private List<String> tags;

    @SerializedName("downloads")
    private int downloads;

    @SerializedName("created_at")
    private Object createdAt;

    @SerializedName("approved")
    private boolean approved;

    @SerializedName("json")
    private String json;

    @SerializedName("content_names")
    private List<String> contentNames; // list of block/component names inside

    public StoreItem() {
        this.tags = new ArrayList<>();
        this.contentNames = new ArrayList<>();
    }

    public StoreItem(String id, String name, String author, String description, String type,
                     List<String> tags, int downloads, long createdAt, boolean approved,
                     String json, List<String> contentNames) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.description = description;
        this.type = type;
        this.tags = tags != null ? tags : new ArrayList<>();
        this.downloads = downloads;
        this.createdAt = createdAt;
        this.approved = approved;
        this.json = json;
        this.contentNames = contentNames != null ? contentNames : new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public int getDownloads() { return downloads; }
    public void setDownloads(int downloads) { this.downloads = downloads; }

    public long getCreatedAt() {
        if (createdAt instanceof Number) {
            return ((Number) createdAt).longValue();
        } else if (createdAt instanceof String) {
            try {
                return Instant.parse((String) createdAt).toEpochMilli();
            } catch (Exception e) {
                try {
                    return Long.parseLong((String) createdAt);
                } catch (Exception ignored) {}
            }
        }
        return System.currentTimeMillis();
    }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public String getJson() { return json; }
    public void setJson(String json) { this.json = json; }

    public List<String> getContentNames() { return contentNames; }
    public void setContentNames(List<String> contentNames) { this.contentNames = contentNames; }
}
