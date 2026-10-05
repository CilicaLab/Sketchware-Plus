package sketchware.plus.store.auth;

import com.google.gson.annotations.SerializedName;

public class UserMetadata {
    @SerializedName("username")
    private String username;

    @SerializedName(value = "is_admin", alternate = {"isAdmin", "admin", "role"})
    private Object isAdminObj;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isAdmin() {
        if (isAdminObj instanceof Boolean) {
            return (Boolean) isAdminObj;
        } else if (isAdminObj instanceof String) {
            String s = ((String) isAdminObj).toLowerCase();
            return s.equals("true") || s.equals("admin") || s.equals("1") || s.equals("yes");
        }
        return false;
    }

    public void setAdmin(boolean admin) {
        this.isAdminObj = admin;
    }
}
