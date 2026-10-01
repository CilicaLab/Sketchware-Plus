package mod.hey.studios.util;

import com.besome.sketch.tools.CompileErrorItem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompileLogParser {
    private static final Pattern ECJ_ERROR_PATTERN = Pattern.compile("([0-9]+)\\.\\s+(ERROR|WARNING)\\s+in\\s+(.+?)\\s+\\(at line\\s+([0-9]+)\\)\\s*\n([\\s\\S]*?)(?=\n----------|\n[0-9]+\\.\\s+(?:ERROR|WARNING)|$)", Pattern.MULTILINE);
    private static final Pattern XML_ERROR_PATTERN = Pattern.compile("(.+?):([0-9]+):\\s+(error|warning):\\s+(.+)", Pattern.MULTILINE);

    public static List<CompileErrorItem> parseErrors(String logs) {
        List<CompileErrorItem> items = new ArrayList<>();
        if (logs == null || logs.isEmpty() || logs.startsWith("No entries found")) {
            return items;
        }

        Matcher ecjMatcher = ECJ_ERROR_PATTERN.matcher(logs);
        while (ecjMatcher.find()) {
            String type = ecjMatcher.group(2);
            String filePath = ecjMatcher.group(3);
            int line = Integer.parseInt(ecjMatcher.group(4));
            String message = ecjMatcher.group(5).trim();

            items.add(new CompileErrorItem(filePath, line, type, message, ecjMatcher.group(0)));
        }

        Matcher xmlMatcher = XML_ERROR_PATTERN.matcher(logs);
        while (xmlMatcher.find()) {
            String filePath = xmlMatcher.group(1);
            int line = Integer.parseInt(xmlMatcher.group(2));
            String type = xmlMatcher.group(3).toUpperCase();
            String message = xmlMatcher.group(4).trim();

            items.add(new CompileErrorItem(filePath, line, type, message, xmlMatcher.group(0)));
        }

        if (items.isEmpty()) {
            String[] blocks = logs.split("----------");
            for (String block : blocks) {
                String trimmed = block.trim();
                if (!trimmed.isEmpty()) {
                    items.add(new CompileErrorItem("Build Log", 0, "ERROR", trimmed, trimmed));
                }
            }
        }

        return items;
    }
}
