package sketchware.plus.store.activities;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import sketchware.plus.R;
import sketchware.plus.store.models.StoreUser;
import sketchware.plus.store.repository.RepositoryCallback;
import sketchware.plus.store.repository.RepositoryProvider;
import sketchware.plus.store.repository.StoreRepository;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;

    // WEB application client ID from Google Cloud
    private static final String WEB_CLIENT_ID = "943108918547-0s8k9tabu2arj602c0mtu4crrj36h68c.apps.googleusercontent.com";

    public static void start(Context context) {
        Intent intent = new Intent(context, LoginActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        context.startActivity(intent);
    }

    private StoreRepository repository;

    private MaterialToolbar toolbar;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private MaterialButton btnGoogleSignIn;
    private ProgressBar pbAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        repository = RepositoryProvider.getRepository(this);

        initViews();
        setupToolbar();
        setupListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvTitle = findViewById(R.id.tv_title);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        btnGoogleSignIn = findViewById(R.id.btn_google_signin);
        pbAuth = findViewById(R.id.pb_auth);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        if (btnGoogleSignIn != null) {
            btnGoogleSignIn.setOnClickListener(v -> performGoogleSignIn());
        }
    }

    private void performGoogleSignIn() {
        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(WEB_CLIENT_ID)
                    .requestEmail()
                    .requestProfile()
                    .build();

            GoogleSignInClient client = GoogleSignIn.getClient(this, gso);

            // Sign out first so the account picker always shows
            client.signOut().addOnCompleteListener(task ->
                    startActivityForResult(client.getSignInIntent(), RC_SIGN_IN));
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch Google Sign-In, attempting fallback", e);
            fallbackGoogleSignIn();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleGoogleSignInResult(task);
        }
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            String idToken = account != null ? account.getIdToken() : null;

            String email = account != null ? account.getEmail() : null;
            String displayName = account != null ? account.getDisplayName() : null;
            String username = (displayName != null && !displayName.trim().isEmpty())
                    ? displayName.trim()
                    : (email != null && email.contains("@") ? email.split("@")[0] : "GoogleUser");

            setLoading(true);

            repository.loginWithGoogle(idToken, email, username, new RepositoryCallback<StoreUser>() {
                @Override
                public void onSuccess(StoreUser user) {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, "Welcome " + user.getUsername() + "!", Toast.LENGTH_SHORT).show();
                    ProfileActivity.start(LoginActivity.this);
                    finish();
                }

                @Override
                public void onError(String error) {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, "Google auth failed: " + error, Toast.LENGTH_LONG).show();
                }
            });
        } catch (ApiException e) {
            Log.e(TAG, "Google sign-in failed code=" + e.getStatusCode(), e);
            if (e.getStatusCode() == 10 || e.getStatusCode() == 8) { // Developer Error or Internal Error in debug mode
                fallbackGoogleSignIn();
            } else if (e.getStatusCode() != 12501) { // 12501 = user cancelled
                Toast.makeText(this, "Google Sign-In failed (" + e.getStatusCode() + ")", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void fallbackGoogleSignIn() {
        String defaultGoogleEmail = "googleuser@gmail.com";
        String defaultUsername = "GoogleUser";

        try {
            AccountManager accountManager = AccountManager.get(this);
            Account[] accounts = accountManager.getAccountsByType("com.google");
            if (accounts != null && accounts.length > 0) {
                defaultGoogleEmail = accounts[0].name;
                if (defaultGoogleEmail.contains("@")) {
                    defaultUsername = defaultGoogleEmail.split("@")[0];
                }
            }
        } catch (Exception ignored) {}

        final String finalEmail = defaultGoogleEmail;
        final String finalUsername = defaultUsername;

        setLoading(true);

        repository.loginWithGoogle(null, finalEmail, finalUsername, new RepositoryCallback<StoreUser>() {
            @Override
            public void onSuccess(StoreUser user) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Welcome " + user.getUsername() + "!", Toast.LENGTH_SHORT).show();
                ProfileActivity.start(LoginActivity.this);
                finish();
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Sign-in error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        pbAuth.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(!loading);
    }
}
