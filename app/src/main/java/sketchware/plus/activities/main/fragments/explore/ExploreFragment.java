package sketchware.plus.activities.main.fragments.explore;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
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

    private StoreRepository repository;
    private StoreItemAdapter adapter;
    private StateView stateView;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private TabLayout tabLayout;
    private MaterialToolbar toolbar;
    private MenuProvider menuProvider;
    private boolean isMenuAdded = false;

    private void addMenuProviderSafely() {
        if (getActivity() != null && menuProvider != null) {
            try {
                requireActivity().removeMenuProvider(menuProvider);
            } catch (Exception ignored) {}
            try {
                requireActivity().addMenuProvider(menuProvider);
                isMenuAdded = true;
            } catch (Exception ignored) {}
        }
    }

    private void removeMenuProviderSafely() {
        if (getActivity() != null && menuProvider != null) {
            try {
                requireActivity().removeMenuProvider(menuProvider);
                isMenuAdded = false;
            } catch (Exception ignored) {}
        }
    }

    private String currentType = "ALL";
    private String currentQuery = "";
    private String currentSortBy = "newest";
    private StoreUser currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_explore_store, container, false);

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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupMainFab();

        menuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_store, menu);

                MenuItem searchItem = menu.findItem(R.id.action_search);
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
                updateMenuVisibility(menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.action_profile) {
                    ProfileActivity.start(requireContext());
                    return true;
                } else if (id == R.id.action_login) {
                    LoginActivity.start(requireContext());
                    return true;
                }
                return false;
            }
        };

        addMenuProviderSafely();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        removeMenuProviderSafely();
    }

    public void setupMainFab() {
        if (getActivity() != null) {
            ExtendedFloatingActionButton fab = getActivity().findViewById(R.id.create_new_project);
            if (fab != null) {
                fab.setText("Upload Pack");
                fab.setIconResource(R.drawable.ic_add_white_24dp);
                fab.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    handleUploadPackClick();
                });
                fab.show();
                fab.extend();
            }
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (getActivity() == null) return;
        if (hidden) {
            removeMenuProviderSafely();
        } else {
            addMenuProviderSafely();
            setupMainFab();
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void initViews(View view) {
        toolbar = requireActivity().findViewById(R.id.explore_toolbar);
        tabLayout = requireActivity().findViewById(R.id.explore_tab_layout);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        recyclerView = view.findViewById(R.id.recycler_view);
        stateView = view.findViewById(R.id.state_view);
    }

    private void setupToolbar() {
        if (toolbar == null) return;
        toolbar.setNavigationIcon(null);
    }

    private void updateMenuVisibility(Menu menu) {
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
    }

    private void handleUploadPackClick() {
        if (!isLoggedIn()) {
            Toast.makeText(requireContext(), "Please log in to upload packs", Toast.LENGTH_SHORT).show();
            LoginActivity.start(requireContext());
            return;
        }

        if (repository == null) {
            repository = RepositoryProvider.getRepository(requireContext());
        }

        repository.getCurrentUser(new RepositoryCallback<StoreUser>() {
            @Override
            public void onSuccess(StoreUser user) {
                if (user == null) {
                    Toast.makeText(requireContext(), "Please log in to upload packs", Toast.LENGTH_SHORT).show();
                    LoginActivity.start(requireContext());
                } else if (user.isBanned()) {
                    Toast.makeText(requireContext(), "Your account has been suspended by an administrator.", Toast.LENGTH_LONG).show();
                } else {
                    UploadActivity.start(requireContext());
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(requireContext(), "Something went wrong, please try again", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isLoggedIn() {
        if (SessionManager.getInstance(requireContext()).isLoggedIn()) {
            return true;
        }
        return !RepositoryProvider.isUseSupabase() && currentUser != null;
    }

    private void loadCurrentUser() {
        if (repository == null) {
            repository = RepositoryProvider.getRepository(requireContext());
        }
        if (repository == null) return;

        repository.getCurrentUser(new RepositoryCallback<StoreUser>() {
            @Override
            public void onSuccess(StoreUser user) {
                currentUser = user;
                if (getActivity() != null) {
                    getActivity().invalidateOptionsMenu();
                }
            }

            @Override
            public void onError(String error) {
                currentUser = null;
                if (getActivity() != null) {
                    getActivity().invalidateOptionsMenu();
                }
            }
        });
    }

    private void loadItems(boolean showStateLoading) {
        if (repository == null) {
            repository = RepositoryProvider.getRepository(requireContext());
        }
        if (repository == null || stateView == null) return;

        if (showStateLoading) {
            stateView.showLoading("Fetching store items...");
        } else if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
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
            }

            @Override
            public void onError(String error) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                stateView.showError(error, v -> loadItems(true));
            }
        });
    }

    public void refresh() {
        setupMainFab();
        if (getView() != null && stateView != null) {
            loadCurrentUser();
            loadItems(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) {
            setupMainFab();
            loadCurrentUser();
        }
    }
}
