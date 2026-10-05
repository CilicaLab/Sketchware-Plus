package sketchware.plus.store.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class StoreItemInsert {
    @SerializedName("author_id")
    public String authorId;

    @SerializedName("name")
    public String name;

    @SerializedName("description")
    public String description;

    @SerializedName("type")
    public String type;

    @SerializedName("tags")
    public List<String> tags;

    @SerializedName("content_names")
    public List<String> contentNames;

    @SerializedName("json")
    public String json;
    // Add other fields as needed
}
