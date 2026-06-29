package tblack.stackwise.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ValidationResult {
    private final List<ValidationIssue> errors = new ArrayList<>();
    private final List<ValidationIssue> warnings = new ArrayList<>();

    public void addError(String path, String key, Object... args) {
        errors.add(new ValidationIssue(path, key, args));
    }

    public void addWarning(String path, String key, Object... args) {
        warnings.add(new ValidationIssue(path, key, args));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<ValidationIssue> errors() {
        return Collections.unmodifiableList(errors);
    }

    public List<ValidationIssue> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    public String firstError() {
        return firstError("en-US");
    }

    public String firstError(String locale) {
        if (errors.isEmpty()) return "";
        ValidationIssue issue = errors.getFirst();
        return issue.path() + ": " + issue.message(locale);
    }
}
