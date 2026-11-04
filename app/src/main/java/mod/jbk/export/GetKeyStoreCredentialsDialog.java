package mod.jbk.export;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.LinkedList;

import a.a.a.wq;
import mod.hey.studios.util.Helper;
import sketchware.plus.R;
import sketchware.plus.databinding.DialogKeystoreCredentialsBinding;
import sketchware.plus.utility.SketchwareUtil;

public class GetKeyStoreCredentialsDialog {

    private final MaterialAlertDialogBuilder dialog;
    private final DialogKeystoreCredentialsBinding binding;
    private CredentialsReceiver receiver;
    private SigningMode mode;

    public GetKeyStoreCredentialsDialog(Activity activity, int iconResourceId, String title, String noticeText) {
        dialog = new MaterialAlertDialogBuilder(activity);
        dialog.setIcon(iconResourceId);
        dialog.setTitle(title);
        dialog.setMessage(noticeText);
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_next), (dialog1, which) -> onNextButtonClick(dialog1));

        binding = DialogKeystoreCredentialsBinding.inflate(LayoutInflater.from(activity));
        dialog.setView(binding.getRoot());

        setupSpinner(activity);
    }

    private void setupSpinner(Activity activity) {
        String[] dropdownItems = getDropdownItems();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, dropdownItems);
        binding.actSigningMode.setAdapter(adapter);
        binding.actSigningMode.setOnItemClickListener((parent, view, position, id) -> {
            mode = SigningMode.values()[position];
            updateInputFieldsState();
        });
    }

    private String[] getDropdownItems() {
        LinkedList<String> labels = new LinkedList<>();
        for (SigningMode mode : SigningMode.values()) {
            labels.add(mode.label);
        }
        return labels.toArray(new String[0]);
    }

    private void updateInputFieldsState() {
        boolean signingWithKeyStore = mode == SigningMode.OWN_KEY_STORE;
        binding.tilAlias.setEnabled(signingWithKeyStore);
        binding.tilPassword.setEnabled(signingWithKeyStore);
        binding.tilSigningAlgorithm.setEnabled(signingWithKeyStore);
        binding.cbV1.setEnabled(signingWithKeyStore || mode == SigningMode.TESTKEY);
        binding.cbV2.setEnabled(signingWithKeyStore || mode == SigningMode.TESTKEY);
        binding.cbV3.setEnabled(signingWithKeyStore || mode == SigningMode.TESTKEY);
    }

    private void onNextButtonClick(DialogInterface dialogInterface) {
        boolean v1 = binding.cbV1.isChecked();
        boolean v2 = binding.cbV2.isChecked();
        boolean v3 = binding.cbV3.isChecked();

        if (mode == SigningMode.OWN_KEY_STORE) {
            if (new File(wq.j()).exists()) {
                if (validateInputs()) {
                    dialogInterface.dismiss();
                    receiver.gotCredentials(new Credentials(
                            Helper.getText(binding.etSigningAlgorithm),
                            Helper.getText(binding.etPassword),
                            Helper.getText(binding.etAlias),
                            Helper.getText(binding.etPassword),
                            v1, v2, v3
                    ));
                }
            } else {
                SketchwareUtil.toastError("Keystore not found");
            }
        } else if (mode == SigningMode.TESTKEY) {
            dialogInterface.dismiss();
            receiver.gotCredentials(new Credentials(Helper.getText(binding.etSigningAlgorithm), v1, v2, v3));
        } else if (mode == SigningMode.DONT_SIGN) {
            dialogInterface.dismiss();
            receiver.gotCredentials(null);
        }
    }

    private boolean validateInputs() {
        boolean isValid = true;

        if (TextUtils.isEmpty(binding.etAlias.getText())) {
            binding.tilAlias.setError("Alias can't be empty");
            isValid = false;
        } else {
            binding.tilAlias.setError(null);
        }

        if (TextUtils.isEmpty(binding.etPassword.getText())) {
            binding.tilPassword.setError("Password can't be empty");
            isValid = false;
        } else {
            binding.tilPassword.setError(null);
        }

        if (TextUtils.isEmpty(binding.etSigningAlgorithm.getText())) {
            binding.tilSigningAlgorithm.setError("Algorithm can't be empty");
            isValid = false;
        } else {
            binding.tilSigningAlgorithm.setError(null);
        }

        if (!binding.cbV1.isChecked() && !binding.cbV2.isChecked() && !binding.cbV3.isChecked()) {
            SketchwareUtil.toastError("Please select at least one signature scheme (V1, V2, or V3)");
            isValid = false;
        }

        return isValid;
    }

    public void show() {
        binding.etAlias.requestFocus();
        dialog.show();
    }

    public void setListener(CredentialsReceiver receiver) {
        this.receiver = receiver;
    }

    private enum SigningMode {
        OWN_KEY_STORE("Sign using keystore"),
        TESTKEY("Sign using a test key"),
        DONT_SIGN("Don't sign");

        private final String label;

        SigningMode(String label) {
            this.label = label;
        }
    }

    public interface CredentialsReceiver {
        /**
         * @param credentials The {@link Credentials} object made from user input.
         *                    May be null. In that case, the user disabled signing the file.
         */
        void gotCredentials(Credentials credentials);
    }

    public static class Credentials {

        private final boolean signWithTestkey;
        private final String keyStorePassword;
        private final String keyAlias;
        private final String keyPassword;
        private final String signingAlgorithm;
        private final boolean v1SigningEnabled;
        private final boolean v2SigningEnabled;
        private final boolean v3SigningEnabled;

        /**
         * Constructs a credentials holder configured to sign with testkey,
         * meaning that no key store, aliases, and passwords were entered.
         */
        public Credentials(String signingAlgorithm) {
            this(signingAlgorithm, true, true, true);
        }

        public Credentials(String signingAlgorithm, boolean v1, boolean v2, boolean v3) {
            signWithTestkey = true;
            keyStorePassword = null;
            keyAlias = null;
            keyPassword = null;
            this.signingAlgorithm = signingAlgorithm;
            this.v1SigningEnabled = v1;
            this.v2SigningEnabled = v2;
            this.v3SigningEnabled = v3;
        }

        /**
         * Constructs a credentials holder configured to sign with a private key taken from a key store.
         */
        public Credentials(String signingAlgorithm, String keyPassword, String keyAlias, String keyStorePassword) {
            this(signingAlgorithm, keyPassword, keyAlias, keyStorePassword, true, true, true);
        }

        public Credentials(String signingAlgorithm, String keyPassword, String keyAlias, String keyStorePassword, boolean v1, boolean v2, boolean v3) {
            signWithTestkey = false;
            this.keyStorePassword = keyStorePassword;
            this.keyAlias = keyAlias;
            this.keyPassword = keyPassword;
            this.signingAlgorithm = signingAlgorithm;
            this.v1SigningEnabled = v1;
            this.v2SigningEnabled = v2;
            this.v3SigningEnabled = v3;
        }

        /**
         * @return False if this credentials holder contains credentials for signing, true if not.
         */
        public boolean isForSigningWithTestkey() {
            return signWithTestkey;
        }

        /**
         * @return {@link #keyStorePassword}
         */
        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        /**
         * @return {@link #keyAlias}
         */
        public String getKeyAlias() {
            return keyAlias;
        }

        /**
         * @return {@link #keyPassword}
         */
        public String getKeyPassword() {
            return keyPassword;
        }

        /**
         * @return {@link #signingAlgorithm}
         */
        public String getSigningAlgorithm() {
            return signingAlgorithm;
        }

        public boolean isV1SigningEnabled() {
            return v1SigningEnabled;
        }

        public boolean isV2SigningEnabled() {
            return v2SigningEnabled;
        }

        public boolean isV3SigningEnabled() {
            return v3SigningEnabled;
        }
    }
}