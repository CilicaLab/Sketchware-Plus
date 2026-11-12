package sketchware.plus.ai;

/**
 * Model representing a single tool-call execution step in the Agent Activity Log.
 */
public class ActivityStep {

    public enum Status {
        RUNNING, DONE, FAILED
    }

    public String toolName;
    public String label;
    public Status status;
    public long startTime;

    public ActivityStep(String toolName, String label, Status status, long startTime) {
        this.toolName = toolName;
        this.label = label;
        this.status = status;
        this.startTime = startTime;
    }

    public ActivityStep(String label, Status status, long startTime) {
        this("", label, status, startTime);
    }
}
