package sketchware.plus.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import sketchware.plus.R;
import sketchware.plus.databinding.ProgressMsgBoxBinding;

public class ProgressDialog {

    private final Context context;
    private final ProgressMsgBoxBinding binding;
    private boolean isCancelable = true;
    private CharSequence customTitle = null;

    private DialogInterface.OnDismissListener onDismissListener;
    private DialogInterface.OnCancelListener onCancelListener;

    private AlertDialog dialog;

    public ProgressDialog(Context context) {
        this.context = context;
        binding = ProgressMsgBoxBinding.inflate(LayoutInflater.from(context));
        binding.tvPercent.setVisibility(View.GONE);
    }

    public void setTitle(CharSequence title) {
        this.customTitle = title;
        if (dialog != null) {
            dialog.setTitle(title);
        }
    }

    public void setMessage(CharSequence message) {
        binding.tvProgress.setText(message);
    }

    public void setProgress(int progress) {
        binding.progressBar.setIndeterminate(false);
        binding.progressBar.setProgress(progress);
        binding.linearProgressBar.setIndeterminate(false);
        binding.linearProgressBar.setProgress(progress);
        binding.tvPercent.setVisibility(View.VISIBLE);
        binding.tvPercent.setText(progress + "%");
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
        this.onDismissListener = onDismissListener;
        if (dialog != null) {
            dialog.setOnDismissListener(onDismissListener);
        }
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener onCancelListener) {
        this.onCancelListener = onCancelListener;
        if (dialog != null) {
            dialog.setOnCancelListener(onCancelListener);
        }
    }

    public void show() {
        if (dialog == null) {
            MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(context);
            if (customTitle != null) {
                dialogBuilder.setTitle(customTitle);
            } else {
                dialogBuilder.setTitle(getStr(R.string.common_message_loading));
            }
            dialogBuilder.setView(binding.getRoot());
            dialog = dialogBuilder.create();
            dialog.setCancelable(isCancelable);
            dialog.setCanceledOnTouchOutside(false);

            if (onDismissListener != null) {
                dialog.setOnDismissListener(onDismissListener);
            }
            if (onCancelListener != null) {
                dialog.setOnCancelListener(onCancelListener);
            }
        }
        if (!dialog.isShowing()) {
            dialog.show();
        }
    }

    public void dismiss() {
        if (dialog == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        } else {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            });
        }
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    public boolean isCancelable() {
        return isCancelable;
    }

    public void setCancelable(boolean isCancelable) {
        this.isCancelable = isCancelable;
        if (dialog != null) {
            dialog.setCancelable(isCancelable);
        }
    }

    private String getStr(@StringRes int resId) {
        return context.getString(resId);
    }
}
