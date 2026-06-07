//GENERATED USING AI

public class WarningRecord {
    private final String key;
    private final String severity;
    private final String message;
    private final boolean overridden;
    private final String overrideReason;

    public WarningRecord(String key, String severity, String message, boolean overridden, String overrideReason) {
        this.key = key;
        this.severity = severity;
        this.message = message;
        this.overridden = overridden;
        this.overrideReason = overrideReason;
    }

    public String getKey() {
        return key;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public boolean isOverridden() {
        return overridden;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    @Override
    public String toString() {
        return severity + ": " + message + (overridden ? " [overridden]" : "");
    }
}
