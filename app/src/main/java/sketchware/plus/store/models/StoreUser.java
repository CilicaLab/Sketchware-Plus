package sketchware.plus.store.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class StoreUser implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("username")
    private String username;

    @SerializedName("is_admin")
    private boolean isAdmin;

    @SerializedName("banned")
    private boolean isBanned;

    public StoreUser() {
    }

    public StoreUser(String id, String username, boolean isAdmin) {
        this.id = id;
        this.username = username;
        this.isAdmin = isAdmin;
        this.isBanned = false;
    }

    public StoreUser(String id, String username, boolean isAdmin, boolean isBanned) {
        this.id = id;
        this.username = username;
        this.isAdmin = isAdmin;
        this.isBanned = isBanned;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }

    public boolean isBanned() { return isBanned; }
    public void setBanned(boolean banned) { isBanned = banned; }
}
