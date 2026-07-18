package tblack.stackwise.stack;

import org.junit.jupiter.api.Test;
import tblack.stackwise.config.GlobalStackMode;
import tblack.stackwise.config.StackWiseConfig;
import tblack.stackwise.rule.MatchType;
import tblack.stackwise.rule.RuleAction;
import tblack.stackwise.rule.StackRule;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StackApplyServiceTest {
    @Test
    void appliesMatchingRule() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig config = config(rule("test", MatchType.EXACT, "Test_Item", 100, false));
        StackApplyService service = new StackApplyService(adapter);

        StackApplyReport report = service.onAssetsLoaded(Map.of("Test_Item", item), config);

        assertEquals(100, adapter.value(item));
        assertEquals(1, report.changed);
        assertEquals(0, report.failures);
    }

    @Test
    void defaultArrowPrefixAppliesToStackableArrowAssets() {
        StatefulTestItem arrow = new StatefulTestItem();
        FakeAdapter adapter = new FakeAdapter(arrow, 100);
        StackWiseConfig config = new StackWiseConfig();
        StackApplyService service = new StackApplyService(adapter);

        StackApplyReport report = service.onAssetsLoaded(Map.of("Weapon_Arrow_Iron", arrow), config);

        assertEquals(1000, adapter.value(arrow));
        assertEquals(1, report.matched);
        assertEquals(1, report.changed);
        assertEquals(0, report.unsafeBlocked);
        assertEquals(1, service.matchCount("arrows"));
    }

    @Test
    void globalLimitAppliesToUnmatchedStackableItems() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig config = new StackWiseConfig();
        config.rules = List.of();
        StackApplyService service = new StackApplyService(adapter);

        StackApplyReport report = service.onAssetsLoaded(Map.of("Unmatched_Item", item), config);

        assertEquals(1000, adapter.value(item));
        assertEquals(1, report.matched);
        assertEquals(1, report.changed);
    }

    @Test
    void globalMultiplierUsesTheCapturedBaseline() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 99);
        StackWiseConfig config = new StackWiseConfig();
        config.rules = List.of();
        config.globalStackMode = GlobalStackMode.MULTIPLIER;
        config.globalStackMultiplier = 2.0D;
        config.globalStackCap = 999;
        StackApplyService service = new StackApplyService(adapter);

        StackApplyReport report = service.onAssetsLoaded(Map.of("Ingredient_Stick", item), config);

        assertEquals(198, adapter.value(item));
        assertEquals(1, report.matched);
        assertEquals(1, report.changed);
    }

    @Test
    void globalMultiplierAppliesItsCap() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 100);
        StackWiseConfig config = new StackWiseConfig();
        config.rules = List.of();
        config.globalStackMode = GlobalStackMode.MULTIPLIER;
        config.globalStackMultiplier = 20.0D;
        config.globalStackCap = 999;
        StackApplyService service = new StackApplyService(adapter);

        service.onAssetsLoaded(Map.of("Capped_Item", item), config);

        assertEquals(999, adapter.value(item));
    }

    @Test
    void capBelowBaselineStillUsesDecreaseSafeguards() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 100);
        StackWiseConfig config = new StackWiseConfig();
        config.rules = List.of();
        config.globalStackMode = GlobalStackMode.MULTIPLIER;
        config.globalStackMultiplier = 2.0D;
        config.globalStackCap = 50;
        StackApplyService service = new StackApplyService(adapter);

        StackApplyReport blocked = service.onAssetsLoaded(Map.of("Test_Item", item), config);

        assertEquals(100, adapter.value(item));
        assertEquals(1, blocked.decreaseBlocked);

        config.allowDecreases = true;
        config.allowRuntimeDecreases = true;
        StackApplyReport applied = service.applyRuntime(config);

        assertEquals(50, adapter.value(item));
        assertEquals(1, applied.changed);
    }

    @Test
    void explicitRulesOverrideTheGlobalLimit() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig config = new StackWiseConfig();
        config.globalStackMode = GlobalStackMode.MULTIPLIER;
        config.globalStackMultiplier = 2.0D;
        config.globalStackCap = 999;
        config.rules = List.of(rule("specific", MatchType.EXACT, "Test_Item", 250, false));
        StackApplyService service = new StackApplyService(adapter);

        service.onAssetsLoaded(Map.of("Test_Item", item), config);

        assertEquals(250, adapter.value(item));
    }

    @Test
    void explicitRulesMayExceedTheMultiplierCap() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig config = new StackWiseConfig();
        config.globalStackMode = GlobalStackMode.MULTIPLIER;
        config.globalStackMultiplier = 2.0D;
        config.globalStackCap = 999;
        config.rules = List.of(rule("specific", MatchType.EXACT, "Test_Item", 1000, false));
        StackApplyService service = new StackApplyService(adapter);

        service.onAssetsLoaded(Map.of("Test_Item", item), config);

        assertEquals(1000, adapter.value(item));
    }

    @Test
    void exclusionsOverrideTheGlobalLimit() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig config = new StackWiseConfig();
        StackRule exclude = rule("exclude", MatchType.EXACT, "Test_Item", 1, false);
        exclude.action = RuleAction.EXCLUDE;
        config.rules = List.of(exclude);
        StackApplyService service = new StackApplyService(adapter);

        StackApplyReport report = service.onAssetsLoaded(Map.of("Test_Item", item), config);

        assertEquals(64, adapter.value(item));
        assertEquals(1, report.excluded);
    }

    @Test
    void globalLimitDoesNotAffectOriginallyNonStackableItems() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 1);
        StackWiseConfig config = new StackWiseConfig();
        config.globalStackMode = GlobalStackMode.MULTIPLIER;
        config.globalStackMultiplier = 2.0D;
        config.rules = List.of();
        StackApplyService service = new StackApplyService(adapter);

        StackApplyReport report = service.onAssetsLoaded(Map.of("NonStackable_Item", item), config);

        assertEquals(1, adapter.value(item));
        assertEquals(0, report.matched);
        assertEquals(0, report.changed);
    }

    @Test
    void disabledFirstLoadDoesNotChangeItems() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig config = new StackWiseConfig();
        config.enabled = false;
        StackApplyService service = new StackApplyService(adapter);

        StackApplyReport report = service.onAssetsLoaded(Map.of("Weapon_Arrow_Iron", item), config);

        assertEquals(64, adapter.value(item));
        assertEquals(0, report.changed);
        assertEquals(0, report.matched);
    }

    @Test
    void disablingStackWiseRestoresOwnedValuesWithoutRuntimeDecreasePermission() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig enabled = config(rule("test", MatchType.EXACT, "Test_Item", 1000, false));
        StackApplyService service = new StackApplyService(adapter);
        service.onAssetsLoaded(Map.of("Test_Item", item), enabled);

        StackWiseConfig disabled = config();
        disabled.enabled = false;
        disabled.allowRuntimeDecreases = false;
        disabled.restoreUnmatchedItems = false;
        StackApplyReport report = service.applyRuntime(disabled);

        assertEquals(64, adapter.value(item));
        assertEquals(1, report.changed);
        assertEquals(0, report.restartRequired);
    }

    @Test
    void disablingGlobalLimitRestoresUnmatchedItems() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig initial = new StackWiseConfig();
        initial.rules = List.of();
        StackApplyService service = new StackApplyService(adapter);
        service.onAssetsLoaded(Map.of("Test_Item", item), initial);

        StackWiseConfig disabled = new StackWiseConfig();
        disabled.rules = List.of();
        disabled.globalLimitEnabled = false;
        disabled.restoreUnmatchedItems = false;
        service.applyRuntime(disabled);

        assertEquals(64, adapter.value(item));
    }

    @Test
    void safeModeBlocksOriginallyNonStackableItems() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 1);
        StackWiseConfig config = config(rule("test", MatchType.EXACT, "Test_Item", 100, false));
        StackApplyService service = new StackApplyService(adapter);

        StackApplyReport report = service.onAssetsLoaded(Map.of("Test_Item", item), config);

        assertEquals(1, adapter.value(item));
        assertEquals(1, report.unsafeBlocked);
    }

    @Test
    void explicitUnsafeOverrideAllowsOriginallyNonStackableItems() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 1);
        StackWiseConfig config = config(rule("test", MatchType.EXACT, "Test_Item", 100, true));
        StackApplyService service = new StackApplyService(adapter);

        StackApplyReport report = service.onAssetsLoaded(Map.of("Test_Item", item), config);

        assertEquals(100, adapter.value(item));
        assertEquals(1, report.changed);
    }

    @Test
    void runtimeDecreaseRequiresExplicitPermission() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig initial = config(rule("test", MatchType.EXACT, "Test_Item", 100, false));
        StackApplyService service = new StackApplyService(adapter);
        service.onAssetsLoaded(Map.of("Test_Item", item), initial);

        StackWiseConfig reduced = config(rule("test", MatchType.EXACT, "Test_Item", 50, false));
        reduced.allowDecreases = true;
        StackApplyReport report = service.applyRuntime(reduced);

        assertEquals(100, adapter.value(item));
        assertEquals(1, report.restartRequired);
    }

    @Test
    void switchingFromFixedToMultiplierUsesTheRuntimeDecreaseSafeguard() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig fixed = new StackWiseConfig();
        fixed.rules = List.of();
        StackApplyService service = new StackApplyService(adapter);
        service.onAssetsLoaded(Map.of("Test_Item", item), fixed);

        StackWiseConfig multiplied = new StackWiseConfig();
        multiplied.rules = List.of();
        multiplied.globalStackMode = GlobalStackMode.MULTIPLIER;
        multiplied.globalStackMultiplier = 2.0D;
        multiplied.globalStackCap = 999;
        StackApplyReport blocked = service.applyRuntime(multiplied);

        assertEquals(1000, adapter.value(item));
        assertEquals(1, blocked.restartRequired);

        multiplied.allowRuntimeDecreases = true;
        StackApplyReport applied = service.applyRuntime(multiplied);

        assertEquals(128, adapter.value(item));
        assertEquals(1, applied.changed);
    }

    @Test
    void increasingMultiplierAppliesImmediatelyWithoutRuntimeDecreasePermission() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 100);
        StackWiseConfig multiplied = new StackWiseConfig();
        multiplied.rules = List.of();
        multiplied.globalStackMode = GlobalStackMode.MULTIPLIER;
        multiplied.globalStackMultiplier = 4.0D;
        multiplied.globalStackCap = 999;
        StackApplyService service = new StackApplyService(adapter);
        service.onAssetsLoaded(Map.of("Test_Item", item), multiplied);

        multiplied.globalStackMultiplier = 5.0D;
        StackApplyReport report = service.applyRuntime(multiplied);

        assertEquals(500, adapter.value(item));
        assertEquals(1, report.changed);
        assertEquals(0, report.restartRequired);
        assertEquals(1, report.changedItemIds().size());
    }

    @Test
    void switchingFromMultiplierToHigherFixedValueAppliesImmediately() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 100);
        StackWiseConfig multiplied = new StackWiseConfig();
        multiplied.rules = List.of();
        multiplied.globalStackMode = GlobalStackMode.MULTIPLIER;
        multiplied.globalStackMultiplier = 4.0D;
        multiplied.globalStackCap = 999;
        StackApplyService service = new StackApplyService(adapter);
        service.onAssetsLoaded(Map.of("Test_Item", item), multiplied);

        StackWiseConfig fixed = new StackWiseConfig();
        fixed.rules = List.of();
        fixed.globalStackMode = GlobalStackMode.FIXED;
        fixed.globalStackLimit = 1000;
        StackApplyReport report = service.applyRuntime(fixed);

        assertEquals(1000, adapter.value(item));
        assertEquals(1, report.changed);
        assertEquals(0, report.restartRequired);
    }

    @Test
    void externalChangesReleaseMultiplierControlledItems() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig config = new StackWiseConfig();
        config.rules = List.of();
        config.globalStackMode = GlobalStackMode.MULTIPLIER;
        config.globalStackMultiplier = 2.0D;
        StackApplyService service = new StackApplyService(adapter);
        service.onAssetsLoaded(Map.of("Test_Item", item), config);
        adapter.set(item, 80);

        config.globalStackMultiplier = 3.0D;
        StackApplyReport report = service.applyRuntime(config);

        assertEquals(80, adapter.value(item));
        assertEquals(1, report.externalConflict);
    }

    @Test
    void externalChangesAreNotOverwritten() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig initial = config(rule("test", MatchType.EXACT, "Test_Item", 100, false));
        StackApplyService service = new StackApplyService(adapter);
        service.onAssetsLoaded(Map.of("Test_Item", item), initial);
        adapter.set(item, 80);

        StackWiseConfig updated = config(rule("test", MatchType.EXACT, "Test_Item", 120, false));
        StackApplyReport report = service.applyRuntime(updated);

        assertEquals(80, adapter.value(item));
        assertEquals(1, report.externalConflict);
    }

    @Test
    void disablingDoesNotOverwriteAnExternalOwner() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig initial = config(rule("test", MatchType.EXACT, "Test_Item", 100, false));
        StackApplyService service = new StackApplyService(adapter);
        service.onAssetsLoaded(Map.of("Test_Item", item), initial);
        adapter.set(item, 80);

        StackWiseConfig disabled = config();
        disabled.enabled = false;
        StackApplyReport report = service.applyRuntime(disabled);

        assertEquals(80, adapter.value(item));
        assertEquals(1, report.externalConflict);
    }

    @Test
    void unmatchedItemsCanBeRestored() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 64);
        StackWiseConfig initial = config(rule("test", MatchType.EXACT, "Test_Item", 100, false));
        StackApplyService service = new StackApplyService(adapter);
        service.onAssetsLoaded(Map.of("Test_Item", item), initial);

        StackWiseConfig empty = config();
        empty.allowRuntimeDecreases = true;
        StackApplyReport report = service.applyRuntime(empty);

        assertEquals(64, adapter.value(item));
        assertEquals(1, report.changed);
    }

    @Test
    void repeatedAssetEventKeepsTheOriginalLimitForTheSameItemObject() {
        TestItem item = new TestItem();
        FakeAdapter adapter = new FakeAdapter(item, 1);
        StackWiseConfig initial = config(rule("test", MatchType.EXACT, "Test_Item", 100, true));
        StackApplyService service = new StackApplyService(adapter);
        service.onAssetsLoaded(Map.of("Test_Item", item), initial);

        StackWiseConfig updated = config(rule("test", MatchType.EXACT, "Test_Item", 150, false));
        StackApplyReport report = service.onAssetsLoaded(Map.of("Test_Item", item), updated);

        assertEquals(100, adapter.value(item));
        assertEquals(1, report.unsafeBlocked);
    }

    private StackWiseConfig config(StackRule... rules) {
        StackWiseConfig config = new StackWiseConfig();
        config.globalLimitEnabled = false;
        config.rules = List.of(rules);
        return config;
    }

    private StackRule rule(String id, MatchType type, String value, int maxStack, boolean allowUnsafe) {
        StackRule rule = new StackRule();
        rule.id = id;
        rule.action = RuleAction.SET;
        rule.matchType = type;
        rule.value = value;
        rule.maxStack = maxStack;
        rule.allowUnsafe = allowUnsafe;
        return rule;
    }

    private static class TestItem {
    }

    private static final class StatefulTestItem extends TestItem {
        private Object getWeapon() {
            return new Object();
        }
    }

    private static final class FakeAdapter implements ItemStackLimitAdapter {
        private final Map<Object, Integer> values = new IdentityHashMap<>();

        private FakeAdapter(Object item, int value) {
            values.put(item, value);
        }

        @Override
        public int read(Object item) {
            return values.get(item);
        }

        @Override
        public void write(Object item, int value) {
            values.put(item, value);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String description() {
            return "Fake";
        }

        private int value(Object item) {
            return values.get(item);
        }

        private void set(Object item, int value) {
            values.put(item, value);
        }
    }
}
