package tblack.stackwise.diagnostics;

import tblack.stackwise.i18n.I18n;

public record MessageLogEntry(LogSeverity severity, String messageKey, Object[] messageArgs) implements OperationLogEntry {
    public MessageLogEntry {
        messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
    }

    @Override
    public String translated(String locale) {
        return I18n.translate(locale, messageKey, messageArgs);
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs.clone();
    }
}
