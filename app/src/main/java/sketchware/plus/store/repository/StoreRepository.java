package sketchware.plus.store.repository;

import java.util.List;
import sketchware.plus.store.models.StoreItem;
import sketchware.plus.store.models.StoreReport;
import sketchware.plus.store.models.StoreUser;

public interface StoreRepository {
    void getItems(String type, String query, String sortBy, RepositoryCallback<List<StoreItem>> callback);
    void getMyItems(String authorId, RepositoryCallback<List<StoreItem>> callback);
    void getItemDetails(String id, RepositoryCallback<StoreItem> callback);
    void incrementDownloads(String itemId);
    void uploadItem(StoreItem item, RepositoryCallback<Boolean> callback);
    void reportItem(StoreReport report, RepositoryCallback<Boolean> callback);
    void getCurrentUser(RepositoryCallback<StoreUser> callback);
    void login(String email, String password, RepositoryCallback<StoreUser> callback);
    void signup(String username, String email, String password, RepositoryCallback<StoreUser> callback);
    void deleteItem(String itemId, RepositoryCallback<Boolean> callback);
}
