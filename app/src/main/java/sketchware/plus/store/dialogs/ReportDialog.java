package sketchware.plus.store.dialogs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import sketchware.plus.R;
import sketchware.plus.store.models.StoreReport;
import sketchware.plus.store.repository.RepositoryCallback;
import sketchware.plus.store.repository.RepositoryProvider;

public class ReportDialog {

    public interface OnReportSubmittedListener {
        void onSubmitted();
    }

    public static void show(Context context, String itemId, OnReportSubmittedListener listener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_report, null);
        AutoCompleteTextView actvReason = dialogView.findViewById(R.id.actv_reason);
        TextInputEditText etNote = dialogView.findViewById(R.id.et_note);

        String[] reasons = new String[]{"Malicious", "Spam", "Broken", "Stolen", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, reasons);
        actvReason.setAdapter(adapter);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setPositiveButton("Submit Report", null) // Set listener later to control dismiss
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String reason = actvReason.getText().toString().trim();
                String note = etNote.getText() != null ? etNote.getText().toString().trim() : "";

                if (reason.isEmpty()) {
                    Toast.makeText(context, "Please select a reason", Toast.LENGTH_SHORT).show();
                    return;
                }

                StoreReport report = new StoreReport(
                        null,
                        itemId,
                        reason,
                        note,
                        "User_" + System.currentTimeMillis() % 1000
                );

                RepositoryProvider.getRepository(context).reportItem(report, new RepositoryCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        Toast.makeText(context, "Report submitted. Thank you!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        if (listener != null) {
                            listener.onSubmitted();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(context, "Failed to submit report: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });

        dialog.show();
    }
}
