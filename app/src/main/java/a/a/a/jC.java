package a.a.a;

import com.besome.sketch.beans.ProjectLibraryBean;
import sketchware.plus.utility.FileUtil;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class jC {

    public static final ExecutorService projectOperationsExecutor = Executors.newSingleThreadExecutor();
    public static eC a;
    public static hC b;
    public static kC c;
    public static iC d;

    public static void a() {
        a = null;
        b = null;
        c = null;
        d = null;
    }

    public static void b() {
        a.i();
        a = null;
    }

    public static void c() {
        b.k();
        b = null;
    }

    public static void d() {
        d.j();
        d = null;
    }

    public static void e() {
        c.t();
        c = null;
    }

    public static synchronized hC b(String str) {
        return b(str, true);
    }

    public static synchronized iC c(String str) {
        return c(str, true);
    }

    public static synchronized kC d(String str) {
        return d(str, true);
    }

    public static synchronized hC b(String str, boolean z) {
        if (b != null && !str.equals(b.e)) {
            c();
        }
        if (b == null) {
            b = new hC(str);
            if (!z) {
                b.i();
            } else if (b.g()) {
                b.h();
            } else {
                b.i();
            }
        }
        return b;
    }

    public static synchronized iC c(String str, boolean z) {
        if (d != null && !str.equals(d.a)) {
            d();
        }
        if (d == null) {
            d = new iC(str);
            if (!z) {
                d.i();
            } else if (d.g()) {
                d.h();
            } else {
                d.i();
            }
        }
        loadFirebaseLibraryFromDisk(str, d);
        return d;
    }

    public static void saveFirebaseLibraryToDisk(String sc_id, ProjectLibraryBean bean) {
        try {
            if (sc_id != null && bean != null) {
                String path = wq.b(sc_id) + File.separator + "firebase_library.json";
                String json = new com.google.gson.Gson().toJson(bean);
                FileUtil.writeFile(path, json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ProjectLibraryBean loadFirebaseLibraryFromDisk(String sc_id) {
        try {
            if (sc_id != null) {
                String path = wq.b(sc_id) + File.separator + "firebase_library.json";
                if (FileUtil.isExistFile(path)) {
                    String json = FileUtil.readFile(path);
                    if (json != null && !json.trim().isEmpty()) {
                        return new com.google.gson.Gson().fromJson(json, ProjectLibraryBean.class);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void loadFirebaseLibraryFromDisk(String sc_id, iC libraryManager) {
        if (libraryManager != null) {
            ProjectLibraryBean diskBean = loadFirebaseLibraryFromDisk(sc_id);
            if (diskBean != null) {
                boolean diskHasData = diskBean.useYn != null && (diskBean.useYn.equals("Y") || !cleanStringValue(diskBean.reserved1).isEmpty() || !cleanStringValue(diskBean.reserved2).isEmpty());
                if (diskHasData) {
                    libraryManager.c = diskBean;
                }
            }
        }
    }

    private static String cleanStringValue(String str) {
        return str != null ? str.trim() : "";
    }

    public static synchronized kC d(String str, boolean z) {
        if (c != null && !str.equals(c.i)) {
            e();
        }
        if (c == null) {
            c = new kC(str);
            if (!z) {
                c.s();
            } else if (c.q()) {
                c.r();
            } else {
                c.s();
            }
        }
        return c;
    }

    public static synchronized eC a(String str) {
        return a(str, true);
    }

    public static synchronized eC a(String str, boolean z) {
        if (a != null && !str.equals(a.a)) {
            b();
        }
        if (a == null) {
            a = new eC(str);
            if (!z) {
                a.g();
                a.e();
            } else {
                if (a.d()) {
                    a.h();
                } else {
                    a.g();
                }
                if (a.c()) {
                    a.f();
                } else {
                    a.e();
                }
            }
        }
        return a;
    }
}
