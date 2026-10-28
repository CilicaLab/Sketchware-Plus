package sketchware.plus.filepicker;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import sketchware.plus.R;

public class FilePickerAdapter extends RecyclerView.Adapter<FilePickerAdapter.ViewHolder> {

    private final List<File> items = new ArrayList<>();
    private final Set<File> selectedFiles = new HashSet<>();
    private final boolean multipleSelection;
    private final SelectionMode selectionMode;
    private final OnItemClickListener listener;
    private OnSelectionChangedListener selectionChangedListener;

    public interface OnItemClickListener {
        void onItemClick(File file);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int count);
    }

    public FilePickerAdapter(boolean multipleSelection, SelectionMode selectionMode, OnItemClickListener listener) {
        this.multipleSelection = multipleSelection;
        this.selectionMode = selectionMode != null ? selectionMode : SelectionMode.BOTH;
        this.listener = listener;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    public void setItems(List<File> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public Set<File> getSelectedFiles() {
        return selectedFiles;
    }

    public void toggleSelection(File file) {
        if (file.isDirectory()) return; // Folders are navigational only

        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
        } else {
            if (!multipleSelection) {
                selectedFiles.clear();
            }
            selectedFiles.add(file);
        }
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedFiles.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.manage_file_picker_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = items.get(position);
        holder.tvFileName.setText(file.getName());

        String details;
        if (file.isDirectory()) {
            File[] subFiles = file.listFiles();
            int count = subFiles != null ? subFiles.length : 0;
            details = count + " items • " + formatDate(file.lastModified());
        } else {
            details = formatFileSize(file.length()) + " • " + formatDate(file.lastModified());
        }
        holder.tvFileDetails.setText(details);

        int count = getItemCount();
        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
        float dp16 = 16f * density;
        float dp4 = 4f * density;

        float[] radii;
        if (count == 1) {
            radii = new float[]{dp16, dp16, dp16, dp16, dp16, dp16, dp16, dp16};
        } else if (position == 0) {
            radii = new float[]{dp16, dp16, dp16, dp16, dp4, dp4, dp4, dp4};
        } else if (position == count - 1) {
            radii = new float[]{dp4, dp4, dp4, dp4, dp16, dp16, dp16, dp16};
        } else {
            radii = new float[]{dp4, dp4, dp4, dp4, dp4, dp4, dp4, dp4};
        }

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadii(radii);

        int backgroundColor = MaterialColors.getColor(
                holder.itemView.getContext(),
                com.google.android.material.R.attr.colorSurfaceContainer,
                Color.GRAY
        );
        drawable.setColor(backgroundColor);
        holder.itemContainer.setBackground(drawable);

        if (file.isDirectory()) {
            holder.imgIcon.setImageResource(R.drawable.ic_mtrl_folder);
            holder.checkboxSelected.setVisibility(View.GONE);
        } else {
            holder.imgIcon.setImageResource(R.drawable.ic_mtrl_file);
            holder.checkboxSelected.setVisibility(View.VISIBLE);
            holder.checkboxSelected.setChecked(selectedFiles.contains(file));
        }

        holder.itemView.setOnClickListener(v -> {
            if (file.isDirectory()) {
                listener.onItemClick(file);
            } else {
                toggleSelection(file);
            }
        });
    }

    private static String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private static String formatDate(long timeMs) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(timeMs));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout itemContainer;
        TextView tvFileName;
        TextView tvFileDetails;
        ImageView imgIcon;
        MaterialCheckBox checkboxSelected;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemContainer = itemView.findViewById(R.id.item_container);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileDetails = itemView.findViewById(R.id.tv_file_details);
            imgIcon = itemView.findViewById(R.id.img_icon);
            checkboxSelected = itemView.findViewById(R.id.checkbox_selected);
        }
    }
}
