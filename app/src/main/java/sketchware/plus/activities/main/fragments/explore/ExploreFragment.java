package sketchware.plus.activities.main.fragments.explore;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

import sketchware.plus.R;
import sketchware.plus.activities.main.fragments.explore.views.ContributionGraphView;
import sketchware.plus.activities.main.fragments.explore.views.ProfilerUIHelper;
import sketchware.plus.utility.ActivityTracker;

import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.ViewCompositionStrategy;

public class ExploreFragment extends Fragment {

    private ContributionGraphView contributionGraph;
    private HorizontalScrollView hscrollContribution;
    private TextView tvTodayActivity;
    private TextView tvTotalActivity;
    private TextView tvCurrentStreak;
    private TextView tvBestDay;
    private ComposeView composeProfiler;
    private SwipeRefreshLayout swipeRefresh;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_explore, container, false);
        contributionGraph = view.findViewById(R.id.contributionGraph);
        hscrollContribution = view.findViewById(R.id.hscroll_contribution);
        tvTodayActivity = view.findViewById(R.id.tvTodayActivity);
        tvTotalActivity = view.findViewById(R.id.tvTotalActivity);
        tvCurrentStreak = view.findViewById(R.id.tvCurrentStreak);
        tvBestDay = view.findViewById(R.id.tvBestDay);
        composeProfiler = view.findViewById(R.id.compose_profiler);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);

        swipeRefresh.setOnRefreshListener(this::loadData);
        swipeRefresh.setColorSchemeResources(R.color.color_primary);

        composeProfiler.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE);
        ProfilerUIHelper.setContent(composeProfiler);

        contributionGraph.setOnCellClickListener((date, count) -> 
            Toast.makeText(getContext(), date + ": " + count + " contributions", Toast.LENGTH_SHORT).show()
        );

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadData();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadData();
        }
    }

    @Override
    public void onResume() {
        super.onResume() ;
        loadData();
    }

    public void refresh() {
        loadData();
    }

    private void loadData() {
        if (contributionGraph == null || hscrollContribution == null || tvTotalActivity == null) return;
        
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }

        Map<String, Integer> data = ActivityTracker.loadActivity(ActivityTracker.DEFAULT_FILE_PATH);
        contributionGraph.setActivityData(data);
        
        // Refresh Profiler UI content to pick up latest BuildStatsManager values
        ProfilerUIHelper.setContent(composeProfiler);
        
        // Auto-scroll to the end (current month)
        hscrollContribution.post(() -> hscrollContribution.fullScroll(View.FOCUS_RIGHT));

        int total = 0;
        int max = 0;
        for (int count : data.values()) {
            total += count;
            if (count > max) max = count;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String today = sdf.format(Calendar.getInstance().getTime());
        int todayCount = data.getOrDefault(today, 0);

        tvTodayActivity.setText(String.valueOf(todayCount));
        tvTotalActivity.setText(String.valueOf(total));
        tvBestDay.setText(String.valueOf(max));
        tvCurrentStreak.setText(String.valueOf(calculateStreak(data)));

        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
    }

    private int calculateStreak(Map<String, Integer> data) {
        if (data == null || data.isEmpty()) return 0;
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        int streak = 0;
        
        // Start from today
        String dateStr = sdf.format(cal.getTime());
        
        // If no activity today, check if there was activity yesterday to keep the streak "alive" for display
        // or if it just broke today.
        if (!data.containsKey(dateStr) || data.get(dateStr) == 0) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
            dateStr = sdf.format(cal.getTime());
        }

        while (data.containsKey(dateStr)) {
            Integer count = data.get(dateStr);
            if (count != null && count > 0) {
                streak++;
                cal.add(Calendar.DAY_OF_YEAR, -1);
                dateStr = sdf.format(cal.getTime());
            } else {
                break;
            }
        }
        
        return streak;
    }
}
