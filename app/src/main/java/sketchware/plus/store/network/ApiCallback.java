package sketchware.plus.store.network;

public interface ApiCallback<T> {
    void onSuccess(T result);
    void onError(StoreError error);
}
