package com.google.android.recaptcha;

import androidx.annotation.NonNull;

public class RecaptchaAction {
    public static final RecaptchaAction LOGIN = new RecaptchaAction("login");
    public static final RecaptchaAction SIGNUP = new RecaptchaAction("signup");

    private final String action;

    public RecaptchaAction() {
        this.action = "default";
    }

    public RecaptchaAction(@NonNull String action) {
        this.action = action;
    }

    @NonNull
    public static RecaptchaAction custom(@NonNull String customAction) {
        return new RecaptchaAction(customAction);
    }

    @NonNull
    public String getAction() {
        return action;
    }
}
