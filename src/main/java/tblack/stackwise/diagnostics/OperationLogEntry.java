package tblack.stackwise.diagnostics;

public sealed interface OperationLogEntry permits MessageLogEntry, ValidationLogEntry, ChangeLogEntry {
    LogSeverity severity();

    String translated(String locale);
}
