package sketchware.plus.store.auth;

import com.google.gson.annotations.SerializedName;

public class AuthUser {
    @SerializedName("id")
    private String id;

    @SerializedName("email")
    private String email;

    @SerializedName("user_metadata")
    private UserMetadata userMetadata;

    @SerializedName("app_metadata")
    private UserMetadata appMetadata;

    public AuthUser() {}

    public AuthUser(String id, String email, UserMetadata userMetadata) {
        this.id = id;
        this.email = email;
        this.userMetadata = userMetadata;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public UserMetadata getUserMetadata() {
        return userMetadata;
    }

    public UserMetadata getAppMetadata() {
        return appMetadata;
    }

    public boolean isAdmin() {
        if (userMetadata != null && userMetadata.isAdmin()) {
            return true;
        }
        if (appMetadata != null && appMetadata.isAdmin()) {
            return true;
        }
        return false;
    }
}
