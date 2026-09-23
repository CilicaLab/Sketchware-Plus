package sketchware.plus.ai;

import android.graphics.Color;
import android.util.Log;

import com.besome.sketch.beans.BlockBean;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.eC;
import a.a.a.jC;
import mod.hilal.saif.blocks.BlocksHandler;

/**
 * Bidirectional Intermediate Representation (IR) Bridge for Sketchware Plus AI.
 * <p>
 * Architecture Flow:
 * <pre>
 *   Sketchware Blocks (BlockBean) ◄──► IR (Abstract JSON) ◄──► Readable Java Code
 * </pre>
 * <p>
 * Supports full recursive resolution of `@id` embedded expression blocks (variable getters,
 * string concats, math ops, nested conditionals, etc.) and token-aware parsing of nested parentheses,
 * string literals, and multi-line conditions.
 */
public class LogicIrBridge {

    private static final String TAG = "LogicIrBridge";

    // Only matches simple counting loops: for (int i = 0; i < N; i++) style, with < or <=.
    // Anything else (>=, !=, compound conditions, non-standard increments) is intentionally
    // NOT matched here and falls back to raw_code so semantics are never silently mangled.
    private static final Pattern FOR_LOOP_PATTERN =
            Pattern.compile("for\\s*\\(\\s*int\\s+\\w+\\s*=\\s*0\\s*;\\s*\\w+\\s*(<=?)\\s*([^;]+);\\s*\\w+\\s*\\+\\+\\s*\\)");

    public static class ValidationResult {
        public final boolean isValid;
        public final String errorMessage;
        public final String generatedJava;
        public final int blockCount;

