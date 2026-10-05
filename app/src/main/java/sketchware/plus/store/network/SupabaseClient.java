package sketchware.plus.store.network;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import sketchware.plus.BuildConfig;
import sketchware.plus.store.activities.LoginActivity;
import sketchware.plus.store.auth.AuthRequest;
import sketchware.plus.store.auth.AuthResponse;
import sketchware.plus.store.auth.SessionManager;

public class SupabaseClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static SupabaseClient instance;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private TokenProvider tokenProvider;

    public interface TokenProvider {
        String getToken();
    }

    private SupabaseClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new GsonBuilder()
                .setLenient()
                .create();
    }

    public static synchronized SupabaseClient getInstance() {
        if (instance == null) {
            instance = new SupabaseClient();
        }
        return instance;
    }

    public void setTokenProvider(TokenProvider provider) {
        this.tokenProvider = provider;
    }

    public Gson getGson() {
        return gson;
    }

    public String getBaseUrl() {
        return BuildConfig.SUPABASE_URL != null ? BuildConfig.SUPABASE_URL : "";
    }

    private String getEffectiveToken() {
        if (tokenProvider != null) {
            String token = tokenProvider.getToken();
            if (token != null && !token.isEmpty()) {
                return token;
            }
        }
        return BuildConfig.SUPABASE_ANON_KEY != null ? BuildConfig.SUPABASE_ANON_KEY : "";
    }

    public <T> void executeGet(Context context, String endpoint, Class<T> responseClass, ApiCallback<T> callback) {
        execute(context, "GET", endpoint, null, responseClass, false, callback);
    }

    public <T> void executePost(Context context, String endpoint, Object requestBodyObj, Class<T> responseClass, ApiCallback<T> callback) {
        String jsonBody = requestBodyObj != null ? gson.toJson(requestBodyObj) : null;
        execute(context, "POST", endpoint, jsonBody, responseClass, false, callback);
    }

    public <T> void executePatch(Context context, String endpoint, Object requestBodyObj, Class<T> responseClass, ApiCallback<T> callback) {
        String jsonBody = requestBodyObj != null ? gson.toJson(requestBodyObj) : null;
        execute(context, "PATCH", endpoint, jsonBody, responseClass, false, callback);
    }

    public <T> void executeDelete(Context context, String endpoint, ApiCallback<T> callback) {
        execute(context, "DELETE", endpoint, null, null, false, callback);
    }

    public <T> void execute(Context context, String method, String endpoint, String jsonBody, Class<T> responseClass, boolean isRetry, ApiCallback<T> callback) {
        if (!NetworkUtils.isConnected(context)) {
            if (callback != null) {
                runOnMainThread(() -> callback.onError(StoreError.NO_CONNECTION));
            }
            return;
        }

        String baseUrl = getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            if (callback != null) {
                runOnMainThread(() -> callback.onError(StoreError.SERVER));
            }
            return;
        }

        String url = baseUrl.endsWith("/") ? baseUrl + endpoint : baseUrl + "/" + endpoint;

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY != null ? BuildConfig.SUPABASE_ANON_KEY : "")
                .addHeader("Authorization", "Bearer " + getEffectiveToken())
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json");

        RequestBody body = null;
        if (jsonBody != null && !method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD")) {
            body = RequestBody.create(jsonBody, JSON);
        } else if (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PATCH") || method.equalsIgnoreCase("PUT")) {
            body = RequestBody.create("", JSON);
        }

        requestBuilder.method(method, body);

        httpClient.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) {
                    runOnMainThread(() -> callback.onError(StoreError.NO_CONNECTION));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                int code = response.code();

                if (code == 401 && !isRetry && !endpoint.contains("auth/v1")) {
                    try {
                        String refreshToken = SessionManager.getInstance(context).getRefreshToken();
                        if (refreshToken != null && !refreshToken.isEmpty()) {
                            boolean refreshed = performSynchronousRefresh(context, refreshToken);
                            if (refreshed) {
                                execute(context, method, endpoint, jsonBody, responseClass, true, callback);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    try {
                        SessionManager.getInstance(context).clearSession();
                    } catch (Exception ignored) {}
                    navigateToLogin(context);

                    if (callback != null) {
                        runOnMainThread(() -> callback.onError(StoreError.UNAUTHORIZED.withDetails("Session expired. Please log in again.")));
                    }
                    return;
                }

                if (!response.isSuccessful()) {
                    String errorBody = "";
                    try {
                        if (response.body() != null) {
                            errorBody = response.body().string();
                        }
                    } catch (Exception ignored) {}

                    String errorMessage = parseErrorMessage(errorBody, code);
                    StoreError error = mapHttpCodeToError(code).withDetails(errorMessage);
                    if (callback != null) {
                        runOnMainThread(() -> callback.onError(error));
                    }
                    return;
                }

                try {
                    String respStr = response.body() != null ? response.body().string() : "";
                    if (responseClass == null || responseClass == Void.class) {
                        if (callback != null) {
                            runOnMainThread(() -> callback.onSuccess(null));
                        }
                    } else if (responseClass == String.class) {
                        if (callback != null) {
                            @SuppressWarnings("unchecked")
                            T parsed = (T) respStr;
                            runOnMainThread(() -> callback.onSuccess(parsed));
                        }
                    } else {
                        T result = gson.fromJson(respStr, responseClass);
                        if (callback != null) {
                            runOnMainThread(() -> callback.onSuccess(result));
                        }
                    }
                } catch (Exception e) {
                    if (callback != null) {
                        runOnMainThread(() -> callback.onError(StoreError.UNKNOWN.withDetails("Failed to parse response: " + e.getMessage())));
                    }
                }
            }
        });
    }

    private boolean performSynchronousRefresh(Context context, String refreshToken) {
        try {
            String baseUrl = getBaseUrl();
            String url = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "auth/v1/token?grant_type=refresh_token";
            AuthRequest req = new AuthRequest(refreshToken);
            String json = gson.toJson(req);
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY != null ? BuildConfig.SUPABASE_ANON_KEY : "")
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, JSON))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String respStr = response.body().string();
                    AuthResponse authResponse = gson.fromJson(respStr, AuthResponse.class);
                    if (authResponse != null && authResponse.getAccessToken() != null) {
                        SessionManager.getInstance(context).saveSession(authResponse);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void navigateToLogin(Context context) {
        if (context == null) return;
        runOnMainThread(() -> {
            try {
                Intent intent = new Intent(context, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private StoreError mapHttpCodeToError(int code) {
        if (code >= 200 && code < 300) {
            return null; // Success
        }
        switch (code) {
            case 401:
                return StoreError.UNAUTHORIZED;
            case 403:
                return StoreError.FORBIDDEN;
            case 404:
                return StoreError.NOT_FOUND;
            case 500:
            case 502:
            case 503:
            case 504:
                return StoreError.SERVER;
            default:
                if (code >= 500) return StoreError.SERVER;
                return StoreError.UNKNOWN;
        }
    }

    private String parseErrorMessage(String jsonStr, int httpCode) {
        if (jsonStr != null && !jsonStr.trim().isEmpty()) {
            try {
                JsonObject jsonObject = gson.fromJson(jsonStr, JsonObject.class);
                if (jsonObject != null) {
                    if (jsonObject.has("error_description") && !jsonObject.get("error_description").isJsonNull()) {
                        return jsonObject.get("error_description").getAsString();
                    }
                    if (jsonObject.has("msg") && !jsonObject.get("msg").isJsonNull()) {
                        return jsonObject.get("msg").getAsString();
                    }
                    if (jsonObject.has("message") && !jsonObject.get("message").isJsonNull()) {
                        return jsonObject.get("message").getAsString();
                    }
                    if (jsonObject.has("error") && !jsonObject.get("error").isJsonNull()) {
                        return jsonObject.get("error").getAsString();
                    }
                }
            } catch (Exception ignored) {}
        }
        switch (httpCode) {
            case 400: return "Invalid login credentials or bad request";
            case 401: return "Invalid credentials or unauthorized";
            case 403: return "Access forbidden";
            case 404: return "Requested resource not found";
            case 422: return "Unprocessable entry or invalid input format";
            default:
                if (httpCode >= 500) return "Server error (" + httpCode + ")";
                return "Request failed (" + httpCode + ")";
        }
    }

    private void runOnMainThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }
}
