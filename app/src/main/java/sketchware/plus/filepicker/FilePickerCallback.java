package sketchware.plus.filepicker;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.List;

public abstract class FilePickerCallback {
    public void onFilesSelected(@NonNull List<? extends File> files) {
        if (!files.isEmpty()) {
            onFileSelected(files.get(0));
        }
    }

    public void onFileSelected(@NonNull File file) {
        // Optional override
    }

    public boolean onFileSelectionCancelled() {
        return false;
    }
}