        public ValidationResult(boolean isValid, String errorMessage, String generatedJava, int blockCount) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.generatedJava = generatedJava;
            this.blockCount = blockCount;
        }
    }

    /**
     * Validates an IR JSON structure via a dry-run conversion without touching any project files.
     *
     * @param ir The proposed IR JSONObject.
     * @return ValidationResult indicating whether the IR is safe to apply.
     */
    public static ValidationResult validateIr(JSONObject ir) {
        if (ir == null) {
            return new ValidationResult(false, "IR JSON object is null", "", 0);
        }
        try {
            JSONArray body = ir.optJSONArray("body");
            if (body == null) {
                return new ValidationResult(false, "IR JSON is missing 'body' array", "", 0);
            }

            // Dry-run IR -> BlockBeans
            ArrayList<BlockBean> testBlocks = irToBlocks(ir);
            if (testBlocks == null) {
                return new ValidationResult(false, "Failed to reconstruct BlockBean objects from IR", "", 0);
            }

            // Dry-run IR -> Java
            String testJava = irToJava(ir);

            return new ValidationResult(true, "Validation successful", testJava, testBlocks.size());
        } catch (Exception e) {
            return new ValidationResult(false, "IR Validation Exception: " + e.getMessage(), "", 0);
        }
    }

    // Cache of Sketchware built-in block definitions (opCode -> Metadata)
    private static Map<String, BlockSpecDef> blockSpecCache = null;

    private static class BlockSpecDef {
        String spec = "";
        String type = "e";
        String typeName = "";
        int color = -11751600; // Default teal color
    }

    private static synchronized void ensureBlockSpecCache() {
        if (blockSpecCache != null) return;
        blockSpecCache = new HashMap<>();

        try {
            ArrayList<HashMap<String, Object>> blockList = new ArrayList<>();
            BlocksHandler.builtInBlocks(blockList);

            for (HashMap<String, Object> def : blockList) {
                String name = (String) def.get("name");
                if (name == null || name.isEmpty()) continue;

                BlockSpecDef bSpec = new BlockSpecDef();
                bSpec.spec = (String) def.getOrDefault("spec", "");
                bSpec.type = (String) def.getOrDefault("type", "e");
                bSpec.typeName = (String) def.getOrDefault("typeName", "");

                Object colorObj = def.get("color");
                if (colorObj instanceof String) {
                    try {
                        bSpec.color = Color.parseColor((String) colorObj);
                    } catch (Exception ignored) {}
                }

                blockSpecCache.put(name, bSpec);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load built-in block specs from BlocksHandler", e);
        }
    }

    // =========================================================================
    // 1. BLOCK -> IR (Sketchware BlockBean List -> Abstract IR JSON)
    // =========================================================================

    /**
     * Converts a flat list of Sketchware {@link BlockBean} objects into an abstract IR JSON structure.
     * Recursively resolves all `@id` embedded expression blocks.
     *
     * @param blocks The list of blocks for an event or method.
     * @return JSONObject representing the IR tree.
     */
    public static JSONObject blocksToIr(List<BlockBean> blocks) {
        JSONObject ir = new JSONObject();
        try {
            if (blocks == null || blocks.isEmpty()) {
                ir.put("type", "event_logic");
                ir.put("body", new JSONArray());
                return ir;
            }

            Map<Integer, BlockBean> blockMap = new HashMap<>();
            Set<Integer> childBlockIds = new HashSet<>();

            for (BlockBean bean : blocks) {
                try {
                    int id = Integer.parseInt(bean.id);
                    blockMap.put(id, bean);

                    if (bean.nextBlock >= 0) childBlockIds.add(bean.nextBlock);
                    if (bean.subStack1 >= 0) childBlockIds.add(bean.subStack1);
                    if (bean.subStack2 >= 0) childBlockIds.add(bean.subStack2);

                    // Track embedded expression block references in parameters (@id)
                    if (bean.parameters != null) {
                        for (String param : bean.parameters) {
                            if (param != null && param.startsWith("@") && param.length() > 1) {
                                try {
                                    childBlockIds.add(Integer.parseInt(param.substring(1)));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }

            // Root blocks are those not pointed to by nextBlock, subStack1, subStack2, or @param
            List<BlockBean> rootBlocks = new ArrayList<>();
            for (BlockBean bean : blocks) {
                try {
                    int id = Integer.parseInt(bean.id);
                    if (!childBlockIds.contains(id)) {
                        rootBlocks.add(bean);
                    }
                } catch (NumberFormatException ignored) {}
            }

            JSONArray bodyArray = new JSONArray();
            for (BlockBean root : rootBlocks) {
                JSONArray chain = processBlockChain(root, blockMap);
                for (int idx = 0; idx < chain.length(); idx++) {
                    bodyArray.put(chain.get(idx));
                }
            }

            ir.put("type", "event_logic");
            ir.put("body", bodyArray);
        } catch (Exception e) {
            Log.e(TAG, "Error in blocksToIr", e);
        }
        return ir;
    }

    private static JSONArray processBlockChain(BlockBean startBlock, Map<Integer, BlockBean> blockMap) throws JSONException {
        JSONArray chain = new JSONArray();
        BlockBean current = startBlock;

        while (current != null) {
            JSONObject node = blockToIrNode(current, blockMap);
            chain.put(node);

            if (current.nextBlock >= 0 && blockMap.containsKey(current.nextBlock)) {
                current = blockMap.get(current.nextBlock);
            } else {
                current = null;
            }
        }

        return chain;
    }

    private static JSONObject blockToIrNode(BlockBean bean, Map<Integer, BlockBean> blockMap) throws JSONException {
        JSONObject node = new JSONObject();
        String opCode = bean.opCode != null ? bean.opCode : "";

        switch (opCode) {
            case "addSourceDirectly":
                node.put("type", "raw_code");
                node.put("opCode", "addSourceDirectly");
                String code = (!bean.parameters.isEmpty()) ? bean.parameters.get(0) : "";
                node.put("code", code);
                return node;

            case "if":
            case "ifElse":
                node.put("type", opCode);
                node.put("opCode", opCode);

                Object condObj = resolveParameterValue(bean.parameters, 0, blockMap);
                node.put("condition", condObj != null ? condObj : "true");

                if (bean.subStack1 >= 0 && blockMap.containsKey(bean.subStack1)) {
                    node.put("then", processBlockChain(blockMap.get(bean.subStack1), blockMap));
                } else {
                    node.put("then", new JSONArray());
                }

                if ("ifElse".equals(opCode)) {
                    if (bean.subStack2 >= 0 && blockMap.containsKey(bean.subStack2)) {
                        node.put("else", processBlockChain(blockMap.get(bean.subStack2), blockMap));
                    } else {
                        node.put("else", new JSONArray());
                    }
                }

                return node;

            case "repeat":
            case "forever":
            case "while":
                node.put("type", opCode);
                node.put("opCode", opCode);

                if (!bean.parameters.isEmpty()) {
                    if ("while".equals(opCode)) {
                        node.put("condition", resolveParameterValue(bean.parameters, 0, blockMap));
                    } else {
                        node.put("count", resolveParameterValue(bean.parameters, 0, blockMap));
                    }
                }

                if (bean.subStack1 >= 0 && blockMap.containsKey(bean.subStack1)) {
                    node.put("body", processBlockChain(blockMap.get(bean.subStack1), blockMap));
                } else {
                    node.put("body", new JSONArray());
                }

                return node;

            default:
                boolean isExpression = "b".equals(bean.type) || "d".equals(bean.type)
                        || "s".equals(bean.type) || "v".equals(bean.type)
                        || "n".equals(bean.type) || "a".equals(bean.type)
                        || "l".equals(bean.type);

                node.put("type", isExpression ? "expression" : "statement");
                node.put("opCode", opCode);
                node.put("spec", bean.spec != null ? bean.spec : "");

                JSONArray paramsArr = new JSONArray();
                if (bean.parameters != null) {
                    for (int p = 0; p < bean.parameters.size(); p++) {
                        Object resolved = resolveParameterValue(bean.parameters, p, blockMap);
                        paramsArr.put(resolved != null ? resolved : "");
                    }
                }
                node.put("parameters", paramsArr);

                return node;
        }
    }

    /**
     * Resolves a parameter at position p. If it references `@id` (an embedded expression block),
     * it recursively resolves that child block into an expression JSON object.
     */
    private static Object resolveParameterValue(List<String> parameters, int index, Map<Integer, BlockBean> blockMap) throws JSONException {
        if (parameters == null || index < 0 || index >= parameters.size()) {
            return null;
        }

        String rawVal = parameters.get(index);
        if (rawVal != null && rawVal.startsWith("@") && rawVal.length() > 1) {
            try {
                int childId = Integer.parseInt(rawVal.substring(1));
                BlockBean childBlock = blockMap.get(childId);
                if (childBlock != null) {
                    return blockToIrNode(childBlock, blockMap);
                }
            } catch (NumberFormatException ignored) {}
        }

        return rawVal;
    }

    // =========================================================================
    // 2. IR -> JAVA (Abstract IR JSON -> Readable Formatted Java Code)
    // =========================================================================

    /**
     * Converts an IR JSON structure into readable Java code.
     *
     * @param ir The IR JSONObject.
     * @return Formatted Java code string.
     */
    public static String irToJava(JSONObject ir) {
        StringBuilder sb = new StringBuilder();
        try {
            JSONArray body = ir.optJSONArray("body");
            if (body != null) {
                irArrayToJava(body, sb, "");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in irToJava", e);
        }
        return sb.toString().trim();
    }

    private static void irArrayToJava(JSONArray array, StringBuilder sb, String indent) {
        if (array == null) return;

        for (int i = 0; i < array.length(); i++) {
            JSONObject node = array.optJSONObject(i);
            if (node == null) continue;

            String type = node.optString("type", "statement");

            switch (type) {
                case "raw_code":
                    String code = node.optString("code", "");
                    for (String line : code.split("\n")) {
                        sb.append(indent).append(line).append("\n");
                    }
                    break;

                case "if":
                case "ifElse":
                    String condition = formatParamToJava(node.opt("condition"));
                    sb.append(indent).append("if (").append(condition).append(") {\n");

                    JSONArray thenArr = node.optJSONArray("then");
                    irArrayToJava(thenArr, sb, indent + "    ");

                    JSONArray elseArr = node.optJSONArray("else");
                    if ("ifElse".equals(type) || (elseArr != null && elseArr.length() > 0)) {
                        sb.append(indent).append("} else {\n");
                        irArrayToJava(elseArr, sb, indent + "    ");
                    }

                    sb.append(indent).append("}\n");
                    break;

                case "repeat":
                    String count = formatParamToJava(node.opt("count"));
                    sb.append(indent).append("for (int _repeat_i = 0; _repeat_i < ").append(count).append("; _repeat_i++) {\n");
                    JSONArray bodyArr = node.optJSONArray("body");
                    irArrayToJava(bodyArr, sb, indent + "    ");
                    sb.append(indent).append("}\n");
                    break;

                case "forever":
                    sb.append(indent).append("while (true) {\n");
                    JSONArray fBodyArr = node.optJSONArray("body");
                    irArrayToJava(fBodyArr, sb, indent + "    ");
                    sb.append(indent).append("}\n");
                    break;

                case "while":
                    String whileCond = formatParamToJava(node.has("condition") ? node.opt("condition") : node.opt("count"));
                    if (whileCond.isEmpty()) whileCond = "true";
                    sb.append(indent).append("while (").append(whileCond).append(") {\n");
                    JSONArray wBodyArr = node.optJSONArray("body");
                    irArrayToJava(wBodyArr, sb, indent + "    ");
                    sb.append(indent).append("}\n");
                    break;

                default:
                    // Statement Node
                    String opCode = node.optString("opCode", "");
                    JSONArray params = node.optJSONArray("parameters");

                    String formattedCall = formatOpCodeToJava(opCode, params);
                    sb.append(indent).append(formattedCall).append("\n");
                    break;
            }
        }
    }

    private static String formatParamToJava(Object paramObj) {
        if (paramObj == null) return "\"\"";
        if (paramObj instanceof JSONObject exprNode) {
            return formatExpressionToJava(exprNode);
        }
        return paramObj.toString();
    }

    private static String formatExpressionToJava(JSONObject expr) {
        String opCode = expr.optString("opCode", "");
        JSONArray params = expr.optJSONArray("parameters");

        List<String> paramList = new ArrayList<>();
        if (params != null) {
            for (int i = 0; i < params.length(); i++) {
                paramList.add(formatParamToJava(params.opt(i)));
            }
        }

        switch (opCode) {
            case "getVar":
            case "getVarInt":
            case "getVarDouble":
            case "getVarString":
            case "getVarBool":
                return !paramList.isEmpty() ? paramList.get(0) : "var";

            case "stringJoin":
            case "concat":
                // Join ALL supplied parts, not just the first two - Sketchware string-join
                // blocks can chain an arbitrary number of arguments.
                if (paramList.isEmpty()) return "\"\"";
                return String.join(" + ", paramList);

            case "operatorAdd":
                return "(" + (!paramList.isEmpty() ? paramList.get(0) : "0") + " + " + (paramList.size() > 1 ? paramList.get(1) : "0") + ")";

            case "operatorMinus":
                return "(" + (!paramList.isEmpty() ? paramList.get(0) : "0") + " - " + (paramList.size() > 1 ? paramList.get(1) : "0") + ")";

            case "operatorMultiply":
                return "(" + (!paramList.isEmpty() ? paramList.get(0) : "0") + " * " + (paramList.size() > 1 ? paramList.get(1) : "0") + ")";

            case "operatorDivide":
                return "(" + (!paramList.isEmpty() ? paramList.get(0) : "0") + " / " + (paramList.size() > 1 ? paramList.get(1) : "0") + ")";

            case "equal":
                // Objects.equals is null-safe and works whether the operands are primitives
                // (auto-boxed) or Strings/objects - a plain a.equals(b) call breaks at compile
                // time the moment either side is a primitive int/double/boolean.
                return "java.util.Objects.equals(" + (!paramList.isEmpty() ? paramList.get(0) : "") + ", " + (paramList.size() > 1 ? paramList.get(1) : "") + ")";

            case "not":
                return "!(" + (!paramList.isEmpty() ? paramList.get(0) : "true") + ")";

            case "stringLength":
                return (!paramList.isEmpty() ? paramList.get(0) : "str") + ".length()";

            default:
                if (paramList.isEmpty()) {
                    return opCode + "()";
                } else {
                    return opCode + "(" + String.join(", ", paramList) + ")";
                }
        }
    }

    private static String formatOpCodeToJava(String opCode, JSONArray params) {
        List<String> paramList = new ArrayList<>();
        if (params != null) {
            for (int i = 0; i < params.length(); i++) {
                paramList.add(formatParamToJava(params.opt(i)));
            }
        }

        switch (opCode) {
            case "doToast":
                String msg = !paramList.isEmpty() ? paramList.get(0) : "\"\"";
                return "SketchwareUtil.showMessage(getApplicationContext(), " + msg + ");";

            case "setVarInt":
            case "setVarDouble":
            case "setVarString":
            case "setVarBool":
                String varName = !paramList.isEmpty() ? paramList.get(0) : "var";
                String val = paramList.size() > 1 ? paramList.get(1) : "0";
                return varName + " = " + val + ";";

            case "intentSetScreen":
                String intent = !paramList.isEmpty() ? paramList.get(0) : "intent";
                String target = paramList.size() > 1 ? paramList.get(1) : "TargetActivity";
                return intent + ".setClass(getApplicationContext(), " + target + ".class);";

            default:
                if (paramList.isEmpty()) {
                    return opCode + "();";
                } else {
                    return opCode + "(" + String.join(", ", paramList) + ");";
                }
        }
    }

    // =========================================================================
    // 3. JAVA -> IR (Java Source Code String -> Abstract IR JSON)
    // =========================================================================

    /**
     * Parses Java source code back into the abstract IR JSON shape.
     * Unrecognized statements are cleanly grouped into raw_code ASD blocks.
     * Losslessly parses repeat `for` loops, forever `while(true)` loops, and conditional `while(cond)` loops.
     *
     * @param javaCode The Java code string.
     * @return JSONObject representing the IR tree.
     */
    public static JSONObject javaToIr(String javaCode) {
        JSONObject ir = new JSONObject();
        try {
            ir.put("type", "event_logic");

            JSONArray body = parseJavaLinesToIr(javaCode);
            ir.put("body", body);
        } catch (Exception e) {
            Log.e(TAG, "Error in javaToIr", e);
        }
        return ir;
    }

    private static JSONArray parseJavaLinesToIr(String javaCode) throws JSONException {
        JSONArray body = new JSONArray();
        if (javaCode == null || javaCode.trim().isEmpty()) {
            return body;
        }

        String[] lines = javaCode.split("\n");
        int i = 0;
        StringBuilder rawCodeBuffer = new StringBuilder();

        while (i < lines.length) {
            String line = lines[i].trim();

            if (line.isEmpty() || line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
                if (rawCodeBuffer.length() > 0) {
                    rawCodeBuffer.append("\n").append(line);
                }
                i++;
                continue;
            }

            // IF / IF-ELSE Parsing
            if (line.startsWith("if (") || line.startsWith("if(")) {
                flushRawBuffer(body, rawCodeBuffer);

                JSONObject ifNode = new JSONObject();
                ifNode.put("type", "if");
                ifNode.put("opCode", "if");

                String condition = extractOuterBracketContent(line, 0);
                ifNode.put("condition", !condition.isEmpty() ? condition : "true");

                int blockStart = i;
                int braceCount = 0;
                StringBuilder thenBuilder = new StringBuilder();
                boolean foundElse = false;
                StringBuilder elseBuilder = new StringBuilder();
                boolean insideElse = false;

                while (i < lines.length) {
                    String current = lines[i];
                    for (char c : current.toCharArray()) {
                        if (c == '{') braceCount++;
                        if (c == '}') braceCount--;
                    }

                    if (i > blockStart) {
                        if (current.trim().startsWith("} else {") || current.trim().startsWith("}else{")) {
                            insideElse = true;
                            foundElse = true;
                            i++;
                            continue;
                        }

                        if (braceCount == 0 && current.trim().endsWith("}")) {
                            i++;
                            break;
                        }

                        if (insideElse) {
                            elseBuilder.append(current).append("\n");
                        } else {
                            thenBuilder.append(current).append("\n");
                        }
                    }
                    i++;
                }

                ifNode.put("then", parseJavaLinesToIr(thenBuilder.toString()));

                if (foundElse) {
                    ifNode.put("type", "ifElse");
                    ifNode.put("opCode", "ifElse");
                    ifNode.put("else", parseJavaLinesToIr(elseBuilder.toString()));
                }

                body.put(ifNode);
                continue;
            }

            // FOR Parsing: consume the whole block first, THEN decide whether it fits the
            // simple "repeat" shape. Loops that don't fit (>=, !=, compound conditions,
            // non-standard increments) are preserved whole as raw_code instead of being
            // shredded line-by-line, which was silently mangling anything but `for(...; i < N; i++)`.
            if (line.startsWith("for (") || line.startsWith("for(")) {
                flushRawBuffer(body, rawCodeBuffer);

                int blockStart = i;
                int braceCount = 0;
                StringBuilder wholeBlockBuilder = new StringBuilder();
                StringBuilder bodyBuilder = new StringBuilder();

                while (i < lines.length) {
                    String current = lines[i];
                    wholeBlockBuilder.append(current).append("\n");

                    for (char c : current.toCharArray()) {
                        if (c == '{') braceCount++;
                        if (c == '}') braceCount--;
                    }

                    if (i > blockStart && braceCount > 0) {
                        bodyBuilder.append(current).append("\n");
                    }

                    if (i > blockStart && braceCount == 0) {
                        i++;
                        break;
                    }
                    i++;
                }

                Matcher forMatcher = FOR_LOOP_PATTERN.matcher(line);
                if (forMatcher.find()) {
                    JSONObject repeatNode = new JSONObject();
                    repeatNode.put("type", "repeat");
                    repeatNode.put("opCode", "repeat");
                    String countVal = forMatcher.group(2);
                    repeatNode.put("count", countVal != null ? countVal.trim() : "10");
                    repeatNode.put("body", parseJavaLinesToIr(bodyBuilder.toString()));
                    body.put(repeatNode);
                } else {
                    // Doesn't map to a native "repeat" block - keep it intact as one raw_code
                    // node rather than corrupting it by parsing line-by-line.
                    JSONObject rawNode = new JSONObject();
                    rawNode.put("type", "raw_code");
                    rawNode.put("opCode", "addSourceDirectly");
                    rawNode.put("code", wholeBlockBuilder.toString().trim());
                    body.put(rawNode);
                }
                continue;
            }

            // WHILE / FOREVER Parsing
            if (line.startsWith("while (") || line.startsWith("while(")) {
                flushRawBuffer(body, rawCodeBuffer);

                JSONObject whileNode = new JSONObject();
                String condition = extractOuterBracketContent(line, 0);

                if ("true".equals(condition)) {
                    whileNode.put("type", "forever");
                    whileNode.put("opCode", "forever");
                } else {
                    whileNode.put("type", "while");
                    whileNode.put("opCode", "while");
                    whileNode.put("condition", !condition.isEmpty() ? condition : "true");
                }

                int blockStart = i;
                int braceCount = 0;
                StringBuilder bodyBuilder = new StringBuilder();

                while (i < lines.length) {
                    String current = lines[i];
                    for (char c : current.toCharArray()) {
                        if (c == '{') braceCount++;
                        if (c == '}') braceCount--;
                    }

                    if (i > blockStart && braceCount > 0) {
                        bodyBuilder.append(current).append("\n");
                    }

                    if (i > blockStart && braceCount == 0) {
                        i++;
                        break;
                    }
                    i++;
                }

                whileNode.put("body", parseJavaLinesToIr(bodyBuilder.toString()));
                body.put(whileNode);
                continue;
            }

            // Toast / Sketchware Message Macro
            if (line.contains("SketchwareUtil.showMessage(")) {
                flushRawBuffer(body, rawCodeBuffer);

                JSONObject toastNode = new JSONObject();
                toastNode.put("type", "statement");
                toastNode.put("opCode", "doToast");
                toastNode.put("spec", "toast %s");

                JSONArray params = new JSONArray();
                List<String> args = extractTopLevelMethodArguments(line);
                String msg = args.size() > 1 ? args.get(1) : (!args.isEmpty() ? args.get(0) : "\"\"");
                params.put(msg);
                toastNode.put("parameters", params);

                body.put(toastNode);
                i++;
                continue;
            }

            // Accumulate unmapped statements in rawCodeBuffer (fallback safety net)
            if (rawCodeBuffer.length() > 0) {
                rawCodeBuffer.append("\n");
            }
            rawCodeBuffer.append(line);

            i++;
        }

        flushRawBuffer(body, rawCodeBuffer);
        return body;
    }

    private static void flushRawBuffer(JSONArray body, StringBuilder buffer) throws JSONException {
        if (buffer.length() > 0) {
            JSONObject rawNode = new JSONObject();
            rawNode.put("type", "raw_code");
            rawNode.put("opCode", "addSourceDirectly");
            rawNode.put("code", buffer.toString().trim());
            body.put(rawNode);
            buffer.setLength(0);
        }
    }

    /**
     * Token-aware outer parenthesis extractor. Properly respects nested parentheses, string literals,
     * character literals, and escaped characters.
     */
    private static String extractOuterBracketContent(String text, int searchStartPos) {
        if (text == null || text.isEmpty()) return "";

        int startParen = text.indexOf('(', searchStartPos);
        if (startParen == -1) return "";

        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int i = startParen; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"' && !inChar) {
                inString = !inString;
                continue;
            }

            if (c == '\'' && !inString) {
                inChar = !inChar;
                continue;
            }

            if (!inString && !inChar) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(startParen + 1, i).trim();
                    }
                }
            }
        }

        return text.substring(startParen + 1).trim();
    }

    /**
     * Token-aware top-level argument splitter for method calls. Respects string literals and nested brackets.
     */
    private static List<String> extractTopLevelMethodArguments(String line) {
        List<String> args = new ArrayList<>();
        String content = extractOuterBracketContent(line, 0);
        if (content.isEmpty()) return args;

        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        StringBuilder currentArg = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (escaped) {
                escaped = false;
                currentArg.append(c);
                continue;
            }

            if (c == '\\') {
                escaped = true;
                currentArg.append(c);
                continue;
            }

            if (c == '"' && !inChar) {
                inString = !inString;
                currentArg.append(c);
                continue;
            }

            if (c == '\'' && !inString) {
                inChar = !inChar;
                currentArg.append(c);
                continue;
            }

            if (!inString && !inChar) {
                if (c == '(' || c == '[' || c == '{') {
                    depth++;
                } else if (c == ')' || c == ']' || c == '}') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    args.add(currentArg.toString().trim());
                    currentArg.setLength(0);
                    continue;
                }
            }

            currentArg.append(c);
        }

        if (currentArg.length() > 0) {
            args.add(currentArg.toString().trim());
        }

        return args;
    }

    // =========================================================================
    // 4. IR -> BLOCKS (Abstract IR JSON -> Sketchware BlockBean List)
    // =========================================================================

    /**
     * Converts an abstract IR JSON structure back into Sketchware {@link BlockBean} objects.
     * Guaranteed consistent ID indexing to avoid orphaned links.
     * Recursively reconstructs `@id` embedded expression blocks.
     *
     * @param ir The IR JSONObject.
     * @return List of reconstructed BlockBean objects ready for Sketchware.
     */
    public static ArrayList<BlockBean> irToBlocks(JSONObject ir) {
        ensureBlockSpecCache();
        ArrayList<BlockBean> blocks = new ArrayList<>();
        try {
            JSONArray body = ir.optJSONArray("body");
            if (body != null && body.length() > 0) {
                IntWrapper idCounter = new IntWrapper(1);
                parseIrArrayToBlocks(body, blocks, idCounter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in irToBlocks", e);
        }
        return blocks;
    }

    private static int parseIrArrayToBlocks(JSONArray array, ArrayList<BlockBean> blocks, IntWrapper idCounter) {
        if (array == null || array.length() == 0) {
            return -1;
        }

        int firstBlockId = -1;
        BlockBean previousBlock = null;

        for (int idx = 0; idx < array.length(); idx++) {
            JSONObject node = array.optJSONObject(idx);
            if (node == null) continue;

            int currentId = idCounter.getAndIncrement();
            String currentIdStr = String.valueOf(currentId);

            if (firstBlockId == -1) {
                firstBlockId = currentId;
            }

            if (previousBlock != null) {
                previousBlock.nextBlock = currentId;
            }

            String type = node.optString("type", "statement");
            String opCode = node.optString("opCode", "");

            if ("raw_code".equals(type) || "addSourceDirectly".equals(opCode)) {
                BlockBean block = new BlockBean(currentIdStr, "", "e", "", "addSourceDirectly");
                block.color = -11751600; // Command block Teal (#26A69A)
                block.nextBlock = -1;
                block.subStack1 = -1;
                block.subStack2 = -1;

                block.parameters = new ArrayList<>();
                block.parameters.add(node.optString("code", ""));

                blocks.add(block);
                previousBlock = block;
                continue;
            }

            // Look up built-in spec/type metadata if available
            BlockSpecDef specDef = blockSpecCache.get(opCode);
            String spec = node.optString("spec", specDef != null ? specDef.spec : "");
            String bType = specDef != null ? specDef.type : "e";
            String bTypeName = specDef != null ? specDef.typeName : "";

            boolean isLoop = "repeat".equals(type) || "forever".equals(type) || "while".equals(type);

            if ("if".equals(type) || "ifElse".equals(type)) {
                bType = "c";
                spec = "ifElse".equals(type) ? "if %b else" : "if %b";
            } else if (isLoop) {
                bType = "c";
                spec = "repeat".equals(type) ? "repeat %d" : ("while".equals(type) ? "while %b" : "forever");
            }

            BlockBean block = new BlockBean(currentIdStr, spec, bType, bTypeName, opCode);
            if (specDef != null) {
                block.color = specDef.color;
            }
            block.nextBlock = -1;
            block.subStack1 = -1;
            block.subStack2 = -1;

            if ("if".equals(type) || "ifElse".equals(type)) {
                block.parameters = new ArrayList<>();
                String paramRef = processParameterToBlock(node.opt("condition"), blocks, idCounter);
                block.parameters.add(paramRef != null ? paramRef : "true");

                JSONArray thenArr = node.optJSONArray("then");
                if (thenArr != null && thenArr.length() > 0) {
                    block.subStack1 = parseIrArrayToBlocks(thenArr, blocks, idCounter);
                }

                if ("ifElse".equals(type)) {
                    JSONArray elseArr = node.optJSONArray("else");
                    if (elseArr != null && elseArr.length() > 0) {
                        block.subStack2 = parseIrArrayToBlocks(elseArr, blocks, idCounter);
                    }
                }
            } else if (isLoop) {
                block.parameters = new ArrayList<>();
                if ("repeat".equals(type)) {
                    String paramRef = processParameterToBlock(node.opt("count"), blocks, idCounter);
                    block.parameters.add(paramRef != null ? paramRef : "10");
                } else if ("while".equals(type)) {
                    String paramRef = processParameterToBlock(node.opt("condition"), blocks, idCounter);
                    block.parameters.add(paramRef != null ? paramRef : "true");
                }

                JSONArray bodyArr = node.optJSONArray("body");
                if (bodyArr != null && bodyArr.length() > 0) {
                    block.subStack1 = parseIrArrayToBlocks(bodyArr, blocks, idCounter);
                }
            } else { // Generic Statement / Expression
                block.parameters = new ArrayList<>();
                JSONArray params = node.optJSONArray("parameters");
                if (params != null) {
                    for (int p = 0; p < params.length(); p++) {
                        String paramRef = processParameterToBlock(params.opt(p), blocks, idCounter);
                        block.parameters.add(paramRef != null ? paramRef : "");
                    }
                }
            }

            blocks.add(block);
            previousBlock = block;
        }

        return firstBlockId;
    }

    /**
     * Processes a parameter value when reconstructing blocks. If the parameter is an embedded
     * expression JSON object, it recursively generates a child expression BlockBean,
     * appends it to blocks, and returns `@childId`.
     */
    private static String processParameterToBlock(Object paramObj, ArrayList<BlockBean> blocks, IntWrapper idCounter) {
        if (paramObj == null) return "";

        if (paramObj instanceof JSONObject exprNode) {
            return parseExpressionIrToBlock(exprNode, blocks, idCounter);
        }

        return paramObj.toString();
    }

    private static String parseExpressionIrToBlock(JSONObject exprNode, ArrayList<BlockBean> blocks, IntWrapper idCounter) {
        int currentId = idCounter.getAndIncrement();
        String currentIdStr = String.valueOf(currentId);

        String opCode = exprNode.optString("opCode", "");

        BlockSpecDef specDef = blockSpecCache.get(opCode);
        String spec = exprNode.optString("spec", specDef != null ? specDef.spec : "");
        String bType = specDef != null ? specDef.type : "s"; // Default string expression
        String bTypeName = exprNode.optString("typeName", specDef != null ? specDef.typeName : "");

        BlockBean exprBlock = new BlockBean(currentIdStr, spec, bType, bTypeName, opCode);
        if (specDef != null) {
            exprBlock.color = specDef.color;
        }
        exprBlock.nextBlock = -1;
        exprBlock.subStack1 = -1;
        exprBlock.subStack2 = -1;

        exprBlock.parameters = new ArrayList<>();
        JSONArray params = exprNode.optJSONArray("parameters");
        if (params != null) {
            for (int p = 0; p < params.length(); p++) {
                String paramRef = processParameterToBlock(params.opt(p), blocks, idCounter);
                exprBlock.parameters.add(paramRef != null ? paramRef : "");
            }
        }

        blocks.add(exprBlock);
        return "@" + currentIdStr;
    }

    private static class IntWrapper {
        private int value;

        public IntWrapper(int initialValue) {
            this.value = initialValue;
        }

        public int getAndIncrement() {
            return value++;
        }
    }

    // =========================================================================
    // 5. HELPER INTEGRATION WITH SKETCHWARE PROJECT (eC / jC)
    // =========================================================================

    /**
     * Loads event logic from Sketchware project storage and converts it directly into IR JSON.
     * Safely wrapped with error handling around internal Sketchware APIs.
     *
     * @param scId The project ID (e.g. "601").
     * @param javaName The activity/file name (e.g. "main").
     * @param eventKey The event key (e.g. "onCreate" or "button1_onClick").
     * @return JSONObject representing the IR.
     */
    public static JSONObject loadIrFromProject(String scId, String javaName, String eventKey) {
        try {
            eC manager = jC.a(scId);
            if (manager == null) {
                Log.e(TAG, "loadIrFromProject: eC manager is null for scId: " + scId);
                return new JSONObject().put("type", "event_logic").put("body", new JSONArray());
            }

            synchronized (manager) {
                ArrayList<BlockBean> blocks = manager.a(javaName, eventKey);
                return blocksToIr(blocks);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load IR from project scId: " + scId + ", event: " + eventKey, e);
            try {
                return new JSONObject().put("type", "event_logic").put("body", new JSONArray());
            } catch (JSONException ignored) {
                return new JSONObject();
            }
        }
    }

    /**
     * Converts an IR JSON structure into Sketchware BlockBeans and saves it directly to project storage.
     * Safely wrapped with error handling around internal Sketchware APIs.
     *
     * @param scId The project ID (e.g. "601").
     * @param javaName The activity/file name (e.g. "main").
     * @param eventKey The event key (e.g. "onCreate" or "button1_onClick").
     * @param ir The IR JSONObject to apply.
     * @return true if successful.
     */
    public static boolean saveIrToProject(String scId, String javaName, String eventKey, JSONObject ir) {
        try {
            if (ir == null) return false;

            ArrayList<BlockBean> newBlocks = irToBlocks(ir);
            eC manager = jC.a(scId);
            if (manager == null) {
                Log.e(TAG, "saveIrToProject: eC manager is null for scId: " + scId);
                return false;
            }

            synchronized (manager) {
                manager.a(javaName, eventKey, newBlocks);
                manager.k(); // Persist changes to disk
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save IR to project scId: " + scId + ", event: " + eventKey, e);
            return false;
        }
    }
}
