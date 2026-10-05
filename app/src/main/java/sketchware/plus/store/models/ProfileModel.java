package sketchware.plus.store.models;

import com.google.gson.annotations.SerializedName;

public class ProfileModel {
    @SerializedName("id")
    private String id;

    @SerializedName("username")
    private String username;

    @SerializedName("is_admin")
    private boolean isAdmin;

    @SerializedName("banned")
    private boolean banned;

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public boolean isBanned() {
        return banned;
    }
}
