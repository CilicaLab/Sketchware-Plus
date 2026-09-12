package sketchware.plus.activities.main.fragments.explore.views;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

public class ContributionGraphView extends View {

    private static final int CELL_SIZE_DP = 15;
    private static final int CELL_SPACING_DP = 4;
    private static final int CORNER_RADIUS_DP = 3;
    private static final int TEXT_SIZE_DP = 10;
    private static final int DAY_LABEL_WIDTH_DP = 35;
    private static final int MONTH_LABEL_HEIGHT_DP = 25;

    private Map<String, Integer> activityData;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("MMM", Locale.US);

    private float cellSize;
    private float cellSpacing;
    private float cornerRadius;
    private float dayLabelWidth;
    private float monthLabelHeight;
    private int emptyCellColor;

    private OnCellClickListener listener;

    public interface OnCellClickListener {
        void onCellClick(String date, int count);
    }

    public ContributionGraphView(Context context) {
        super(context);
        init();
    }

    public ContributionGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        cellSize = CELL_SIZE_DP * density;
        cellSpacing = CELL_SPACING_DP * density;
        cornerRadius = CORNER_RADIUS_DP * density;
        dayLabelWidth = DAY_LABEL_WIDTH_DP * density;
        monthLabelHeight = MONTH_LABEL_HEIGHT_DP * density;

        textPaint.setTextSize(TEXT_SIZE_DP * density);
        
        // Resolve text color from theme
        TypedValue textValue = new TypedValue();
        if (getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, textValue, true)) {
            textPaint.setColor(textValue.data);
        } else {
            textPaint.setColor(0xFF888888);
        }

        // Resolve theme color for empty cells - use colorOutlineVariant for better contrast
        TypedValue typedValue = new TypedValue();
        if (getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, typedValue, true)) {
            emptyCellColor = typedValue.data;
        } else {
            // Fallback based on light/dark mode
            boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            emptyCellColor = isNight ? 0xFF333333 : 0xFFD0D0D0;
        }
    }

    public void setActivityData(Map<String, Integer> data) {
        this.activityData = data;
        invalidate();
    }

    public void setOnCellClickListener(OnCellClickListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = (int) (dayLabelWidth + (52 * (cellSize + cellSpacing)) + cellSpacing);
        int height = (int) (monthLabelHeight + (7 * (cellSize + cellSpacing)) + cellSpacing);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        Calendar calendar = getStartDate();
        
        // Draw Month Labels
        drawMonthLabels(canvas, calendar);

        // Reset calendar for grid drawing
        calendar = getStartDate();
        
        // Draw Day Labels (Mon, Wed, Fri)
        drawDayLabels(canvas);

        // Draw Cells
        for (int c = 0; c < 52; c++) {
            for (int r = 0; r < 7; r++) {
                String dateStr = dateFormat.format(calendar.getTime());
                int count = activityData != null && activityData.containsKey(dateStr) ? activityData.get(dateStr) : 0;
                
                paint.setColor(getColorForCount(count));
                
                float left = dayLabelWidth + c * (cellSize + cellSpacing);
                float top = monthLabelHeight + r * (cellSize + cellSpacing);
                rect.set(left, top, left + cellSize, top + cellSize);
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
                
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
    }

    private void drawMonthLabels(Canvas canvas, Calendar cal) {
        int lastMonth = -1;
        for (int c = 0; c < 52; c++) {
            int currentMonth = cal.get(Calendar.MONTH);
            if (currentMonth != lastMonth) {
                String monthName = monthFormat.format(cal.getTime());
                float x = dayLabelWidth + c * (cellSize + cellSpacing);
                canvas.drawText(monthName, x, monthLabelHeight - 5, textPaint);
                lastMonth = currentMonth;
            }
            cal.add(Calendar.WEEK_OF_YEAR, 1);
        }
    }

    private void drawDayLabels(Canvas canvas) {
        String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int r = 0; r < 7; r++) {
            float y = monthLabelHeight + r * (cellSize + cellSpacing) + cellSize / 2 + (textPaint.getTextSize() / 3);
            canvas.drawText(days[r], 5, y, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && listener != null) {
            float x = event.getX();
            float y = event.getY();
            
            if (x >= dayLabelWidth && y >= monthLabelHeight) {
                int c = (int) ((x - dayLabelWidth) / (cellSize + cellSpacing));
                int r = (int) ((y - monthLabelHeight) / (cellSize + cellSpacing));
                
                if (c >= 0 && c < 52 && r >= 0 && r < 7) {
                    Calendar cal = getStartDate();
                    cal.add(Calendar.DAY_OF_YEAR, c * 7 + r);
                    String dateStr = dateFormat.format(cal.getTime());
                    int count = activityData != null && activityData.containsKey(dateStr) ? activityData.get(dateStr) : 0;
                    listener.onCellClick(dateStr, count);
                    return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    private Calendar getStartDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.WEEK_OF_YEAR, -51);
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        return calendar;
    }

    private int getColorForCount(int count) {
        if (count == 0) return emptyCellColor;
        if (count == 1) return 0xFFD6F5D6; // Level 1: Extremely light green
        if (count == 11) return 0xFFC6E48B; // Level 2: Very light green
        if (count < 19) return 0xFF9BE9A8;  // Level 3: Light green
        if (count < 31) return 0xFF7BC96F;  // Level 4: Medium-light green
        if (count < 55) return 0xFF40C463; // Level 5: Medium green
        if (count < 87) return 0xFF30A14E; // Level 6: Dark green
        return 0xFF216E39;                // Level 7: Very dark green
    }
}
