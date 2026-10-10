package sketchware.plus.activities.main.fragments.explore;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
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
import sketchware.plus.store.activities.ItemDetailActivity;
import sketchware.plus.store.activities.LoginActivity;
import sketchware.plus.store.activities.ProfileActivity;
import sketchware.plus.store.activities.UploadActivity;
import sketchware.plus.store.adapters.StoreItemAdapter;
import sketchware.plus.store.auth.SessionManager;
import sketchware.plus.store.models.StoreItem;
import sketchware.plus.store.models.StoreUser;
import sketchware.plus.store.repository.RepositoryCallback;
import sketchware.plus.store.repository.RepositoryProvider;
import sketchware.plus.store.repository.StoreRepository;
import sketchware.plus.store.widget.StateView;

public class ExploreFragment extends Fragment {

    private static final String PREF_STORE_DEV_MODE = "pref_store_dev_mode";
    private static final int REQUIRED_TAPS = 16;

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
    private String currentSortBy = "newest";
    private StoreUser currentUser;

    // Secret tap counter for developer mode toggle
    private int tapCount = 0;
    private long lastTapTime = 0;

    private boolean isDevModeEnabled() {
        if (getContext() == null) return false;
        SharedPreferences prefs = requireContext().getSharedPreferences("sketchware_plus_prefs", Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_STORE_DEV_MODE, false);
    }

    private void setDevModeEnabled(boolean enabled) {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("sketchware_plus_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_STORE_DEV_MODE, enabled).apply();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (!isDevModeEnabled()) {
            return setupUnderConstructionView(inflater, container);
        }

        View view = inflater.inflate(R.layout.activity_store, container, false);

        repository = RepositoryProvider.getRepository(requireContext());

        initViews(view);
        setupToolbar();
        setupTabs();
        setupRecyclerView();
        setupListeners();

        loadCurrentUser();
        loadItems(true);

        return view;
    }

