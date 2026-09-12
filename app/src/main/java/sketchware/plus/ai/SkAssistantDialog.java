package sketchware.plus.ai;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.besome.sketch.beans.ProjectFileBean;

public class SkAssistantDialog extends DialogFragment {

    private String scId;
    private ProjectFileBean projectFile;

    public static SkAssistantDialog newInstance(String scId, ProjectFileBean projectFile) {
        SkAssistantDialog fragment = new SkAssistantDialog();
        Bundle args = new Bundle();
        args.putString("scId", scId);
        args.putParcelable("projectFile", projectFile);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            scId = getArguments().getString("scId");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                projectFile = getArguments().getParcelable("projectFile", ProjectFileBean.class);
            } else {
                projectFile = getArguments().getParcelable("projectFile");
            }
        }
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_NoActionBar);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(sketchware.plus.R.layout.sk_assistant_dialog_host, container, false);
        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(sketchware.plus.R.id.fragment_container, SkAssistantFragment.newInstance(scId, projectFile))
                    .commit();
        }
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return view;
    }
}
