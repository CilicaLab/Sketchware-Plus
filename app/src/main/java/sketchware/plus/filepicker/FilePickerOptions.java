package sketchware.plus.filepicker;

import android.os.Environment;

import java.io.File;
import java.io.Serializable;

public class FilePickerOptions implements Serializable {
    private boolean multipleSelection = false;
    private String[] extensions = null;
    private File initialDirectory = Environment.getExternalStorageDirectory() != null ? Environment.getExternalStorageDirectory() : new File("/storage/emulated/0");
    private String title = "Select File";
    private SelectionMode selectionMode = SelectionMode.FILE_ONLY;

    public boolean isMultipleSelection() {
        return multipleSelection;
    }

    public void setMultipleSelection(boolean multipleSelection) {
        this.multipleSelection = multipleSelection;
    }

    public String[] getExtensions() {
        return extensions;
    }

    public void setExtensions(String[] extensions) {
        this.extensions = extensions;
    }

    public File getInitialDirectory() {
        return initialDirectory;
    }

    public void setInitialDirectory(File initialDirectory) {
        this.initialDirectory = initialDirectory;
    }

    public void setInitialDirectory(String path) {
        if (path != null) {
            this.initialDirectory = new File(path);
        }
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public SelectionMode getSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(SelectionMode selectionMode) {
        this.selectionMode = selectionMode;
    }
}
