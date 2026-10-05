package sketchware.plus.store.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class StoreReport implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("item_id")
    private String itemId;

    @SerializedName("reason")
    private String reason; // Malicious, Spam, Broken, Stolen, Other

    @SerializedName("note")
    private String note;

    @SerializedName("reporter_id")
    private String reporter;

    public StoreReport() {
    }

    @SerializedName("store_items") private ItemRef itemRef;

    public static class ItemRef implements Serializable {
        @SerializedName("name") public String name;
        @SerializedName("author_id") public String authorId;
    }

    public String getItemName() { return itemRef != null ? itemRef.name : null; }
    public String getAuthorId() { return itemRef != null ? itemRef.authorId : null; }

    public StoreReport(String id, String itemId, String reason, String note, String reporter) {
        this.id = id;
        this.itemId = itemId;
        this.reason = reason;
        this.note = note;
        this.reporter = reporter;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getReporter() { return reporter; }
    public void setReporter(String reporter) { this.reporter = reporter; }
}
