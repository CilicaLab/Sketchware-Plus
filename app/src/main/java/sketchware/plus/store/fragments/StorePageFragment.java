package sketchware.plus.store.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import sketchware.plus.R;
import sketchware.plus.store.activities.ItemDetailActivity;
import sketchware.plus.store.adapters.StoreItemAdapter;
import sketchware.plus.store.models.StoreItem;
import sketchware.plus.store.repository.RepositoryCallback;
import sketchware.plus.store.repository.RepositoryProvider;
import sketchware.plus.store.repository.StoreRepository;
import sketchware.plus.store.widget.StateView;

public class StorePageFragment extends Fragment {
    private static final String ARG_TYPE = "arg_type";
    private String itemType;
    private StoreRepository repository;
    private StoreItemAdapter adapter;
    private StateView stateView;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private String currentQuery = "";

    public static StorePageFragment newInstance(String type) {
        StorePageFragment fragment = new StorePageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            itemType = getArguments().getString(ARG_TYPE, "ALL");
        }
        repository = RepositoryProvider.getRepository(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_store_page, container, false);
        initViews(view);
        setupRecyclerView();
        setupListeners();
        loadItems(true);
        return view;
    }

    private void initViews(View view) {
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        recyclerView = view.findViewById(R.id.recycler_view);
        stateView = view.findViewById(R.id.state_view);
    }

    private void setupRecyclerView() {
        adapter = new StoreItemAdapter(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

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
        swipeRefresh.setColorSchemeResources(R.color.color_primary);
        swipeRefresh.setOnRefreshListener(() -> loadItems(false));
    }

    public void search(String query) {
        currentQuery = query != null ? query : "";
        loadItems(false);
    }

    public void refresh() {
        loadItems(true);
    }

    private void loadItems(boolean showStateLoading) {
        if (repository == null) {
            repository = RepositoryProvider.getRepository(requireContext());
        }
        if (repository == null || stateView == null) return;

        if (showStateLoading) {
            stateView.showLoading("Fetching store items...");
        } else {
            swipeRefresh.setRefreshing(true);
        }

        repository.getItems(itemType, currentQuery, "newest", new RepositoryCallback<List<StoreItem>>() {
            @Override
            public void onSuccess(List<StoreItem> items) {
                swipeRefresh.setRefreshing(false);
                List<StoreItem> safeItems = items != null ? items : new ArrayList<>();
                adapter.setItems(safeItems);
                if (safeItems.isEmpty()) {
                    String label = "ALL".equalsIgnoreCase(itemType) || itemType == null ? "item" : itemType.toLowerCase();
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
                swipeRefresh.setRefreshing(false);
                stateView.showError(error, v -> loadItems(true));
            }
        });
    }
}
