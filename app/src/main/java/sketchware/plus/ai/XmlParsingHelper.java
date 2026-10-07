package sketchware.plus.ai;

import android.content.Context;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import sketchware.plus.ai.specialists.LayoutSpecialist;

public class XmlParsingHelper {

    private final SkAssistantFragment fragment;
    private final LayoutSpecialist layoutSpecialist;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;

    public XmlParsingHelper(SkAssistantFragment fragment, LayoutSpecialist layoutSpecialist) {
        this.fragment = fragment;
        this.layoutSpecialist = layoutSpecialist;
    }

    public void resetRetryCount() {
        retryCount = 0;
    }

    public void handleXmlError(String editedXml, String errorMessage, String targetXmlName) {
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            fragment.appendSystemMessage("XML Parse Error. Auto retrying fix (" + retryCount + "/" + MAX_RETRIES + ")...");
            autoFixXml(editedXml, errorMessage, targetXmlName);
        } else {
            retryCount = 0;
            Context context = fragment.getContext();
            if (context == null) return;
            new MaterialAlertDialogBuilder(context)
                    .setTitle("Parse Error")
                    .setMessage(errorMessage)
                    .setPositiveButton("Fix", (d, w) -> layoutSpecialist.applyXml(editedXml, targetXmlName))
                    .setNegativeButton("Close", null)
                    .show();
        }
    }

    public void autoFixXml(String failedXml, String errorMessage, String targetXmlName) {
        String systemPrompt = "The user provided XML that failed to parse. FIX the XML so it parses correctly. " + fragment.getXmlRules();
        String userPrompt = "Failed XML:\n" + failedXml + "\n\nError Message: " + errorMessage + "\n\nPlease provide the corrected XML.";

        Context context = fragment.getContext();
        if (context == null) return;

        AiClient.askAi(context, systemPrompt, userPrompt, AiClient.AiTemperatureType.CODE_GENERATION, new AiClient.AiCallback() {
            @Override
            public void onSuccess(String response) {
                if (fragment.isRequestCanceled()) return;
                if (fragment.getActivity() != null) {
                    fragment.getActivity().runOnUiThread(() -> {
                        fragment.appendAssistantMessage(new SkAssistantFragment.Message("assistant", response, targetXmlName));
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (fragment.getActivity() != null) {
                    fragment.getActivity().runOnUiThread(() -> {
                        if (!"Canceled".equals(error)) {
                            fragment.log("Auto-fix ERROR: " + error);
                            fragment.appendSystemMessage("Auto-fix failed: " + error);
                        }
                    });
                }
            }
        });
    }
}
