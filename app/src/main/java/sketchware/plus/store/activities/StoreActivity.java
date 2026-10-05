package sketchware.plus.store.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import sketchware.plus.R;
import sketchware.plus.store.adapters.StoreItemAdapter;
import sketchware.plus.store.auth.SessionManager;
import sketchware.plus.store.models.StoreItem;
import sketchware.plus.store.models.StoreUser;
import sketchware.plus.store.repository.RepositoryCallback;
import sketchware.plus.store.repository.RepositoryProvider;
import sketchware.plus.store.repository.StoreRepository;
import sketchware.plus.store.widget.StateView;

public class StoreActivity extends AppCompatActivity {

    public static void start(Context context) {
        Intent intent = new Intent(context, StoreActivity.class);
        context.startActivity(intent);
    }

    private StoreRepository repository;
    private StoreItemAdapter adapter;
    private StateView stateView;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private TabLayout tabLayout;
    private MaterialButton btnSort;
    private ExtendedFloatingActionButton fabUpload;
    private MaterialToolbar toolbar;

    private String currentType = "ALL";
    private String currentQuery = "";
    private String currentSortBy = "newest"; // "newest" or "most_downloaded"
    private StoreUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);

        repository = RepositoryProvider.getRepository(this);

        initViews();
        setupToolbar();
        setupTabs();
        setupRecyclerView();
        setupListeners();

        loadCurrentUser();
        loadItems(true);
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tab_layout);
        btnSort = findViewById(R.id.btn_sort);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        recyclerView = findViewById(R.id.recycler_view);
        stateView = findViewById(R.id.state_view);
        fabUpload = findViewById(R.id.fab_upload);
        if (fabUpload != null) {
            fabUpload.bringToFront();
            fabUpload.show();
        }
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
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        currentType = "ALL";
                        break;
                    case 1:
                        currentType = StoreItem.TYPE_BLOCK;
                        break;
                    case 2:
                        currentType = StoreItem.TYPE_COMPONENT;
                        break;
                }
                loadItems(true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclerView() {
        adapter = new StoreItemAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (fabUpload != null) {
                    fabUpload.setVisibility(View.VISIBLE);
                    fabUpload.show();
                }
            }
        });

        adapter.setOnItemClickListener(item -> {
            ItemDetailActivity.start(this, item);
        });

        adapter.setOnInstallClickListener((item, position) -> {
            // Simulate installation
            item.setDownloads(item.getDownloads() + 1);
            adapter.notifyItemChanged(position);
            Snackbar.make(recyclerView, "Installed " + item.getName() + " successfully!", Snackbar.LENGTH_LONG).show();
        });
    }

    private void setupListeners() {
        swipeRefresh.setColorSchemeResources(R.color.color_primary);
        swipeRefresh.setOnRefreshListener(() -> loadItems(false));

        btnSort.setOnClickListener(this::showSortMenu);

        fabUpload.setOnClickListener(v -> handleUploadPackClick());
    }

    private void handleUploadPackClick() {
        if (isLoggedIn()) {
            UploadActivity.start(this);
        } else {
            Toast.makeText(this, "Please log in or sign up to upload packs", Toast.LENGTH_SHORT).show();
            LoginActivity.start(this);
        }
    }

    private boolean isLoggedIn() {
        if (SessionManager.getInstance(this).isLoggedIn()) {
            return true;
        }
        return !RepositoryProvider.isUseSupabase() && currentUser != null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentUser();
    }

    private void showSortMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add(0, 1, 0, "Newest First");
        popupMenu.getMenu().add(0, 2, 1, "Most Downloaded");

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                currentSortBy = "newest";
                btnSort.setText("Newest");
            } else if (item.getItemId() == 2) {
                currentSortBy = "most_downloaded";
                btnSort.setText("Most Downloaded");
            }
            loadItems(true);
            return true;
        });
        popupMenu.show();
    }

    private void loadCurrentUser() {
        repository.getCurrentUser(new RepositoryCallback<StoreUser>() {
            @Override
            public void onSuccess(StoreUser user) {
                currentUser = user;
                invalidateOptionsMenu();
            }

            @Override
            public void onError(String error) {
                currentUser = null;
                invalidateOptionsMenu();
            }
        });
    }

    private void loadItems(boolean showStateLoading) {
        if (showStateLoading) {
            stateView.showLoading("Fetching store items...");
        } else {
            swipeRefresh.setRefreshing(true);
        }

        if (fabUpload != null) {
            fabUpload.setVisibility(View.VISIBLE);
            fabUpload.bringToFront();
            fabUpload.show();
        }

        repository.getItems(currentType, currentQuery, currentSortBy, new RepositoryCallback<List<StoreItem>>() {
            @Override
            public void onSuccess(List<StoreItem> items) {
                swipeRefresh.setRefreshing(false);
                List<StoreItem> safeItems = items != null ? items : new ArrayList<>();
                adapter.setItems(safeItems);
                if (safeItems.isEmpty()) {
                    String label = "ALL".equalsIgnoreCase(currentType) || currentType == null ? "item" : currentType.toLowerCase();
                    stateView.showEmpty(
                            "No " + label + "s found",
                            TextUtils.isEmpty(currentQuery)
                                    ? "Be the first to upload a " + label + " pack!"
                                    : "No items match your search '" + currentQuery + "'"
                    );
                } else {
                    stateView.showContent();
                }
                if (fabUpload != null) {
                    fabUpload.setVisibility(View.VISIBLE);
                    fabUpload.bringToFront();
                    fabUpload.show();
                }
            }

            @Override
            public void onError(String error) {
                swipeRefresh.setRefreshing(false);
                stateView.showError(error, v -> loadItems(true));
                if (fabUpload != null) {
                    fabUpload.setVisibility(View.VISIBLE);
                    fabUpload.bringToFront();
                    fabUpload.show();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_store, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint("Search packs...");
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        currentQuery = query;
                        loadItems(true);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        currentQuery = newText;
                        loadItems(false);
                        return true;
                    }
                });
            }
        }

        updateMenuVisibility(menu);
        return true;
    }

    private void updateMenuVisibility(Menu menu) {
        if (menu == null) return;
        boolean loggedIn = SessionManager.getInstance(this).isLoggedIn() || (currentUser != null);
        MenuItem loginItem = menu.findItem(R.id.action_login);
        MenuItem profileItem = menu.findItem(R.id.action_profile);

        if (loginItem != null) loginItem.setVisible(!loggedIn);
        if (profileItem != null) profileItem.setVisible(loggedIn);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            ProfileActivity.start(this);
            return true;
        }
        if (id == R.id.action_login) {
            LoginActivity.start(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
