package sketchware.plus.store.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import sketchware.plus.R;
import sketchware.plus.store.models.LocalItem;

public class LocalItemsAdapter extends RecyclerView.Adapter<LocalItemsAdapter.ViewHolder> {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(List<LocalItem> selectedItems);
    }

    private List<LocalItem> items;
    private OnSelectionChangedListener listener;

    public LocalItemsAdapter() {
        this.items = new ArrayList<>();
    }

    public void setItems(List<LocalItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.listener = listener;
    }

    public List<LocalItem> getSelectedItems() {
        List<LocalItem> selected = new ArrayList<>();
        if (items != null) {
            for (LocalItem item : items) {
                if (item.isSelected()) {
                    selected.add(item);
                    break;
                }
            }
        }
        return selected;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_local_selectable, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocalItem item = items.get(position);
        holder.tvLocalName.setText(item.getName());
        holder.tvLocalType.setText("Local " + (item.getType() != null ? item.getType().toLowerCase() : "item"));
        holder.cbSelect.setChecked(item.isSelected());

        View.OnClickListener clickListener = v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < items.size()) {
                for (int i = 0; i < items.size(); i++) {
                    items.get(i).setSelected(i == pos);
                }
                notifyDataSetChanged();
                if (listener != null) {
                    listener.onSelectionChanged(getSelectedItems());
                }
            }
        };

        holder.itemView.setOnClickListener(clickListener);
        holder.cbSelect.setOnClickListener(clickListener);
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbSelect;
        TextView tvLocalName;
        TextView tvLocalType;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbSelect = itemView.findViewById(R.id.cb_select);
            tvLocalName = itemView.findViewById(R.id.tv_local_name);
            tvLocalType = itemView.findViewById(R.id.tv_local_type);
        }
    }
}
