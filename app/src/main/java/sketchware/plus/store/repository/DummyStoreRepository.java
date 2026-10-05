package sketchware.plus.store.repository;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import sketchware.plus.store.models.StoreItem;
import sketchware.plus.store.models.StoreReport;
import sketchware.plus.store.models.StoreUser;

public class DummyStoreRepository implements StoreRepository {
    private static DummyStoreRepository instance;
    private final List<StoreItem> dummyItems;
    private final List<StoreReport> dummyReports;
    private StoreUser currentUser;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private DummyStoreRepository() {
        dummyItems = new ArrayList<>();
        dummyReports = new ArrayList<>();
        currentUser = new StoreUser("usr_001", "SketchwareDev", true); // Set as admin by default for demo
        initDummyData();
    }

    public static synchronized DummyStoreRepository getInstance() {
        if (instance == null) {
            instance = new DummyStoreRepository();
        }
        return instance;
    }

    private void initDummyData() {
        // Block Packs
        dummyItems.add(new StoreItem(
                "blk_001",
                "Advanced Math & Physics Logic",
                "MathWizard",
                "A full collection of custom math operations including Vector calculations, Trigonometry helpers, and Physics physics formulas.",
                StoreItem.TYPE_BLOCK,
                Arrays.asList("math", "physics", "logic", "utils"),
                1240,
                System.currentTimeMillis() - 86400000L * 10,
                true,
                "{}",
                Arrays.asList("Vector2D_Add", "Vector2D_Distance", "SinDeg", "CosDeg", "GravityForce")
        ));

        dummyItems.add(new StoreItem(
                "blk_002",
                "JSON & String Utility Pack",
                "CodeMaster",
                "Helper blocks for parsing JSON arrays, formatting dates, regex matching, and string encryption/decryption.",
                StoreItem.TYPE_BLOCK,
                Arrays.asList("json", "string", "helpers"),
                890,
                System.currentTimeMillis() - 86400000L * 5,
                true,
                "{}",
                Arrays.asList("ParseJsonArray", "FormatDate", "RegexReplace", "EncryptAES")
        ));

        dummyItems.add(new StoreItem(
                "blk_003",
                "Custom Animation & Motion Blocks",
                "UIExpert",
                "Smooth elevation, spring physics animations, and view transition blocks for custom views.",
                StoreItem.TYPE_BLOCK,
                Arrays.asList("ui", "animation", "motion"),
                2100,
                System.currentTimeMillis() - 86400000L * 15,
                true,
                "{}",
                Arrays.asList("SpringAnimateView", "FadeInView", "RotateView", "ElevationShadow")
        ));

        dummyItems.add(new StoreItem(
                "blk_004",
                "Pending Community Block Pack",
                "NewbieDev",
                "A newly submitted set of experimental custom blocks waiting for approval.",
                StoreItem.TYPE_BLOCK,
                Arrays.asList("experimental", "utility"),
                12,
                System.currentTimeMillis() - 3600000L * 2,
                false,
                "{}",
                Arrays.asList("TestBlock1", "TestBlock2")
        ));

        // Component Packs
        dummyItems.add(new StoreItem(
                "cmp_001",
                "Enhanced RequestNetwork + Cache",
                "NetworkNinja",
                "Upgraded RequestNetwork component with built-in response disk caching, header management, and file upload progress listener.",
                StoreItem.TYPE_COMPONENT,
                Arrays.asList("network", "http", "cache"),
                3400,
                System.currentTimeMillis() - 86400000L * 20,
                true,
                "{}",
                Arrays.asList("RequestNetworkPlus", "HttpCacheManager")
        ));

        dummyItems.add(new StoreItem(
                "cmp_002",
                "AudioFX & Custom SoundPool",
                "AudioLab",
                "Audio playback component supporting multiple simultaneous streams, speed pitch modulation, and background loopers.",
                StoreItem.TYPE_COMPONENT,
                Arrays.asList("audio", "sound", "media"),
                1560,
                System.currentTimeMillis() - 86400000L * 8,
                true,
                "{}",
                Arrays.asList("CustomSoundPool", "AudioFXEngine")
        ));

        dummyItems.add(new StoreItem(
                "cmp_003",
                "Supabase DB & Auth Component",
                "BackendGuy",
                "Direct client component for connecting Sketchware apps to Supabase Database and Authentication.",
                StoreItem.TYPE_COMPONENT,
                Arrays.asList("supabase", "database", "auth"),
                4200,
                System.currentTimeMillis() - 86400000L * 2,
                true,
                "{}",
                Arrays.asList("SupabaseClient", "SupabaseAuth")
        ));

        // Dummy Reports
        dummyReports.add(new StoreReport(
                "rpt_001",
                "blk_004",
                "Spam",
                "Duplicate block set uploaded multiple times.",
                "User123"
        ));
    }

