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
        JSONArray t = new JSONArray();

        t.put(createTool("web_search", "Search web (DuckDuckGo)",
                new ParameterBuilder().addProperty("query", "string", "query").setRequired("query").build()));

        t.put(createTool("web_browse", "Fetch content of a URL",
                new ParameterBuilder().addProperty("url", "string", "full URL").setRequired("url").build()));

        t.put(createTool("list_project_files", "List Java/Kotlin/XML files in project",
                new ParameterBuilder().build()));

        t.put(createTool("search_project_files", "Find files by name",
                new ParameterBuilder().addProperty("query", "string", "file name or part").setRequired("query").build()));

        t.put(createTool("get_layout_xml", "Get current activity XML + view hierarchy",
                new ParameterBuilder().build()));

        t.put(createTool("apply_layout_xml", "Replace current activity XML layout",
                new ParameterBuilder()
                        .addProperty("xml", "string", "full valid layout XML")
                        .addProperty("summary", "string", "what changed")
                        .setRequired("xml", "summary").build()));

        t.put(createTool("inject_imports", "Add imports to current activity",
                new ParameterBuilder()
                        .addProperty("packages", "array", "e.g. [\"java.util.List\"]", "string")
                        .addProperty("javaName", "string", "optional target java file name, e.g. MainActivity.java")
                        .setRequired("packages").build()));

        t.put(createTool("read_method", "Read a method's source",
                new ParameterBuilder()
                        .addProperty("javaName", "string", "file, e.g. MainActivity.java")
                        .addProperty("methodName", "string", "method name")
                        .setRequired("javaName", "methodName").build()));

        t.put(createTool("list_methods", "List method names in a file",
                new ParameterBuilder().addProperty("javaName", "string", "file name").setRequired("javaName").build()));

        t.put(createTool("get_full_code", "Read whole file",
                new ParameterBuilder().addProperty("javaName", "string", "file name").setRequired("javaName").build()));

        t.put(createTool("search_in_code", "Search keyword in a file, returns lines w/ context",
                new ParameterBuilder()
                        .addProperty("javaName", "string", "file name")
                        .addProperty("query", "string", "keyword/snippet")
                        .addProperty("contextLines", "integer", "default 2")
                        .setRequired("javaName", "query").build()));

        t.put(createTool("add_java_patch", "Patch generated source code",
                new ParameterBuilder()
                        .addProperty("javaName", "string", "target file")
                        .addProperty("reference", "string", "EXACT anchor line")
                        .addProperty("command", "string", "insert|add|replace|find-replace")
                        .addProperty("inputCode", "string", "new code")
                        .addProperty("distance", "integer", "line offset from anchor, default 0")
                        .addProperty("front", "integer", "lines to delete before anchor")
                        .addProperty("back", "integer", "lines to delete after anchor")
                        .setRequired("javaName", "reference", "command", "inputCode").build()));

        t.put(createTool("manage_local_library", "Enable/disable local library",
                new ParameterBuilder()
                        .addProperty("libraryName", "string", "library folder name")
                        .addProperty("enabled", "boolean", "true=enable")
                        .setRequired("libraryName", "enabled").build()));

        t.put(createTool("add_permission", "Add permission to manifest",
                new ParameterBuilder().addProperty("permission", "string", "e.g. android.permission.CAMERA")
                        .setRequired("permission").build()));

        t.put(createTool("add_component", "Add Sketchware component to activity",
                new ParameterBuilder()
                        .addProperty("type", "integer", "type ID from list_available_components")
                        .addProperty("id", "string", "unique instance name")
                        .setRequired("type", "id").build()));

        t.put(createTool("list_available_components", "List built-in + custom components",
                new ParameterBuilder().build()));

        t.put(createTool("apply_custom_view", "Create/update custom view XML",
                new ParameterBuilder()
                        .addProperty("name", "string", "view name")
                        .addProperty("xml", "string", "XML content")
                        .setRequired("name", "xml").build()));

        return t;
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
