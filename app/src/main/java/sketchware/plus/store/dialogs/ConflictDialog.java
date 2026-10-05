package sketchware.plus.store.dialogs;

import android.content.Context;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ConflictDialog {

    public enum ConflictAction {
        REPLACE,
        SKIP,
        RENAME
    }

    public interface OnConflictResolvedListener {
        void onResolved(ConflictAction action);
    }

    public static void show(Context context, String itemName, OnConflictResolvedListener listener) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Item already exists")
                .setMessage("A pack or item named '" + itemName + "' already exists in your Sketchware library. How would you like to proceed?")
                .setPositiveButton("Replace", (dialog, which) -> {
                    if (listener != null) listener.onResolved(ConflictAction.REPLACE);
                })
                .setNegativeButton("Rename", (dialog, which) -> {
                    if (listener != null) listener.onResolved(ConflictAction.RENAME);
                })
                .setNeutralButton("Skip", (dialog, which) -> {
                    if (listener != null) listener.onResolved(ConflictAction.SKIP);
                })
                .setCancelable(false)
                .show();
    }
}
