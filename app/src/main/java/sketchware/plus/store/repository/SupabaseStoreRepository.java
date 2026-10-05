package sketchware.plus.store.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sketchware.plus.store.auth.AuthApi;
import sketchware.plus.store.auth.AuthResponse;
import sketchware.plus.store.auth.AuthUser;
import sketchware.plus.store.auth.SessionManager;
import sketchware.plus.store.models.ProfileModel;
import sketchware.plus.store.models.StoreItem;
import sketchware.plus.store.models.StoreItemInsert;
import sketchware.plus.store.models.StoreReport;
import sketchware.plus.store.models.StoreUser;
import sketchware.plus.store.network.ApiCallback;
import sketchware.plus.store.network.StoreError;
import sketchware.plus.store.network.SupabaseClient;

public class SupabaseStoreRepository implements StoreRepository {
    private static SupabaseStoreRepository instance;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SupabaseStoreRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized SupabaseStoreRepository getInstance(Context context) {
        if (instance == null) {
            instance = new SupabaseStoreRepository(context);
        }
        return instance;
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    @Override
    public void getItems(String type, String query, String sortBy, RepositoryCallback<List<StoreItem>> callback) {
        StringBuilder endpoint = new StringBuilder("rest/v1/store_items?approved=eq.true");
        if (type != null && !type.isEmpty() && !type.equalsIgnoreCase("ALL")) {
            endpoint.append("&type=eq.").append(encode(type));
        }
        if (query != null && !query.isEmpty()) {
            endpoint.append("&name=ilike.*").append(encode(query)).append("*");
        }
        if ("downloads".equals(sortBy)) {
            endpoint.append("&order=downloads.desc");
        } else {
            endpoint.append("&order=created_at.desc");
        }

        SupabaseClient.getInstance().executeGet(context, endpoint.toString(), String.class, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    Type listType = new TypeToken<List<StoreItem>>() {}.getType();
                    List<StoreItem> items = SupabaseClient.getInstance().getGson().fromJson(result, listType);
                    handler.post(() -> callback.onSuccess(items != null ? items : new ArrayList<>()));
                } catch (Exception e) {
                    handler.post(() -> callback.onError("Failed to parse items: " + e.getMessage()));
                }
            }

            @Override
            public void onError(StoreError error) {
                handler.post(() -> callback.onError(error.getMessage()));
            }
        });
    }

    @Override
    public void getMyItems(String authorId, RepositoryCallback<List<StoreItem>> callback) {
        String endpoint = "rest/v1/store_items?author_id=eq." + authorId + "&order=created_at.desc";
        SupabaseClient.getInstance().executeGet(context, endpoint, String.class, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    Type listType = new TypeToken<List<StoreItem>>() {}.getType();
                    List<StoreItem> items = SupabaseClient.getInstance().getGson().fromJson(result, listType);
                    handler.post(() -> callback.onSuccess(items != null ? items : new ArrayList<>()));
                } catch (Exception e) {
                    handler.post(() -> callback.onError("Failed to parse my items: " + e.getMessage()));
                }
            }

            @Override
            public void onError(StoreError error) {
                handler.post(() -> callback.onError(error.getMessage()));
            }
        });
    }

    @Override
    public void getItemDetails(String id, RepositoryCallback<StoreItem> callback) {
        String endpoint = "rest/v1/store_items?id=eq." + encode(id) + "&limit=1";
        SupabaseClient.getInstance().executeGet(context, endpoint, String.class, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    Type listType = new TypeToken<List<StoreItem>>() {}.getType();
                    List<StoreItem> items = SupabaseClient.getInstance().getGson().fromJson(result, listType);
                    if (items != null && !items.isEmpty()) {
                        handler.post(() -> callback.onSuccess(items.get(0)));
                    } else {
                        handler.post(() -> callback.onError("Item not found"));
                    }
                } catch (Exception e) {
                    handler.post(() -> callback.onError("Failed to parse item details: " + e.getMessage()));
                }
            }

            @Override
            public void onError(StoreError error) {
                handler.post(() -> callback.onError(error.getMessage()));
            }
        });
    }

    /** Call this from the Install SUCCESS path only, not on page view. */
    @Override
    public void incrementDownloads(String itemId) {
        Map<String, Object> body = new HashMap<>();
        body.put("item", itemId);
        body.put("item_id", itemId);
        SupabaseClient.getInstance().executePost(context, "rest/v1/rpc/increment_downloads", body, Void.class, null);
    }

    @Override
    public void uploadItem(StoreItem item, RepositoryCallback<Boolean> callback) {
        String userId = SessionManager.getInstance(context).getUserId();
        if (userId == null) {
            handler.post(() -> callback.onError("You need to log in first"));
            return;
        }

        // Only real columns. approved/downloads/created_at/id are DB defaults.
        StoreItemInsert insertObj = new StoreItemInsert();
        insertObj.authorId = userId;
        insertObj.name = item.getName();
        insertObj.description = item.getDescription();
        insertObj.type = item.getType();
        insertObj.tags = item.getTags();
        insertObj.contentNames = item.getContentNames();
        insertObj.json = item.getJson();

        SupabaseClient.getInstance().executePost(context, "rest/v1/store_items", insertObj, Void.class, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                handler.post(() -> callback.onSuccess(true));
            }

            @Override
            public void onError(StoreError error) {
                handler.post(() -> callback.onError(error.getMessage()));
            }
        });
    }

    @Override
    public void reportItem(StoreReport report, RepositoryCallback<Boolean> callback) {
        String userId = SessionManager.getInstance(context).getUserId();
        if (userId == null) {
            handler.post(() -> callback.onError("You need to log in first"));
            return;
        }

        // Build the body by hand so only real columns get sent.
        Map<String, Object> body = new HashMap<>();
        body.put("item_id", report.getItemId());
        body.put("reporter_id", userId);
        body.put("reason", report.getReason());
        body.put("note", report.getNote());

        SupabaseClient.getInstance().executePost(context, "rest/v1/reports", body, Void.class, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                handler.post(() -> callback.onSuccess(true));
            }

            @Override
            public void onError(StoreError error) {
                handler.post(() -> callback.onError(error.getMessage()));
            }
        });
    }

    @Override
    public void getCurrentUser(RepositoryCallback<StoreUser> callback) {
        SessionManager session = SessionManager.getInstance(context);
        if (!session.isLoggedIn()) {
            handler.post(() -> callback.onSuccess(null));
            return;
        }

        String userId = session.getUserId();
        if (userId == null) {
            fetchUserFromAuth(session, callback);
            return;
        }

        SupabaseClient.getInstance().executeGet(context,
                "rest/v1/profiles?id=eq." + encode(userId) + "&select=id,username,is_admin,banned",
                ProfileModel[].class, new ApiCallback<ProfileModel[]>() {
                    @Override
                    public void onSuccess(ProfileModel[] profiles) {
                        if (profiles != null && profiles.length > 0 && profiles[0] != null) {
                            ProfileModel profile = profiles[0];
                            String username = profile.getUsername() != null ? profile.getUsername() : session.getUsername();
                            StoreUser user = new StoreUser(profile.getId(), username, profile.isAdmin());
                            handler.post(() -> callback.onSuccess(user));
                        } else {
                            fetchUserFromAuth(session, callback);
                        }
                    }

                    @Override
                    public void onError(StoreError error) {
                        fetchUserFromAuth(session, callback);
                    }
                });
    }

    private void fetchUserFromAuth(SessionManager session, RepositoryCallback<StoreUser> callback) {
        SupabaseClient.getInstance().executeGet(context, "auth/v1/user", AuthUser.class, new ApiCallback<AuthUser>() {
            @Override
            public void onSuccess(AuthUser authUser) {
                if (authUser != null) {
                    String username = authUser.getUserMetadata() != null
                            ? authUser.getUserMetadata().getUsername() : session.getUsername();
                    boolean isAdmin = authUser.getUserMetadata() != null && authUser.getUserMetadata().isAdmin();
                    StoreUser user = new StoreUser(authUser.getId(), username, isAdmin);
                    handler.post(() -> callback.onSuccess(user));
                } else {
                    StoreUser user = new StoreUser(session.getUserId(), session.getUsername(), session.isAdmin());
                    handler.post(() -> callback.onSuccess(user));
                }
            }

            @Override
            public void onError(StoreError error) {
                StoreUser user = new StoreUser(session.getUserId(), session.getUsername(), session.isAdmin());
                handler.post(() -> callback.onSuccess(user));
            }
        });
    }

    @Override
    public void login(String email, String password, RepositoryCallback<StoreUser> callback) {
        AuthApi.signIn(context, email, password, new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                if (result != null && result.getUser() != null) {
                    AuthUser authUser = result.getUser();
                    String username = authUser.getUserMetadata() != null
                            ? authUser.getUserMetadata().getUsername() : email;
                    boolean isAdmin = authUser.getUserMetadata() != null && authUser.getUserMetadata().isAdmin();
                    StoreUser user = new StoreUser(authUser.getId(), username, isAdmin);
                    handler.post(() -> callback.onSuccess(user));
                } else {
                    handler.post(() -> callback.onError("Login failed"));
                }
            }

            @Override
            public void onError(StoreError error) {
                handler.post(() -> callback.onError(error.getMessage()));
            }
        });
    }

    @Override
    public void signup(String username, String email, String password, RepositoryCallback<StoreUser> callback) {
        AuthApi.signUp(context, email, password, username, new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                if (result != null && result.getUser() != null) {
                    AuthUser authUser = result.getUser();
                    StoreUser user = new StoreUser(authUser.getId(), username, false);
                    handler.post(() -> callback.onSuccess(user));
                } else {
                    handler.post(() -> callback.onError("Signup failed"));
                }
            }

            @Override
            public void onError(StoreError error) {
                handler.post(() -> callback.onError(error.getMessage()));
            }
        });
    }

    @Override
    public void deleteItem(String itemId, RepositoryCallback<Boolean> callback) {
        String endpoint = "rest/v1/store_items?id=eq." + encode(itemId);
        SupabaseClient.getInstance().execute(context, "DELETE", endpoint, null, Void.class, false, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                handler.post(() -> callback.onSuccess(true));
            }

            @Override
            public void onError(StoreError error) {
                handler.post(() -> callback.onError(error.getMessage()));
            }
        });
    }
}