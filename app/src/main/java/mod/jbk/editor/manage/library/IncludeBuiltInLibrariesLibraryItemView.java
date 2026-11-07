package mod.jbk.editor.manage.library;

import android.content.Context;

import androidx.annotation.Nullable;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.editor.manage.library.LibraryItemView;

import java.util.List;

import mod.jbk.build.BuiltInLibraries;

public class IncludeBuiltInLibrariesLibraryItemView extends LibraryItemView {
    private final String sc_id;

    public IncludeBuiltInLibrariesLibraryItemView(Context context, String sc_id) {
        super(context);
        this.sc_id = sc_id;
    }

    @Override
    public void setData(@Nullable ProjectLibraryBean projectLibraryBean) {
        boolean includingEnabled = IncludeBuiltInLibrariesActivity.isIncludingEnabled(sc_id);
        List<BuiltInLibraries.BuiltInLibrary> includedLibraries = IncludeBuiltInLibrariesActivity.getIncludedLibraries(sc_id);
        icon.setImageResource(IncludeBuiltInLibrariesActivity.getItemIcon());
        title.setText(IncludeBuiltInLibrariesActivity.getItemTitle());
        description.setText(
                !includingEnabled ? IncludeBuiltInLibrariesActivity.getDefaultItemDescription()
                        : String.format(IncludeBuiltInLibrariesActivity.getSelectedLibrariesItemDescription(),
                        includedLibraries.size(), BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES.length)
        );
        enabled.setText(includingEnabled ? "ON" : "OFF");
        enabled.setSelected(includingEnabled);
    }
}
