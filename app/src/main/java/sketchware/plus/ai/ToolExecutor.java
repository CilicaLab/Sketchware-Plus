package sketchware.plus.ai;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Optimized tool execution with parallel read operations, result caching, and adaptive rate limiting.
 * 
 * Strategy:
 * - Read-only tools (list_*, get_*, search_*, read_*, web_*) execute in parallel
 * - Write tools (add_*, apply_*, inject_*, etc.) execute sequentially to prevent conflicts
 * - Results are cached for 30 seconds to avoid redundant API calls
 * - Rate limiting is adaptive: fast tools wait less, external APIs wait more
 */
public class ToolExecutor {
    
    private final ExecutorService parallelExecutor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ToolOrchestrator orchestrator;
    
    private static final int BASE_DELAY_MS = 200;
    private static final int API_DELAY_MS = 1000;
    private static final int TIMEOUT_SECONDS = 30;
    
    // Cache for read-only tool results
    private final Map<String, CachedResult> resultCache = new HashMap<>();
    private static final long CACHE_TTL_MS = 30000;  // 30 seconds
    
    private int totalCacheHits = 0;
    private int totalToolCalls = 0;
    
    public ToolExecutor(ToolOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }
    
    /**
     * Execute a batch of tool calls with optimal parallelization and caching.
     * Read tools execute in parallel, write tools execute sequentially.
     */
    public void executeBatch(JSONArray toolCalls, Callback callback) {
        // Categorize tools by type
        List<ToolCallInfo> readTools = new ArrayList<>();
        List<ToolCallInfo> writeTools = new ArrayList<>();
        
        for (int i = 0; i < toolCalls.length(); i++) {
            try {
                JSONObject call = toolCalls.getJSONObject(i);
                ToolCallInfo info = parseToolCall(call);
                
                if (isReadOnlyTool(info.toolName)) {
                    readTools.add(info);
                } else {
                    writeTools.add(info);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // Execute in batches: parallel reads first, then sequential writes
        List<ToolResult> results = new ArrayList<>();
        
        if (!readTools.isEmpty()) {
            executeReadToolsParallel(readTools, results);
        }
        
        if (!writeTools.isEmpty()) {
            executeWriteToolsSequential(writeTools, results);
        }
        
        callback.onComplete(results);
    }
    
    /**
     * Execute read-only tools in parallel for maximum throughput.
     */
    private void executeReadToolsParallel(List<ToolCallInfo> toolCalls, List<ToolResult> results) {
        List<Future<ToolResult>> futures = new ArrayList<>();
        
        for (ToolCallInfo info : toolCalls) {
            futures.add(parallelExecutor.submit(new Callable<ToolResult>() {
                @Override
                public ToolResult call() throws Exception {
                    return executeToolInternal(info);
                }
            }));
        }
        
        // Wait for all to complete with timeout
        for (int i = 0; i < futures.size(); i++) {
            try {
                Future<ToolResult> future = futures.get(i);
                ToolResult result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                results.add(result);
            } catch (Exception e) {
                ToolResult errorResult = new ToolResult(
                    "ERROR",
                    "Timeout or execution error: " + e.getMessage(),
                    "",
                    TIMEOUT_SECONDS * 1000,
                    false,
                    true
                );
                results.add(errorResult);
            }
        }
    }
    
    /**
     * Execute write tools sequentially to prevent race conditions and data corruption.
     */
    private void executeWriteToolsSequential(List<ToolCallInfo> toolCalls, List<ToolResult> results) {
        for (int i = 0; i < toolCalls.size(); i++) {
            try {
                ToolCallInfo info = toolCalls.get(i);
                ToolResult result = executeToolInternal(info);
                results.add(result);
                
                // Adaptive delay between write operations
                if (i < toolCalls.size() - 1) {
                    long delay = calculateAdaptiveDelay(result.toolName, result.executionTimeMs);
                    if (delay > 0) {
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            } catch (Exception e) {
                ToolResult errorResult = new ToolResult(
                    toolCalls.get(i).toolName,
                    "Execution error: " + e.getMessage(),
                    toolCalls.get(i).id,
                    0,
                    false,
                    true
                );
                results.add(errorResult);
            }
        }
    }
    
    /**
     * Internal execution with caching for read-only tools.
     */
    private ToolResult executeToolInternal(ToolCallInfo info) throws Exception {
        long startTime = System.currentTimeMillis();
        
        // Check cache for read-only tools
        if (isReadOnlyTool(info.toolName)) {
            String cacheKey = generateCacheKey(info.toolName, info.args);
            CachedResult cached = resultCache.get(cacheKey);
            
            if (cached != null && !cached.isExpired()) {
                long executionTime = System.currentTimeMillis() - startTime;
                totalCacheHits++;
                return new ToolResult(
                    info.toolName,
                    cached.content,
                    info.id,
                    executionTime,
                    true,  // fromCache
                    false
                );
            }
        }
        
        // Execute the actual tool through orchestrator
        String result = orchestrator.dispatchTool(info.toolName, info.args);
        
        long executionTime = System.currentTimeMillis() - startTime;
        totalToolCalls++;
        
        // Cache read-only results
        if (isReadOnlyTool(info.toolName)) {
            String cacheKey = generateCacheKey(info.toolName, info.args);
            resultCache.put(cacheKey, new CachedResult(result, System.currentTimeMillis()));
        }
        
        return new ToolResult(
            info.toolName,
            result,
            info.id,
            executionTime,
            false,  // fromCache
            false
        );
    }
    
    /**
     * Calculate adaptive delay based on tool type and execution time.
     * - External API tools: longer delay (rate limiting)
     * - Fast tools: minimal delay
     * - Slow tools: minimal delay (already took time)
     */
    private long calculateAdaptiveDelay(String toolName, long executionTimeMs) {
        if (toolName.contains("web_")) {
            // External APIs need longer delay for rate limiting
            return API_DELAY_MS;
        }
        
        if (executionTimeMs > 1000) {
            // Already took significant time, minimal pause
            return BASE_DELAY_MS;
        }
        
        // Fast tools: compensate to maintain throughput
        // Formula: BASE + (500ms - half of execution time)
        return BASE_DELAY_MS + Math.max(0, (500 - executionTimeMs / 2));
    }
    
    /**
     * Determine if a tool is read-only (doesn't modify project state).
     */
    private boolean isReadOnlyTool(String toolName) {
        return toolName.matches(
            "(list_.*|get_.*|read_.*|search_.*|web_.*)"
        );
    }
    
    /**
     * Parse a tool call JSON object into structured data.
     */
    private ToolCallInfo parseToolCall(JSONObject call) throws JSONException {
        String id = call.getString("id");
        JSONObject function = call.getJSONObject("function");
        String name = function.getString("name");
        
        Object argsRaw = function.get("arguments");
        JSONObject args;
        
        if (argsRaw instanceof String) {
            String argsStr = (String) argsRaw;
            String trimmed = argsStr.trim();
            
            // Robust repair for common model glitches
            if (trimmed.equals("{\"\"}") || trimmed.equals("{ \"\" }") || 
                trimmed.equals("{}") || trimmed.isEmpty()) {
                argsStr = "{}";
            }
            
            try {
                args = new JSONObject(argsStr);
            } catch (JSONException e) {
                args = new JSONObject();
            }
        } else if (argsRaw instanceof JSONObject) {
            args = (JSONObject) argsRaw;
        } else {
            args = new JSONObject();
        }
        
        return new ToolCallInfo(id, name, args);
    }
    
    /**
     * Generate a cache key from tool name and arguments.
     */
    private String generateCacheKey(String toolName, JSONObject args) {
        return toolName + ":" + args.toString();
    }
    
    /**
     * Clear the result cache.
     */
    public void clearCache() {
        resultCache.clear();
        totalCacheHits = 0;
        totalToolCalls = 0;
    }
    
    /**
     * Get cache statistics.
     */
    public CacheStats getCacheStats() {
        return new CacheStats(totalToolCalls, totalCacheHits, resultCache.size());
    }
    
    /**
     * Shutdown executor service.
     */
    public void shutdown() {
        parallelExecutor.shutdown();
        try {
            if (!parallelExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                parallelExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            parallelExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // ========== INNER CLASSES ==========
    
    /**
     * Represents the result of a single tool execution.
     */
    public static class ToolResult {
        public String toolName;
        public String content;
        public String toolCallId;
        public long executionTimeMs;
        public boolean fromCache;
        public boolean isError;
        
        public ToolResult(String toolName, String content, String id, 
                         long executionTimeMs, boolean fromCache, boolean isError) {
            this.toolName = toolName;
            this.content = content;
            this.toolCallId = id;
            this.executionTimeMs = executionTimeMs;
            this.fromCache = fromCache;
            this.isError = isError;
        }
    }
    
    /**
     * Represents a cached tool result with expiration time.
     */
    private static class CachedResult {
        String content;
        long timestamp;
        
        CachedResult(String content, long timestamp) {
            this.content = content;
            this.timestamp = timestamp;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
    
    /**
     * Represents parsed information from a tool call JSON.
     */
    private static class ToolCallInfo {
        String id;
        String toolName;
        JSONObject args;
        
        ToolCallInfo(String id, String toolName, JSONObject args) {
            this.id = id;
            this.toolName = toolName;
            this.args = args;
        }
    }
    
    /**
     * Cache statistics.
     */
    public static class CacheStats {
        public int totalCalls;
        public int cacheHits;
        public int cacheSize;
        public double hitRate;
        
        public CacheStats(int total, int hits, int size) {
            this.totalCalls = total;
            this.cacheHits = hits;
            this.cacheSize = size;
            this.hitRate = total > 0 ? (double) hits / total : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Cache Stats: %d calls, %d hits (%.1f%% hit rate), %d cached results",
                totalCalls, cacheHits, hitRate * 100, cacheSize
            );
        }
    }
    
    /**
     * Callback for async tool batch execution.
     */
    public interface Callback {
        void onComplete(List<ToolResult> results);
    }
}

