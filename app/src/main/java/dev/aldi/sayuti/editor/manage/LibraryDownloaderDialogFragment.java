package dev.aldi.sayuti.editor.manage;

import static dev.aldi.sayuti.editor.manage.LocalLibrariesUtil.createLibraryMap;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.Gson;

import org.cosmic.ide.dependency.resolver.api.Artifact;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;
import sketchware.plus.R;
import sketchware.plus.databinding.LibraryDownloaderDialogBinding;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.SketchwareUtil;

public class LibraryDownloaderDialogFragment extends BottomSheetDialogFragment {
    private LibraryDownloaderDialogBinding binding;

    private final Gson gson = new Gson();
    private BuildSettings buildSettings;

    private boolean notAssociatedWithProject;
    private String dependencyName;
    private String localLibFile;
    private OnLibraryDownloadedTask onLibraryDownloadedTask;

    private ExecutorService executorService;
    private DependencyResolver currentResolver;
    private volatile boolean isCancelled = false;

    private Runnable dexingAnimationRunnable;
    private int currentDexingProgress = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LibraryDownloaderDialogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() == null) return;

        notAssociatedWithProject = getArguments().getBoolean("notAssociatedWithProject", false);
        buildSettings = (BuildSettings) getArguments().getSerializable("buildSettings");
        localLibFile = getArguments().getString("localLibFile");

        executorService = Executors.newSingleThreadExecutor();

        binding.btnCancel.setOnClickListener(v -> cancelDownload());
        binding.btnRetry.setOnClickListener(v -> initDownloadFlow());
        binding.btnClose.setOnClickListener(v -> dismiss());
        binding.btnDownload.setOnClickListener(v -> initDownloadFlow());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isCancelled = true;
        stopDexingProgressAnimation();
        if (currentResolver != null) {
            currentResolver.cancel();
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
        binding = null;
    }

    public void setOnLibraryDownloadedTask(OnLibraryDownloadedTask onLibraryDownloadedTask) {
        this.onLibraryDownloadedTask = onLibraryDownloadedTask;
    }

    private boolean isUiAlive() {
        return !isCancelled && isAdded() && binding != null && getActivity() != null;
    }

    private void cancelDownload() {
        isCancelled = true;
        stopDexingProgressAnimation();
        if (currentResolver != null) {
            currentResolver.cancel();
        }
        setErrorState(getString(R.string.lib_downloader_cancelled));
    }

    private void startDexingProgressAnimation(Handler handler, String artifactId, int totalClasses) {
        stopDexingProgressAnimation();
        currentDexingProgress = 10;

        if (binding != null) {
            binding.tvDexingLabel.setVisibility(View.VISIBLE);
            binding.dexingProgressIndicator.setVisibility(View.VISIBLE);
            binding.dexingProgressIndicator.setIndeterminate(false);
            binding.dexingProgressIndicator.setProgress(currentDexingProgress, true);

            if (totalClasses > 0) {
                binding.tvDexingLabel.setText(getString(R.string.lib_downloader_dexing, artifactId, 0, totalClasses));
            } else {
                binding.tvDexingLabel.setText(getString(R.string.lib_downloader_dexing_indeterminate, artifactId));
            }
        }

        dexingAnimationRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isUiAlive() || binding == null) return;
                if (currentDexingProgress < 92) {
                    int step = Math.max(1, (92 - currentDexingProgress) / 8);
                    currentDexingProgress += step;
                    binding.dexingProgressIndicator.setProgress(currentDexingProgress, true);
                    handler.postDelayed(this, 180);
                }
            }
        };
        handler.postDelayed(dexingAnimationRunnable, 180);
    }

    private void stopDexingProgressAnimation() {
        if (dexingAnimationRunnable != null) {
            if (binding != null) {
                binding.getRoot().getHandler();
            }
            dexingAnimationRunnable = null;
        }
    }

    private void initDownloadFlow() {
        dependencyName = Helper.getText(binding.dependencyInput);
        if (dependencyName == null || dependencyName.isEmpty()) {
            binding.dependencyInputLayout.setError(getString(R.string.lib_downloader_err_please_enter));
            binding.dependencyInputLayout.setErrorEnabled(true);
            return;
        }

        var parts = dependencyName.split(":");
        if (parts.length != 3) {
            binding.dependencyInputLayout.setError(getString(R.string.lib_downloader_err_invalid_format));
            binding.dependencyInputLayout.setErrorEnabled(true);
            return;
        }

        binding.dependencyInputLayout.setErrorEnabled(false);
        setDownloadState();

        isCancelled = false;
        var group = parts[0];
        var artifact = parts[1];
        var version = parts[2];
        currentResolver = new DependencyResolver(group, artifact, version, binding.cbSkipSubdependencies.isChecked(), buildSettings);
        var handler = new Handler(Looper.getMainLooper());

        executorService.submit(() -> {
            try {
                BuiltInLibraries.maybeExtractAndroidJar((message, progress) -> handler.post(() -> {
                    if (isUiAlive()) {
                        binding.dependencyInfo.setText(message);
                    }
                }));
                BuiltInLibraries.maybeExtractCoreLambdaStubsJar();

                if (!isUiAlive()) return;

                currentResolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                    private String lastRepo = "";
                    private final StringBuilder searchStatus = new StringBuilder();

                    @Override
                    public void onSearchingRepository(@NonNull String repoName) {
                        handler.post(() -> {
                            if (!isUiAlive()) return;
                            if (!repoName.equals(lastRepo)) {
                                if (!lastRepo.isEmpty()) {
                                    searchStatus.append(lastRepo).append(" - Not Found\n");
                                }
                                lastRepo = repoName;
                                binding.dependencyInfo.setText(searchStatus.toString() + getString(R.string.lib_downloader_searching));
                                binding.tvDownloadLabel.setText(R.string.lib_downloader_searching);
                                binding.downloadProgressIndicator.setIndeterminate(true);
                            }
                        });
                    }

                    @Override
                    public void artifactFound(@NonNull Artifact artifact) {
                        handler.post(() -> {
                            if (!isUiAlive()) return;
                            String repoName = artifact.getRepository() != null ? artifact.getRepository().getName() : "Cache";
                            searchStatus.append(repoName).append(" - Found\n");
                            lastRepo = "";
                            binding.dependencyInfo.setText(searchStatus.toString());
                        });
                    }

                    @Override
                    public void onDownloadStart(@NonNull Artifact dep) {
                        handler.post(() -> {
                            if (!isUiAlive()) return;
                            String artifactName = dep.getArtifactId() + ":" + dep.getVersion();
                            binding.tvDownloadLabel.setText(getString(R.string.lib_downloader_downloading_file, artifactName));
                            binding.downloadProgressIndicator.setIndeterminate(true);
                            binding.tvDownloadDetails.setText("");
                            binding.tvDownloadSize.setText("");
                            binding.dependencyInfo.setText(searchStatus.toString() + "Downloading " + dep + "...");
                        });
                    }

                    @Override
                    public void onDownloadProgress(@NonNull Artifact dep, long bytesRead, long totalBytes) {
                        handler.post(() -> {
                            if (!isUiAlive()) return;
                            if (getContext() == null) return;

                            String readFormatted = Formatter.formatShortFileSize(getContext(), bytesRead);
                            if (totalBytes > 0) {
                                int percent = (int) ((bytesRead * 100) / totalBytes);
                                String totalFormatted = Formatter.formatShortFileSize(getContext(), totalBytes);

                                binding.downloadProgressIndicator.setIndeterminate(false);
                                binding.downloadProgressIndicator.setProgress(percent, true);
                                binding.tvDownloadDetails.setText(percent + "%");
                                binding.tvDownloadSize.setText(getString(R.string.lib_downloader_size_formatted, readFormatted, totalFormatted));
                            } else {
                                binding.downloadProgressIndicator.setIndeterminate(true);
                                binding.tvDownloadDetails.setText("");
                                binding.tvDownloadSize.setText(readFormatted);
                            }
                        });
                    }

                    @Override
                    public void onDownloadEnd(@NonNull Artifact dep) {
                        handler.post(() -> {
                            if (!isUiAlive()) return;
                            binding.downloadProgressIndicator.setIndeterminate(false);
                            binding.downloadProgressIndicator.setProgress(100, true);
                            binding.tvDownloadDetails.setText("100%");
                        });
                    }

                    @Override
                    public void onDownloadError(@NonNull Artifact dep, @NonNull Throwable e) {
                        handler.post(() -> {
                            if (!isUiAlive()) return;
                            stopDexingProgressAnimation();
                            setErrorState(dep + ": " + e.getMessage());
                        });
                    }

                    @Override
                    public void unzipping(@NonNull Artifact artifact) {
                        handler.post(() -> {
                            if (!isUiAlive()) return;
                            binding.tvDownloadLabel.setText(getString(R.string.lib_downloader_unzipping, artifact.getArtifactId()));
                            binding.downloadProgressIndicator.setIndeterminate(true);
                        });
                    }

                    @Override
                    public void dexing(@NonNull Artifact dep) {
                        handler.post(() -> {
                            if (!isUiAlive()) return;
                            startDexingProgressAnimation(handler, dep.getArtifactId(), -1);
                        });
                    }

                    @Override
                    public void onDexingProgress(@NonNull Artifact dep, int processed, int total) {
                        handler.post(() -> {
                            if (!isUiAlive()) return;
                            if (processed == 0) {
                                startDexingProgressAnimation(handler, dep.getArtifactId(), total);
                            } else if (total > 0 && processed >= total) {
                                stopDexingProgressAnimation();
                                binding.tvDexingLabel.setVisibility(View.VISIBLE);
                                binding.dexingProgressIndicator.setVisibility(View.VISIBLE);
                                binding.dexingProgressIndicator.setIndeterminate(false);
                                binding.dexingProgressIndicator.setProgress(100, true);
                                binding.tvDexingLabel.setText(getString(R.string.lib_downloader_dexing, dep.getArtifactId(), total, total));
                            }
                        });
                    }

                    @Override
                    public void dexingFailed(@NonNull Artifact dependency, @NonNull Exception e) {
                        handler.post(() -> {
                            if (!isUiAlive()) return;
                            stopDexingProgressAnimation();
                            setErrorState("Dexing failed for " + dependency + ": " + e.getMessage());
                        });
                    }

                    @Override
                    public void onArtifactNotFound(@NonNull Artifact dep) {
                        handler.post(() -> {
                            if (!isUiAlive()) return;
                            stopDexingProgressAnimation();
                            if (!lastRepo.isEmpty()) {
                                searchStatus.append(lastRepo).append(" - Not Found\n");
                            }
                            setErrorState("Dependency '" + dep + "' not found in configured repositories:\n\n" + searchStatus.toString());
                        });
                    }

                    @Override
                    public void onTaskCompleted(@NonNull List<String> dependencies) {
                        handler.post(() -> {
                            if (!isUiAlive()) return;
                            stopDexingProgressAnimation();
                            binding.dexingProgressIndicator.setIndeterminate(false);
                            binding.dexingProgressIndicator.setProgress(100, true);
                            binding.tvDexingLabel.setText(R.string.lib_downloader_dexing_complete);

                            SketchwareUtil.toast(getString(R.string.lib_downloader_success));
                            if (!notAssociatedWithProject) {
                                binding.dependencyInfo.setText(R.string.lib_downloader_adding_deps);
                                var fileContent = FileUtil.readFile(localLibFile);
                                var enabledLibs = gson.fromJson(fileContent, Helper.TYPE_MAP_LIST);
                                enabledLibs.addAll(dependencies.stream()
                                        .map(name -> createLibraryMap(name, dependencyName))
                                        .toList());
                                FileUtil.writeFile(localLibFile, gson.toJson(enabledLibs));
                            }
                            dismiss();
                            if (onLibraryDownloadedTask != null) onLibraryDownloadedTask.invoke();
                        });
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (!isUiAlive()) return;
                    stopDexingProgressAnimation();
                    setErrorState(e.getMessage());
                });
            }
        });
    }

    private void setDownloadState() {
        binding.btnCancel.setVisibility(View.VISIBLE);
        binding.btnRetry.setVisibility(View.GONE);
        binding.btnClose.setVisibility(View.GONE);
        binding.btnDownload.setVisibility(View.GONE);
        binding.dependencyInput.setEnabled(false);
        binding.cbSkipSubdependencies.setEnabled(false);
        binding.progressContainer.setVisibility(View.VISIBLE);

        binding.downloadProgressIndicator.setVisibility(View.VISIBLE);
        binding.downloadProgressIndicator.setIndeterminate(true);
        binding.tvDownloadLabel.setText(R.string.lib_downloader_searching);
        binding.tvDownloadDetails.setText("");
        binding.tvDownloadSize.setText("");

        binding.tvDexingLabel.setVisibility(View.GONE);
        binding.dexingProgressIndicator.setVisibility(View.GONE);

        setCancelable(false);
    }

    private void setErrorState(String errorMessage) {
        if (binding == null) return;
        stopDexingProgressAnimation();
        binding.btnCancel.setVisibility(View.GONE);
        binding.btnDownload.setVisibility(View.GONE);
        binding.btnRetry.setVisibility(View.VISIBLE);
        binding.btnClose.setVisibility(View.VISIBLE);
        binding.dependencyInput.setEnabled(true);
        binding.cbSkipSubdependencies.setEnabled(true);
        setCancelable(true);

        binding.tvDownloadLabel.setText(R.string.lib_downloader_failed);
        binding.downloadProgressIndicator.setVisibility(View.GONE);
        binding.tvDexingLabel.setVisibility(View.GONE);
        binding.dexingProgressIndicator.setVisibility(View.GONE);

        binding.dependencyInfo.setText(errorMessage);
    }

    public interface OnLibraryDownloadedTask {
        void invoke();
    }
}
