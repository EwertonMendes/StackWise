package tblack.stackwise.ui;

final class RuleCardLayout {
    static final int ROW_GAP = 8;
    static final int CONTENT_GAP = 12;
    static final int ICON_WIDTH = 62;
    static final int ICON_HEIGHT = 60;
    static final int ACTIONS_WIDTH = 302;
    static final int ACTIONS_HEIGHT = 50;
    static final int CONFIRMATION_WIDTH = 690;
    static final int CONFIRMATION_HEIGHT = 62;

    private static final int MINIMUM_DESCRIPTION_LINES = 4;
    private static final int CHARACTERS_PER_LINE = 60;
    private static final int DESCRIPTION_LINE_HEIGHT = 20;
    private static final int NAME_HEIGHT = 28;
    private static final int VERTICAL_PADDING = 8;

    private RuleCardLayout() {
    }

    static Dimensions measure(String description) {
        int lines = Math.max(MINIMUM_DESCRIPTION_LINES, wrappedLines(description));
        int descriptionHeight = lines * DESCRIPTION_LINE_HEIGHT;
        int textHeight = NAME_HEIGHT + descriptionHeight;
        return new Dimensions(descriptionHeight, textHeight, textHeight + VERTICAL_PADDING);
    }

    private static int wrappedLines(String value) {
        if (value == null || value.isBlank()) return 1;
        int total = 0;
        for (String paragraph : value.split("\\R", -1)) {
            total += wrappedParagraphLines(paragraph);
        }
        return Math.max(1, total);
    }

    private static int wrappedParagraphLines(String paragraph) {
        if (paragraph.isBlank()) return 1;
        int lines = 1;
        int current = 0;
        for (String word : paragraph.trim().split("\\s+")) {
            int length = Math.max(1, word.length());
            if (current == 0) {
                lines += Math.max(0, (length - 1) / CHARACTERS_PER_LINE);
                current = length % CHARACTERS_PER_LINE;
                if (current == 0) current = CHARACTERS_PER_LINE;
                continue;
            }
            if (current + 1 + length <= CHARACTERS_PER_LINE) {
                current += 1 + length;
                continue;
            }
            lines++;
            lines += Math.max(0, (length - 1) / CHARACTERS_PER_LINE);
            current = length % CHARACTERS_PER_LINE;
            if (current == 0) current = CHARACTERS_PER_LINE;
        }
        return lines;
    }

    record Dimensions(int descriptionHeight, int textHeight, int rowHeight) {
        int centeredTop(int elementHeight) {
            return Math.max(0, (textHeight - elementHeight) / 2);
        }
    }
}
