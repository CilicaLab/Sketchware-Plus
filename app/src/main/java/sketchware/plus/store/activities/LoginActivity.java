package sketchware.plus.store.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import sketchware.plus.R;
import sketchware.plus.store.models.StoreUser;
import sketchware.plus.store.repository.RepositoryCallback;
import sketchware.plus.store.repository.RepositoryProvider;
import sketchware.plus.store.repository.StoreRepository;

public class LoginActivity extends AppCompatActivity {

    public static void start(Context context) {
        Intent intent = new Intent(context, LoginActivity.class);
        context.startActivity(intent);
    }

    private StoreRepository repository;

    private MaterialToolbar toolbar;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private TabLayout tabAuth;
    private TextInputLayout tilUsername;
    private TextInputEditText etUsername;
    private TextInputLayout tilEmail;
    private TextInputEditText etEmail;
    private TextInputLayout tilPassword;
    private TextInputEditText etPassword;
    private MaterialButton btnAuthSubmit;
    private MaterialButton btnGuest;
    private ProgressBar pbAuth;

    private boolean isSignUpMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        repository = RepositoryProvider.getRepository(this);

        initViews();
        setupToolbar();
        setupTabs();
        setupListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvTitle = findViewById(R.id.tv_title);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        tabAuth = findViewById(R.id.tab_auth);
        tilUsername = findViewById(R.id.til_username);
        etUsername = findViewById(R.id.et_username);
        tilEmail = findViewById(R.id.til_email);
        etEmail = findViewById(R.id.et_email);
        tilPassword = findViewById(R.id.til_password);
        etPassword = findViewById(R.id.et_password);
        btnAuthSubmit = findViewById(R.id.btn_auth_submit);
        btnGuest = findViewById(R.id.btn_guest);
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

    private void setupTabs() {
        tabAuth.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isSignUpMode = tab.getPosition() == 1;
                updateMode();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void updateMode() {
        if (isSignUpMode) {
            tvTitle.setText("Create Account");
            tvSubtitle.setText("Join the community to upload and share custom packs.");
            tilUsername.setVisibility(View.VISIBLE);
            btnAuthSubmit.setText("Sign Up");
        } else {
            tvTitle.setText("Welcome Back");
            tvSubtitle.setText("Sign in to manage and share custom blocks & components.");
            tilUsername.setVisibility(View.GONE);
            btnAuthSubmit.setText("Log In");
        }
    }

    private void setupListeners() {
        btnAuthSubmit.setOnClickListener(v -> performAuth());
        btnGuest.setOnClickListener(v -> finish());
    }

    private void performAuth() {
        tilUsername.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);

        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (isSignUpMode && TextUtils.isEmpty(username)) {
            tilUsername.setError("Username is required");
            etUsername.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        // Show loading state
        btnAuthSubmit.setEnabled(false);
        btnAuthSubmit.setText("");
        pbAuth.setVisibility(View.VISIBLE);

        if (isSignUpMode) {
            repository.signup(username, email, password, new RepositoryCallback<StoreUser>() {
                @Override
                public void onSuccess(StoreUser user) {
                    onAuthSuccess(user, "Account created successfully!");
                }

                @Override
                public void onError(String error) {
                    onAuthError(error);
                }
            });
        } else {
            repository.login(email, password, new RepositoryCallback<StoreUser>() {
                @Override
                public void onSuccess(StoreUser user) {
                    onAuthSuccess(user, "Logged in successfully!");
                }

                @Override
                public void onError(String error) {
                    onAuthError(error);
                }
            });
        }
    }

    private void onAuthSuccess(StoreUser user, String message) {
        pbAuth.setVisibility(View.GONE);
        btnAuthSubmit.setEnabled(true);
        btnAuthSubmit.setText(isSignUpMode ? "Sign Up" : "Log In");

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        ProfileActivity.start(this);
        finish();
    }

    private void onAuthError(String error) {
        pbAuth.setVisibility(View.GONE);
        btnAuthSubmit.setEnabled(true);
        btnAuthSubmit.setText(isSignUpMode ? "Sign Up" : "Log In");

        String displayMsg = (error != null && !error.isEmpty()) ? error : "Authentication failed";
        Toast.makeText(this, displayMsg, Toast.LENGTH_LONG).show();
    }
}
