package sketchware.plus.store.auth;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class AuthRequest {
    @SerializedName("email")
    private String email;

    @SerializedName("password")
    private String password;

    @SerializedName("data")
    private Map<String, Object> data;

    @SerializedName("refresh_token")
    private String refreshToken;

    public AuthRequest(String email, String password, Map<String, Object> data) {
        this.email = email;
        this.password = password;
        this.data = data;
    }

    public AuthRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
