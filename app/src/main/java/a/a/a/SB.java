package a.a.a;

import android.content.Context;

import com.google.android.material.textfield.TextInputLayout;

import sketchware.plus.R;

public class SB extends MB {

    private final int min;
    private final int max;

    public SB(Context context, TextInputLayout textInputLayout, int min, int max) {
        super(context, textInputLayout);
        this.min = min;
        this.max = max;
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        Context context = getContext();
        if (s.toString().trim().length() < min) {
            b.setErrorEnabled(true);
            if (context != null) {
                if (e == 0) {
                    b.setError(context.getString(R.string.invalid_value_min_lenth, min));
                } else {
                    b.setError(context.getString(e, min));
                }
            }

            d = false;
        } else {
            if (s.toString().trim().length() > max) {
                b.setErrorEnabled(true);
                if (context != null) {
                    if (e == 0) {
                        b.setError(context.getString(R.string.invalid_value_max_lenth, max));
                    } else {
                        b.setError(context.getString(e, max));
                    }
                }

                d = false;
            } else {
                b.setErrorEnabled(false);
                d = true;
            }

        }
    }
}
