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

        t.put(createTool("read_url", "Fetch URL content",
                new ParameterBuilder().addProperty("url", "string", "full URL").setRequired("url").build()));

        t.put(createTool("list_files", "List project Java/Kotlin/XML files",
                new ParameterBuilder().build()));

        t.put(createTool("find_files", "Find files by name",
                new ParameterBuilder().addProperty("query", "string", "name or part").setRequired("query").build()));

        t.put(createTool("get_layout_xml", "Get activity XML + view hierarchy",
                new ParameterBuilder().build()));

        t.put(createTool("update_layout_xml", "Create/modify activity layout views (never manifest)",
                new ParameterBuilder()
                        .addProperty("xml", "string", "full layout XML")
                        .addProperty("summary", "string", "what changed")
                        .setRequired("xml", "summary").build()));

        t.put(createTool("find_manifest_entries", "Search AndroidManifest.xml for permissions, activities, services, tags",
                new ParameterBuilder().addProperty("query", "string", "keyword/tag/permission, or 'all'").setRequired("query").build()));

        t.put(createTool("add_java_imports", "Add imports to activity",
                new ParameterBuilder()
                        .addProperty("packages", "array", "e.g. [\"java.util.List\"]", "string")
                        .addProperty("javaName", "string", "optional file, e.g. MainActivity.java")
                        .setRequired("packages").build()));

        t.put(createTool("add_source_code", "Add manual Java via 'Add source directly' block in onCreate/initializeLogic, a view/component event, or a More Block",
                new ParameterBuilder()
                        .addProperty("code", "string", "Java snippet")
                        .addProperty("javaName", "string", "optional file, e.g. MainActivity.java")
                        .addProperty("moreBlockName", "string", "optional More Block name")
                        .addProperty("eventKey", "string", "optional event key, e.g. onCreate_initializeLogic, button1_onClick, net_onResponse, auth_onSignInUserComplete, <funcName>_moreBlock")
                        .addProperty("targetId", "string", "optional view or component ID, e.g. button1, net, fauth")
                        .addProperty("eventName", "string", "optional event name, e.g. onClick, onResponse, onChildAdded")
                        .addProperty("targetKind", "string", "optional activity|view|component")
                        .setRequired("code").build()));

        t.put(createTool("read_method", "Read method source",
                new ParameterBuilder()
                        .addProperty("javaName", "string", "file")
                        .addProperty("methodName", "string", "method")
                        .setRequired("javaName", "methodName").build()));

        t.put(createTool("list_methods", "List method names in file",
                new ParameterBuilder().addProperty("javaName", "string", "file").setRequired("javaName").build()));

        t.put(createTool("view_file_outline", "Class outline (package, fields, method signatures + line numbers)",
                new ParameterBuilder().addProperty("javaName", "string", "file").setRequired("javaName").build()));

        t.put(createTool("grep_code", "Search keyword in file, returns lines with context",
                new ParameterBuilder()
                        .addProperty("javaName", "string", "file")
                        .addProperty("query", "string", "keyword/snippet")
                        .addProperty("contextLines", "integer", "default 2")
                        .setRequired("javaName", "query").build()));

        t.put(createTool("modify_java_file", "Patch generated source",
                new ParameterBuilder()
                        .addProperty("javaName", "string", "target file")
                        .addProperty("reference", "string", "EXACT anchor line")
                        .addProperty("command", "string", "insert|add|replace|find-replace")
                        .addProperty("inputCode", "string", "new code")
                        .addProperty("distance", "integer", "offset from anchor, default 0")
                        .addProperty("front", "integer", "lines to delete before anchor")
                        .addProperty("back", "integer", "lines to delete after anchor")
                        .setRequired("javaName", "reference", "command", "inputCode").build()));

        t.put(createTool("modify_manifest_xml", "Patch AndroidManifest.xml only (never layouts)",
                new ParameterBuilder()
                        .addProperty("xmlName", "string", "must be 'AndroidManifest.xml'")
                        .addProperty("reference", "string", "EXACT anchor line")
                        .addProperty("command", "string", "insert|add|replace|find-replace|find-replace-first|find-replace-all")
                        .addProperty("inputCode", "string", "new XML")
                        .addProperty("distance", "integer", "offset from anchor, default 0")
                        .addProperty("front", "integer", "lines to delete before anchor")
                        .addProperty("back", "integer", "lines to delete after anchor")
                        .setRequired("xmlName", "reference", "command", "inputCode").build()));

        t.put(createTool("toggle_local_library", "Enable/disable local library",
                new ParameterBuilder()
                        .addProperty("libraryName", "string", "folder name")
                        .addProperty("enabled", "boolean", "true=enable")
                        .setRequired("libraryName", "enabled").build()));

        t.put(createTool("add_manifest_permission", "Add manifest permission",
                new ParameterBuilder().addProperty("permission", "string", "e.g. android.permission.CAMERA")
                        .setRequired("permission").build()));

        t.put(createTool("add_sketchware_component", "Add component(s) to activity",
                new ParameterBuilder()
                        .addProperty("type", "integer", "type ID from list_components")
                        .addProperty("id", "string", "optional name/prefix, auto-generated if empty")
                        .addProperty("count", "integer", "optional batch count, default 1")
                        .setRequired("type").build()));

        t.put(createTool("list_components", "List built-in + custom components",
                new ParameterBuilder().build()));

        t.put(createTool("apply_custom_view", "Create/update custom view XML",
                new ParameterBuilder()
                        .addProperty("name", "string", "view name")
                        .addProperty("xml", "string", "XML")
                        .setRequired("name", "xml").build()));

        t.put(createTool("get_device_info", "Device metadata (time, hardware, display, battery, memory, storage)",
                new ParameterBuilder()
                        .addProperty("category", "string", "optional: all|time|hardware|display|battery|memory")
                        .build()));

        t.put(createTool("search_chat_history", "Search past messages to recall earlier instructions/context/code",
                new ParameterBuilder()
                        .addProperty("query", "string", "optional keyword")
                        .addProperty("role", "string", "optional: user|assistant|system|all (default all)")
                        .addProperty("limit", "integer", "optional, default 15, max 50")
                        .addProperty("startIndex", "integer", "optional start index, negative = from recent (e.g. -15)")
                        .build()));

        t.put(createTool("get_project_structure", "Inspect activities, custom views, variables, lists, More Blocks, components, view IDs, events",
                new ParameterBuilder()
                        .addProperty("javaName", "string", "optional activity, e.g. MainActivity.java")
                        .build()));

        t.put(createTool("add_variables", "Add variables to activity",
                new ParameterBuilder()
                        .addProperty("javaName", "string", "optional activity")
                        .addProperty("items", "array", "e.g. [{\"name\":\"count\",\"type\":\"number\"}]; name optional, auto-generated")
                        .setRequired("items").build()));

        t.put(createTool("add_lists", "Add lists (ListInt, ListString, ListMap) to activity",
                new ParameterBuilder()
                        .addProperty("javaName", "string", "optional activity")
                        .addProperty("items", "array", "e.g. [{\"name\":\"userList\",\"type\":\"string\"}]; name optional, auto-generated")
                        .setRequired("items").build()));

        t.put(createTool("add_more_block", "Create More Block (custom function). 'body_java' is added via 'Add source directly' block",
                new ParameterBuilder()
                        .addProperty("javaName", "string", "optional activity")
                        .addProperty("name", "string", "optional, auto-generated if empty")
                        .addProperty("params", "array", "optional; strings or {name, type} (string|number|boolean|map|list string|list map|view), default string")
                        .addPropertyEnum("returnType", "string", "optional", new String[]{"void", "string", "number", "boolean", "map", "list string", "list map", "view"})
                        .addProperty("body_java", "string", "optional Java body")
                        .addProperty("allow_empty_on_body_error", "boolean", "optional, default false")
                        .build()));

        t.put(createTool("register_event", "Register activity/view/component event listener, returns eventKey",
                new ParameterBuilder()
                        .addProperty("javaName", "string", "optional activity")
                        .addProperty("targetKind", "string", "activity|view|component (auto-detected if omitted)")
                        .addProperty("targetId", "string", "e.g. button1, timer1, onCreate")
                        .addProperty("eventName", "string", "e.g. onClick, onTimer, initializeLogic")
                        .setRequired("targetId", "eventName").build()));

        t.put(createTool("create_activity", "Create new Activity",
                new ParameterBuilder()
                        .addProperty("name", "string", "optional, no .java, auto-generated if empty")
                        .addProperty("options", "object", "optional: {toolbar, fullscreen, drawer, fab} booleans")
                        .addProperty("orientation", "integer", "0=portrait, 1=landscape, 2=both (default 0)")
                        .addProperty("keyboard", "integer", "0=unspecified, 1=visible, 2=hidden (default 0)")
                        .addProperty("layout_xml", "string", "optional initial layout XML")
                        .build()));

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

        public ParameterBuilder addPropertyEnum(String name, String type, String description, String[] enumValues) {
            try {
                JSONArray enumArr = new JSONArray();
                for (String v : enumValues) enumArr.put(v);
                JSONObject prop = new JSONObject()
                    .put("type", type)
                    .put("description", description)
                    .put("enum", enumArr);
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
