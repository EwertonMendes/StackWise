package tblack.stackwise.stack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StackApplyReport {
    public int scanned;
    public int matched;
    public int changed;
    public int unchanged;
    public int excluded;
    public int unsafeBlocked;
    public int decreaseBlocked;
    public int restartRequired;
    public int externalConflict;
    public int failures;
    public boolean adapterAvailable;
    public String adapterDescription = "";
    private final List<StackChange> changes = new ArrayList<>();

    public void addChange(StackChange change) {
        if (changes.size() < 1000) {
            changes.add(change);
            return;
        }
        if ("changed".equals(change.outcome())) return;
        for (int index = changes.size() - 1; index >= 0; index--) {
            if (!"changed".equals(changes.get(index).outcome())) continue;
            changes.set(index, change);
            return;
        }
        if (!isFailure(change)) return;
        for (int index = changes.size() - 1; index >= 0; index--) {
            if (isFailure(changes.get(index))) continue;
            changes.set(index, change);
            return;
        }
    }

    private boolean isFailure(StackChange change) {
        String outcome = change.outcome() == null ? "" : change.outcome();
        return outcome.startsWith("failure") || outcome.equals("verification-failed");
    }

    public int blockedCount() {
        return unsafeBlocked + decreaseBlocked + restartRequired + externalConflict;
    }

    public List<StackChange> changes() {
        return Collections.unmodifiableList(changes);
    }

    public String summary() {
        return "Scanned " + scanned
                + ", matched " + matched
                + ", changed " + changed
                + ", blocked " + blockedCount()
                + ", failures " + failures;
    }
}
