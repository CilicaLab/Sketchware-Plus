package mod.hilal.saif.asd;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.content.res.AppCompatResources;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import a.a.a.Lx;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.util.Helper;
import sketchware.plus.R;
import sketchware.plus.databinding.CodeEditorHsBinding;
import sketchware.plus.utility.EditorUtils;
import sketchware.plus.utility.SketchwareUtil;
import sketchware.plus.utility.UI;

public class AsdCodeEditorActivity extends BaseAppCompatActivity {
    private String beforeContent = "";
    private CodeEditorHsBinding binding;
    private SharedPreferences pref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = CodeEditorHsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String title = getIntent().getStringExtra("title");
        if (title == null || title.isEmpty()) {
            title = "Add Source Directly";
        }

        String initialContent = getIntent().getStringExtra("content");
        if (initialContent != null) {
            beforeContent = initialContent;
        }

        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setTextSize(16);
        binding.editor.setText(beforeContent);
        binding.editor.setEditorLanguage(new JavaLanguage());

        SrcCodeEditor.loadCESettings(this, binding.editor, "act", true);
        pref = SrcCodeEditor.pref;

        loadToolbar(title);

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);
    }

    public void save() {
        String newContent = binding.editor.getText().toString();
        beforeContent = newContent;

        Intent resultIntent = new Intent();
        resultIntent.putExtra("content", newContent);
        setResult(RESULT_OK, resultIntent);

        SketchwareUtil.toast("Saved");
        finish();
    }

    @Override
    public void onBackPressed() {
        if (beforeContent.equals(binding.editor.getText().toString())) {
            super.onBackPressed();
        } else {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
            dialog.setIcon(R.drawable.ic_warning_96dp);
            dialog.setTitle(Helper.getResString(R.string.common_word_warning));
            dialog.setMessage(Helper.getResString(R.string.src_code_editor_unsaved_changes_dialog_warning_message));

            dialog.setPositiveButton(Helper.getResString(R.string.common_word_exit), (v, which) -> {
                v.dismiss();
                finish();
            });
            dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
            dialog.show();
        }
    }

    private void loadToolbar(String title) {
        binding.toolbar.setTitle(title);
        binding.toolbar.setNavigationIcon(AppCompatResources.getDrawable(this, R.drawable.abc_ic_ab_back_material));
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        SharedPreferences local_pref = getSharedPreferences("hsce", Activity.MODE_PRIVATE);
        Menu toolbarMenu = binding.toolbar.getMenu();
        toolbarMenu.clear();

        toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Undo")
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_undo))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Redo")
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_redo))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Save")
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_save))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Find & Replace");
        toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Word wrap")
                .setCheckable(true)
                .setChecked(local_pref.getBoolean("act_ww", false));

        toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Pretty print");
        toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Select language");
        toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Select theme");
        toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Auto complete")
                .setCheckable(true)
                .setChecked(local_pref.getBoolean("act_ac", true));

        toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Auto complete symbol pair")
                .setCheckable(true)
                .setChecked(local_pref.getBoolean("act_acsp", true));

        binding.toolbar.setOnMenuItemClickListener(item -> {
            String itemTitle = item.getTitle().toString();
            switch (itemTitle) {
                case "Undo":
                    binding.editor.undo();
                    break;

                case "Redo":
                    binding.editor.redo();
                    break;

                case "Save":
                    save();
                    break;

                case "Pretty print":
                    StringBuilder sb = new StringBuilder();
                    for (String line : binding.editor.getText().toString().split("\n")) {
                        String trim = (line + "X").trim();
                        sb.append(trim.substring(0, trim.length() - 1)).append("\n");
                    }
                    boolean err = false;
                    String code = sb.toString();
                    try {
                        code = Lx.j(code, true);
                    } catch (Exception e) {
                        err = true;
                        SketchwareUtil.toastError("Your code contains incorrectly nested parentheses");
                    }
                    if (!err) binding.editor.setText(code);
                    break;

                case "Select language":
                    SrcCodeEditor.showSwitchLanguageDialog(this, binding.editor, (dialog, which) -> {
                        SrcCodeEditor.selectLanguage(binding.editor, which);
                        dialog.dismiss();
                    });
                    break;

                case "Find & Replace":
                    binding.editor.getSearcher().stopSearch();
                    binding.editor.beginSearchMode();
                    binding.editor.postDelayed(() -> {
                        View decor = getWindow().getDecorView();
                        View searchSrcText = null;
                        int resId = getResources().getIdentifier("search_src_text", "id", "android");
                        if (resId != 0) {
                            searchSrcText = decor.findViewById(resId);
                        }
                        if (searchSrcText == null) {
                            searchSrcText = decor.findViewById(R.id.search_src_text);
                        }
                        if (searchSrcText != null) {
                            searchSrcText.setFocusableInTouchMode(true);
                            searchSrcText.requestFocus();
                            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.showSoftInput(searchSrcText, InputMethodManager.SHOW_IMPLICIT);
                            }
                        }
                    }, 250);
                    break;

                case "Select theme":
                    SrcCodeEditor.showSwitchThemeDialog(this, binding.editor, (dialog, which) -> {
                        SrcCodeEditor.selectTheme(binding.editor, which);
                        if (pref != null) {
                            pref.edit().putInt("act_theme", which).apply();
                        }
                        dialog.dismiss();
                    });
                    break;

                case "Word wrap":
                    item.setChecked(!item.isChecked());
                    binding.editor.setWordwrap(item.isChecked());
                    if (pref != null) {
                        pref.edit().putBoolean("act_ww", item.isChecked()).apply();
                    }
                    break;

                case "Auto complete symbol pair":
                    item.setChecked(!item.isChecked());
                    binding.editor.getProps().symbolPairAutoCompletion = item.isChecked();
                    if (pref != null) {
                        pref.edit().putBoolean("act_acsp", item.isChecked()).apply();
                    }
                    break;

                case "Auto complete":
                    item.setChecked(!item.isChecked());
                    binding.editor.getComponent(EditorAutoCompletion.class).setEnabled(item.isChecked());
                    if (pref != null) {
                        pref.edit().putBoolean("act_ac", item.isChecked()).apply();
                    }
                    break;

                default:
                    return false;
            }
            return true;
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (pref != null) {
            float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
            pref.edit().putInt("act_ts", (int) (binding.editor.getTextSizePx() / scaledDensity)).apply();
        }
    }
}
