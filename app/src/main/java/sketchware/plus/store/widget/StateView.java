package sketchware.plus.store.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import sketchware.plus.R;

public class StateView extends FrameLayout {
    private View layoutLoading;
    private View layoutEmpty;
    private View layoutError;
    private TextView tvLoadingMsg;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private TextView tvErrorTitle;
    private TextView tvErrorMsg;
    private Button btnRetry;

    public StateView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public StateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public StateView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.layout_state_view, this, true);
        layoutLoading = findViewById(R.id.layout_loading);
        layoutEmpty = findViewById(R.id.layout_empty);
        layoutError = findViewById(R.id.layout_error);

        tvLoadingMsg = findViewById(R.id.tv_loading_msg);
        tvEmptyTitle = findViewById(R.id.tv_empty_title);
        tvEmptySubtitle = findViewById(R.id.tv_empty_subtitle);
        tvErrorTitle = findViewById(R.id.tv_error_title);
        tvErrorMsg = findViewById(R.id.tv_error_msg);
        btnRetry = findViewById(R.id.btn_retry);
    }

    public void showLoading() {
        showLoading("Loading items...");
    }

    public void showLoading(String message) {
        setVisibility(VISIBLE);
        layoutLoading.setVisibility(VISIBLE);
        layoutEmpty.setVisibility(GONE);
        layoutError.setVisibility(GONE);
        if (message != null && tvLoadingMsg != null) {
            tvLoadingMsg.setText(message);
        }
    }

    public void showEmpty(String title, String subtitle) {
        setVisibility(VISIBLE);
        layoutLoading.setVisibility(GONE);
        layoutEmpty.setVisibility(VISIBLE);
        layoutError.setVisibility(GONE);
        if (title != null && tvEmptyTitle != null) {
            tvEmptyTitle.setText(title);
        }
        if (subtitle != null && tvEmptySubtitle != null) {
            tvEmptySubtitle.setText(subtitle);
        }
    }

    public void showError(String message, OnClickListener onRetryListener) {
        setVisibility(VISIBLE);
        layoutLoading.setVisibility(GONE);
        layoutEmpty.setVisibility(GONE);
        layoutError.setVisibility(VISIBLE);
        if (message != null && tvErrorMsg != null) {
            tvErrorMsg.setText(message);
        }
        if (btnRetry != null) {
            btnRetry.setOnClickListener(onRetryListener);
        }
    }

    public void showContent() {
        setVisibility(GONE);
        layoutLoading.setVisibility(GONE);
        layoutEmpty.setVisibility(GONE);
        layoutError.setVisibility(GONE);
    }
}
