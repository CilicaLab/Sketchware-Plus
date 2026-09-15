package sketchware.plus.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * A utility class enabling the SK Assistant to search the web and browse URLs.
 * It processes and structures the information by removing HTML boilerplates (scripts, styles, navigation)
 * to prevent feeding unnecessary or bloated information into the AI context.
 */
public class WebSearchAide {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    /**
     * Performs a web search using a privacy-focused HTML endpoint and extracts structured results.
     * @param query The search query string.
     * @return A JSONObject containing a structured array of search results (title, snippet, url).
     */
    public static JSONObject searchWeb(String query) {
        JSONObject result = new JSONObject();
        JSONArray resultsArray = new JSONArray();

        try {
            String url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8");
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    result.put("status", "error");
                    result.put("message", "HTTP Request failed with code: " + response.code());
                    return result;
                }

                String html = response.body().string();
                
                // Regex patterns to parse DuckDuckGo HTML results safely without heavy external parser libraries
                Pattern resultPattern = Pattern.compile("<div class=\"result__body\">([\r\n\\s\\S]*?)</div\\s*>\\s*</div\\s*>");
                Pattern titleUrlPattern = Pattern.compile("<a class=\"result__url\" href=\"([^\"]+)\"[^>]*>([\r\n\\s\\S]*?)</a>");
                Pattern snippetPattern = Pattern.compile("<a class=\"result__snippet\"[^>]*>([\r\n\\s\\S]*?)</a>");

                Matcher matcher = resultPattern.matcher(html);
                int count = 0;
                while (matcher.find() && count < 8) { // Limit to top 8 clean results
                    String body = matcher.group(1);

                    Matcher titleUrlMatch = titleUrlPattern.matcher(body);
                    Matcher snippetMatch = snippetPattern.matcher(body);

                    if (titleUrlMatch.find()) {
                        String resUrl = titleUrlMatch.group(1).trim();
                        String resTitle = stripHtmlTags(titleUrlMatch.group(2)).trim();
                        String resSnippet = "";

                        if (snippetMatch.find()) {
                            resSnippet = stripHtmlTags(snippetMatch.group(1)).trim();
                        }

                        JSONObject singleResult = new JSONObject();
                        singleResult.put("title", resTitle);
                        singleResult.put("snippet", resSnippet);
                        singleResult.put("url", resUrl);
                        resultsArray.put(singleResult);
                        count++;
                    }
                }

                result.put("status", "success");
                result.put("count", resultsArray.length());
                result.put("results", resultsArray);
            }
        } catch (Exception e) {
            try {
                result.put("status", "error");
                result.put("message", e.getMessage());
            } catch (Exception ignored) {}
        }
        return result;
    }

    /**
     * Fetches a web page by URL, strips all boilerplates (scripts, styles, footers, headers),
     * and extracts a clean, structured text output suitable for an LLM context.
     * @param url The page URL to browse.
     * @return A JSONObject containing the clean structured lines, title, and word count.
     */
    public static JSONObject browseWebPage(String url) {
        JSONObject result = new JSONObject();
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    result.put("status", "error");
                    result.put("message", "Failed to fetch page, HTTP: " + response.code());
                    return result;
                }

                String rawHtml = response.body().string();
                
                // Extract Title
                String title = "";
                Matcher titleMatcher = Pattern.compile("<title>\\s*([\\s\\S]*?)\\s*</title>", Pattern.CASE_INSENSITIVE).matcher(rawHtml);
                if (titleMatcher.find()) {
                    title = stripHtmlTags(titleMatcher.group(1)).trim();
                }

                // Clean the HTML from non-content sections
                String cleanHtml = rawHtml;
                cleanHtml = Pattern.compile("<script[\\s\\S]*?</script>", Pattern.CASE_INSENSITIVE).matcher(cleanHtml).replaceAll("");
                cleanHtml = Pattern.compile("<style[\\s\\S]*?</style>", Pattern.CASE_INSENSITIVE).matcher(cleanHtml).replaceAll("");
                cleanHtml = Pattern.compile("<nav[\\s\\S]*?</nav>", Pattern.CASE_INSENSITIVE).matcher(cleanHtml).replaceAll("");
                cleanHtml = Pattern.compile("<header[\\s\\S]*?</header>", Pattern.CASE_INSENSITIVE).matcher(cleanHtml).replaceAll("");
                cleanHtml = Pattern.compile("<footer[\\s\\S]*?</footer>", Pattern.CASE_INSENSITIVE).matcher(cleanHtml).replaceAll("");
                cleanHtml = Pattern.compile("<!--[\\s\\S]*?-->").matcher(cleanHtml).replaceAll(""); // Comments

                // Parse out block texts (headings, paragraphs, list items) to build clean semantic info
                Pattern textBlockPattern = Pattern.compile("<(p|h1|h2|h3|h4|li|article)[^>]*>([\\s\\S]*?)</\\1>", Pattern.CASE_INSENSITIVE);
                Matcher textMatcher = textBlockPattern.matcher(cleanHtml);

                ArrayList<String> cleanLines = new ArrayList<>();
                int totalWords = 0;

                while (textMatcher.find()) {
                    String cleanText = stripHtmlTags(textMatcher.group(2)).trim();
                    // Remove double spacing and normalize whitespace
                    cleanText = cleanText.replaceAll("\\s+", " ");
                    
                    // Filter out short menu items, empty entries, or cookie warning boilerplate fragments
                    if (cleanText.length() > 20 && !cleanText.toLowerCase().contains("cookie") && !cleanText.toLowerCase().contains("privacy policy")) {
                        cleanLines.add(cleanText);
                        totalWords += cleanText.split("\\s+").length;
                    }
                    
                    // Cap the text limit to avoid blowing up the LLM window tokens context unexpectedly
                    if (totalWords > 1200) {
                        cleanLines.add("[... Content truncated to save context window tokens ...]");
                        break;
                    }
                }

                JSONArray linesJson = new JSONArray(cleanLines);
                result.put("status", "success");
                result.put("title", title);
                result.put("url", url);
                result.put("wordCount", totalWords);
                result.put("content", linesJson);
            }
        } catch (Exception e) {
            try {
                result.put("status", "error");
                result.put("message", e.getMessage());
            } catch (Exception ignored) {}
        }
        return result;
    }

    /**
     * Helper method to strip all raw HTML tags using regex pattern replacing.
     */
    private static String stripHtmlTags(String html) {
        if (html == null) return "";
        // Replace common entities
        String txt = html.replaceAll("&amp;", "&")
                         .replaceAll("&lt;", "<")
                         .replaceAll("&gt;", ">")
                         .replaceAll("&quot;", "\"")
                         .replaceAll("&nbsp;", " ");
        // Strip tags
        return txt.replaceAll("<[^>]*>", "");
    }
}
