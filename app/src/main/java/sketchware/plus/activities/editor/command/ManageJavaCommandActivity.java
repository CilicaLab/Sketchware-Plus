package sketchware.plus.activities.editor.command;

import static sketchware.plus.utility.GsonUtils.getGson;
import static sketchware.plus.utility.SketchwareUtil.getDip;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Executors;

import a.a.a.Jx;
import a.a.a.hC;
import a.a.a.jC;
import a.a.a.mB;
import a.a.a.wq;
import a.a.a.yq;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.component.Magnifier;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.blocks.CommandBlock;
import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages;
import sketchware.plus.R;
import sketchware.plus.activities.editor.command.adapters.XMLCommandAdapter;
import sketchware.plus.databinding.ManageXmlCommandAddBinding;
import sketchware.plus.databinding.ManageXmlCommandBinding;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.SketchwareUtil;
import sketchware.plus.utility.ThemeUtils;
import sketchware.plus.utility.UI;

public class ManageJavaCommandActivity extends BaseAppCompatActivity {

    private static final String[] COMMANDS_ACTION = {
            "insert", "add", "replace", "find-replace", "find-replace-first", "find-replace-all"
    };
    private String sc_id;
    private String commandPath;
    private XMLCommandAdapter adapter;
    private ArrayList<HashMap<String, Object>> commands = new ArrayList<>();
    private ArrayList<String> javaFiles;

