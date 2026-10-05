package sketchware.plus.store.auth;

import android.content.Context;
import sketchware.plus.store.network.ApiCallback;
import sketchware.plus.store.network.StoreError;
import sketchware.plus.store.network.SupabaseClient;

import java.util.HashMap;
import java.util.Map;

public class AuthApi {

    public static void signUp(Context context, String email, String password, String username, ApiCallback<AuthResponse> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("is_admin", false);

        AuthRequest request = new AuthRequest(email, password, data);
        SupabaseClient.getInstance().executePost(context, "auth/v1/signup", request, AuthResponse.class, new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                if (result != null && result.getAccessToken() != null) {
                    SessionManager.getInstance(context).saveSession(result);
                    if (callback != null) callback.onSuccess(result);
                } else if (result != null && result.getUser() != null) {
                    signIn(context, email, password, new ApiCallback<AuthResponse>() {
                        @Override
                        public void onSuccess(AuthResponse signInResult) {
                            if (callback != null) callback.onSuccess(signInResult);
                        }

                        @Override
                        public void onError(StoreError error) {
                            if (callback != null) callback.onSuccess(result);
                        }
                    });
                } else {
                    if (callback != null) callback.onSuccess(result);
                }
            }

            @Override
            public void onError(StoreError error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    public static void signIn(Context context, String email, String password, ApiCallback<AuthResponse> callback) {
        AuthRequest request = new AuthRequest(email, password, null);
        SupabaseClient.getInstance().executePost(context, "auth/v1/token?grant_type=password", request, AuthResponse.class, new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                if (result != null && result.getAccessToken() != null) {
                    SessionManager.getInstance(context).saveSession(result);
                }
                if (callback != null) callback.onSuccess(result);
            }

            @Override
            public void onError(StoreError error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    public static void refreshToken(Context context, String refreshToken, ApiCallback<AuthResponse> callback) {
        AuthRequest request = new AuthRequest(refreshToken);
        SupabaseClient.getInstance().executePost(context, "auth/v1/token?grant_type=refresh_token", request, AuthResponse.class, new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                if (result != null && result.getAccessToken() != null) {
                    SessionManager.getInstance(context).saveSession(result);
                }
                if (callback != null) callback.onSuccess(result);
            }

            @Override
            public void onError(StoreError error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    public static void signOut(Context context, ApiCallback<Void> callback) {
        SupabaseClient.getInstance().executePost(context, "auth/v1/logout", null, Void.class, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                SessionManager.getInstance(context).clearSession();
                if (callback != null) callback.onSuccess(result);
            }

            @Override
            public void onError(StoreError error) {
                SessionManager.getInstance(context).clearSession();
                if (callback != null) callback.onError(error);
            }
        });
    }
}
