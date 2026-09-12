package sketchware.plus.fragments.settings.appearance;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;

import a.a.a.qA;
import sketchware.plus.R;
import sketchware.plus.databinding.FragmentSettingsAppearanceBinding;
import sketchware.plus.utility.AppIconManager;
import sketchware.plus.utility.theme.ThemeManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.List;

public class SettingsAppearanceFragment extends qA {
    private FragmentSettingsAppearanceBinding binding;
    private MaterialCardView selectedThemeCard;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsAppearanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupToolbar();
        initializeThemeSettings();
        initializeIconSettings();
        setupClickListeners();

        {
            View view1 = binding.content;
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

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (requireActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
                requireActivity().getSupportFragmentManager().popBackStack();
            } else {
                requireActivity().onBackPressed();
            }
        });

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
    }

    private void initializeThemeSettings() {
        boolean isSystemTheme = ThemeManager.isSystemTheme(requireContext());
        binding.switchSystem.setChecked(isSystemTheme);

        updateThemeCardSelection(ThemeManager.getCurrentTheme(requireContext()));

        setThemeCardsEnabled(!isSystemTheme);
    }

    private void setupClickListeners() {
        binding.themeSystem.setOnClickListener(v -> binding.switchSystem.setChecked(!binding.switchSystem.isChecked()));

        binding.switchSystem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            unselectSelectedThemeCard();
            setThemeCardsEnabled(!isChecked);
            if (isChecked) {
                ThemeManager.applyTheme(requireContext(), ThemeManager.THEME_SYSTEM);
                return;
            }
            int theme = ThemeManager.getSystemAppliedTheme(requireContext());
            ThemeManager.applyTheme(requireContext(), theme);
            updateThemeCardSelection(theme);
        });

        binding.themeLight.setOnClickListener(v -> {
            if (!binding.switchSystem.isChecked()) {
                updateThemeCardSelection(ThemeManager.THEME_LIGHT);
                ThemeManager.applyTheme(requireContext(), ThemeManager.THEME_LIGHT);
            }
        });

        binding.themeDark.setOnClickListener(v -> {
            if (!binding.switchSystem.isChecked()) {
                updateThemeCardSelection(ThemeManager.THEME_DARK);
                ThemeManager.applyTheme(requireContext(), ThemeManager.THEME_DARK);
            }
        });
    }

    private void updateThemeCardSelection(int theme) {
        unselectSelectedThemeCard();

        MaterialCardView newSelection = switch (theme) {
            case ThemeManager.THEME_LIGHT -> binding.themeLight;
            case ThemeManager.THEME_DARK -> binding.themeDark;
            default -> null;
        };

        if (newSelection != null && !binding.switchSystem.isChecked()) {
            newSelection.setChecked(true);
            selectedThemeCard = newSelection;
        }
    }

    private void unselectSelectedThemeCard() {
        if (selectedThemeCard != null) {
            selectedThemeCard.setChecked(false);
            selectedThemeCard = null;
        }
    }

    private void setThemeCardsEnabled(boolean enabled) {
        binding.themeLight.setEnabled(enabled);
        binding.themeDark.setEnabled(enabled);

        float alpha = enabled ? 1.0f : 0.5f;
        binding.themeLight.animate().alpha(alpha).start();
        binding.themeDark.animate().alpha(alpha).start();
    }

    private void initializeIconSettings() {
        int selectedIcon = AppIconManager.getSelectedIcon(requireContext());
        AppIconAdapter adapter = new AppIconAdapter(selectedIcon);
        binding.iconRecycler.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        binding.iconRecycler.setAdapter(adapter);
    }

    private class AppIconAdapter extends RecyclerView.Adapter<AppIconAdapter.ViewHolder> {
        private int selectedIndex;

        AppIconAdapter(int selectedIndex) {
            this.selectedIndex = selectedIndex;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_icon_selection, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            holder.icon.setImageResource(AppIconManager.ICON_DRAWABLES[pos]);
            holder.name.setText(AppIconManager.ICON_NAMES[pos]);
            holder.card.setChecked(pos == selectedIndex);
            holder.itemView.setOnClickListener(v -> {
                int currentPos = holder.getBindingAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION && selectedIndex != currentPos) {
                    int oldIndex = selectedIndex;
                    selectedIndex = currentPos;
                    notifyItemChanged(oldIndex);
                    notifyItemChanged(selectedIndex);
                    AppIconManager.switchIcon(requireContext(), currentPos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return AppIconManager.ICON_NAMES.length;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView card;
            ImageView icon;
            TextView name;

            ViewHolder(View itemView) {
                super(itemView);
                card = (MaterialCardView) itemView;
                icon = itemView.findViewById(R.id.icon);
                name = itemView.findViewById(R.id.name);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}