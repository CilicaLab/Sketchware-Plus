package <?package_name?>;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

public class DebugActivity extends Activity {

    private static final Map<String, String> exceptionMap = new HashMap<String, String>() {{
        put("StringIndexOutOfBoundsException", "Invalid string operation");
        put("IndexOutOfBoundsException", "Invalid list operation");
        put("ArithmeticException", "Invalid arithmetical operation");
        put("NumberFormatException", "Invalid toNumber block operation");
        put("ActivityNotFoundException", "Invalid intent operation");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        final String errorMessage = intent != null ? intent.getStringExtra("error") : "No error message available.";

        // Root layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF8F9FA); 
        root.setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(24));

        // Header Title
        TextView header = new TextView(this);
        header.setText("Application Crashed");
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        header.setTextColor(0xFF1A1C1E);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        header.setGravity(Gravity.START);
        root.addView(header);

        // Friendly Note
        TextView subheader = new TextView(this);
        subheader.setText("An unexpected error occurred in your project. You can review the technical details below.");
        subheader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subheader.setTextColor(0xFF44474E);
        subheader.setPadding(0, dpToPx(8), 0, dpToPx(24));
        root.addView(subheader);

        // Error Container (Card-like)
        LinearLayout errorContainer = new LinearLayout(this);
        errorContainer.setOrientation(LinearLayout.VERTICAL);
        errorContainer.setBackgroundColor(Color.WHITE);
        
        // Dynamic border/background for "card"
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.WHITE);
        gd.setCornerRadius(dpToPx(12));
        gd.setStroke(dpToPx(1), 0xFFDDE2EA);
        errorContainer.setBackground(gd);
        errorContainer.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        ScrollView vscroll = new ScrollView(this);
        vscroll.setFillViewport(true);
        
        HorizontalScrollView hscroll = new HorizontalScrollView(this);
        
        TextView errorView = new TextView(this);
        errorView.setText(getFormattedError(errorMessage));
        errorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        errorView.setTypeface(Typeface.MONOSPACE);
        errorView.setTextIsSelectable(true);
        errorView.setTextColor(0xFF1A1C1E);
        
        hscroll.addView(errorView);
        vscroll.addView(hscroll);

        errorContainer.addView(vscroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        
        root.addView(errorContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        // Action Buttons
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dpToPx(24), 0, 0);

        Button btnCopy = new Button(this, null, android.R.attr.borderlessButtonStyle);
        btnCopy.setText("COPY");
        btnCopy.setTextColor(0xFF0061A4);
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Error Log", errorMessage);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(DebugActivity.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
        actions.addView(btnCopy);

        Button btnShare = new Button(this, null, android.R.attr.borderlessButtonStyle);
        btnShare.setText("SHARE");
        btnShare.setTextColor(0xFF0061A4);
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, errorMessage);
                startActivity(Intent.createChooser(i, "Share Error Log"));
            }
        });
        actions.addView(btnShare);

        Button btnRestart = new Button(this);
        btnRestart.setText("RESTART");
        
        GradientDrawable bgBtn = new GradientDrawable();
        bgBtn.setColor(0xFF0061A4);
        bgBtn.setCornerRadius(dpToPx(20));
        btnRestart.setBackground(bgBtn);
        btnRestart.setTextColor(Color.WHITE);
        btnRestart.setPadding(dpToPx(24), 0, dpToPx(24), 0);
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(40));
        btnParams.setMargins(dpToPx(8), 0, 0, 0);
        
        btnRestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
                finish();
            }
        });
        actions.addView(btnRestart, btnParams);

        root.addView(actions);

        // Device Info Footer
        TextView deviceInfo = new TextView(this);
        deviceInfo.setText("Device: " + Build.MODEL + " (" + Build.MANUFACTURER + ")\n" +
                "Android Version: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        deviceInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        deviceInfo.setTextColor(0xFF757575);
        deviceInfo.setGravity(Gravity.CENTER);
        deviceInfo.setPadding(0, dpToPx(16), 0, 0);
        root.addView(deviceInfo);

        setContentView(root);
    }

    private SpannableStringBuilder getFormattedError(String error) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String[] lines = error.split("\n");
        
        if (lines.length > 0) {
            String firstLine = lines[0];
            String type = firstLine.contains(":") ? firstLine.substring(0, firstLine.indexOf(":")) : firstLine;
            String shortName = type.contains(".") ? type.substring(type.lastIndexOf(".") + 1) : type;
            
            String friendlyMsg = exceptionMap.get(shortName);
            if (friendlyMsg != null) {
                int start = builder.length();
                builder.append(friendlyMsg).append("\n\n");
                builder.setSpan(new ForegroundColorSpan(0xFFD32F2F), start, builder.length(), 0);
                builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), 0);
            }
            
            for (String line : lines) {
                int start = builder.length();
                builder.append(line).append("\n");
                
                // Highlight project package calls (where the actual user error usually is)
                if (line.contains(getPackageName())) {
                    builder.setSpan(new ForegroundColorSpan(0xFF0061A4), start, builder.length(), 0);
                    builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), 0);
                }
            }
        }
        
        return builder;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
