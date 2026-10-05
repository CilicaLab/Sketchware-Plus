package sketchware.plus.store.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import sketchware.plus.R;
import sketchware.plus.store.models.StoreItem;

public class MyUploadsAdapter extends RecyclerView.Adapter<MyUploadsAdapter.ViewHolder> {

    public interface OnItemActionListener {
        void onItemClick(StoreItem item);
        void onEditClick(StoreItem item);
        void onDeleteClick(StoreItem item, int position);
    }

    private final Context context;
    private List<StoreItem> items;
    private OnItemActionListener actionListener;

    public MyUploadsAdapter(Context context) {
        this.context = context;
        this.items = new ArrayList<>();
    }

    public void setItems(List<StoreItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnItemActionListener(OnItemActionListener listener) {
        this.actionListener = listener;
    }

    public void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, items.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_my_upload_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StoreItem item = items.get(position);

        holder.tvBadgeType.setText(item.getType() != null ? item.getType().toUpperCase(Locale.US) : "PACK");
        holder.tvItemName.setText(item.getName());
        holder.tvItemDesc.setText(item.getDescription());

        if (item.isApproved()) {
            holder.tvStatusBadge.setText("APPROVED");
            holder.tvStatusBadge.setTextColor(context.getColor(R.color.monokia_pro_green));
        } else {
            holder.tvStatusBadge.setText("PENDING");
            holder.tvStatusBadge.setTextColor(Color.parseColor("#FF9800"));
        }

        holder.tvDownloads.setText(NumberFormat.getInstance(Locale.US).format(item.getDownloads()) + " downloads");

        holder.itemView.setOnClickListener(v -> {
            if (actionListener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < items.size()) {
                    actionListener.onItemClick(items.get(pos));
                }
            }
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (actionListener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < items.size()) {
                    actionListener.onEditClick(items.get(pos));
                }
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (actionListener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < items.size()) {
                    actionListener.onDeleteClick(items.get(pos), pos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvBadgeType;
        TextView tvStatusBadge;
        TextView tvItemName;
        TextView tvItemDesc;
        TextView tvDownloads;
        MaterialButton btnEdit;
        MaterialButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBadgeType = itemView.findViewById(R.id.tv_badge_type);
            tvStatusBadge = itemView.findViewById(R.id.tv_status_badge);
            tvItemName = itemView.findViewById(R.id.tv_item_name);
            tvItemDesc = itemView.findViewById(R.id.tv_item_desc);
            tvDownloads = itemView.findViewById(R.id.tv_downloads);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
