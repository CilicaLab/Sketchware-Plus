package sketchware.plus.activities.importicon.adapters;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import sketchware.plus.databinding.ImportIconListItemBinding;
import sketchware.plus.utility.SvgUtils;

public class IconAdapter extends RecyclerView.Adapter<IconAdapter.ViewHolder> {

    private final SvgUtils svgUtils;
    private final OnIconSelectedListener listener;
    private String selected_icon_type;
    private int selected_color;
    private List<Pair<String, String>> items = new ArrayList<>();

    public IconAdapter(Context context, String selected_icon_type, int selected_color, OnIconSelectedListener listener) {
        svgUtils = new SvgUtils(context);
        this.selected_icon_type = selected_icon_type;
        this.selected_color = selected_color;
        this.listener = listener;
    }

    public void setSelectedIconType(String selected_icon_type) {
        this.selected_icon_type = selected_icon_type;
        notifyDataSetChanged();
    }

    public void setSelectedColor(int selected_color) {
        this.selected_color = selected_color;
        notifyDataSetChanged();
    }

    public void setItems(List<Pair<String, String>> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addItems(List<Pair<String, String>> newItems) {
        if (newItems != null && !newItems.isEmpty()) {
            int startPosition = items.size();
            items.addAll(newItems);
            notifyItemRangeInserted(startPosition, newItems.size());
        }
    }

    public Pair<String, String> getItem(int position) {
        if (position >= 0 && position < items.size()) {
            return items.get(position);
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Pair<String, String> item = getItem(position);
        if (item != null) {
            String filePath = item.second + File.separator + selected_icon_type + ".svg";
            svgUtils.loadImage(holder.itemBinding.img, filePath);
            holder.itemBinding.img.setColorFilter(selected_color, PorterDuff.Mode.SRC_IN);
            holder.itemBinding.title.setText(item.first);
        }
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImportIconListItemBinding binding = ImportIconListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    public interface OnIconSelectedListener {
        void onIconSelected(int position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ImportIconListItemBinding itemBinding;

        public ViewHolder(ImportIconListItemBinding binding) {
            super(binding.getRoot());
            itemBinding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onIconSelected(position);
                }
            });
        }
    }
}
