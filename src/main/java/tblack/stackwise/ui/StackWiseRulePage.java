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
import tblack.stackwise.icon.ItemIconEntry;
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
    private RuleEditorDraft draft;
    private boolean pendingDelete;
    private String status = "";

    public StackWiseRulePage(
            @Nonnull PlayerRef playerRef,
            StackWisePlugin plugin,
            int ruleIndex,
            int returnPage,
            String returnSearch
    ) {
        this(playerRef, plugin, ruleIndex, returnPage, returnSearch, null);
    }

    public StackWiseRulePage(
            @Nonnull PlayerRef playerRef,
            StackWisePlugin plugin,
            int ruleIndex,
            int returnPage,
            String returnSearch,
            RuleEditorDraft draft
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, RuleEventData.CODEC);
        this.plugin = plugin;
        this.viewerRef = playerRef;
        this.locale = I18n.localeFromPlayerRef(playerRef);
        this.ruleIndex = ruleIndex;
        this.returnPage = Math.max(0, returnPage);
        this.returnSearch = returnSearch == null ? "" : returnSearch;
        this.draft = draft;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store
    ) {
        initializeDraft();
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
            case "choose-icon" -> openIconPicker(ref, store, draftFrom(data));
            case "remove-icon" -> removeIcon();
            case "delete-request" -> requestDelete();
            case "delete-cancel" -> cancelDelete();
            case "delete-confirm" -> deleteRule(ref, store);
            case "save" -> saveRule(ref, store, data);
            default -> sendStatus(translate("messages.unknown_action"));
        }
    }

    private void saveRule(Ref<EntityStore> ref, Store<EntityStore> store, RuleEventData data) {
        draft = draftFrom(data);
        StackWiseConfig config = plugin.getConfig();
        StackRule rule = draft.toRule();
        if (ruleIndex >= 0 && ruleIndex < config.rules.size()) config.rules.set(ruleIndex, rule);
        else config.rules.add(rule);

        OperationResult result = plugin.saveAndApply(config);
        if (!result.success()) {
            sendStatus(result.translated(locale));
            return;
        }
        openAdmin(ref, store, translate("messages.rule_saved", rule.id) + " " + result.translated(locale));
    }

    private void openIconPicker(Ref<EntityStore> ref, Store<EntityStore> store, RuleEditorDraft currentDraft) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            sendStatus(translate("messages.player_not_found"));
            return;
        }
        draft = currentDraft;
        player.getPageManager().openCustomPage(
                ref,
                store,
                new StackWiseIconPickerPage(
                        viewerRef,
                        plugin,
                        ruleIndex,
                        returnPage,
                        returnSearch,
                        currentDraft
                )
        );
    }

    private void removeIcon() {
        draft = draft.withIconItemId(null);
        status = "";
        refreshIconState();
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
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClosePageButton", event("close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", event("back"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ChooseIconButton", formEvent("choose-icon"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RemoveIconButton", event("remove-icon"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteButton", event("delete-request"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelDeleteButton", event("delete-cancel"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmDeleteButton", event("delete-confirm"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton", formEvent("save"), false);
    }

    private EventData event(String action) {
        return EventData.of("Action", action);
    }

    private EventData formEvent(String action) {
        return EventData.of("Action", action)
                .append("@RuleId", "#RuleIdField.Value")
                .append("@RuleAction", "#ActionDropdown.Value")
                .append("@MatchType", "#MatchTypeDropdown.Value")
                .append("@MatchValue", "#MatchValueField.Value")
                .append("@MaxStack", "#MaxStackInput.Value")
                .append("@Priority", "#PriorityInput.Value")
                .append("@Enabled", "#EnabledCheck.Value")
                .append("@AllowUnsafe", "#AllowUnsafeCheck.Value");
    }

    private void render(UICommandBuilder commands) {
        renderStaticText(commands);
        renderDynamicText(commands);
        commands.set("#RuleIdField.Value", draft.ruleId());
        commands.set("#MatchValueField.Value", draft.matchValue());
        commands.set("#MaxStackInput.Value", draft.maxStack());
        commands.set("#PriorityInput.Value", draft.priority());
        commands.set("#EnabledCheck.Value", draft.enabled());
        commands.set("#AllowUnsafeCheck.Value", draft.allowUnsafe());
        commands.set("#DeleteButton.Visible", ruleIndex >= 0 && !pendingDelete);
        commands.set("#DeleteConfirmation.Visible", ruleIndex >= 0 && pendingDelete);
        commands.set("#StatusLabel.TextSpans", Message.raw(status));

        commands.set("#ActionDropdown.Entries", List.of(
                entry(translate("ui.action.set"), RuleAction.SET.name()),
                entry(translate("ui.action.exclude"), RuleAction.EXCLUDE.name())
        ));
        commands.set("#ActionDropdown.Value", draft.action().name());
        commands.set("#MatchTypeDropdown.Entries", List.of(
                entry(translate("ui.match_type.exact"), MatchType.EXACT.name()),
                entry(translate("ui.match_type.prefix"), MatchType.PREFIX.name()),
                entry(translate("ui.match_type.suffix"), MatchType.SUFFIX.name()),
                entry(translate("ui.match_type.glob"), MatchType.GLOB.name()),
                entry(translate("ui.match_type.regex"), MatchType.REGEX.name())
        ));
        commands.set("#MatchTypeDropdown.Value", draft.matchType().name());
        renderIcon(commands);
    }

    private void renderStaticText(UICommandBuilder commands) {
        setText(commands, "#RuleIdLabel", translate("ui.rule.id"));
        commands.set("#RuleIdField.PlaceholderText", translate("ui.rule.id_placeholder"));
        setText(commands, "#ActionLabel", translate("ui.rule.action"));
        setText(commands, "#MatchTypeLabel", translate("ui.rule.match_type"));
        setText(commands, "#MatchValueLabel", translate("ui.rule.match_value"));
        commands.set("#MatchValueField.PlaceholderText", translate("ui.rule.match_value_placeholder"));
        setText(commands, "#IconLabel", translate("ui.rule.icon"));
        setText(commands, "#IconHintLabel", translate("ui.rule.icon_hint"));
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

    private void renderDynamicText(UICommandBuilder commands) {
        commands.set("#EditorTitle.TextSpans", Message.raw(translate(
                ruleIndex >= 0 ? "ui.rule.edit_title" : "ui.rule.create_title"
        )));
        commands.set("#DeleteConfirmationLabel.TextSpans", Message.raw(
                translate("ui.rule.delete_confirmation", draft.ruleId())
        ));
    }

    private void renderIcon(UICommandBuilder commands) {
        String itemId = draft.iconItemId();
        boolean hasIcon = itemId != null;
        boolean validIcon = hasIcon && plugin.getItemIconCatalog().isValidItemId(itemId);
        ItemIconEntry entry = validIcon ? plugin.getItemIconCatalog().describe(itemId, locale) : null;
        String displayName;
        if (!hasIcon) displayName = translate("ui.rule.no_icon");
        else if (!validIcon) displayName = translate("ui.rule.icon_unavailable");
        else displayName = entry != null && entry.hasDisplayName() ? entry.displayName() : itemId;

        commands.set("#SelectedIcon.Visible", validIcon);
        if (validIcon) {
            commands.set("#SelectedIcon.ItemId", itemId);
            commands.set("#SelectedIcon.ShowItemTooltip", false);
        }
        commands.set("#NoIconLabel.Visible", !hasIcon);
        commands.set("#MissingIconLabel.Visible", hasIcon && !validIcon);
        commands.set("#IconNameLabel.TextSpans", Message.raw(displayName));
        commands.set("#IconIdLabel.TextSpans", Message.raw(hasIcon ? itemId : ""));
        setText(commands, "#ChooseIconButton", translate(hasIcon ? "ui.rule.change_icon" : "ui.rule.choose_icon"));
        commands.set("#RemoveIconButton.Visible", hasIcon);
        setText(commands, "#RemoveIconButton", translate("ui.rule.remove_icon"));
    }

    private void initializeDraft() {
        if (draft != null) return;
        StackWiseConfig config = plugin.getConfig();
        StackRule rule = ruleIndex >= 0 && ruleIndex < config.rules.size()
                ? config.rules.get(ruleIndex).copy()
                : newRule(config);
        draft = RuleEditorDraft.fromRule(rule);
    }

    private StackRule newRule(StackWiseConfig config) {
        StackRule rule = new StackRule();
        rule.id = nextId(config);
        rule.maxStack = Math.min(100, StackWiseConfig.MAX_STACK_LIMIT);
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

    private RuleEditorDraft draftFrom(RuleEventData data) {
        return new RuleEditorDraft(
                data.ruleId,
                data.enabled,
                parseAction(data.ruleAction),
                parseMatchType(data.matchType),
                data.matchValue,
                data.maxStack,
                data.priority,
                data.allowUnsafe,
                draft.iconItemId()
        );
    }

    private DropdownEntryInfo entry(String label, String value) {
        return new DropdownEntryInfo(LocalizableString.fromString(label), value);
    }

    private void refreshIconState() {
        UICommandBuilder updates = new UICommandBuilder();
        renderIcon(updates);
        updates.set("#StatusLabel.TextSpans", Message.raw(status));
        sendUpdate(updates, false);
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

    private void setText(UICommandBuilder commands, String selector, String value) {
        commands.set(selector + ".TextSpans", Message.raw(value));
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
