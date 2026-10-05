package sketchware.plus.store.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import sketchware.plus.R;
import sketchware.plus.store.adapters.MyUploadsAdapter;
import sketchware.plus.store.auth.AuthApi;
import sketchware.plus.store.auth.SessionManager;
import sketchware.plus.store.models.StoreItem;
import sketchware.plus.store.models.StoreUser;
import sketchware.plus.store.network.ApiCallback;
import sketchware.plus.store.network.StoreError;
import sketchware.plus.store.repository.RepositoryCallback;
import sketchware.plus.store.repository.RepositoryProvider;
import sketchware.plus.store.repository.StoreRepository;
import sketchware.plus.store.widget.StateView;

public class ProfileActivity extends AppCompatActivity {

    public static void start(Context context) {
        Intent intent = new Intent(context, ProfileActivity.class);
        context.startActivity(intent);
    }

    private StoreRepository repository;
    private StoreUser currentUser;
    private MyUploadsAdapter uploadsAdapter;

    private MaterialToolbar toolbar;
    private TextView tvProfileUsername;
    private TextView tvProfileAdminBadge;
    private TextView tvTotalUploads;
    private TextView tvTotalDownloads;
    private RecyclerView rvMyUploads;
    private StateView stateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        repository = RepositoryProvider.getRepository(this);

        initViews();
        setupToolbar();
        setupRecyclerView();

        loadProfileData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvProfileUsername = findViewById(R.id.tv_profile_username);
        tvProfileAdminBadge = findViewById(R.id.tv_profile_admin_badge);
        tvTotalUploads = findViewById(R.id.tv_total_uploads);
        tvTotalDownloads = findViewById(R.id.tv_total_downloads);
        rvMyUploads = findViewById(R.id.rv_my_uploads);
        stateView = findViewById(R.id.state_view);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        uploadsAdapter = new MyUploadsAdapter(this);
        rvMyUploads.setLayoutManager(new LinearLayoutManager(this));
        rvMyUploads.setAdapter(uploadsAdapter);

        uploadsAdapter.setOnItemActionListener(new MyUploadsAdapter.OnItemActionListener() {
            @Override
            public void onItemClick(StoreItem item) {
                ItemDetailActivity.start(ProfileActivity.this, item);
            }

            @Override
            public void onEditClick(StoreItem item) {
                showEditDialog(item);
            }

            @Override
            public void onDeleteClick(StoreItem item, int position) {
                confirmDelete(item, position);
            }
        });
    }

    private void loadProfileData() {
        stateView.showLoading("Loading user profile...");

        repository.getCurrentUser(new RepositoryCallback<StoreUser>() {
            @Override
            public void onSuccess(StoreUser user) {
                if (user == null) {
                    Toast.makeText(ProfileActivity.this, "Please log in to view profile", Toast.LENGTH_SHORT).show();
                    LoginActivity.start(ProfileActivity.this);
                    finish();
                    return;
                }
                currentUser = user;
                invalidateOptionsMenu();
                displayUserHeader(user);
                loadMyUploads(user);
            }

            @Override
            public void onError(String error) {
                stateView.showError(error, v -> loadProfileData());
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_profile, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_logout) {
            showLogoutConfirmationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showLogoutConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of your account?")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    AuthApi.signOut(this, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(ProfileActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        @Override
                        public void onError(StoreError error) {
                            SessionManager.getInstance(ProfileActivity.this).clearSession();
                            Toast.makeText(ProfileActivity.this, "Logged out", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void displayUserHeader(StoreUser user) {
        if (user == null) return;
        tvProfileUsername.setText(user.getUsername() != null ? user.getUsername() : "Guest");
        if (user.isAdmin()) {
            tvProfileAdminBadge.setVisibility(View.VISIBLE);
        } else {
            tvProfileAdminBadge.setVisibility(View.GONE);
        }
    }

    private void loadMyUploads(StoreUser user) {
        if (user == null || user.getId() == null) return;
        repository.getMyItems(user.getId(), new RepositoryCallback<List<StoreItem>>() {
            @Override
            public void onSuccess(List<StoreItem> items) {
                List<StoreItem> myItems = items != null ? items : new ArrayList<>();
                int sumDownloads = 0;
                for (StoreItem item : myItems) {
                    sumDownloads += item.getDownloads();
                }

                tvTotalUploads.setText(String.valueOf(myItems.size()));
                tvTotalDownloads.setText(NumberFormat.getInstance(Locale.US).format(sumDownloads));

                if (myItems.isEmpty()) {
                    stateView.showEmpty(
                            "No Uploaded Packs",
                            "You haven't uploaded any block or component packs yet."
                    );
                } else {
                    stateView.showContent();
                    uploadsAdapter.setItems(myItems);
                }
            }

            @Override
            public void onError(String error) {
                stateView.showError(error, v -> loadMyUploads(user));
            }
        });
    }

    private void showEditDialog(StoreItem item) {
        final TextInputEditText input = new TextInputEditText(this);
        input.setText(item.getDescription());

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit Pack Description")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newDesc = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!newDesc.isEmpty()) {
                        item.setDescription(newDesc);
                        uploadsAdapter.notifyDataSetChanged();
                        Snackbar.make(rvMyUploads, "Description updated!", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(StoreItem item, int position) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Pack?")
                .setMessage("Are you sure you want to delete '" + item.getName() + "'? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.deleteItem(item.getId(), new RepositoryCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {
                            uploadsAdapter.removeItem(position);
                            Snackbar.make(rvMyUploads, "Pack deleted", Snackbar.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(ProfileActivity.this, "Failed to delete: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
