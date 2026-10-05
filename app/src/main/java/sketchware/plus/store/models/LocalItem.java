package sketchware.plus.store.models;

import java.io.Serializable;

public class LocalItem implements Serializable {
    private String id;
    private String name;
    private String type; // BLOCK or COMPONENT
    private boolean selected;

    public LocalItem(String id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.selected = false;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
