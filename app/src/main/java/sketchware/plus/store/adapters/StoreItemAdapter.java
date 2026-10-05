package sketchware.plus.store.adapters;

import android.content.Context;
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

public class StoreItemAdapter extends RecyclerView.Adapter<StoreItemAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(StoreItem item);
    }

    public interface OnInstallClickListener {
        void onInstallClick(StoreItem item, int position);
    }

    private final Context context;
    private List<StoreItem> items;
    private OnItemClickListener itemClickListener;
    private OnInstallClickListener installClickListener;

    public StoreItemAdapter(Context context) {
        this.context = context;
        this.items = new ArrayList<>();
    }

    public void setItems(List<StoreItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setOnInstallClickListener(OnInstallClickListener listener) {
        this.installClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_store_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StoreItem item = items.get(position);

        holder.tvBadgeType.setText(item.getType() != null ? item.getType().toUpperCase(Locale.US) : "PACK");
        holder.tvItemName.setText(item.getName());
        holder.tvItemAuthor.setText("by " + (item.getAuthor() != null ? item.getAuthor() : "Anonymous"));
        holder.tvItemDesc.setText(item.getDescription());

        // Downloads
        String downloadsText = NumberFormat.getInstance(Locale.US).format(item.getDownloads()) + " downloads";
        holder.tvItemDownloads.setText(downloadsText);

        // Tags
        if (item.getTags() != null && !item.getTags().isEmpty()) {
            StringBuilder tagsSb = new StringBuilder();
            for (String tag : item.getTags()) {
                tagsSb.append("#").append(tag).append("  ");
            }
            holder.tvItemTags.setText(tagsSb.toString().trim());
            holder.tvItemTags.setVisibility(View.VISIBLE);
        } else {
            holder.tvItemTags.setVisibility(View.GONE);
        }

        // Card Click
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(item);
            }
        });

        // Install Button Click
        holder.btnItemInstall.setOnClickListener(v -> {
            if (installClickListener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    installClickListener.onInstallClick(item, pos);
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
        TextView tvItemName;
        TextView tvItemAuthor;
        TextView tvItemDesc;
        TextView tvItemTags;
        TextView tvItemDownloads;
        MaterialButton btnItemInstall;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBadgeType = itemView.findViewById(R.id.tv_badge_type);
            tvItemName = itemView.findViewById(R.id.tv_item_name);
            tvItemAuthor = itemView.findViewById(R.id.tv_item_author);
            tvItemDesc = itemView.findViewById(R.id.tv_item_desc);
            tvItemTags = itemView.findViewById(R.id.tv_item_tags);
            tvItemDownloads = itemView.findViewById(R.id.tv_item_downloads);
            btnItemInstall = itemView.findViewById(R.id.btn_item_install);
        }
    }
}
