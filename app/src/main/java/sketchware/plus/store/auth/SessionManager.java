package sketchware.plus.store.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import sketchware.plus.store.network.SupabaseClient;

public class SessionManager implements SupabaseClient.TokenProvider {
    private static final String PREF_NAME = "supabase_secure_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_IS_ADMIN = "is_admin";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private static SessionManager instance;
    private SharedPreferences sharedPreferences;

    private SessionManager(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            sharedPreferences = EncryptedSharedPreferences.create(
                    context.getApplicationContext(),
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            e.printStackTrace();
            sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }

        SupabaseClient.getInstance().setTokenProvider(this);
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    @Override
    public String getToken() {
        return getAccessToken();
    }

    public void saveSession(AuthResponse response) {
        if (response == null) return;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_ACCESS_TOKEN, response.getAccessToken());
        editor.putString(KEY_REFRESH_TOKEN, response.getRefreshToken());
        
        long expiresAt = System.currentTimeMillis() + (response.getExpiresIn() * 1000L);
        editor.putLong(KEY_EXPIRES_AT, expiresAt);

        if (response.getUser() != null) {
            AuthUser user = response.getUser();
            editor.putString(KEY_USER_ID, user.getId());
            if (user.getUserMetadata() != null) {
                editor.putString(KEY_USERNAME, user.getUserMetadata().getUsername());
                editor.putBoolean(KEY_IS_ADMIN, user.getUserMetadata().isAdmin());
            }
        }
        editor.apply();
    }

    public void updateTokens(String accessToken, String refreshToken, long expiresIn) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (accessToken != null) editor.putString(KEY_ACCESS_TOKEN, accessToken);
        if (refreshToken != null) editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        if (expiresIn > 0) {
            long expiresAt = System.currentTimeMillis() + (expiresIn * 1000L);
            editor.putLong(KEY_EXPIRES_AT, expiresAt);
        }
        editor.apply();
    }

    public String getAccessToken() {
        return sharedPreferences.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getUserId() {
        return sharedPreferences.getString(KEY_USER_ID, null);
    }

    public String getUsername() {
        return sharedPreferences.getString(KEY_USERNAME, "Guest");
    }

    public boolean isAdmin() {
        return sharedPreferences.getBoolean(KEY_IS_ADMIN, false);
    }

    public boolean isLoggedIn() {
        String token = getAccessToken();
        return token != null && !token.isEmpty();
    }

    public void clearSession() {
        sharedPreferences.edit().clear().apply();
    }
}
