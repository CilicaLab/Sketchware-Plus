package sketchware.plus.security;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import mod.hey.studios.project.stringfog.StringfogHandler;
import sketchware.plus.databinding.FragmentSecurityGuardManagerBinding;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.apk.ApkSignatures;
import sketchware.plus.utility.apk.ApkUtils;

public class ManageSecurityGuardFragment extends BottomSheetDialogFragment {

    private FragmentSecurityGuardManagerBinding binding;
    private SecurityGuardHandler handler;
    private String scId;

    // Standard Sketchware Testkey SHA-256 (Commonly known)
    private static final String TESTKEY_SHA256 = "28F04BB2D60300E6B51D64D06E9A805F6230D609905E216E9F80C7A99DE19209";

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSecurityGuardManagerBinding.inflate(inflater, container, false);
        initializeLogic();
        return binding.getRoot();
    }

    private void initializeLogic() {
        scId = requireActivity().getIntent().getStringExtra("sc_id");
        handler = new SecurityGuardHandler(scId);
        final StringfogHandler stringfogHandler = new StringfogHandler(scId);

        binding.swStringfog.setChecked(stringfogHandler.isStringfogEnabled());
        binding.swStringfog.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                stringfogHandler.setStringfogEnabled(isChecked);
            }
        });

        setupToggle(binding.swPackageLock, "package_lock");
        setupToggle(binding.swSignatureCheck, "signature_check");
        setupToggle(binding.swAutoSignatureSync, "auto_signature_sync");
        setupToggle(binding.swRootDetection, "root_detection");
        setupToggle(binding.swEmulatorDetection, "emulator_detection");
        setupToggle(binding.swAntiDebug, "anti_debug");

        updateUI();

        binding.btnViewSignature.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewSignatureDetails();
            }
        });
    }

    private void setupToggle(MaterialSwitch sw, final String key) {
        sw.setChecked(handler.isFeatureEnabled(key));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                handler.setFeatureValue(key, isChecked);
                updateUI();
            }
        });
    }

    private void updateUI() {
        // Package Info
        HashMap<String, Object> projectInfo = lC.b(scId);
        String pkgName = yB.c(projectInfo, "my_sc_pkg_name");
        binding.tvLockedPackage.setText("Package: " + pkgName);
        binding.tvLockedPackage.setAlpha(handler.isFeatureEnabled("package_lock") ? 1.0f : 0.5f);

        // Signature Info
        boolean autoSync = handler.isFeatureEnabled("auto_signature_sync");
        String locked = handler.getFeatureString("expected_signature");
        
        if (autoSync) {
            binding.tvCurrentSignature.setText("Signature: Auto-Syncing on build");
            binding.tvCurrentSignature.setAlpha(0.6f);
        } else {
            if (locked.isEmpty()) {
                binding.tvCurrentSignature.setText("Signature: Not locked");
            } else {
                binding.tvCurrentSignature.setText("Locked: " + (locked.length() > 8 ? locked.substring(0, 8) : locked) + "...");
            }
            binding.tvCurrentSignature.setAlpha(1.0f);
        }

        // Show warning if using Testkey
        if (locked.equals(TESTKEY_SHA256)) {
            binding.layoutTestkeyWarning.setVisibility(View.VISIBLE);
        } else {
            binding.layoutTestkeyWarning.setVisibility(View.GONE);
        }
    }

    private void viewSignatureDetails() {
        String binDir = wq.d(scId) + File.separator + "bin";
        List<String> files = FileUtil.listFiles(binDir, "apk");
        String apkPath = null;
        for (String file : files) {
            if (file.endsWith(".apk") && !file.endsWith(".unsigned") && !file.endsWith(".aligned")) {
                apkPath = file;
                break;
            }
        }

        if (apkPath != null) {
            ApkSignatures apkSignatures = new ApkSignatures(requireContext(), apkPath);
            apkSignatures.showSignaturesDialog();
        } else {
            Toast.makeText(getContext(), "Please build the project once first to view signatures!", Toast.LENGTH_LONG).show();
        }
    }
}
