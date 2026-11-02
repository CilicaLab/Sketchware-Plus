package sketchware.plus.store.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import sketchware.plus.R;
import sketchware.plus.store.adapters.StorePagerAdapter;
import sketchware.plus.store.auth.SessionManager;
import sketchware.plus.store.fragments.StorePageFragment;
import sketchware.plus.store.models.StoreUser;
import sketchware.plus.store.repository.RepositoryCallback;
import sketchware.plus.store.repository.RepositoryProvider;
import sketchware.plus.store.repository.StoreRepository;

public class StoreActivity extends AppCompatActivity {

    public static void start(Context context) {
        Intent intent = new Intent(context, StoreActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    private StoreRepository repository;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private StorePagerAdapter pagerAdapter;
    private ExtendedFloatingActionButton fabUpload;
    private MaterialToolbar toolbar;

    private String currentQuery = "";
    private StoreUser currentUser;

    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable searchRunnable;
    private static final long SEARCH_DEBOUNCE_DELAY_MS = 300L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);

        repository = RepositoryProvider.getRepository(this);

        initViews();
        setupToolbar();
        setupViewPager();
        setupListeners();

        loadCurrentUser();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);
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

    private void setupViewPager() {
        pagerAdapter = new StorePagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 1:
                    tab.setText("Blocks");
                    break;
                case 2:
                    tab.setText("Components");
                    break;
                case 0:
                default:
                    tab.setText("All");
                    break;
            }
        }).attach();
    }

    private void setupListeners() {
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
                        if (searchRunnable != null) {
                            searchHandler.removeCallbacks(searchRunnable);
                        }
                        currentQuery = query;
                        dispatchSearch(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        if (searchRunnable != null) {
                            searchHandler.removeCallbacks(searchRunnable);
                        }
                        searchRunnable = () -> {
                            currentQuery = newText;
                            dispatchSearch(newText);
                        };
                        searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_DELAY_MS);
                        return true;
                    }
                });
            }
        }

        updateMenuVisibility(menu);
        return true;
    }

    private void dispatchSearch(String query) {
        if (pagerAdapter != null) {
            for (StorePageFragment fragment : pagerAdapter.getFragments().values()) {
                if (fragment != null) {
                    fragment.search(query);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
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
