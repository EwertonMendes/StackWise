package tblack.stackwise.diagnostics;

import tblack.stackwise.config.ValidationIssue;
import tblack.stackwise.i18n.I18n;

public record ValidationLogEntry(LogSeverity severity, ValidationIssue issue) implements OperationLogEntry {
    @Override
    public String translated(String locale) {
        return I18n.translate(locale, "ui.log.validation_issue", issue.path(), issue.message(locale));
    }
}
