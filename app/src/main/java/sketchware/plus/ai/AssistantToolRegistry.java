package sketchware.plus.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines the schemas for all tools available to the SK Assistant.
 * These follow the OpenAI tool calling format.
 */
public class AssistantToolRegistry {

    /**
     * @return A JSONArray of all tool definitions for the AI request.
     */
    public static JSONArray getAllTools() {
        JSONArray tools = new JSONArray();
        tools.put(createTool("web_search", "Search the web for Android documentation, library versions, or code examples using DuckDuckGo.", 
                new ParameterBuilder()
                    .addProperty("query", "string", "The search query.")
                    .setRequired("query")
                    .build()));

        tools.put(createTool("web_browse", "Fetch and read the content of a specific web URL. Use this to read documentation found via search.", 
                new ParameterBuilder()
                    .addProperty("url", "string", "The full URL to browse.")
                    .setRequired("url")
                    .build()));

        tools.put(createTool("list_project_files", "Lists all Java, Kotlin, and XML files available in the current project.", 
                new ParameterBuilder().build()));

        tools.put(createTool("get_layout_xml", "Returns the current Activity's XML layout code and view hierarchy.", 
                new ParameterBuilder().build()));

        tools.put(createTool("apply_layout_xml", "Updates the current activity's XML layout. Use this to add, modify or delete views.", 
                new ParameterBuilder()
                    .addProperty("xml", "string", "The full valid Android XML layout code.")
                    .addProperty("summary", "string", "A short description of what was changed.")
                    .setRequired("xml", "summary")
                    .build()));

        tools.put(createTool("inject_imports", "Injects Java/Kotlin package imports into the current activity screen.", 
                new ParameterBuilder()
                    .addProperty("packages", "array", "Array of package strings, e.g., [\"java.util.List\", \"android.widget.Button\"]", "string")
                    .setRequired("packages")
                    .build()));

        tools.put(createTool("read_method", "Read the source code of a specific Java method by name.", 
                new ParameterBuilder()
                    .addProperty("javaName", "string", "The name of the file, e.g., MainActivity.java")
                    .addProperty("methodName", "string", "The name of the method to read.")
                    .setRequired("javaName", "methodName")
                    .build()));

        tools.put(createTool("list_methods", "List all method names in a specific Java file.", 
                new ParameterBuilder()
                    .addProperty("javaName", "string", "The file name, e.g., MainActivity.java")
                    .setRequired("javaName")
                    .build()));

        tools.put(createTool("get_full_code", "Read the entire source code of a Java/Kotlin file.", 
                new ParameterBuilder()
                    .addProperty("javaName", "string", "The file name.")
                    .setRequired("javaName")
                    .build()));

        tools.put(createTool("add_java_patch", "Apply a persistent code patch to the project's generated source code.", 
                new ParameterBuilder()
                    .addProperty("javaName", "string", "Target file name.")
                    .addProperty("reference", "string", "The EXACT line of code to use as an anchor/reference.")
                    .addProperty("command", "string", "The operation type: insert, add, replace, find-replace.")
                    .addProperty("inputCode", "string", "The new code content to apply.")
                    .addProperty("distance", "integer", "Line offset from reference (default 0).")
                    .addProperty("front", "integer", "Number of lines to delete before reference.")
                    .addProperty("back", "integer", "Number of lines to delete after reference.")
                    .setRequired("javaName", "reference", "command", "inputCode")
                    .build()));

        tools.put(createTool("manage_library", "Enable or disable built-in libraries (Firebase, AdMob, Appcompat, Google Maps).", 
                new ParameterBuilder()
                    .addProperty("libraryId", "string", "The library identifier (e.g., 'appcompat', 'firebase', 'admob', 'googlemap').")
                    .addProperty("enabled", "boolean", "True to enable, False to disable.")
                    .setRequired("libraryId", "enabled")
                    .build()));

        tools.put(createTool("add_permission", "Add a standard Android permission to the project manifest.", 
                new ParameterBuilder()
                    .addProperty("permission", "string", "The full permission string, e.g., 'android.permission.CAMERA'.")
                    .setRequired("permission")
                    .build()));

        tools.put(createTool("add_component", "Add a Sketchware component to the current Activity.", 
                new ParameterBuilder()
                    .addProperty("type", "integer", "The component type ID (use list_available_components to find it).")
                    .addProperty("id", "string", "The unique name for the component instance.")
                    .setRequired("type", "id")
                    .build()));

        tools.put(createTool("list_available_components", "Returns a list of all available Sketchware components (built-in and local/custom).", 
                new ParameterBuilder().build()));

        tools.put(createTool("apply_custom_view", "Create or update a Sketchware Custom View XML file.", 
                new ParameterBuilder()
                    .addProperty("name", "string", "The name of the custom view.")
                    .addProperty("xml", "string", "The XML content.")
                    .setRequired("name", "xml")
                    .build()));

        return tools;
    }

    private static JSONObject createTool(String name, String description, JSONObject parameters) {
        try {
            return new JSONObject()
                .put("type", "function")
                .put("function", new JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("parameters", parameters));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /**
     * Helper to build the 'parameters' object for tools.
     */
    private static class ParameterBuilder {
        private final JSONObject properties = new JSONObject();
        private final List<String> required = new ArrayList<>();

        public ParameterBuilder addProperty(String name, String type, String description) {
            return addProperty(name, type, description, null);
        }

        public ParameterBuilder addProperty(String name, String type, String description, String itemsType) {
            try {
                JSONObject prop = new JSONObject()
                    .put("type", type)
                    .put("description", description);
                if (itemsType != null && type.equals("array")) {
                    prop.put("items", new JSONObject().put("type", itemsType));
                }
                properties.put(name, prop);
            } catch (Exception ignored) {}
            return this;
        }

        public ParameterBuilder setRequired(String... names) {
            for (String name : names) required.add(name);
            return this;
        }

        public JSONObject build() {
            try {
                return new JSONObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", new JSONArray(required));
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }
}
