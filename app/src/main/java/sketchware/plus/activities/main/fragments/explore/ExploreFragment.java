package sketchware.plus.activities.main.fragments.explore;

import android.os.Bundle;
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
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import sketchware.plus.R;
import sketchware.plus.store.activities.LoginActivity;
import sketchware.plus.store.activities.ProfileActivity;
import sketchware.plus.store.activities.UploadActivity;
import sketchware.plus.store.adapters.StorePagerAdapter;
import sketchware.plus.store.auth.SessionManager;
import sketchware.plus.store.fragments.StorePageFragment;
import sketchware.plus.store.models.StoreUser;
import sketchware.plus.store.repository.RepositoryCallback;
import sketchware.plus.store.repository.RepositoryProvider;
import sketchware.plus.store.repository.StoreRepository;

public class ExploreFragment extends Fragment {

    private StoreRepository repository;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private StorePagerAdapter pagerAdapter;
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

    private StoreUser currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_explore_store, container, false);

        repository = RepositoryProvider.getRepository(requireContext());

        initViews(view);
        setupToolbar();
        setupViewPager();

        loadCurrentUser();

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
                                dispatchSearch(query);
                                return true;
                            }

                            @Override
                            public boolean onQueryTextChange(String newText) {
                                dispatchSearch(newText);
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
        viewPager = view.findViewById(R.id.view_pager);
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

    private void setupViewPager() {
        if (tabLayout == null || viewPager == null) return;
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

    private void dispatchSearch(String query) {
        if (pagerAdapter != null) {
            for (StorePageFragment fragment : pagerAdapter.getFragments().values()) {
                if (fragment != null) {
                    fragment.search(query);
                }
            }
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

    public void refresh() {
        setupMainFab();
        if (getView() != null) {
            loadCurrentUser();
            if (pagerAdapter != null) {
                for (StorePageFragment fragment : pagerAdapter.getFragments().values()) {
                    if (fragment != null) {
                        fragment.refresh();
                    }
                }
            }
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
