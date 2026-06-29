package tblack.stackwise.diagnostics;

import tblack.stackwise.OperationResult;
import tblack.stackwise.config.ValidationIssue;
import tblack.stackwise.config.ValidationResult;
import tblack.stackwise.stack.StackApplyReport;
import tblack.stackwise.stack.StackChange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class OperationLogService {
    private static final int MAX_ENTRIES = 200;
    private static final int MAX_SUCCESS_ENTRIES = 24;

    private final List<OperationLogEntry> entries = new ArrayList<>();

    public synchronized void record(OperationResult result) {
        entries.clear();
        entries.add(new MessageLogEntry(
                severity(result),
                result.messageKey(),
                result.messageArgs()
        ));
        appendValidation(result.validation());
        appendReport(result.report());
    }

    public synchronized void recordReport(String messageKey, StackApplyReport report, Object... args) {
        entries.clear();
        entries.add(new MessageLogEntry(
                severity(report),
                messageKey,
                args
        ));
        appendReport(report);
    }

    public synchronized List<OperationLogEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized boolean hasEntries() {
        return !entries.isEmpty();
    }

    public synchronized int size() {
        return entries.size();
    }

    private void appendValidation(ValidationResult validation) {
        if (validation == null) return;
        for (ValidationIssue issue : validation.errors()) {
            add(new ValidationLogEntry(LogSeverity.ERROR, issue));
        }
        for (ValidationIssue issue : validation.warnings()) {
            add(new ValidationLogEntry(LogSeverity.WARNING, issue));
        }
    }

    private void appendReport(StackApplyReport report) {
        if (report == null) return;
        add(new MessageLogEntry(
                report.failures > 0
                        ? LogSeverity.ERROR
                        : report.blockedCount() > 0
                        ? LogSeverity.WARNING
                        : LogSeverity.INFO,
                "messages.report",
                new Object[]{report.scanned, report.matched, report.changed, report.blockedCount(), report.failures}
        ));
        if (!report.adapterAvailable) {
            add(new MessageLogEntry(LogSeverity.ERROR, "ui.log.adapter_unavailable", new Object[]{report.adapterDescription}));
        }

        List<StackChange> important = report.changes().stream()
                .filter(change -> severity(change) != LogSeverity.SUCCESS)
                .sorted(Comparator.comparingInt(change -> severityOrder(severity(change))))
                .toList();
        for (StackChange change : important) {
            add(new ChangeLogEntry(severity(change), change));
        }

        int successCount = 0;
        for (StackChange change : report.changes()) {
            if (severity(change) != LogSeverity.SUCCESS) continue;
            if (successCount >= MAX_SUCCESS_ENTRIES) break;
            add(new ChangeLogEntry(LogSeverity.SUCCESS, change));
            successCount++;
        }

        int represented = important.size() + successCount;
        int omitted = Math.max(0, report.changes().size() - represented);
        if (omitted > 0) {
            add(new MessageLogEntry(LogSeverity.INFO, "ui.log.omitted", new Object[]{omitted}));
        }
    }


    private LogSeverity severity(OperationResult result) {
        if (!result.success()) return LogSeverity.ERROR;
        if (result.validation() != null && !result.validation().warnings().isEmpty()) return LogSeverity.WARNING;
        LogSeverity reportSeverity = severity(result.report());
        return reportSeverity == LogSeverity.INFO ? LogSeverity.SUCCESS : reportSeverity;
    }

    private LogSeverity severity(StackApplyReport report) {
        if (report == null) return LogSeverity.INFO;
        if (!report.adapterAvailable || report.failures > 0) return LogSeverity.ERROR;
        if (report.blockedCount() > 0) return LogSeverity.WARNING;
        return LogSeverity.INFO;
    }

    private void add(OperationLogEntry entry) {
        if (entries.size() < MAX_ENTRIES) entries.add(entry);
    }

    private LogSeverity severity(StackChange change) {
        String outcome = change.outcome() == null ? "" : change.outcome();
        if (outcome.startsWith("failure") || outcome.equals("verification-failed")) return LogSeverity.ERROR;
        if (outcome.startsWith("unsafe-blocked:")
                || outcome.equals("below-original-blocked")
                || outcome.equals("restart-required")
                || outcome.equals("external-conflict")) {
            return LogSeverity.WARNING;
        }
        if (outcome.equals("changed")) return LogSeverity.SUCCESS;
        return LogSeverity.INFO;
    }

    private int severityOrder(LogSeverity severity) {
        return switch (severity) {
            case ERROR -> 0;
            case WARNING -> 1;
            case INFO -> 2;
            case SUCCESS -> 3;
        };
    }
}
