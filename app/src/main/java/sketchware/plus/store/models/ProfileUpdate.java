package sketchware.plus.store.models;

import com.google.gson.annotations.SerializedName;

public class ProfileUpdate {
    @SerializedName("username")
    public String username;

    public ProfileUpdate(String username) {
        this.username = username;
    }
}
