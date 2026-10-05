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

    public StoreUser() {
    }

    public StoreUser(String id, String username, boolean isAdmin) {
        this.id = id;
        this.username = username;
        this.isAdmin = isAdmin;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }
}
