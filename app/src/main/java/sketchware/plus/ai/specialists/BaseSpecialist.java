package sketchware.plus.ai.specialists;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;
import sketchware.plus.ai.SkAssistantFragment;
import a.a.a.jC;
import com.besome.sketch.beans.ProjectFileBean;

public abstract class BaseSpecialist {
    protected final SkAssistantFragment fragment;
    protected final String scId;
    protected final ProjectFileBean projectFile;

    public BaseSpecialist(SkAssistantFragment fragment) {
        this.fragment = fragment;
        this.scId = fragment.scId;
        this.projectFile = fragment.projectFile;
    }

    public abstract void process(String prompt, String reasoning);
    
    public abstract void handleAction(JSONObject action) throws JSONException;

    protected void log(String msg) {
        fragment.log(msg);
    }

    protected void setStatus(String status) {
        fragment.setStatus(status);
    }

    protected Context getContext() {
        return fragment.getContext();
    }
}
