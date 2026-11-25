package com.google.android.recaptcha;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;

public interface RecaptchaTasksClient {
    @NonNull
    Task<String> executeTask(@NonNull RecaptchaAction action);

    @NonNull
    Task<String> executeTask(@NonNull RecaptchaAction action, long timeout);
}