    @Override
    public void getItems(String type, String query, String sortBy, RepositoryCallback<List<StoreItem>> callback) {
        handler.postDelayed(() -> {
            List<StoreItem> result = new ArrayList<>();
            String lowerQuery = query != null ? query.toLowerCase().trim() : "";

            for (StoreItem item : dummyItems) {
                if (!item.isApproved()) continue; // Only approved items in public list
                if (type != null && !"ALL".equalsIgnoreCase(type) && !type.equalsIgnoreCase(item.getType())) continue;

                if (!lowerQuery.isEmpty()) {
                    boolean matchesName = item.getName() != null && item.getName().toLowerCase().contains(lowerQuery);
                    boolean matchesDesc = item.getDescription() != null && item.getDescription().toLowerCase().contains(lowerQuery);
                    boolean matchesAuthor = item.getAuthor() != null && item.getAuthor().toLowerCase().contains(lowerQuery);
                    boolean matchesTags = false;
                    if (item.getTags() != null) {
                        for (String tag : item.getTags()) {
                            if (tag.toLowerCase().contains(lowerQuery)) {
                                matchesTags = true;
                                break;
                            }
                        }
                    }
                    if (!matchesName && !matchesDesc && !matchesAuthor && !matchesTags) {
                        continue;
                    }
                }

                result.add(item);
            }

            // Sort
            if ("most_downloaded".equalsIgnoreCase(sortBy)) {
                Collections.sort(result, (a, b) -> Integer.compare(b.getDownloads(), a.getDownloads()));
            } else { // default "newest"
                Collections.sort(result, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
            }

            callback.onSuccess(result);
        }, 400); // Simulate network delay
    }

    @Override
    public void getMyItems(String authorId, RepositoryCallback<List<StoreItem>> callback) {
        handler.postDelayed(() -> {
            List<StoreItem> result = new ArrayList<>();
            for (StoreItem item : dummyItems) {
                result.add(item);
            }
            callback.onSuccess(result);
        }, 300);
    }

    @Override
    public void getItemDetails(String id, RepositoryCallback<StoreItem> callback) {
        handler.postDelayed(() -> {
            for (StoreItem item : dummyItems) {
                if (item.getId().equals(id)) {
                    callback.onSuccess(item);
                    return;
                }
            }
            callback.onError("Item not found");
        }, 300);
    }

    @Override
    public void incrementDownloads(String itemId) {
        for (StoreItem item : dummyItems) {
            if (item.getId().equals(itemId)) {
                item.setDownloads(item.getDownloads() + 1);
                break;
            }
        }
    }

    @Override
    public void uploadItem(StoreItem item, RepositoryCallback<Boolean> callback) {
        handler.postDelayed(() -> {
            item.setId("item_" + System.currentTimeMillis());
            item.setApproved(false); // Requires admin approval
            item.setCreatedAt(System.currentTimeMillis());
            dummyItems.add(0, item);
            callback.onSuccess(true);
        }, 600);
    }

    @Override
    public void reportItem(StoreReport report, RepositoryCallback<Boolean> callback) {
        handler.postDelayed(() -> {
            report.setId("rpt_" + System.currentTimeMillis());
            dummyReports.add(report);
            callback.onSuccess(true);
        }, 400);
    }

    @Override
    public void getCurrentUser(RepositoryCallback<StoreUser> callback) {
        handler.postDelayed(() -> callback.onSuccess(currentUser), 200);
    }

    @Override
    public void login(String email, String password, RepositoryCallback<StoreUser> callback) {
        handler.postDelayed(() -> {
            currentUser = new StoreUser("usr_100", email.split("@")[0], true);
            callback.onSuccess(currentUser);
        }, 500);
    }

    @Override
    public void signup(String username, String email, String password, RepositoryCallback<StoreUser> callback) {
        handler.postDelayed(() -> {
            currentUser = new StoreUser("usr_" + System.currentTimeMillis(), username, false);
            callback.onSuccess(currentUser);
        }, 500);
    }

    @Override
    public void deleteItem(String itemId, RepositoryCallback<Boolean> callback) {
        handler.postDelayed(() -> {
            dummyItems.removeIf(item -> item.getId().equals(itemId));
            callback.onSuccess(true);
        }, 300);
    }
}
