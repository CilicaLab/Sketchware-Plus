package mod.hilal.saif.blocks;

import android.util.Pair;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import mod.hey.studios.util.Helper;
import sketchware.plus.utility.FileUtil;

public class CommandBlock {

    public static String applyCommands(String fileName, String c) {
        String str = c;
        String path = FileUtil.getExternalStorageDir().concat("/.sketchware/temp/commands");
        ArrayList<HashMap<String, Object>> data;
        try {
            if (FileUtil.isExistFile(path)) {
                String content = FileUtil.readFile(path);
                if (!content.isEmpty() && !content.equals("[]")) {
                    data = new Gson().fromJson(content, Helper.TYPE_MAP_LIST);
                    if (data != null) {
                        for (int i = 0; i < data.size(); i++) {
                            String target = getInputName((String) data.get(i).get("input"));
                            if (target.equals(fileName)) {
                                str = N(str, data.get(i));
                            }
                        }
                    }
                }
            }
            return str;
        } catch (Exception e) {
            return c;
        }
    }

    private static String N(String c, HashMap<String, Object> map) {
        ArrayList<String> a = new ArrayList<>(Arrays.asList(c.split("\n")));
        String reference = (String) map.get("reference");
        int distance = getInt(map.get("distance"));
        int after = getInt(map.get("after"));
        int before = getInt(map.get("before"));
        String command = (String) map.get("command");
        String input = getExceptFirstLine((String) map.get("input"));

        if (command.equals("find-replace")) {
            return c.replace(reference, input);
        }
        if (command.equals("find-replace-first")) {
            try {
                return c.replaceFirst(reference, input);
            } catch (Exception e) {
                return c;
            }
        }

        if (command.equals("find-replace-all")) {
            try {
                return c.replaceAll(reference, input);
            } catch (Exception e) {
                return c;
            }
        }

        int index = getIndex(a, reference);
        if (index == -1) {
            return c;
        }

        if (command.equals("insert")) {
            int targetIndex = index + distance - before;
            if (targetIndex < 0) targetIndex = 0;
            if (targetIndex > a.size()) targetIndex = a.size();
            a.add(targetIndex, input);
        }
        if (command.equals("add")) {
            int targetIndex = index + distance + after + 1;
            if (targetIndex < 0) targetIndex = 0;
            if (targetIndex > a.size()) targetIndex = a.size();
            a.add(targetIndex, input);
        }

        if (command.equals("replace")) {
            int lineToChange = index + distance;
            if (before == 0 && after == 0) {
                if (lineToChange >= 0 && lineToChange < a.size()) {
                    a.set(lineToChange, input);
                }
            } else {
                int from = lineToChange - before;
                int to = lineToChange + after + 1;
                if (from < 0) from = 0;
                if (to > a.size()) to = a.size();
                if (from < to) {
                    a.subList(from, to).clear();
                    int insertAt = from;
                    if (insertAt > a.size()) insertAt = a.size();
                    a.add(insertAt, input);
                }
            }
        }

        return assemble(a);
    }

