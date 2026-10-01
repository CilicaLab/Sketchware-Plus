package com.besome.sketch.tools;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import mod.hey.studios.util.CompileLogHelper;
import mod.hey.studios.util.CompileLogParser;
import sketchware.plus.ai.AiClient;
import sketchware.plus.databinding.CompileLogPageBinding;

public class CompileLogFragment extends Fragment {
    private static final String ARG_CATEGORY = "category";
    private int category;
    private CompileLogPageBinding binding;
    private CompileLogAdapter adapter;

    public static CompileLogFragment newInstance(int category) {
        CompileLogFragment fragment = new CompileLogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_CATEGORY, category);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            category = getArguments().getInt(ARG_CATEGORY, 0);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = CompileLogPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CompileLogViewModel viewModel = new ViewModelProvider(requireActivity()).get(CompileLogViewModel.class);

        adapter = new CompileLogAdapter(this::showErrorDetailsDialog);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        viewModel.getRawLogs().observe(getViewLifecycleOwner(), logs -> {
            String safeLogs = logs != null ? logs : "";
            String filtered = CompileLogHelper.filterLogs(safeLogs, category);
            List<CompileErrorItem> items = CompileLogParser.parseErrors(filtered);
            adapter.setItems(items);
        });

        viewModel.getMonospacedFont().observe(getViewLifecycleOwner(), adapter::setMonospaced);
        viewModel.getFontSize().observe(getViewLifecycleOwner(), adapter::setFontSize);
    }

    private void showErrorDetailsDialog(CompileErrorItem item) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.filePath + (item.lineNumber > 0 ? " (Line " + item.lineNumber + ")" : ""))
                .setMessage(item.rawBlock)
                .setPositiveButton("Close", null);

        if (AiClient.isAiEnabled(requireContext())) {
            builder.setNeutralButton("Explain with AI", (dialog, which) -> {
                if (requireActivity() instanceof CompileLogActivity activity) {
                    activity.explainErrorsWithSk(item.rawBlock);
                }
            });
        }

        builder.show();
    }
}
