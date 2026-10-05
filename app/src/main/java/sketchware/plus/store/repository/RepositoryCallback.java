package sketchware.plus.store.repository;

public interface RepositoryCallback<T> {
    void onSuccess(T result);
    void onError(String error);
}
