package tblack.stackwise.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import tblack.stackwise.OperationResult;
import tblack.stackwise.StackWisePlugin;
import tblack.stackwise.config.StackWiseConfig;
import tblack.stackwise.i18n.I18n;
import tblack.stackwise.rule.MatchType;
import tblack.stackwise.rule.RuleAction;
import tblack.stackwise.rule.StackRule;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;

public final class StackWiseRulePage extends InteractiveCustomUIPage<StackWiseRulePage.RuleEventData> {
    private static final String LAYOUT = "StackWise/RuleEditor.ui";

    private final StackWisePlugin plugin;
    private final PlayerRef viewerRef;
    private final String locale;
    private final int ruleIndex;
    private final int returnPage;
    private final String returnSearch;
    private boolean pendingDelete;
    private String status = "";

    public StackWiseRulePage(
            @Nonnull PlayerRef playerRef,
            StackWisePlugin plugin,
            int ruleIndex,
            int returnPage,
            String returnSearch
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, RuleEventData.CODEC);
        this.plugin = plugin;
        this.viewerRef = playerRef;
        this.locale = I18n.localeFromPlayerRef(playerRef);
        this.ruleIndex = ruleIndex;
        this.returnPage = Math.max(0, returnPage);
        this.returnSearch = returnSearch == null ? "" : returnSearch;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store
    ) {
        commands.append(LAYOUT);
        bindEvents(events);
        render(commands);
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            RuleEventData data
    ) {
        super.handleDataEvent(ref, store, data);
        if (!hasPermission()) {
            close();
            return;
        }
        if (data == null || data.action == null || data.action.isBlank()) {
            sendUpdate();
            return;
        }
        switch (data.action) {
            case "close" -> close();
            case "back" -> openAdmin(ref, store, "");
            case "delete-request" -> requestDelete();
            case "delete-cancel" -> cancelDelete();
            case "delete-confirm" -> deleteRule(ref, store);
            case "save" -> saveRule(ref, store, data);
            default -> sendStatus(translate("messages.unknown_action"));
        }
    }

    private void saveRule(Ref<EntityStore> ref, Store<EntityStore> store, RuleEventData data) {
        StackWiseConfig config = plugin.getConfig();
        StackRule rule = new StackRule();
        rule.id = normalizeId(data.ruleId);
        rule.enabled = data.enabled;
        rule.action = parseAction(data.ruleAction);
        rule.matchType = parseMatchType(data.matchType);
        rule.value = data.matchValue == null ? "" : data.matchValue.trim();
        rule.maxStack = data.maxStack;
        rule.priority = data.priority;
        rule.allowUnsafe = data.allowUnsafe;

        if (ruleIndex >= 0 && ruleIndex < config.rules.size()) config.rules.set(ruleIndex, rule);
        else config.rules.add(rule);

        OperationResult result = plugin.saveAndApply(config);
        if (!result.success()) {
            sendStatus(result.translated(locale));
            return;
        }
        openAdmin(ref, store, translate("messages.rule_saved", rule.id) + " " + result.translated(locale));
    }

    private void requestDelete() {
        if (ruleIndex < 0) {
            sendStatus(translate("messages.rule_missing"));
            return;
        }
        pendingDelete = true;
        status = "";
        refreshDeleteState();
    }

    private void cancelDelete() {
        pendingDelete = false;
        status = "";
        refreshDeleteState();
    }

    private void deleteRule(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!pendingDelete) {
            requestDelete();
            return;
        }
        StackWiseConfig config = plugin.getConfig();
        if (ruleIndex < 0 || ruleIndex >= config.rules.size()) {
            openAdmin(ref, store, translate("messages.rule_missing"));
            return;
        }
        String id = config.rules.get(ruleIndex).id;
        config.rules.remove(ruleIndex);
        OperationResult result = plugin.saveAndApply(config);
        String message = result.success()
                ? translate("messages.rule_deleted", id) + " " + result.translated(locale)
                : result.translated(locale);
        openAdmin(ref, store, message);
    }

    private void openAdmin(Ref<EntityStore> ref, Store<EntityStore> store, String message) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            close();
            return;
        }
        player.getPageManager().openCustomPage(
                ref,
                store,
                new StackWiseAdminPage(viewerRef, plugin, returnPage, returnSearch, message)
        );
    }

    private void bindEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClosePageButton", EventData.of("Action", "close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", EventData.of("Action", "back"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteButton", EventData.of("Action", "delete-request"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelDeleteButton", EventData.of("Action", "delete-cancel"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmDeleteButton", EventData.of("Action", "delete-confirm"), false);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SaveButton",
                EventData.of("Action", "save")
                        .append("@RuleId", "#RuleIdField.Value")
                        .append("@RuleAction", "#ActionDropdown.Value")
                        .append("@MatchType", "#MatchTypeDropdown.Value")
                        .append("@MatchValue", "#MatchValueField.Value")
                        .append("@MaxStack", "#MaxStackInput.Value")
                        .append("@Priority", "#PriorityInput.Value")
                        .append("@Enabled", "#EnabledCheck.Value")
                        .append("@AllowUnsafe", "#AllowUnsafeCheck.Value"),
                false
        );
    }

    private void render(UICommandBuilder commands) {
        StackWiseConfig config = plugin.getConfig();
        StackRule rule = ruleIndex >= 0 && ruleIndex < config.rules.size()
                ? config.rules.get(ruleIndex).copy()
                : newRule(config);

        renderStaticText(commands);
        renderDynamicText(commands, rule);
        commands.set("#RuleIdField.Value", rule.id);
        commands.set("#MatchValueField.Value", rule.value);
        commands.set("#MaxStackInput.Value", rule.maxStack);
        commands.set("#PriorityInput.Value", rule.priority);
        commands.set("#EnabledCheck.Value", rule.enabled);
        commands.set("#AllowUnsafeCheck.Value", rule.allowUnsafe);
        commands.set("#DeleteButton.Visible", ruleIndex >= 0 && !pendingDelete);
        commands.set("#DeleteConfirmation.Visible", ruleIndex >= 0 && pendingDelete);
        commands.set("#StatusLabel.TextSpans", Message.raw(status));

        commands.set("#ActionDropdown.Entries", List.of(
                entry(translate("ui.action.set"), RuleAction.SET.name()),
                entry(translate("ui.action.exclude"), RuleAction.EXCLUDE.name())
        ));
        commands.set("#ActionDropdown.Value", rule.action.name());
        commands.set("#MatchTypeDropdown.Entries", List.of(
                entry(translate("ui.match_type.exact"), MatchType.EXACT.name()),
                entry(translate("ui.match_type.prefix"), MatchType.PREFIX.name()),
                entry(translate("ui.match_type.suffix"), MatchType.SUFFIX.name()),
                entry(translate("ui.match_type.glob"), MatchType.GLOB.name()),
                entry(translate("ui.match_type.regex"), MatchType.REGEX.name())
        ));
        commands.set("#MatchTypeDropdown.Value", rule.matchType.name());
    }

    private void renderStaticText(UICommandBuilder commands) {
        setText(commands, "#RuleIdLabel", translate("ui.rule.id"));
        commands.set("#RuleIdField.PlaceholderText", translate("ui.rule.id_placeholder"));
        setText(commands, "#ActionLabel", translate("ui.rule.action"));
        setText(commands, "#MatchTypeLabel", translate("ui.rule.match_type"));
        setText(commands, "#MatchValueLabel", translate("ui.rule.match_value"));
        commands.set("#MatchValueField.PlaceholderText", translate("ui.rule.match_value_placeholder"));
        setText(commands, "#MaxStackLabel", translate("ui.rule.maximum_stack"));
        setText(commands, "#PriorityLabel", translate("ui.rule.priority"));
        setText(commands, "#EnabledLabel", translate("ui.rule.enabled"));
        setText(commands, "#AllowUnsafeLabel", translate("ui.rule.allow_unsafe"));
        setText(commands, "#CancelDeleteButton", translate("ui.common.cancel"));
        setText(commands, "#ConfirmDeleteButton", translate("ui.common.confirm_delete"));
        setText(commands, "#BackButton", translate("ui.common.back"));
        setText(commands, "#SaveButton", translate("ui.rule.save"));
        setText(commands, "#DeleteButton", translate("ui.common.delete"));
        setText(commands, "#ClosePageButton", translate("ui.common.close"));
    }

    private void setText(UICommandBuilder commands, String selector, String value) {
        commands.set(selector + ".TextSpans", Message.raw(value));
    }

    private void renderDynamicText(UICommandBuilder commands, StackRule rule) {
        commands.set("#EditorTitle.TextSpans", Message.raw(translate(
                ruleIndex >= 0 ? "ui.rule.edit_title" : "ui.rule.create_title"
        )));
        commands.set("#DeleteConfirmationLabel.TextSpans", Message.raw(translate("ui.rule.delete_confirmation", rule.id)));
    }

    private StackRule newRule(StackWiseConfig config) {
        StackRule rule = new StackRule();
        rule.id = nextId(config);
        rule.maxStack = Math.min(100, config.maximumStack);
        return rule;
    }

    private String nextId(StackWiseConfig config) {
        int number = 1;
        while (true) {
            String candidate = "rule-" + number;
            boolean exists = config.rules.stream().anyMatch(rule -> rule != null && candidate.equalsIgnoreCase(rule.id));
            if (!exists) return candidate;
            number++;
        }
    }

    private DropdownEntryInfo entry(String label, String value) {
        return new DropdownEntryInfo(LocalizableString.fromString(label), value);
    }


    private void refreshDeleteState() {
        UICommandBuilder updates = new UICommandBuilder();
        updates.set("#DeleteButton.Visible", ruleIndex >= 0 && !pendingDelete);
        updates.set("#DeleteConfirmation.Visible", ruleIndex >= 0 && pendingDelete);
        updates.set("#StatusLabel.TextSpans", Message.raw(status));
        sendUpdate(updates, false);
    }

    private void sendStatus(String message) {
        status = message == null ? translate("messages.unknown_error") : message;
        UICommandBuilder updates = new UICommandBuilder();
        updates.set("#StatusLabel.TextSpans", Message.raw(status));
        sendUpdate(updates, false);
    }

    private String normalizeId(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private RuleAction parseAction(String value) {
        try {
            return RuleAction.valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return RuleAction.SET;
        }
    }

    private MatchType parseMatchType(String value) {
        try {
            return MatchType.valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return MatchType.EXACT;
        }
    }

    private boolean hasPermission() {
        String permission = plugin.getConfig().commands.adminPermission;
        if (viewerRef.hasPermission(permission)
                || viewerRef.hasPermission("stackwise.admin")
                || viewerRef.hasPermission("*")) {
            return true;
        }
        return plugin.getPermissionService().hasPermission(viewerRef.getUuid(), permission);
    }

    private String translate(String key, Object... args) {
        return I18n.translate(locale, key, args);
    }

    public static final class RuleEventData {
        public static final BuilderCodec<RuleEventData> CODEC = BuilderCodec.builder(RuleEventData.class, RuleEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action).add()
                .append(new KeyedCodec<>("@RuleId", Codec.STRING), (data, value) -> data.ruleId = value, data -> data.ruleId).add()
                .append(new KeyedCodec<>("@RuleAction", Codec.STRING), (data, value) -> data.ruleAction = value, data -> data.ruleAction).add()
                .append(new KeyedCodec<>("@MatchType", Codec.STRING), (data, value) -> data.matchType = value, data -> data.matchType).add()
                .append(new KeyedCodec<>("@MatchValue", Codec.STRING), (data, value) -> data.matchValue = value, data -> data.matchValue).add()
                .append(new KeyedCodec<>("@MaxStack", Codec.INTEGER), (data, value) -> data.maxStack = value, data -> data.maxStack).add()
                .append(new KeyedCodec<>("@Priority", Codec.INTEGER), (data, value) -> data.priority = value, data -> data.priority).add()
                .append(new KeyedCodec<>("@Enabled", Codec.BOOLEAN), (data, value) -> data.enabled = value, data -> data.enabled).add()
                .append(new KeyedCodec<>("@AllowUnsafe", Codec.BOOLEAN), (data, value) -> data.allowUnsafe = value, data -> data.allowUnsafe).add()
                .build();

        public String action = "";
        public String ruleId = "";
        public String ruleAction = RuleAction.SET.name();
        public String matchType = MatchType.EXACT.name();
        public String matchValue = "";
        public int maxStack = 100;
        public int priority;
        public boolean enabled = true;
        public boolean allowUnsafe;
    }
}