    private View setupUnderConstructionView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        View view = inflater.inflate(R.layout.fragment_store_under_construction, container, false);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar_under_construction);
        if (toolbar != null) {
            toolbar.setNavigationIcon(null);
        }

        ImageView imgConstruction = view.findViewById(R.id.img_under_construction);
        TextView tvTitle = view.findViewById(R.id.tv_title_under_construction);

        View.OnClickListener tapListener = v -> handleSecretTap();

        if (imgConstruction != null) {
            imgConstruction.setOnClickListener(tapListener);
        }

        if (tvTitle != null) {
            tvTitle.setOnClickListener(tapListener);
        }

        return view;
    }

    private void handleSecretTap() {
        long now = System.currentTimeMillis();
        if (now - lastTapTime > 2000) {
            tapCount = 0;
        }
        lastTapTime = now;
        tapCount++;

        if (tapCount >= REQUIRED_TAPS) {
            tapCount = 0;
            setDevModeEnabled(true);
            Toast.makeText(requireContext(), "⚡ Developer Mode Activated! Unlocking Store...", Toast.LENGTH_SHORT).show();
            reloadFragment();
        }
    }

    private void promptDeveloperModeToggle() {
        if (getContext() == null || !isDevModeEnabled()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Lock Store")
                .setMessage("Developer Mode is currently active. Would you like to lock the Store and return to the Under Construction view for regular users?")
                .setPositiveButton("Lock Store", (dialog, which) -> {
                    setDevModeEnabled(false);
                    Toast.makeText(requireContext(), "🔒 Store Locked (Normal User View)", Toast.LENGTH_SHORT).show();
                    reloadFragment();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void reloadFragment() {
        if (getActivity() != null && isAdded()) {
            getParentFragmentManager().beginTransaction()
                    .detach(this)
                    .attach(this)
                    .commitAllowingStateLoss();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupMainFab();
    }

    private void setupMainFab() {
        if (getActivity() != null) {
            ExtendedFloatingActionButton fab = getActivity().findViewById(R.id.create_new_project);
            if (fab != null) {
                if (isDevModeEnabled()) {
                    fab.setText("Upload Pack");
                    fab.setIconResource(R.drawable.ic_add_white_24dp);
                    fab.setOnClickListener(v -> handleUploadPackClick());
                    fab.show();
                    fab.extend();
                } else {
                    fab.setText("Coming Soon");
                    fab.setIconResource(R.drawable.ic_mtrl_deployed_code);
                    fab.setOnClickListener(v -> {
                        Toast.makeText(requireContext(), "Store feature is coming soon!", Toast.LENGTH_SHORT).show();
                    });
                    fab.show();
                    fab.extend();
                }
            }
        }
        if (fabUpload != null) {
            fabUpload.setVisibility(View.GONE);
        }
    }

    private void initViews(View view) {
        toolbar = view.findViewById(R.id.toolbar);
        tabLayout = view.findViewById(R.id.tab_layout);
        btnSort = view.findViewById(R.id.btn_sort);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        recyclerView = view.findViewById(R.id.recycler_view);
        stateView = view.findViewById(R.id.state_view);
        fabUpload = view.findViewById(R.id.fab_upload);
        if (fabUpload != null) {
            fabUpload.bringToFront();
            fabUpload.show();
        }
    }

    private void setupToolbar() {
        if (toolbar == null) return;
        toolbar.setNavigationIcon(null);
        toolbar.inflateMenu(R.menu.menu_store);

        // Long press on store toolbar title to allow developer to lock store back
        toolbar.setOnLongClickListener(v -> {
            promptDeveloperModeToggle();
            return true;
        });

        MenuItem searchItem = toolbar.getMenu().findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint("Search store packs...");
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

        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_profile) {
                ProfileActivity.start(requireContext());
                return true;
            } else if (id == R.id.action_login) {
                LoginActivity.start(requireContext());
                return true;
            }
            return false;
        });
        updateMenuVisibility();
    }

    private void updateMenuVisibility() {
        if (toolbar == null) return;
        Menu menu = toolbar.getMenu();
        if (menu == null) return;
        boolean loggedIn = SessionManager.getInstance(requireContext()).isLoggedIn() || (currentUser != null);
        MenuItem loginItem = menu.findItem(R.id.action_login);
        MenuItem profileItem = menu.findItem(R.id.action_profile);

        if (loginItem != null) loginItem.setVisible(!loggedIn);
        if (profileItem != null) profileItem.setVisible(loggedIn);
    }

    private void setupTabs() {
        if (tabLayout == null) return;
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
        if (recyclerView == null) return;
        adapter = new StoreItemAdapter(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (getActivity() != null) {
                    ExtendedFloatingActionButton fab = getActivity().findViewById(R.id.create_new_project);
                    if (fab != null) {
                        fab.show();
                        if (dy > 0) {
                            fab.shrink();
                        } else if (dy < 0) {
                            fab.extend();
                        }
                    }
                }
            }
        });

        adapter.setOnItemClickListener(item -> {
            ItemDetailActivity.start(requireContext(), item);
        });

        adapter.setOnInstallClickListener((item, position) -> {
            item.setDownloads(item.getDownloads() + 1);
            adapter.notifyItemChanged(position);
            Snackbar.make(recyclerView, "Installed " + item.getName() + " successfully!", Snackbar.LENGTH_LONG).show();
        });
    }

    private void setupListeners() {
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.color_primary);
            swipeRefresh.setOnRefreshListener(() -> loadItems(false));
        }

        if (btnSort != null) {
            btnSort.setOnClickListener(this::showSortMenu);
        }

        if (fabUpload != null) {
            fabUpload.setOnClickListener(v -> handleUploadPackClick());
        }
    }

    private void handleUploadPackClick() {
        if (isLoggedIn()) {
            UploadActivity.start(requireContext());
        } else {
            Toast.makeText(requireContext(), "Please log in or sign up to upload packs", Toast.LENGTH_SHORT).show();
            LoginActivity.start(requireContext());
        }
    }

    private boolean isLoggedIn() {
        if (SessionManager.getInstance(requireContext()).isLoggedIn()) {
            return true;
        }
        return !RepositoryProvider.isUseSupabase() && currentUser != null;
    }

    private void showSortMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
        popupMenu.getMenu().add(0, 1, 0, "Newest First");
        popupMenu.getMenu().add(0, 2, 1, "Most Downloaded");

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                currentSortBy = "newest";
                if (btnSort != null) btnSort.setText("Newest");
            } else if (item.getItemId() == 2) {
                currentSortBy = "most_downloaded";
                if (btnSort != null) btnSort.setText("Most Downloaded");
            }
            loadItems(true);
            return true;
        });
        popupMenu.show();
    }

    private void loadCurrentUser() {
        if (!isDevModeEnabled()) return;
        if (repository == null) {
            repository = RepositoryProvider.getRepository(requireContext());
        }
        if (repository == null) return;

        repository.getCurrentUser(new RepositoryCallback<StoreUser>() {
            @Override
            public void onSuccess(StoreUser user) {
                currentUser = user;
                updateMenuVisibility();
            }

            @Override
            public void onError(String error) {
                currentUser = null;
                updateMenuVisibility();
            }
        });
    }

    private void loadItems(boolean showStateLoading) {
        if (!isDevModeEnabled()) return;
        if (repository == null) {
            repository = RepositoryProvider.getRepository(requireContext());
        }
        if (repository == null || stateView == null) return;

        if (showStateLoading) {
            stateView.showLoading("Fetching store items...");
        } else if (swipeRefresh != null) {
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
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                List<StoreItem> safeItems = items != null ? items : new ArrayList<>();
                if (adapter != null) adapter.setItems(safeItems);
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
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                stateView.showError(error, v -> loadItems(true));
                if (fabUpload != null) {
                    fabUpload.setVisibility(View.VISIBLE);
                    fabUpload.bringToFront();
                    fabUpload.show();
                }
            }
        });
    }

    public void refresh() {
        setupMainFab();
        if (isDevModeEnabled() && getView() != null && stateView != null) {
            loadCurrentUser();
            loadItems(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setupMainFab();
        if (isDevModeEnabled()) {
            loadCurrentUser();
        }
    }
}
