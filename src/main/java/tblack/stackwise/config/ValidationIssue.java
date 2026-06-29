package tblack.stackwise.config;

import tblack.stackwise.i18n.I18n;

public record ValidationIssue(String path, String key, Object[] args) {
    public ValidationIssue {
        args = args == null ? new Object[0] : args.clone();
    }

    public String message() {
        return message("en-US");
    }

    public String message(String locale) {
        return I18n.translate(locale, key, args);
    }

    @Override
    public Object[] args() {
        return args.clone();
    }
}
