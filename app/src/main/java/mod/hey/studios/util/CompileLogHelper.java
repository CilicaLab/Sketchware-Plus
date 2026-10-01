package mod.hey.studios.util;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import com.google.android.material.color.MaterialColors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sketchware.plus.R;

public class CompileLogHelper {
    private static final String TAG = "CompileLogHelper";
    private static final Pattern ERROR_PATTERN = Pattern.compile("----------\n([0-9]+\\. ERROR)", Pattern.MULTILINE);
    private static final Pattern WARNING_PATTERN = Pattern.compile("----------\n([0-9]+\\. WARNING)", Pattern.MULTILINE);
    private static final Pattern XML_PATTERN = Pattern.compile("error:", Pattern.MULTILINE);

    public static SpannableString getColoredLogs(Context context, String logs) {
        if (logs == null) {
            logs = "";
        }
        int errorColor = MaterialColors.getColor(context, R.attr.colorError, TAG);
        int warningColor = MaterialColors.getColor(context, R.attr.colorAmber, TAG);

        SpannableString spannable = new SpannableString(logs);

        Matcher errorMatcher = ERROR_PATTERN.matcher(logs);
        applyStyle(spannable, errorMatcher, errorColor);

        Matcher warningMatcher = WARNING_PATTERN.matcher(logs);
        applyStyle(spannable, warningMatcher, warningColor);

        Matcher xmlMatcher = XML_PATTERN.matcher(logs);
        applyStyleForXml(spannable, xmlMatcher, errorColor);

        return spannable;
    }

    private static void applyStyle(SpannableString spannable, Matcher matcher, int color) {
        while (matcher.find()) {
            spannable.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(1), matcher.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new ForegroundColorSpan(color), matcher.start(1), matcher.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void applyStyleForXml(SpannableString spannable, Matcher matcher, int color) {
        while (matcher.find()) {
            spannable.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static String filterLogs(String logs, int category) {
        if (logs == null || logs.isEmpty()) return "";
        if (category == 0) return logs; // All

        String[] blocks = logs.split("----------");
        StringBuilder filtered = new StringBuilder();

        for (String block : blocks) {
            if (block.trim().isEmpty()) continue;
            String lower = block.toLowerCase();
            boolean match = false;

            boolean isXml = lower.contains(".xml") || lower.contains("aapt") || lower.contains("layout") || lower.contains("manifest") || lower.contains("resource") || lower.contains("res/");
            boolean isJava = lower.contains(".java") || lower.contains(".kt") || lower.contains("ecj") || lower.contains("javac") || lower.contains("kotlinc");
            boolean isDex = lower.contains("dex") || lower.contains("r8") || lower.contains("dx") || lower.contains("proguard") || lower.contains("multidex") || lower.contains("translation");

            if (category == 1) { // Java / Kotlin
                match = isJava && !isXml;
            } else if (category == 2) { // XML / Layout
                match = isXml;
            } else if (category == 3) { // Dex / R8
                match = isDex && !isJava && !isXml;
            }

            if (match) {
                filtered.append("----------").append(block);
            }
        }

        if (filtered.length() == 0) {
            return filterLogsByLine(logs, category);
        }
        return filtered.toString();
    }

    private static String filterLogsByLine(String logs, int category) {
        StringBuilder filtered = new StringBuilder();
        String[] lines = logs.split("\n");
        for (String line : lines) {
            String lower = line.toLowerCase();
            boolean match = false;
            boolean isXml = lower.contains(".xml") || lower.contains("aapt") || lower.contains("layout") || lower.contains("manifest") || lower.contains("res/");
            boolean isJava = lower.contains(".java") || lower.contains(".kt") || lower.contains("javac");
            boolean isDex = lower.contains("dex") || lower.contains("r8") || lower.contains("dx") || lower.contains("proguard");

            if (category == 1) {
                match = isJava && !isXml;
            } else if (category == 2) {
                match = isXml;
            } else if (category == 3) {
                match = isDex && !isJava && !isXml;
            }
            if (match) {
                filtered.append(line).append("\n");
            }
        }
        if (filtered.length() == 0) {
            return "No entries found for this category.";
        }
        return filtered.toString();
    }
}