    public static void fetchJavaCommand(Context context, String sc_id) {
        var path = wq.b(sc_id) + "/java_command";
        if (FileUtil.isExistFile(path)) {
            return;
        }
        var yq = new yq(context, sc_id);
        var projectLibraryManager = jC.c(sc_id);
        var projectFileManager = jC.b(sc_id);
        var projectDataManager = jC.a(sc_id);
        yq.a(projectLibraryManager, projectFileManager, projectDataManager);
        CommandBlock.x();
        ArrayList<ProjectFileBean> files = new ArrayList<>(projectFileManager.b());
        files.addAll(new ArrayList<>(projectFileManager.c()));
        for (ProjectFileBean file : files) {
            CommandBlock.CB(new Jx(yq.N, file, projectDataManager).generateCode(false, sc_id));
        }
        String commandPath = FileUtil.getExternalStorageDir().concat("/.sketchware/temp/commands");
        if (FileUtil.isExistFile(commandPath)) {
            FileUtil.copyFile(commandPath, path);
            CommandBlock.x();
        } else {
            FileUtil.writeFile(path, "[]");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ManageXmlCommandBinding binding = ManageXmlCommandBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setTitle("Java Command Manager");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        UI.addSystemWindowInsetToPadding(binding.list, false, false, false, true);
        binding.toolbar.setNavigationOnClickListener(
                v -> {
                    if (!mB.a()) {
                        onBackPressed();
                    }
                });
        binding.fab.setOnClickListener(v -> dialog(false, 0));
        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
        } else {
            sc_id = savedInstanceState.getString("sc_id");
        }
        commandPath = wq.b(sc_id) + "/java_command";
        hC projectFile = jC.b(sc_id);
        javaFiles = new ArrayList<>();
        for (ProjectFileBean bean : projectFile.b()) {
            javaFiles.add(bean.getJavaName());
        }
        for (ProjectFileBean bean : projectFile.c()) {
            javaFiles.add(bean.getJavaName());
        }
        
        adapter = new XMLCommandAdapter();
        adapter.setOnItemClickListener(
                item -> {
                    int position = item.second;
                    PopupMenu popupMenu = new PopupMenu(this, item.first);
                    var menu = popupMenu.getMenu();
                    menu.add(Menu.NONE, 0, Menu.NONE, "Edit");
                    menu.add(Menu.NONE, 1, Menu.NONE, "Delete");
                    if (position != 0) menu.add(Menu.NONE, 2, Menu.NONE, "Move up");
                    if (position != adapter.getItemCount() - 1)
                        menu.add(Menu.NONE, 3, Menu.NONE, "Move down");

                    popupMenu.setOnMenuItemClickListener(
                            itemMenu -> {
                                if (itemMenu.getItemId() == 0) {
                                    dialog(true, position);
                                } else if (itemMenu.getItemId() == 2) {
                                    if (position > 0) {
                                        Collections.swap(commands, position, position - 1);
                                        save();
                                    }
                                } else if (itemMenu.getItemId() == 3) {
                                    if (position < adapter.getItemCount() - 1) {
                                        Collections.swap(commands, position, position + 1);
                                        save();
                                    }
                                } else {
                                    MaterialAlertDialogBuilder dialog =
                                            new MaterialAlertDialogBuilder(this);
                                    dialog.setTitle(R.string.common_word_delete);
                                    dialog.setMessage("Are you sure you want to delete this item?");
                                    dialog.setPositiveButton(
                                            R.string.common_word_yes,
                                            (d, w) -> {
                                                if (position != -1) {
                                                    commands.remove(position);
                                                    FileUtil.writeFile(
                                                            commandPath,
                                                            getGson().toJson(commands));
                                                    adapter.notifyDataSetChanged();
                                                }
                                            });
                                    dialog.setNegativeButton(R.string.common_word_no, null);
                                    dialog.show();
                                }
                                return true;
                            });

                    popupMenu.show();
                });
        binding.list.setAdapter(adapter);
        fetchCommand();
        generateCommand();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 0, Menu.NONE, R.string.design_actionbar_title_show_source_code)
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_code))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 0 -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Select a Java file")
                        .setAdapter(
                                new ArrayAdapter<>(
                                        this, android.R.layout.simple_list_item_1, javaFiles),
                                (d, w) -> showSourceCode(javaFiles.get(w)))
                        .show();
                return true;
            }
            default -> {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private void save() {
        FileUtil.writeFile(commandPath, getGson().toJson(commands));
        adapter.submitList(null);
        adapter.submitList(commands);
    }

    private void dialog(boolean edit, int position) {
        var dialog = new BottomSheetDialog(this);
        ManageXmlCommandAddBinding binding =
                ManageXmlCommandAddBinding.inflate(getLayoutInflater());
        dialog.setContentView(binding.getRoot());
        dialog.setOnShowListener(
                d -> {
                    BottomSheetDialog bsd = (BottomSheetDialog) d;
                    View parent =
                            bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                    if (parent != null) {
                        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
                        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                        behavior.setSkipCollapsed(true);

                        ViewGroup.LayoutParams layoutParams = parent.getLayoutParams();
                        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        parent.setLayoutParams(layoutParams);
                    }
                });
        dialog.show();
        binding.title.setText(!edit ? "Add new command" : "Edit command");
        binding.xmlName.setHint("Java name(with .java)");

        if (edit) {
            var command = commands.get(position);
            binding.xmlName.setText(
                    CommandBlock.getInputName(
                            command.get("input") != null ? command.get("input").toString() : ""));
            binding.reference.setText(command.get("reference").toString());
            binding.command.setText(command.get("command").toString());
            binding.distance.setText(getIntValue(command.get("distance").toString()));
            binding.front.setText(getIntValue(command.get("after").toString()));
            binding.backend.setText(getIntValue(command.get("before").toString()));
            binding.changes.setText(
                    CommandBlock.getExceptFirstLine(
                            command.get("input") != null ? command.get("input").toString() : ""));
        }

        binding.xmlName.setAdapter(
                new ArrayAdapter<>(
                        this, android.R.layout.simple_dropdown_item_1line, javaFiles));

        binding.command.setAdapter(
                new ArrayAdapter<>(
                        this, android.R.layout.simple_dropdown_item_1line, COMMANDS_ACTION));

        binding.positive.setText(R.string.common_word_save);
        binding.positive.setOnClickListener(
                v -> {
                    var javaName = Helper.getText(binding.xmlName);
                    var reference = Helper.getText(binding.reference);
                    var command = Helper.getText(binding.command);
                    if (TextUtils.isEmpty(javaName)) {
                        SketchwareUtil.toastError("Java name is required");
                        return;
                    }
                    if (TextUtils.isEmpty(reference)) {
                        SketchwareUtil.toastError("reference is required");
                        return;
                    }
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("reference", reference);
                    map.put("distance", Integer.parseInt(Helper.getText(binding.distance)));
                    map.put("after", Integer.parseInt(Helper.getText(binding.front)));
                    map.put("before", Integer.parseInt(Helper.getText(binding.backend)));
                    map.put("command", Helper.getText(binding.command));
                    String inputBuilder = ">" + javaName + "\n" +
                            Helper.getText(binding.changes);
                    map.put("input", inputBuilder);
                    if (edit) {
                        if (position != -1) {
                            commands.set(position, map);
                        }
                    } else {
                        commands.add(map);
                    }
                    save();
                    dialog.dismiss();
                });
        binding.negative.setOnClickListener(
                v -> {
                    dialog.dismiss();
                });
        binding.xmlName.requestFocus();
    }

    private String getIntValue(String value) {
        return String.valueOf((int) Double.parseDouble(value));
    }

    private void showSourceCode(String filename) {
        k();
        Executors.newSingleThreadExecutor()
                .execute(
                        () -> {
                            String source =
                                    new yq(getApplicationContext(), sc_id)
                                            .getFileSrc(
                                                    filename,
                                                    jC.b(sc_id),
                                                    jC.a(sc_id),
                                                    jC.c(sc_id));

                            var dialogBuilder =
                                    new MaterialAlertDialogBuilder(this)
                                            .setTitle(filename)
                                            .setCancelable(false)
                                            .setPositiveButton("Dismiss", null);

                            runOnUiThread(
                                    () -> {
                                        if (isFinishing()) return;
                                        h();

                                        CodeEditor editor = new CodeEditor(this);
                                        editor.setTypefaceText(Typeface.MONOSPACE);
                                        editor.setEditable(false);
                                        editor.setTextSize(14);
                                        editor.setText(
                                                !source.isEmpty()
                                                        ? source
                                                        : "Failed to generate source.");
                                        editor.getComponent(Magnifier.class)
                                                .setWithinEditorForcibly(true);

                                        editor.setEditorLanguage(
                                                CodeEditorLanguages.loadTextMateLanguage(
                                                        CodeEditorLanguages.SCOPE_NAME_JAVA));
                                        if (ThemeUtils.isDarkThemeEnabled(
                                                getApplicationContext())) {
                                            editor.setColorScheme(
                                                    CodeEditorColorSchemes.loadTextMateColorScheme(
                                                            CodeEditorColorSchemes.THEME_DRACULA));
                                        } else {
                                            editor.setColorScheme(
                                                    CodeEditorColorSchemes.loadTextMateColorScheme(
                                                            CodeEditorColorSchemes.THEME_GITHUB));
                                        }

                                        AlertDialog dialog = dialogBuilder.create();
                                        dialog.setView(
                                                editor,
                                                (int) getDip(24),
                                                (int) getDip(20),
                                                (int) getDip(24),
                                                (int) getDip(0));
                                        dialog.show();
                                    });
                        });
    }

    private void fetchCommand() {
        if (FileUtil.isExistFile(commandPath)) {
            try {
                commands =
                        getGson().fromJson(FileUtil.readFile(commandPath), Helper.TYPE_MAP_LIST);
                adapter.submitList(commands);
            } catch (Exception ignored) {
            }
        }
    }

    private void generateCommand() {
        if (!FileUtil.isExistFile(commandPath)) {
            k();
            Executors.newSingleThreadExecutor()
                    .execute(
                            () -> {
                                fetchJavaCommand(this, sc_id);
                                runOnUiThread(
                                        () -> {
                                            h();
                                            fetchCommand();
                                        });
                            });
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }
}
