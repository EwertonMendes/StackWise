package tblack.stackwise.rule;

import java.util.regex.Pattern;

final class CompiledRule {
    private final StackRule source;
    private final Pattern pattern;
    private final int order;

    CompiledRule(StackRule source, Pattern pattern, int order) {
        this.source = source;
        this.pattern = pattern;
        this.order = order;
    }

    StackRule source() {
        return source;
    }

    int order() {
        return order;
    }

    boolean matches(String itemId) {
        return switch (source.matchType) {
            case EXACT -> itemId.equals(source.value);
            case PREFIX -> itemId.startsWith(source.value);
            case SUFFIX -> itemId.endsWith(source.value);
            case GLOB, REGEX -> pattern.matcher(itemId).matches();
        };
    }

    int specificity() {
        return switch (source.matchType) {
            case EXACT -> 500;
            case PREFIX, SUFFIX -> 400;
            case GLOB -> 300;
            case REGEX -> 200;
        };
    }
}
