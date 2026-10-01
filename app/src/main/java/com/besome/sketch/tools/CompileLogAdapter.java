package com.besome.sketch.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

import sketchware.plus.R;
import sketchware.plus.utility.SketchwareUtil;

public class CompileLogAdapter extends RecyclerView.Adapter<CompileLogAdapter.ViewHolder> {
    private final List<CompileErrorItem> items = new ArrayList<>();
    private final OnErrorClickListener listener;
    private boolean isMonospaced = true;
    private float fontSize = 12f;

    public interface OnErrorClickListener {
        void onErrorClick(CompileErrorItem item);
    }

    public CompileLogAdapter(OnErrorClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<CompileErrorItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void setMonospaced(boolean monospaced) {
        this.isMonospaced = monospaced;
        notifyDataSetChanged();
    }

    public void setFontSize(int size) {
        this.fontSize = Math.max(10, size);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.compile_error_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CompileErrorItem item = items.get(position);
        Context context = holder.itemView.getContext();

        holder.tvFileName.setText(item.filePath + (item.lineNumber > 0 ? " : Line " + item.lineNumber : ""));
        holder.tvErrorType.setText(item.errorType);
        holder.tvErrorMessage.setText(item.message);

        if (isMonospaced) {
            holder.tvErrorMessage.setTypeface(Typeface.MONOSPACE);
            holder.tvFileName.setTypeface(Typeface.MONOSPACE);
        } else {
            holder.tvErrorMessage.setTypeface(Typeface.DEFAULT);
            holder.tvFileName.setTypeface(Typeface.DEFAULT);
        }
        holder.tvErrorMessage.setTextSize(fontSize);
        holder.tvFileName.setTextSize(fontSize + 1);
        holder.tvErrorType.setTextSize(fontSize);

        int errorColor = MaterialColors.getColor(holder.itemView, R.attr.colorError);
        int warningColor = MaterialColors.getColor(holder.itemView, R.attr.colorAmber);

        if ("WARNING".equals(item.errorType)) {
            holder.tvErrorType.setTextColor(warningColor);
            holder.cardView.setStrokeColor(warningColor);
        } else {
            holder.tvErrorType.setTextColor(errorColor);
            holder.cardView.setStrokeColor(errorColor);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onErrorClick(item);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Compile Error", item.rawBlock));
                SketchwareUtil.toast("Error copied to clipboard");
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView tvFileName;
        TextView tvErrorType;
        TextView tvErrorMessage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvErrorType = itemView.findViewById(R.id.tvErrorType);
            tvErrorMessage = itemView.findViewById(R.id.tvErrorMessage);
        }
    }
}
