package sketchware.plus.store.network;

public class StoreError {
    public static final StoreError NO_CONNECTION = new StoreError("NO_CONNECTION", "No internet connection. Please check your network.");
    public static final StoreError UNAUTHORIZED = new StoreError("UNAUTHORIZED", "Unauthorized access or invalid credentials.");
    public static final StoreError FORBIDDEN = new StoreError("FORBIDDEN", "Access denied.");
    public static final StoreError NOT_FOUND = new StoreError("NOT_FOUND", "Resource not found.");
    public static final StoreError SERVER = new StoreError("SERVER", "Server error. Please try again later.");
    public static final StoreError UNKNOWN = new StoreError("UNKNOWN", "An unknown error occurred.");

    private final String name;
    private final String defaultMessage;
    private String details;

    public StoreError(String name, String defaultMessage) {
        this.name = name;
        this.defaultMessage = defaultMessage;
    }

    public String name() {
        return name;
    }

    public String getMessage() {
        if (details != null && !details.isEmpty()) {
            return details;
        }
        return defaultMessage;
    }

    public StoreError withDetails(String details) {
        StoreError err = new StoreError(this.name, this.defaultMessage);
        err.details = details;
        return err;
    }

    @Override
    public String toString() {
        return getMessage();
    }
}
