package sketchware.plus.ai;

import android.graphics.Color;
import android.util.Log;
import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import mod.hey.studios.moreblock.ReturnMoreblockManager;
import mod.hilal.saif.events.EventsHandler;

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
import a.a.a.hC;
import a.a.a.jC;
import a.a.a.wq;
import dev.aldi.sayuti.block.ExtraBlockFile;
import mod.hilal.saif.blocks.BlocksHandler;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    public static void setLogPath(String path) {
    }

    public static void setScId(String scId) {
    }

    public static void log(String scId, String message) {
        Log.d(TAG, message);
    }

    public static void log(String message) {
        log(null, message);
    }

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

        public ValidationResult(boolean isValid, String errorMessage) {
            this(isValid, errorMessage, null, 0);
        }
    }

    public static boolean isOpCodeValid(String opCode, String type) {
        return isOpCodeValid(opCode, type, null, null);
    }

    public static boolean isOpCodeValid(String opCode, String type, String scId, String javaName) {
        ensureBlockSpecCache();

        if (opCode == null) opCode = "";
        if (type == null) type = "";

        if ("raw_code".equals(type) || "addSourceDirectly".equals(opCode)) {
            return true;
        }
        if ("if".equals(type) || "ifElse".equals(type) || "repeat".equals(type) || "while".equals(type) || "forever".equals(type)) {
            return true;
        }
        if ("if".equals(opCode) || "ifElse".equals(opCode) || "repeat".equals(opCode) || "while".equals(opCode) || "forever".equals(opCode)) {
            return true;
        }

        if (opCode.startsWith("getVar")) {
            return true;
        }

        if (blockSpecCache.containsKey(opCode)) {
            return true;
        }

        if (scId != null && !scId.trim().isEmpty() && javaName != null && !javaName.trim().isEmpty()) {
            javaName = ProjectStructureAide.resolveJavaName(scId, javaName);
            eC dataManager = jC.a(scId);
            if (dataManager != null) {
                ArrayList<Pair<String, String>> mbs = dataManager.i(javaName);
                if (mbs != null) {
                    for (Pair<String, String> mb : mbs) {
                        if (mb == null || mb.first == null) continue;
                        String name = ReturnMoreblockManager.getMbName(mb.first);
                        if (opCode.equalsIgnoreCase(name)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static void validateIrNode(Object nodeObj, List<String> invalidOpcodes) {
        validateIrNode(nodeObj, null, null, invalidOpcodes);
    }

    private static void validateIrNode(Object nodeObj, String scId, String javaName, List<String> invalidOpcodes) {
        if (nodeObj == null) return;

        if (nodeObj instanceof JSONArray arr) {
            for (int i = 0; i < arr.length(); i++) {
                validateIrNode(arr.opt(i), scId, javaName, invalidOpcodes);
            }
            return;
        }

        if (nodeObj instanceof JSONObject node) {
            String type = node.optString("type", "");
            String opCode = node.optString("opCode", "");

            if (!"raw_code".equals(type) && !"addSourceDirectly".equals(opCode)) {
                if (!opCode.isEmpty() && !isOpCodeValid(opCode, type, scId, javaName)) {
                    invalidOpcodes.add(opCode);
                }
            }

            if (node.has("then")) validateIrNode(node.opt("then"), scId, javaName, invalidOpcodes);
            if (node.has("else")) validateIrNode(node.opt("else"), scId, javaName, invalidOpcodes);
            if (node.has("body")) validateIrNode(node.opt("body"), scId, javaName, invalidOpcodes);
            if (node.has("condition")) validateIrNode(node.opt("condition"), scId, javaName, invalidOpcodes);
            if (node.has("count")) validateIrNode(node.opt("count"), scId, javaName, invalidOpcodes);

            JSONArray params = node.optJSONArray("parameters");
            if (params != null) {
                for (int p = 0; p < params.length(); p++) {
                    validateIrNode(params.opt(p), scId, javaName, invalidOpcodes);
                }
            }
        }
    }

    public static ValidationResult validateIr(JSONObject ir) {
        return validateIr(ir, null, null);
    }

    public static ValidationResult validateIr(JSONObject ir, String scId, String javaName) {
        if (ir == null) {
            return new ValidationResult(false, "IR is null");
        }

        List<String> invalidOpcodes = new ArrayList<>();
        JSONArray body = ir.optJSONArray("body");
        if (body != null) {
            validateIrNode(body, scId, javaName, invalidOpcodes);
        }

        if (!invalidOpcodes.isEmpty()) {
            return new ValidationResult(false, "Unsupported or unknown opcodes found: " + invalidOpcodes);
        }

        try {
            ArrayList<BlockBean> testBlocks = irToBlocks(ir);
            if (testBlocks == null) {
                return new ValidationResult(false, "Failed to convert IR to BlockBeans");
            }
            ValidationResult integrity = validateBlockIntegrity(testBlocks);
            if (!integrity.isValid) {
                return integrity;
            }
            return new ValidationResult(true, null, irToJava(ir), testBlocks.size());
        } catch (Exception e) {
            return new ValidationResult(false, "Error during IR block conversion test: " + e.getMessage());
        }
    }

    public static String irToJava(JSONObject ir) {
        if (ir == null) return "";
        StringBuilder sb = new StringBuilder();
        JSONArray body = ir.optJSONArray("body");
        if (body != null) {
            formatIrArrayToJava(body, sb, 0);
        } else {
            formatIrNodeToJava(ir, sb, 0);
        }
        return sb.toString().trim();
    }

    private static void formatIrArrayToJava(JSONArray array, StringBuilder sb, int indentLevel) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject node = array.optJSONObject(i);
            if (node != null) {
                formatIrNodeToJava(node, sb, indentLevel);
            }
        }
    }

    private static void formatIrNodeToJava(JSONObject node, StringBuilder sb, int indentLevel) {
        if (node == null) return;
        String type = node.optString("type", "");
        String opCode = node.optString("opCode", "");

        String indent = getIndent(indentLevel);

        if ("raw_code".equals(type) || "addSourceDirectly".equals(opCode)) {
            String code = node.optString("code", "");
            if (code.isEmpty()) {
                JSONArray params = node.optJSONArray("parameters");
                if (params != null && params.length() > 0) {
                    code = formatIrParam(params.opt(0));
                }
            }
            if (!code.isEmpty()) {
                sb.append(indent).append(code);
                if (!code.endsWith(";") && !code.endsWith("}") && !code.endsWith("{")) {
                    sb.append(";");
                }
                sb.append("\n");
            }
            return;
        }

        if ("if".equals(type) || "ifElse".equals(type) || "if".equals(opCode) || "ifElse".equals(opCode)) {
            String cond = formatIrParam(node.opt("condition"));
            if (cond.isEmpty()) cond = "true";
            sb.append(indent).append("if (").append(cond).append(") {\n");
            JSONArray thenArr = node.optJSONArray("then");
            formatIrArrayToJava(thenArr, sb, indentLevel + 1);
            if ("ifElse".equals(type) || "ifElse".equals(opCode)) {
                sb.append(indent).append("} else {\n");
                JSONArray elseArr = node.optJSONArray("else");
                formatIrArrayToJava(elseArr, sb, indentLevel + 1);
            }
            sb.append(indent).append("}\n");
            return;
        }

        if ("repeat".equals(type) || "repeat".equals(opCode)) {
            String count = formatIrParam(node.opt("count"));
            if (count.isEmpty()) count = "10";
            sb.append(indent).append("for (int _repeat = 0; _repeat < (int)(").append(count).append("); _repeat++) {\n");
            JSONArray bodyArr = node.optJSONArray("body");
            formatIrArrayToJava(bodyArr, sb, indentLevel + 1);
            sb.append(indent).append("}\n");
            return;
        }

        if ("while".equals(type) || "while".equals(opCode)) {
            String cond = formatIrParam(node.opt("condition"));
            if (cond.isEmpty()) cond = "true";
            sb.append(indent).append("while (").append(cond).append(") {\n");
            JSONArray bodyArr = node.optJSONArray("body");
            formatIrArrayToJava(bodyArr, sb, indentLevel + 1);
            sb.append(indent).append("}\n");
            return;
        }

        if ("forever".equals(type) || "forever".equals(opCode)) {
            sb.append(indent).append("while (true) {\n");
            JSONArray bodyArr = node.optJSONArray("body");
            formatIrArrayToJava(bodyArr, sb, indentLevel + 1);
            sb.append(indent).append("}\n");
            return;
        }

        ensureBlockSpecCache();
        BlockSpecDef specDef = blockSpecCache.get(opCode);
        String spec = node.optString("spec", specDef != null ? specDef.spec : "");

        List<String> formattedParams = new ArrayList<>();
        JSONArray params = node.optJSONArray("parameters");
        if (params != null) {
            for (int p = 0; p < params.length(); p++) {
                formattedParams.add(formatIrParam(params.opt(p)));
            }
        }

        String code = renderSpecCode(opCode, spec, formattedParams);
        if (!code.isEmpty()) {
            sb.append(indent).append(code);
            if (!code.endsWith(";") && !code.endsWith("}") && !code.endsWith("{")) {
                sb.append(";");
            }
            sb.append("\n");
        }
    }

    private static String formatIrParam(Object paramObj) {
        if (paramObj == null) return "";
        if (paramObj instanceof JSONObject) {
            JSONObject exprNode = (JSONObject) paramObj;
            String opCode = exprNode.optString("opCode", "");
            String spec = exprNode.optString("spec", "");
            if (spec.isEmpty() && blockSpecCache.containsKey(opCode)) {
                BlockSpecDef specDef = blockSpecCache.get(opCode);
                if (specDef != null) spec = specDef.spec;
            }
            List<String> formattedParams = new ArrayList<>();
            JSONArray params = exprNode.optJSONArray("parameters");
            if (params != null) {
                for (int p = 0; p < params.length(); p++) {
                    formattedParams.add(formatIrParam(params.opt(p)));
                }
            }
            return renderSpecCode(opCode, spec, formattedParams);
        }
        return paramObj.toString();
    }

    private static String renderSpecCode(String opCode, String spec, List<String> params) {
        if (spec != null && !spec.isEmpty()) {
            Pattern pattern = Pattern.compile("%[0-9]*\\$?[a-zA-Z0-9._]+|%[a-zA-Z]");
            Matcher matcher = pattern.matcher(spec);
            StringBuffer sb = new StringBuffer();
            int paramIdx = 0;
            while (matcher.find()) {
                String val = paramIdx < params.size() ? params.get(paramIdx) : "";
                matcher.appendReplacement(sb, Matcher.quoteReplacement(val));
                paramIdx++;
            }
            matcher.appendTail(sb);
            return sb.toString();
        }

        if (opCode != null && !opCode.isEmpty()) {
            StringBuilder call = new StringBuilder(opCode).append("(");
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) call.append(", ");
                call.append(params.get(i));
            }
            call.append(")");
            return call.toString();
        }

        return params.isEmpty() ? "" : params.get(0);
    }

    private static String getIndent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("    ");
        }
        return sb.toString();
    }

    /**
     * Post-build integrity check for a generated list of BlockBeans.
     * Verifies:
     * 1. All block IDs in list are unique.
     * 2. All referenced nextBlock, subStack1, subStack2, and @param IDs exist in the block map.
     * 3. No circular references/cycles in parent-child or nextBlock chains.
     * 4. If root block '0' exists, every block in the list is reachable starting from root block '0'.
     */
    public static ValidationResult validateBlockIntegrity(ArrayList<BlockBean> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return new ValidationResult(false, "Block list is null or empty.");
        }

        Map<Integer, BlockBean> blockMap = new HashMap<>();
        Set<Integer> duplicateCheck = new HashSet<>();

        for (BlockBean b : blocks) {
            if (b == null || b.id == null) {
                return new ValidationResult(false, "Found a null BlockBean or block with null id.");
            }
            int id;
            try {
                id = Integer.parseInt(b.id);
            } catch (NumberFormatException e) {
                return new ValidationResult(false, "Invalid non-integer block ID: " + b.id);
            }
            if (!duplicateCheck.add(id)) {
                return new ValidationResult(false, "Duplicate block ID found: " + id);
            }
            blockMap.put(id, b);
        }

        for (BlockBean b : blocks) {
            int id = Integer.parseInt(b.id);

            if (b.nextBlock >= 0 && !blockMap.containsKey(b.nextBlock)) {
                return new ValidationResult(false, "Block " + id + " references non-existent nextBlock ID " + b.nextBlock);
            }

            if (b.subStack1 >= 0 && !blockMap.containsKey(b.subStack1)) {
                return new ValidationResult(false, "Block " + id + " references non-existent subStack1 ID " + b.subStack1);
            }

            if (b.subStack2 >= 0 && !blockMap.containsKey(b.subStack2)) {
                return new ValidationResult(false, "Block " + id + " references non-existent subStack2 ID " + b.subStack2);
            }

            if (b.parameters != null) {
                for (String p : b.parameters) {
                    if (p != null && p.startsWith("@")) {
                        try {
                            int paramId = Integer.parseInt(p.substring(1));
                            if (!blockMap.containsKey(paramId)) {
                                return new ValidationResult(false, "Block " + id + " references non-existent parameter block ID " + paramId);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        if (blockMap.containsKey(0)) {
            Set<Integer> pathStack = new HashSet<>();
            Set<Integer> reachable = new HashSet<>();
            if (hasCycleAndCollectReachable(0, blockMap, pathStack, reachable)) {
                return new ValidationResult(false, "Detected circular reference cycle in block tree.");
            }

            if (reachable.size() < blocks.size()) {
                Set<Integer> orphans = new HashSet<>(blockMap.keySet());
                orphans.removeAll(reachable);
                return new ValidationResult(false, "Found orphan/unreachable blocks with IDs: " + orphans);
            }
        }

        return new ValidationResult(true, null);
    }

    private static boolean hasCycleAndCollectReachable(int id, Map<Integer, BlockBean> blockMap, Set<Integer> pathStack, Set<Integer> reachable) {
        if (pathStack.contains(id)) {
            return true;
        }
        reachable.add(id);
        pathStack.add(id);

        BlockBean b = blockMap.get(id);
        if (b != null) {
            if (b.nextBlock >= 0) {
                if (hasCycleAndCollectReachable(b.nextBlock, blockMap, pathStack, reachable)) return true;
            }
            if (b.subStack1 >= 0) {
                if (hasCycleAndCollectReachable(b.subStack1, blockMap, pathStack, reachable)) return true;
            }
            if (b.subStack2 >= 0) {
                if (hasCycleAndCollectReachable(b.subStack2, blockMap, pathStack, reachable)) return true;
            }
            if (b.parameters != null) {
                for (String p : b.parameters) {
                    if (p != null && p.startsWith("@")) {
                        try {
                            int paramId = Integer.parseInt(p.substring(1));
                            if (hasCycleAndCollectReachable(paramId, blockMap, pathStack, reachable)) return true;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        pathStack.remove(id);
        return false;
    }

    private static final Map<String, BlockSpecDef> blockSpecCache = new HashMap<>();

    public static class BlockSpecDef {
        public final String opCode;
        public final String spec;
        public final String type;
        public final String typeName;
        public final int color;

        public BlockSpecDef(String opCode, String spec, String type, String typeName, int color) {
            this.opCode = opCode;
            this.spec = spec;
            this.type = type;
            this.typeName = typeName;
            this.color = color;
        }
    }

    private static synchronized void ensureBlockSpecCache() {
        if (!blockSpecCache.isEmpty()) return;

        try {
            ArrayList<HashMap<String, Object>> builtIns = new ArrayList<>();
            BlocksHandler.builtInBlocks(builtIns);
            if (!builtIns.isEmpty()) {
                for (HashMap<String, Object> map : builtIns) {
                    if (map == null) continue;
                    String opCode = (String) map.get("name");
                    String spec = (String) map.get("spec");
                    String type = (String) map.get("type");
                    String typeName = (String) map.get("typeName");
                    Object colorObj = map.get("color");

                    int color = -11751600;
                    if (colorObj instanceof Integer) {
                        color = (Integer) colorObj;
                    } else if (colorObj instanceof String) {
                        try {
                            color = Color.parseColor((String) colorObj);
                        } catch (Exception ignored) {}
                    }

                    if (opCode != null && !opCode.isEmpty()) {
                        blockSpecCache.put(opCode, new BlockSpecDef(opCode, spec != null ? spec : "", type != null ? type : "e", typeName != null ? typeName : "", color));
                    }
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "Failed to load built-in block specs into cache", e);
        }
    }

    public static JSONObject javaToIr(String javaCode) {
        if (javaCode == null || javaCode.trim().isEmpty()) {
            try {
                return new JSONObject().put("type", "event_logic").put("body", new JSONArray());
            } catch (JSONException e) {
                return new JSONObject();
            }
        }

        JSONArray body = new JSONArray();
        String[] lines = javaCode.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) continue;

            try {
                JSONObject rawNode = new JSONObject();
                rawNode.put("type", "raw_code");
                rawNode.put("opCode", "addSourceDirectly");
                rawNode.put("code", trimmed);
                body.put(rawNode);
            } catch (JSONException ignored) {}
        }

        try {
            return new JSONObject()
                    .put("type", "event_logic")
                    .put("body", body);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

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
                block.color = -11751600;
                block.nextBlock = -1;
                block.subStack1 = -1;
                block.subStack2 = -1;

                block.parameters = new ArrayList<>();
                block.parameters.add(node.optString("code", ""));

                blocks.add(block);
                previousBlock = block;
                continue;
            }

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
            } else {
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
        String bType = specDef != null ? specDef.type : "s";
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

    public static String normalizeEventKey(String eventKey) {
        if (eventKey == null || eventKey.trim().isEmpty()) {
            return "onCreate_initializeLogic";
        }
        String trimmed = eventKey.trim();
        if ("onCreate".equalsIgnoreCase(trimmed) || "initializeLogic".equalsIgnoreCase(trimmed)) {
            return "onCreate_initializeLogic";
        }
        if ("Import".equalsIgnoreCase(trimmed)) {
            return "Import_Import";
        }
        if (!trimmed.contains("_")) {
            String[] actEvents = EventsHandler.getActivityEvents();
            if (actEvents != null) {
                for (String ae : actEvents) {
                    if (trimmed.equalsIgnoreCase(ae)) {
                        return ae + "_" + ae;
                    }
                }
            }
        }
        return trimmed;
    }

    public static boolean isKnownComponentEvent(String eventName) {
        if (eventName == null || eventName.trim().isEmpty()) return false;
        return getComponentTypeForEvent(eventName) != 0;
    }

    public static int getComponentTypeForEvent(String eventName) {
        if (eventName == null || eventName.trim().isEmpty()) return 0;
        String ev = eventName.trim();
        String evLower = ev.toLowerCase();

        // 1. RequestNetwork (17)
        if (evLower.contains("response") || evLower.contains("network")) return ComponentBean.COMPONENT_TYPE_REQUEST_NETWORK;

        // 2. FilePicker (16)
        if (evLower.contains("filespicked")) return ComponentBean.COMPONENT_TYPE_FILE_PICKER;

        // 3. Camera (15)
        if (evLower.contains("picturetaken")) return ComponentBean.COMPONENT_TYPE_CAMERA;

        // 4. FirebaseDB (6)
        if (evLower.contains("childadded") || evLower.contains("childchanged") ||
                evLower.contains("childremoved") || evLower.contains("childmoved") ||
                evLower.equals("oncancelled")) return ComponentBean.COMPONENT_TYPE_FIREBASE;

        // 5. FirebaseAuth (12)
        if (evLower.contains("usercomplete") || evLower.contains("signinuser") ||
                evLower.contains("createuser") || evLower.contains("resetpassword") ||
                evLower.contains("updateemail") || evLower.contains("updatepassword") ||
                evLower.contains("emailverification") || evLower.contains("deleteuser") ||
                evLower.contains("updateprofile") || evLower.contains("googlesignin")) {
            return ComponentBean.COMPONENT_TYPE_FIREBASE_AUTH;
        }

        // 6. Gyroscope (11)
        if (evLower.contains("sensorchanged")) return ComponentBean.COMPONENT_TYPE_GYROSCOPE;

        // 7. InterstitialAd (13)
        if (evLower.contains("interstitial")) return ComponentBean.COMPONENT_TYPE_INTERSTITIAL_AD;

        // 8. RewardedVideoAd (22)
        if (evLower.contains("rewardad") || evLower.contains("userearnedreward")) return ComponentBean.COMPONENT_TYPE_REWARDED_VIDEO_AD;

        // 9. FirebaseStorage (14)
        if (evLower.contains("uploadprogress") || evLower.contains("downloadprogress") ||
                evLower.contains("uploadsuccess") || evLower.contains("downloadsuccess") ||
                evLower.contains("deletesuccess")) return ComponentBean.COMPONENT_TYPE_FIREBASE_STORAGE;

        // 10. SpeechToText (19)
        if (evLower.contains("speechresult") || evLower.contains("speecherror")) return ComponentBean.COMPONENT_TYPE_SPEECH_TO_TEXT;

        // 11. BluetoothConnect (20)
        if (evLower.contains("connected") || evLower.contains("datareceived") ||
                evLower.contains("datasent") || evLower.contains("connectionerror") ||
                evLower.contains("connectionstopped")) return ComponentBean.COMPONENT_TYPE_BLUETOOTH_CONNECT;

        // 12. LocationManager (21)
        if (evLower.contains("locationchanged")) return ComponentBean.COMPONENT_TYPE_LOCATION_MANAGER;

        // 13. ObjectAnimator (10)
        if (evLower.contains("animationstart") || evLower.contains("animationend") ||
                evLower.contains("animationcancel")) return ComponentBean.COMPONENT_TYPE_OBJECTANIMATOR;

        // 14. FragmentAdapter (27)
        if (evLower.contains("tabadded") || evLower.contains("fragmentadded")) return ComponentBean.COMPONENT_TYPE_FRAGMENT_ADAPTER;

        // 15. TimePickerDialog (25)
        if (evLower.contains("timeset")) return ComponentBean.COMPONENT_TYPE_TIME_PICKER_DIALOG;

        // 16. DatePickerDialog (24)
        if (evLower.contains("dateset") || evLower.contains("datechanged")) return ComponentBean.COMPONENT_TYPE_DATE_PICKER_DIALOG;

        // 17. FirebasePhoneAuth (28)
        if (evLower.contains("verificationcompleted") || evLower.contains("verificationfailed") ||
                evLower.contains("codesent")) return ComponentBean.COMPONENT_TYPE_FIREBASE_AUTH_PHONE;

        // 18. FirebaseCloudMessage (29)
        if (evLower.contains("completeregister")) return ComponentBean.COMPONENT_TYPE_FIREBASE_CLOUD_MESSAGE;

        // 19. FirebaseGoogleLogin (30)
        if (evLower.contains("accountpicker")) return ComponentBean.COMPONENT_TYPE_FIREBASE_AUTH_GOOGLE_LOGIN;

        // 20. Timer (5)
        if (evLower.contains("timer") || evLower.contains("tick")) return ComponentBean.COMPONENT_TYPE_TIMERTASK;

        return 0;
    }

    public static boolean ensureEventRegistered(eC manager, String rawJavaName, String targetKind, String targetId, String eventName) {
        if (manager == null || rawJavaName == null || rawJavaName.trim().isEmpty() || eventName == null || eventName.trim().isEmpty()) {
            return false;
        }

        String scId = manager.a;
        String javaName = ProjectStructureAide.findActivityJavaName(scId, rawJavaName, rawJavaName);
        if (javaName == null) return false;

        String cleanTargetId = targetId != null ? targetId.trim() : eventName;
        String eventKey = (targetId != null && !targetId.isEmpty()) ? (targetId + "_" + eventName) : (eventName + "_" + eventName);
        eventKey = normalizeEventKey(eventKey);

        boolean isComponentTarget = false;
        ComponentBean matchedComponent = null;
        ArrayList<ComponentBean> components = manager.e(javaName);
        if (components != null && cleanTargetId != null) {
            for (ComponentBean cb : components) {
                if (cb != null && cleanTargetId.equalsIgnoreCase(cb.componentId)) {
                    isComponentTarget = true;
                    matchedComponent = cb;
                    cleanTargetId = cb.componentId; // Preserve exact casing
                    break;
                }
            }
        }

        if (!isComponentTarget && ("component".equalsIgnoreCase(targetKind) || isKnownComponentEvent(eventName))) {
            isComponentTarget = true;
            int inferredType = resolveComponentTargetType(manager, javaName, cleanTargetId, eventName);
            manager.a(javaName, inferredType, cleanTargetId);
            manager.k();
        }

        int eventType = EventBean.EVENT_TYPE_ACTIVITY;
        int targetType = 0;

        if (isComponentTarget || "component".equalsIgnoreCase(targetKind)) {
            eventType = EventBean.EVENT_TYPE_COMPONENT;
            targetType = matchedComponent != null ? matchedComponent.type : resolveComponentTargetType(manager, javaName, cleanTargetId, eventName);
        } else if ("view".equalsIgnoreCase(targetKind)) {
            eventType = EventBean.EVENT_TYPE_VIEW;
            targetType = resolveViewTargetType(manager, scId, javaName, cleanTargetId);
        } else if ("drawer_view".equalsIgnoreCase(targetKind)) {
            eventType = EventBean.EVENT_TYPE_DRAWER_VIEW;
            targetType = resolveDrawerViewTargetType(manager, scId, javaName, cleanTargetId);
        } else if ("activity".equalsIgnoreCase(targetKind)) {
            eventType = EventBean.EVENT_TYPE_ACTIVITY;
            targetType = 0;
        } else {
            eventType = isComponentTarget ? EventBean.EVENT_TYPE_COMPONENT : EventBean.EVENT_TYPE_VIEW;
            targetType = isComponentTarget ? resolveComponentTargetType(manager, javaName, cleanTargetId, eventName)
                    : resolveViewTargetType(manager, scId, javaName, cleanTargetId);
        }

        ArrayList<EventBean> events = manager.g(javaName);
        if (events == null) {
            events = new ArrayList<>();
        }

        for (EventBean eb : events) {
            if (eb != null && (eventKey.equalsIgnoreCase(eb.targetId + "_" + eb.eventName)
                    || (cleanTargetId.equalsIgnoreCase(eb.targetId) && eventName.equalsIgnoreCase(eb.eventName)))) {
                boolean needSave = false;
                if (isComponentTarget && eb.eventType != EventBean.EVENT_TYPE_COMPONENT) {
                    eb.eventType = EventBean.EVENT_TYPE_COMPONENT;
                    eb.targetType = targetType;
                    needSave = true;
                } else if (eb.targetType <= 0 && eventType != EventBean.EVENT_TYPE_ACTIVITY) {
                    eb.targetType = targetType;
                    needSave = true;
                }
                if (needSave) {
                    manager.k();
                }
                return true;
            }
        }

        EventBean newEb = new EventBean(eventType, targetType, cleanTargetId, eventName);
        manager.a(javaName, newEb);
        manager.k();
        return true;
    }

    private static int resolveViewTargetType(eC manager, String scId, String javaName, String targetId) {
        if ("_fab".equals(targetId) || "fab".equals(targetId)) {
            return ViewBean.VIEW_TYPE_WIDGET_FAB;
        }

        String xmlName = ProjectStructureAide.getXmlNameForActivity(scId, javaName);
        ArrayList<ViewBean> views = manager.d(xmlName);
        if (views != null && targetId != null) {
            for (ViewBean vb : views) {
                if (vb != null && targetId.equalsIgnoreCase(vb.id)) {
                    return vb.type;
                }
            }
        }

        // Check drawer views if present
        hC fileManager = jC.b(scId);
        ProjectFileBean pfb = fileManager != null ? fileManager.a(javaName) : null;
        if (pfb != null && pfb.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
            ArrayList<ViewBean> drawerViews = manager.d(pfb.getDrawerXmlName());
            if (drawerViews != null && targetId != null) {
                for (ViewBean dvb : drawerViews) {
                    if (dvb != null && targetId.equalsIgnoreCase(dvb.id)) {
                        return dvb.type;
                    }
                }
            }
        }

        // Safe fallback if view not found
        return ViewBean.VIEW_TYPE_WIDGET_BUTTON;
    }

    private static int resolveDrawerViewTargetType(eC manager, String scId, String javaName, String targetId) {
        hC fileManager = jC.b(scId);
        ProjectFileBean pfb = fileManager != null ? fileManager.a(javaName) : null;
        if (pfb != null) {
            ArrayList<ViewBean> drawerViews = manager.d(pfb.getDrawerXmlName());
            if (drawerViews != null && targetId != null) {
                for (ViewBean dvb : drawerViews) {
                    if (dvb != null && targetId.equalsIgnoreCase(dvb.id)) {
                        return dvb.type;
                    }
                }
            }
        }
        return ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
    }

    private static int resolveComponentTargetType(eC manager, String javaName, String targetId, String eventName) {
        ArrayList<ComponentBean> components = manager.e(javaName);
        if (components != null && targetId != null) {
            for (ComponentBean cb : components) {
                if (cb != null && targetId.equalsIgnoreCase(cb.componentId)) {
                    return cb.type;
                }
            }
        }
        int byEvent = getComponentTypeForEvent(eventName);
        if (byEvent != 0) return byEvent;

        if (targetId != null) {
            String tLower = targetId.toLowerCase();
            if (tLower.contains("net") || tLower.contains("req")) return ComponentBean.COMPONENT_TYPE_REQUEST_NETWORK;
            if (tLower.contains("timer") || tLower.contains("time")) return ComponentBean.COMPONENT_TYPE_TIMERTASK;
            if (tLower.contains("auth")) return ComponentBean.COMPONENT_TYPE_FIREBASE_AUTH;
            if (tLower.contains("fdb") || tLower.contains("firebase")) return ComponentBean.COMPONENT_TYPE_FIREBASE;
            if (tLower.contains("storage")) return ComponentBean.COMPONENT_TYPE_FIREBASE_STORAGE;
            if (tLower.contains("cam")) return ComponentBean.COMPONENT_TYPE_CAMERA;
            if (tLower.contains("picker") || tLower.contains("file")) return ComponentBean.COMPONENT_TYPE_FILE_PICKER;
            if (tLower.contains("anim")) return ComponentBean.COMPONENT_TYPE_OBJECTANIMATOR;
            if (tLower.contains("ad") || tLower.contains("interstitial")) return ComponentBean.COMPONENT_TYPE_INTERSTITIAL_AD;
            if (tLower.contains("reward")) return ComponentBean.COMPONENT_TYPE_REWARDED_VIDEO_AD;
            if (tLower.contains("speech")) return ComponentBean.COMPONENT_TYPE_SPEECH_TO_TEXT;
            if (tLower.contains("blue") || tLower.contains("bt")) return ComponentBean.COMPONENT_TYPE_BLUETOOTH_CONNECT;
            if (tLower.contains("loc")) return ComponentBean.COMPONENT_TYPE_LOCATION_MANAGER;
            if (tLower.contains("dialog")) return ComponentBean.COMPONENT_TYPE_DIALOG;
        }

        return ComponentBean.COMPONENT_TYPE_REQUEST_NETWORK;
    }

    private static int resolveComponentTargetType(eC manager, String javaName, String targetId) {
        return resolveComponentTargetType(manager, javaName, targetId, null);
    }

    public static boolean ensureEventRegistered(eC manager, String rawJavaName, String eventKey) {
        if (manager == null || eventKey == null || eventKey.trim().isEmpty()) return false;
        eventKey = normalizeEventKey(eventKey);
        if (eventKey.contains("_")) {
            String scId = manager.a;
            String javaName = ProjectStructureAide.findActivityJavaName(scId, rawJavaName, rawJavaName);
            boolean isComponent = false;
            String targetId = null;
            String eventName = null;

            if (javaName != null) {
                ArrayList<ComponentBean> components = manager.e(javaName);
                if (components != null) {
                    for (ComponentBean cb : components) {
                        if (cb != null && cb.componentId != null) {
                            String prefix = cb.componentId.toLowerCase() + "_";
                            if (eventKey.toLowerCase().startsWith(prefix)) {
                                targetId = cb.componentId;
                                eventName = eventKey.substring(cb.componentId.length() + 1);
                                isComponent = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (targetId == null) {
                int lastUnderscore = eventKey.lastIndexOf('_');
                if (lastUnderscore > 0) {
                    String candidateEvent = eventKey.substring(lastUnderscore + 1);
                    if (isKnownComponentEvent(candidateEvent)) {
                        targetId = eventKey.substring(0, lastUnderscore);
                        eventName = candidateEvent;
                        isComponent = true;
                    }
                }
            }

            if (targetId == null) {
                String[] parts = eventKey.split("_", 2);
                targetId = parts[0];
                eventName = parts[1];
                if (isKnownComponentEvent(eventName)) {
                    isComponent = true;
                }
            }

            return ensureEventRegistered(manager, rawJavaName, isComponent ? "component" : "view", targetId, eventName);
        } else {
            if (isKnownComponentEvent(eventKey)) {
                String scId = manager.a;
                String javaName = ProjectStructureAide.findActivityJavaName(scId, rawJavaName, rawJavaName);
                if (javaName != null) {
                    int compType = getComponentTypeForEvent(eventKey);
                    ArrayList<ComponentBean> components = manager.e(javaName);
                    if (components != null) {
                        for (ComponentBean cb : components) {
                            if (cb != null && cb.type == compType) {
                                return ensureEventRegistered(manager, rawJavaName, "component", cb.componentId, eventKey);
                            }
                        }
                    }
                }
            }
            return ensureEventRegistered(manager, rawJavaName, "activity", eventKey, eventKey);
        }
    }

    public static JSONObject blocksToIr(ArrayList<BlockBean> blocks) {
        try {
            JSONObject ir = new JSONObject();
            ir.put("type", "event_logic");
            JSONArray body = new JSONArray();

            if (blocks != null && !blocks.isEmpty()) {
                for (BlockBean b : blocks) {
                    if (b != null) {
                        JSONObject node = new JSONObject();
                        node.put("opCode", b.opCode);
                        node.put("spec", b.spec);
                        node.put("type", b.type);
                        if (b.parameters != null) {
                            node.put("parameters", new JSONArray(b.parameters));
                        }
                        body.put(node);
                    }
                }
            }
            ir.put("body", body);
            return ir;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static JSONObject loadIrFromProject(String scId, String rawJavaName, String eventKey) {
        String javaName = ProjectStructureAide.resolveJavaName(scId, rawJavaName);
        eventKey = normalizeEventKey(eventKey);
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

    public static boolean saveIrToProject(String scId, String rawJavaName, String eventKey, JSONObject ir) {
        String javaName = ProjectStructureAide.resolveJavaName(scId, rawJavaName);
        eventKey = normalizeEventKey(eventKey);
        try {
            if (ir == null) return false;

            eC manager = jC.a(scId);
            if (manager == null) {
                Log.e(TAG, "saveIrToProject: eC manager is null for scId: " + scId);
                return false;
            }

            synchronized (manager) {
                boolean registered = ensureEventRegistered(manager, javaName, eventKey);
                if (!registered) {
                    Log.e(TAG, "saveIrToProject: Could not register event '" + eventKey + "' for " + javaName);
                    return false;
                }

                ArrayList<BlockBean> newBlocks = irToBlocks(ir);
                manager.a(javaName, eventKey, newBlocks);
                manager.k();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save IR to project scId: " + scId + ", event: " + eventKey, e);
            return false;
        }
    }
}
