package sketchware.plus.store.repository;

import android.content.Context;

public class RepositoryProvider {
    // Switch flag: set to true to use Supabase backend, false to use Dummy local mock repository
    private static boolean useSupabase = true;

    public static void setUseSupabase(boolean use) {
        useSupabase = use;
    }

    public static boolean isUseSupabase() {
        return useSupabase;
    }

    public static StoreRepository getRepository(Context context) {
        if (useSupabase) {
            return SupabaseStoreRepository.getInstance(context);
        } else {
            return DummyStoreRepository.getInstance();
        }
    }
}
