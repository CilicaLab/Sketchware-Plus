package com.google.android.recaptcha;

public class RecaptchaException extends Exception {
    public RecaptchaException(String message) {
        super(message);
    }

    public RecaptchaException(String message, Throwable cause) {
        super(message, cause);
    }
}
