package sketchware.plus.ai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import sketchware.plus.R;
import java.util.List;

/**
 * Adapter for rendering the timeline of tool-call activity steps.
 */
public class ActivityLogAdapter extends RecyclerView.Adapter<ActivityLogAdapter.StepViewHolder> {

    private final List<ActivityStep> steps;

    public ActivityLogAdapter(List<ActivityStep> steps) {
        this.steps = steps;
    }

    @NonNull
    @Override
    public StepViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity_step, parent, false);
        return new StepViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StepViewHolder holder, int position) {
        ActivityStep step = steps.get(position);
        holder.tvLabel.setText(step.label != null ? step.label : ("Running " + step.toolName));
        holder.ivIcon.setImageResource(SkAssistantFragment.getToolIconRes(step.toolName));

        // Clean recycling: explicitly toggle mutual indicators to prevent stale views
        if (step.status == ActivityStep.Status.RUNNING) {
            holder.pbSpinner.setVisibility(View.VISIBLE);
            holder.ivCheck.setVisibility(View.GONE);
            holder.ivFailed.setVisibility(View.GONE);
        } else if (step.status == ActivityStep.Status.DONE) {
            holder.pbSpinner.setVisibility(View.GONE);
            holder.ivCheck.setVisibility(View.VISIBLE);
            holder.ivFailed.setVisibility(View.GONE);
        } else if (step.status == ActivityStep.Status.FAILED) {
            holder.pbSpinner.setVisibility(View.GONE);
            holder.ivCheck.setVisibility(View.GONE);
            holder.ivFailed.setVisibility(View.VISIBLE);
        } else {
            holder.pbSpinner.setVisibility(View.GONE);
            holder.ivCheck.setVisibility(View.GONE);
            holder.ivFailed.setVisibility(View.GONE);
        }

        holder.divider.setVisibility(position == getItemCount() - 1 ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return steps != null ? steps.size() : 0;
    }

    public static class StepViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvLabel;
        ProgressBar pbSpinner;
        ImageView ivCheck;
        ImageView ivFailed;
        View divider;

        StepViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_step_icon);
            tvLabel = itemView.findViewById(R.id.tv_step_label);
            pbSpinner = itemView.findViewById(R.id.pb_step_spinner);
            ivCheck = itemView.findViewById(R.id.iv_step_check);
            ivFailed = itemView.findViewById(R.id.iv_step_failed);
            divider = itemView.findViewById(R.id.step_divider);
        }
    }
}
