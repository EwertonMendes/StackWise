package tblack.stackwise.ui;

import org.junit.jupiter.api.Test;
import tblack.stackwise.rule.MatchType;
import tblack.stackwise.rule.RuleAction;
import tblack.stackwise.rule.StackRule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuleEditorDraftTest {
    @Test
    void preservesOptionalIconAcrossRuleConversion() {
        StackRule source = new StackRule();
        source.id = "Arrows";
        source.enabled = false;
        source.action = RuleAction.SET;
        source.matchType = MatchType.PREFIX;
        source.value = " Weapon_Arrow ";
        source.maxStack = 1000;
        source.priority = 600;
        source.allowUnsafe = true;
        source.iconItemId = " Weapon_Arrow_Crude ";

        StackRule converted = RuleEditorDraft.fromRule(source).toRule();

        assertEquals("arrows", converted.id);
        assertEquals("Weapon_Arrow", converted.value);
        assertEquals("Weapon_Arrow_Crude", converted.iconItemId);
        assertEquals(1000, converted.maxStack);
        assertEquals(600, converted.priority);
    }

    @Test
    void removesBlankIconsWithoutChangingOtherValues() {
        RuleEditorDraft draft = new RuleEditorDraft(
                "rule-1",
                true,
                RuleAction.EXCLUDE,
                MatchType.EXACT,
                "Weapon_Sword_Iron",
                1,
                10,
                false,
                "Weapon_Sword_Iron"
        );

        RuleEditorDraft withoutIcon = draft.withIconItemId(" ");

        assertNull(withoutIcon.iconItemId());
        assertEquals(draft.ruleId(), withoutIcon.ruleId());
        assertEquals(draft.matchValue(), withoutIcon.matchValue());
    }
}
