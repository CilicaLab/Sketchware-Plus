package mod.hey.studios.moreblock;

import android.util.Pair;

import com.besome.sketch.editor.LogicEditorActivity;
import com.google.android.material.chip.ChipGroup;

import java.util.Iterator;

import sketchware.plus.R;

public class ReturnMoreblockManager {

    public static String getLogicEditorTitle(String str) {
        if (str == null) return "";
        int firstBracket = str.indexOf('[');
        if (firstBracket >= 0) {
            int matchingBracket = str.indexOf(']', firstBracket);
            if (matchingBracket > firstBracket) {
                String returnTypeTitle = getMbTypeList(str);
                return str.substring(0, firstBracket) + " (returns " + returnTypeTitle + ")" + str.substring(matchingBracket + 1);
            }
        }
        return str;
    }

    public static String getMbEnd(String str) {
        if (str == null || str.equals(" ")) {
            return ";";
        } else {
            return "";
        }
    }

    public static String getMbName(String str) {
        if (str == null) return "";
        int firstBracket = str.indexOf('[');
        if (firstBracket >= 0) {
            int matchingBracket = str.indexOf(']', firstBracket);
            if (matchingBracket > firstBracket) {
                return str.substring(0, firstBracket) + str.substring(matchingBracket + 1);
            }
        }
        return str;
    }

    public static String getMbType(String str) {
        if (str == null) return "void";
        int firstBracket = str.indexOf('[');
        if (firstBracket < 0) return "void";
        int matchingBracket = str.indexOf(']', firstBracket);
        if (matchingBracket > firstBracket) {
            return str.substring(firstBracket + 1, matchingBracket);
        }
        return "void";
    }

    public static String getMbTypeList(String str) {
        if (str == null) return "void";
        String substring = getMbType(str);
        if ("void".equals(substring)) return "void";
        if (substring.contains("|")) {
            String[] split = substring.split("\\|");
            if (split.length >= 2) {
                substring = split[1];
            }
        }
        return substring;
    }

    public static String getMbTypeCode(String str) {
        if (str == null) return "void";
        String substring = getMbType(str);
        if ("void".equals(substring)) return "void";
        if (substring.contains("|")) {
            String[] split = substring.split("\\|");

            if (substring.equals("a|Map")) {
                substring = "HashMap<String, Object>";

            } else if (substring.equals("l|List String")) {
                substring = "ArrayList<String>";

            } else if (substring.equals("l|List Map")) {
                substring = "ArrayList<HashMap<String, Object>>";

            } else if (split.length == 2) {
                substring = split[1];

            } else if (split.length == 3) {
                substring = split[2];
            }
        }

        return substring;
    }

    public static String getMbTypeFromChipGroup(ChipGroup chipGroup) {
        String type;
        int checkedChipId = chipGroup.getCheckedChipId();
        if (checkedChipId == R.id.radio_mb_type_string) {
            type = "s";
        } else if (checkedChipId == R.id.radio_mb_type_number) {
            type = "d";
        } else if (checkedChipId == R.id.radio_mb_type_boolean) {
            type = "b";
        } else if (checkedChipId == R.id.radio_mb_type_map) {
            type = "a|Map";
        } else if (checkedChipId == R.id.radio_mb_type_liststring) {
            type = "l|List String";
        } else if (checkedChipId == R.id.radio_mb_type_listmap) {
            type = "l|List Map";
        } else if (checkedChipId == R.id.radio_mb_type_view) {
            type = "v|View";
        } else {
            type = " ";
        }
        return type;
    }

    public static void listMoreblocks(Iterator<Pair<String, String>> it, LogicEditorActivity logicEditorActivity) {
        if (it == null || logicEditorActivity == null) return;
        while (it.hasNext()) {
            Pair<String, String> item = it.next();
            if (item == null || item.second == null) continue;
            String str = item.second;
            String moreblockChar = getMoreblockChar(str);

            if (moreblockChar.contains("|")) {
                String[] split = moreblockChar.split("\\|");
                logicEditorActivity.a(getMbName(str), split[0], split[1], "definedFunc").setTag(getMbName(str));
            } else {
                logicEditorActivity.a(getMbName(str), moreblockChar, "definedFunc").setTag(getMbName(str));
            }
        }
    }

    public static String getMoreblockChar(String str) {
        if (str == null) return " ";
        String moreBlockChar;
        String mbType = getMbType(str);

        if (mbType.equals("void")) {
            moreBlockChar = " ";
        } else if (mbType.equals("String")) {
            moreBlockChar = "s";
        } else if (mbType.equals("double")) {
            moreBlockChar = "d";
        } else if (mbType.equals("boolean")) {
            moreBlockChar = "b";
        } else if (mbType.contains("|")) {
            return mbType;
        } else {
            moreBlockChar = " ";
        }

        return moreBlockChar;
    }

    public static String getMoreblockType(String spec) {
        String type = getMoreblockChar(spec);
        if (!type.contains("|")) return type;
        String[] splits = type.split("\\|");
        return splits[0];
    }

    public static String injectMbType(String str, String str2, String str3) {
        String str4 = str;

        if (str3 != null && !str3.equals(" ")) {
            if (str3.contains("|")) {
                str4 = injectToMbName(str4, str2, str3);

            } else if (str3.equals("s")) {
                str4 = injectToMbName(str4, str2, "String");

            } else if (str3.equals("d")) {
                str4 = injectToMbName(str4, str2, "double");

            } else if (str3.equals("b")) {
                str4 = injectToMbName(str4, str2, "boolean");
            }
        }

        return str4;

    }

    public static String injectToMbName(String str, String str2, String str3) {
        if (str == null) return "";
        if (str2 == null || str2.isEmpty()) return str + "[" + str3 + "]";

        if (str.equals(str2)) {
            return str + "[" + str3 + "]";
        } else {
            return str.replaceFirst(str2, str2 + "[" + str3 + "]");
        }
    }

    public static String getPreviewType(String str) {
        if (str != null && str.contains("|")) return "a";

        return str;
    }

    public static String getMbNameWithTypeFromSpec(String spec) {
        if (spec == null) return "";
        int firstBracket = spec.indexOf('[');
        if (firstBracket >= 0) {
            int matchingBracket = spec.indexOf(']', firstBracket);
            if (matchingBracket > firstBracket) {
                return spec.substring(0, matchingBracket + 1);
            }
        }
        
        String name = spec;
        if (spec.contains(" ")) {
            name = name.substring(0, name.indexOf(' '));
        }
        return name;
    }
}