    private static int getInt(Object o) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o instanceof String) {
            try {
                return (int) Double.parseDouble((String) o);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public static String getExceptFirstLine(String c) {
        ArrayList<String> a = new ArrayList<>(Arrays.asList(c.split("\n")));
        if (a.size() <= 1) return "";
        a.remove(0);
        return assemble(a);
    }

    public static String getInputName(String input) {
        ArrayList<String> a = new ArrayList<>(Arrays.asList(input.split("\n")));
        if (a.isEmpty()) return "";
        String name = a.get(0);
        if (name.startsWith(">")) {
            name = name.substring(1).trim();
        }
        return name;
    }

    private static String getFirstLine(String c) {
        ArrayList<String> a = new ArrayList<>(Arrays.asList(c.split("\n")));
        if (!a.isEmpty()) {
            return a.get(0);
        } else {
            return "";
        }
    }

    public static void loadCommands(String path) {
        if (FileUtil.isExistFile(path)) {
            try {
                ArrayList<HashMap<String, Object>> list = new Gson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
                if (list != null) {
                    WTF(list);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static void x() {
        String path = FileUtil.getExternalStorageDir().concat("/.sketchware/temp/commands");
        FileUtil.deleteFile(path);
    }

    public static String CB(String c) {
        String OC = c;
        String RC = OC;
        String SID = "/*-JX4UA2y_f1OckjjvxWI.bQwRei-sLEsBmds7ArsRfi0xSFEP3Php97kjdMCs5ed";
        String EID = "BpWI8U4flOpx8Ke66QTlZYBA_NEusQ7BN-D0wvZs7ArsRfi0.EP3Php97kjdMCs*/";
        try {
            ArrayList<HashMap<String, Object>> Cs = new ArrayList<>();
            getCBs(Cs, RC, SID, EID);
            RC = rCCs(RC, SID, EID);
            RC = CBForXml(RC);
            RC = aCs(Cs, RC);
            return RC;
        } catch (Exception e) {
            writeLog(e.toString());
            return rCCs(c, SID, EID);
        }
    }

    public static String CBForXml(String c) {
        String OC = c;
        String RC = OC;
        String SID = "/*AXAVajPNTpbJjsz-NGVTp08YDzfI-04kA7ZsuCl4GHqTQQiuWL45sV6Vf4gwK";
        String EID = "Ui5_PNTJb21WO6OuGwQ3psk3su1LIvyXo_OAol-kVQBC5jtN_DcPLaRCJ0yXp*/";
        try {
            ArrayList<HashMap<String, Object>> Cs = new ArrayList<>();
            getCBs(Cs, RC, SID, EID);
            RC = rCCs(RC, SID, EID);
            WTF(Cs);
            return RC;
        } catch (Exception e) {
            writeLog(e.toString());
            return rCCs(c, SID, EID);
        }
    }

    public static void WTF(ArrayList<HashMap<String, Object>> list) {
        String path = FileUtil.getExternalStorageDir().concat("/.sketchware/temp/commands");
        ArrayList<HashMap<String, Object>> data = new ArrayList<>();
        try {
            if (FileUtil.isExistFile(path) && !FileUtil.readFile(path).isEmpty() && !FileUtil.readFile(path).equals("[]")) {
                data = new Gson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
            }
        } catch (Exception ignored) {
        }
        data.addAll(list);
        FileUtil.writeFile(path, new Gson().toJson(data));
    }

    private static void getCBs(ArrayList<HashMap<String, Object>> arr, String c, String sid, String eid) {
        ArrayList<String> a = new ArrayList<>(Arrays.asList(c.split("\n")));
        boolean b = false;
        int n = -1;
        for (int i = 0; i < a.size(); i++) {
            if (b) {
                if (a.get(i).contains(eid)) {
                    Pair<Integer, Integer> p = new Pair<>(n, i);
                    aC(a, arr, p);
                    b = false;
                    n = -1;
                }
            } else {
                if (a.get(i).contains(sid)) {
                    n = i;
                    b = true;
                }
            }
        }
    }

    private static String assemble(ArrayList<String> a) {
        String res = "";
        for (int i = 0; i < a.size(); i++) {
            if (res.isEmpty()) {
                res = a.get(i);
            } else {
                res = res.concat("\n").concat(a.get(i));
            }
        }
        return res;
    }

    private static String aCs(ArrayList<HashMap<String, Object>> arr, String c) {
        ArrayList<String> a = new ArrayList<>(Arrays.asList(c.split("\n")));
        for (int i = 0; i < arr.size(); i++) {
            String reference = (String) arr.get(i).get("reference");
            int distance = getInt(arr.get(i).get("distance"));
            int after = getInt(arr.get(i).get("after"));
            int before = getInt(arr.get(i).get("before"));
            String command = (String) arr.get(i).get("command");
            String input = (String) arr.get(i).get("input");

            if (command.equals("find-replace")) {
                String temp = assemble(a);
                temp = temp.replace(reference, input);
                a = new ArrayList<>(Arrays.asList(temp.split("\n")));
                continue;
            }
            if (command.equals("find-replace-first")) {
                try {
                    String temp = assemble(a);
                    temp = temp.replaceFirst(reference, input);
                    a = new ArrayList<>(Arrays.asList(temp.split("\n")));
                    continue;
                } catch (Exception e) {
                    continue;
                }
            }

            if (command.equals("find-replace-all")) {
                try {
                    String temp = assemble(a);
                    temp = temp.replaceAll(reference, input);
                    a = new ArrayList<>(Arrays.asList(temp.split("\n")));
                    continue;
                } catch (Exception e) {
                    continue;
                }
            }

            int index = getIndex(a, reference);
            if (index == -1) {
                continue;
            }

            if (command.equals("insert")) {
                int targetIndex = index + distance - before;
                if (targetIndex < 0) targetIndex = 0;
                if (targetIndex > a.size()) targetIndex = a.size();
                a.add(targetIndex, input);
                continue;
            }
            if (command.equals("add")) {
                int targetIndex = index + distance + after + 1;
                if (targetIndex < 0) targetIndex = 0;
                if (targetIndex > a.size()) targetIndex = a.size();
                a.add(targetIndex, input);
                continue;
            }

            if (command.equals("replace")) {
                int lineToChange = index + distance;
                if (before == 0 && after == 0) {
                    if (lineToChange >= 0 && lineToChange < a.size()) {
                        a.set(lineToChange, input);
                    }
                } else {
                    int from = lineToChange - before;
                    int to = lineToChange + after + 1;
                    if (from < 0) from = 0;
                    if (to > a.size()) to = a.size();
                    if (from < to) {
                        a.subList(from, to).clear();
                        int insertAt = from;
                        if (insertAt > a.size()) insertAt = a.size();
                        a.add(insertAt, input);
                    }
                }
            }
        }
        return assemble(a);
    }

    private static String rCCs(String c, String sid, String eid) {
        ArrayList<String> a = new ArrayList<>(Arrays.asList(c.split("\n")));
        boolean b = false;
        for (int i = 0; i < a.size(); i++) {
            if (b) {
                if (a.get(i).contains(eid)) {
                    a.remove(i);
                    b = false;
                    i--;
                } else {
                    a.remove(i);
                    i--;
                }
            } else {
                if (a.get(i).contains(sid)) {
                    a.remove(i);
                    b = true;
                    i--;
                }
            }
        }
        return assemble(a);
    }

    private static int getIndex(ArrayList<String> a, String r) {
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).contains(r)) {
                return i;
            }
        }
        return -1;
    }

    private static void aC(ArrayList<String> arr, ArrayList<HashMap<String, Object>> arr2, Pair<Integer, Integer> p) {
        String ref;
        int dis;
        int af;
        int be;
        String c;
        String input = "";

        String v = arr.get(p.first + 1);
        String kk = v.substring(v.indexOf(">") + 1);
        ArrayList<String> aa = new Gson().fromJson(kk, Helper.TYPE_STRING);
        ref = aa.get(0);

        v = arr.get(p.first + 2);
        dis = Integer.parseInt(v.substring(v.indexOf(">") + 1));

        v = arr.get(p.first + 3);
        af = Integer.parseInt(v.substring(v.indexOf(">") + 1));

        v = arr.get(p.first + 4);
        be = Integer.parseInt(v.substring(v.indexOf(">") + 1));

        v = arr.get(p.first + 5);
        c = v.substring(v.indexOf(">") + 1);

        for (int i = 0; i < (p.second - p.first - 6); i++) {
            if (i == 0) {
                input = arr.get(p.first + i + 6);
            } else {
                input = input.concat("\n").concat(arr.get(p.first + i + 6));
            }
        }

        HashMap<String, Object> hm = new HashMap<>();
        hm.put("reference", ref);
        hm.put("distance", dis);
        hm.put("after", af);
        hm.put("before", be);
        hm.put("command", c);
        hm.put("input", input);
        arr2.add(hm);
    }

    private static void writeLog(String s) {
        String path = FileUtil.getExternalStorageDir().concat("/.sketchware/temp/log.txt");
        String text = "";
        if (FileUtil.isExistFile(path)) {
            text = FileUtil.readFile(path);
        }
        FileUtil.writeFile(path, text.concat("\n=>").concat(s));
    }
}
