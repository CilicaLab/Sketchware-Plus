package sketchware.plus.store.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import sketchware.plus.R;

public class ContentNamesAdapter extends RecyclerView.Adapter<ContentNamesAdapter.ViewHolder> {

    private List<String> names;

    public ContentNamesAdapter() {
        this.names = new ArrayList<>();
    }

    public void setNames(List<String> names) {
        this.names = names != null ? names : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_content_name, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.tvContentName.setText(names.get(position));
    }

    @Override
    public int getItemCount() {
        return names != null ? names.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvContentName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContentName = itemView.findViewById(R.id.tv_content_name);
        }
    }
}
