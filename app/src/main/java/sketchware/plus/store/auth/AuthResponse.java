package sketchware.plus.store.auth;

import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("refresh_token")
    private String refreshToken;

    @SerializedName("expires_in")
    private long expiresIn;

    @SerializedName("user")
    private AuthUser user;

    @SerializedName("id")
    private String id;

    @SerializedName("email")
    private String email;

    @SerializedName("user_metadata")
    private UserMetadata userMetadata;

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public AuthUser getUser() {
        if (user != null) {
            return user;
        }
        if (id != null || email != null) {
            return new AuthUser(id, email, userMetadata);
        }
        return null;
    }
}
