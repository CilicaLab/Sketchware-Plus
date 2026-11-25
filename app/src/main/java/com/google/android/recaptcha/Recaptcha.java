package com.google.android.recaptcha;

import android.app.Application;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

public class Recaptcha {
    @NonNull
    public static Task<RecaptchaTasksClient> getTasksClient(@NonNull Application application, @NonNull String siteKey) {
        return Tasks.forException(new RecaptchaException("reCAPTCHA not initialized"));
    }
}
