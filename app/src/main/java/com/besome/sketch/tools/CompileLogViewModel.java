package com.besome.sketch.tools;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class CompileLogViewModel extends ViewModel {
    private final MutableLiveData<String> rawLogs = new MutableLiveData<>();
    private final MutableLiveData<Boolean> wrapText = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> monospacedFont = new MutableLiveData<>(true);
    private final MutableLiveData<Integer> fontSize = new MutableLiveData<>(11);

    public void setRawLogs(String logs) {
        rawLogs.setValue(logs);
    }

    public LiveData<String> getRawLogs() {
        return rawLogs;
    }

    public void setWrapText(boolean wrap) {
        wrapText.setValue(wrap);
    }

    public LiveData<Boolean> getWrapText() {
        return wrapText;
    }

    public void setMonospacedFont(boolean monospace) {
        monospacedFont.setValue(monospace);
    }

    public LiveData<Boolean> getMonospacedFont() {
        return monospacedFont;
    }

    public void setFontSize(int size) {
        fontSize.setValue(size);
    }

    public LiveData<Integer> getFontSize() {
        return fontSize;
    }
}
