package com.besome.sketch.help;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import mod.hey.studios.util.Helper;
import sketchware.plus.R;
import sketchware.plus.databinding.PreferenceActivityBinding;

public class SystemSettingActivity extends BaseAppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        var binding = PreferenceActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.topAppBar.setTitle(R.string.main_drawer_title_system_settings);
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        getSupportFragmentManager().beginTransaction()
                .replace(binding.fragmentContainer.getId(), new PreferenceFragment())
                .commit();

        {
            View view1 = binding.appBarLayout;
            int left = view1.getPaddingLeft();
            int top = view1.getPaddingTop();
            int right = view1.getPaddingRight();
            int bottom = view1.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view1, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top + insets.top, right + insets.right, bottom);
                return i;
            });
        }

        {
            View view1 = binding.fragmentContainer;
            int left = view1.getPaddingLeft();
            int top = view1.getPaddingTop();
            int right = view1.getPaddingRight();
            int bottom = view1.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view1, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top, right + insets.right, bottom + insets.bottom);
                return i;
            });
        }
    }

    public static class PreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            getPreferenceManager().setSharedPreferencesName("P12");
            var prefs = getPreferenceManager().getSharedPreferences();

            if (prefs != null) {
                String[] aiKeys = {"P12I7", "P12I8", "P12I9"};
                for (String key : aiKeys) {
                    if (prefs.contains(key)) {
                        try {
                            // Try to get as string, if it fails it might be the old int
                            prefs.getString(key, "");
                        } catch (ClassCastException e) {
                            int oldVal = prefs.getInt(key, 20);
                            prefs.edit().putString(key, String.valueOf(oldVal)).apply();
                        }
                    }
                }
            }
            setPreferencesFromResource(R.xml.preferences_system_settings, rootKey);

            setupAiFocusPreference("P12I7");
            setupAiFocusPreference("P12I8");
            setupAiFocusPreference("P12I9");
        }

        private void setupAiFocusPreference(String key) {
            Preference pref = findPreference(key);
            if (pref == null) return;

            updateAiFocusSummary(pref);
            pref.setOnPreferenceClickListener(p -> {
                showAiFocusDialog(pref);
                return true;
            });
        }

        private void updateAiFocusSummary(Preference pref) {
            if (getPreferenceManager().getSharedPreferences() == null) return;
            String value = getPreferenceManager().getSharedPreferences().getString(pref.getKey(), "20");
            String[] entries = getResources().getStringArray(R.array.ai_focus_entries);
            String[] values = getResources().getStringArray(R.array.ai_focus_values);

            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(value)) {
                    pref.setSummary(entries[i]);
                    return;
                }
            }
        }

        private void showAiFocusDialog(Preference pref) {
            String[] entries = getResources().getStringArray(R.array.ai_focus_entries);
            String[] descriptions = getResources().getStringArray(R.array.ai_focus_descriptions);
            String[] values = getResources().getStringArray(R.array.ai_focus_values);

            var adapter = new ArrayAdapter<String>(requireContext(), R.layout.item_ai_focus_selection, R.id.title, entries) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView desc = view.findViewById(R.id.description);
                    desc.setText(descriptions[position]);
                    return view;
                }
            };

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Model Thinking Temperature")
                    .setAdapter(adapter, (dialog, which) -> {
                        if (getPreferenceManager().getSharedPreferences() != null) {
                            getPreferenceManager().getSharedPreferences().edit()
                                    .putString(pref.getKey(), values[which])
                                    .apply();
                            updateAiFocusSummary(pref);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }
}
