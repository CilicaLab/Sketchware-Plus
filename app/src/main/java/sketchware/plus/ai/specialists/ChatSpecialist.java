package sketchware.plus.ai.specialists;

import android.app.Activity;
import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import a.a.a.jC;
import sketchware.plus.ai.SkAssistantFragment;

public class ChatSpecialist extends BaseSpecialist {

    public ChatSpecialist(SkAssistantFragment fragment) {
        super(fragment);
    }

    @Override
    public void process(String prompt, String reasoning) {
        setStatus("Hold on, let me think...");
        Context androidContext = getContext();
        if (androidContext == null) return;

        jC.projectOperationsExecutor.execute(() -> {
            try { Thread.sleep(1000); } catch (Exception ignored) {}
            String contextStr = fragment.gatherScopedContext(androidContext, "CHAT_ASSISTANT", prompt);

            String systemPrompt = "You are the very friendly Companion.\n" +
                    "Provide helpful, concise, and friendly responses to the user's questions.\n" +
                    "If they ask for technical help you cannot perform directly, explain how to do it in Sketchware pro.\n\n" +
                    "CRITICAL: Put your actual conversational response in the 'summary' field.\n\n" +
                    "RESPONSE CONTRACT:\n" +
                    "{\n" +
                    "  \"category\": \"CHAT_ASSISTANT\",\n" +
                    "  \"summary\": \"<Your actual response here>\",\n" +
                    "  \"actions\": []\n" +
                    "}\n" +
                    "Reasoning: " + reasoning;

            Activity activity = fragment.getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> fragment.executeRequest(systemPrompt, prompt, contextStr, "CHAT_ASSISTANT"));
            }
        });
    }

    @Override
    public void handleAction(JSONObject action) throws JSONException {
        // Chat messages are usually handled via the summary field in handleAiResponse
    }
}
