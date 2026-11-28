package sketchware.plus.utility;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;

public class SmartEditorAutoCompletion extends EditorAutoCompletion {

    public SmartEditorAutoCompletion(@NonNull CodeEditor editor) {
        super(editor);
    }

    @Override
    public void updateCompletionWindowPosition(boolean scrollEditor) {
        CodeEditor ed = getEditor();
        var dp = ed.getDpUnit();
        var cursor = ed.getCursor();
        float panelX = ed.updateCursorAnchor() + dp * 20;
        var rowHeight = ed.getRowHeight();
        float[] rightLayoutOffset = ed.getLayout().getCharLayoutOffset(cursor.getRightLine(), cursor.getRightColumn());
        float cursorBaselineY = rightLayoutOffset[0] - ed.getOffsetY();

        WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(ed);
        int imeHeight = rootInsets != null ? rootInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;

        float spaceBelow = ed.getHeight() - (cursorBaselineY + rowHeight / 2f) - imeHeight;
        float spaceAbove = cursorBaselineY;

        float desiredHeight = dp * 130;
        float panelY;
        float restY;

        // If space below is less than desired height and space above has more room, show above the cursor!
        if (spaceBelow < desiredHeight && spaceAbove > spaceBelow) {
            restY = Math.min(spaceAbove - dp * 10, desiredHeight);
            if (restY < dp * 40) {
                restY = Math.max(spaceAbove, dp * 40);
            }
            int currentHeight = getHeight();
            if (currentHeight <= 0) currentHeight = (int) restY;
            panelY = cursorBaselineY - rowHeight - currentHeight;
            if (panelY < 0) {
                panelY = 0;
            }
            setMaxHeight((int) restY);
        } else {
            panelY = cursorBaselineY + rowHeight / 2f;
            restY = spaceBelow;
            if (restY > desiredHeight) {
                restY = desiredHeight;
            } else if (restY < dp * 100 && scrollEditor) {
                float offset = 0;
                while (restY < dp * 100 && ed.getOffsetY() + offset + rowHeight <= ed.getScrollMaxY()) {
                    restY += rowHeight;
                    panelY -= rowHeight;
                    offset += rowHeight;
                }
                ed.getScroller().startScroll(ed.getOffsetX(), ed.getOffsetY(), 0, (int) offset, 0);
            }
            setMaxHeight((int) restY);
        }

        int width;
        if ((ed.getWidth() < 500 * dp && getCompletionWndPositionMode() == WINDOW_POS_MODE_AUTO) || getCompletionWndPositionMode() == WINDOW_POS_MODE_FULL_WIDTH_ALWAYS) {
            width = ed.getWidth() * 7 / 8;
            panelX = ed.getWidth() / 8f / 2f;
        } else {
            width = (int) Math.min(300 * dp, ed.getWidth() / 2f);
        }
        int height = getHeight();
        setLocation((int) panelX + ed.getOffsetX(), (int) panelY + ed.getOffsetY());
        setSize(width, height);
    }
}
