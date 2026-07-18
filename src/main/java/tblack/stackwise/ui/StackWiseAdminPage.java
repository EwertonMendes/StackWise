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
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import tblack.stackwise.OperationResult;
import tblack.stackwise.StackWisePlugin;
import tblack.stackwise.config.GlobalStackMode;
import tblack.stackwise.config.StackWiseConfig;
import tblack.stackwise.diagnostics.LogSeverity;
import tblack.stackwise.i18n.I18n;
import tblack.stackwise.rule.MatchType;
import tblack.stackwise.rule.RuleAction;
import tblack.stackwise.rule.StackRule;
import tblack.stackwise.stack.StackApplyReport;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StackWiseAdminPage extends InteractiveCustomUIPage<StackWiseAdminPage.AdminEventData> {
    private static final String LAYOUT = "StackWise/Admin.ui";
    private static final String TAB_RULES = "rules";
    private static final String TAB_SETTINGS = "settings";
    private static final int PAGE_SIZE = 8;

    private final StackWisePlugin plugin;
    private final PlayerRef viewerRef;
    private final String locale;
    private int page;
    private Integer pendingDeleteIndex;
    private String searchQuery;
    private String status;
    private String activeTab;
    private LogSeverity statusSeverity;

    public StackWiseAdminPage(
            @Nonnull PlayerRef playerRef,
            StackWisePlugin plugin,
            int page,
            String searchQuery,
            String status
    ) {
        this(playerRef, plugin, page, searchQuery, status, TAB_RULES);
    }

    public StackWiseAdminPage(
            @Nonnull PlayerRef playerRef,
            StackWisePlugin plugin,
            int page,
            String searchQuery,
            String status,
            String activeTab
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, AdminEventData.CODEC);
        this.plugin = plugin;
        this.viewerRef = playerRef;
        this.locale = I18n.localeFromPlayerRef(playerRef);
        this.page = Math.max(0, page);
        this.searchQuery = searchQuery == null ? "" : searchQuery.trim();
        this.status = status == null ? "" : status;
        this.activeTab = TAB_SETTINGS.equals(activeTab) ? TAB_SETTINGS : TAB_RULES;
        this.statusSeverity = this.status.isBlank() ? LogSeverity.INFO : LogSeverity.SUCCESS;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store
    ) {
        List<Integer> filtered = filteredIndexes();
        clampPage(filtered.size());
        commands.append(LAYOUT);
        bindEvents(events);
        render(commands, filtered);
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            AdminEventData data
    ) {
        super.handleDataEvent(ref, store, data);
        if (!hasPermission()) {
            close();
            return;
        }
        if (data == null || data.action == null || data.action.isBlank()) {
            refreshAll();
            return;
        }

        switch (data.action) {
            case "close" -> close();
            case "tab-rules" -> selectTab(TAB_RULES);
            case "tab-settings" -> selectTab(TAB_SETTINGS);
            case "previous" -> previousPage();
            case "next" -> nextPage();
            case "new" -> openRuleEditor(ref, store, -1);
            case "global-mode-changed" -> previewGlobalMode(data.globalStackMode);
            case "save-general" -> saveGeneral(data);
            case "reload" -> finish(plugin.reloadFromDisk());
            case "view-log" -> openLog(ref, store);
            case "search" -> applySearch(data.searchQuery);
            case "clear-search" -> applySearch("");
            case "delete-cancel" -> cancelDelete();
            default -> {
                if (!handleSlotAction(ref, store, data.action)) refreshAll();
            }
        }
    }

    private void selectTab(String tab) {
        activeTab = TAB_SETTINGS.equals(tab) ? TAB_SETTINGS : TAB_RULES;
        pendingDeleteIndex = null;
        status = "";
        statusSeverity = LogSeverity.INFO;
        refreshAll();
    }

    private void previousPage() {
        page = Math.max(0, page - 1);
        pendingDeleteIndex = null;
        refreshAll();
    }

    private void nextPage() {
        page++;
        pendingDeleteIndex = null;
        clampPage(filteredIndexes().size());
        refreshAll();
    }

    private void applySearch(String value) {
        searchQuery = value == null ? "" : value.trim();
        page = 0;
        pendingDeleteIndex = null;
        status = searchQuery.isBlank()
                ? ""
                : translate("ui.admin.search_applied", filteredIndexes().size());
        statusSeverity = LogSeverity.INFO;
        activeTab = TAB_RULES;
        refreshAll();
    }

    private void saveGeneral(AdminEventData data) {
        StackWiseConfig config = plugin.getConfig();
        config.enabled = data.enabled;
        config.globalLimitEnabled = data.globalLimitEnabled;
        config.globalStackMode = parseGlobalStackMode(data.globalStackMode, config.globalStackMode);
        config.globalStackLimit = data.globalStackLimit;
        config.globalStackMultiplier = data.globalStackMultiplier;
        config.globalStackCap = data.globalStackCap;
        config.safeMode = data.safeMode;
        config.allowDecreases = data.allowDecreases;
        config.allowRuntimeDecreases = data.allowRuntimeDecreases;
        config.restoreUnmatchedItems = data.restoreUnmatched;
        config.respectExternalChanges = data.respectExternalChanges;
        finish(plugin.saveAndApply(config));
    }

    private void previewGlobalMode(String value) {
        StackWiseConfig config = plugin.getConfig();
        GlobalStackMode mode = parseGlobalStackMode(value, config.globalStackMode);
        UICommandBuilder commands = new UICommandBuilder();
        renderGlobalModeDetails(commands, mode);
        sendUpdate(commands, false);
    }

    private boolean handleSlotAction(Ref<EntityStore> ref, Store<EntityStore> store, String action) {
        if (action.startsWith("edit-slot:")) {
            int index = indexForSlot(parseIndex(action, "edit-slot:"));
            pendingDeleteIndex = null;
            openRuleEditor(ref, store, index);
            return true;
        }
        if (action.startsWith("delete-request-slot:")) {
            int index = indexForSlot(parseIndex(action, "delete-request-slot:"));
            StackWiseConfig config = plugin.getConfig();
            if (!validIndex(index, config.rules.size())) {
                pendingDeleteIndex = null;
                status = translate("messages.rule_missing");
                statusSeverity = LogSeverity.ERROR;
                refreshAll();
                return true;
            }
            pendingDeleteIndex = index;
            status = "";
            statusSeverity = LogSeverity.INFO;
            refreshAll();
            return true;
        }
        if (action.startsWith("delete-confirm-slot:")) {
            int index = indexForSlot(parseIndex(action, "delete-confirm-slot:"));
            confirmDelete(index);
            return true;
        }
        return false;
    }

    private void confirmDelete(int index) {
        StackWiseConfig config = plugin.getConfig();
        if (!validIndex(index, config.rules.size()) || !Integer.valueOf(index).equals(pendingDeleteIndex)) {
            pendingDeleteIndex = null;
            status = translate("messages.rule_missing");
            statusSeverity = LogSeverity.ERROR;
            refreshAll();
            return;
        }
        String id = config.rules.get(index).id;
        config.rules.remove(index);
        pendingDeleteIndex = null;
        OperationResult result = plugin.saveAndApply(config);
        status = result.success()
                ? translate("messages.rule_deleted", id) + " " + result.translated(locale)
                : result.translated(locale);
        statusSeverity = severity(result);
        clampPage(filteredIndexes().size());
        refreshAll();
    }

    private void cancelDelete() {
        pendingDeleteIndex = null;
        status = "";
        statusSeverity = LogSeverity.INFO;
        refreshAll();
    }

    private int indexForSlot(int slot) {
        if (slot < 0 || slot >= PAGE_SIZE) return -1;
        List<Integer> filtered = filteredIndexes();
        int position = page * PAGE_SIZE + slot;
        return position >= 0 && position < filtered.size() ? filtered.get(position) : -1;
    }

    private void openRuleEditor(Ref<EntityStore> ref, Store<EntityStore> store, int index) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            status = translate("messages.player_not_found");
            statusSeverity = LogSeverity.ERROR;
            refreshAll();
            return;
        }
        player.getPageManager().openCustomPage(
                ref,
                store,
                new StackWiseRulePage(viewerRef, plugin, index, page, searchQuery)
        );
    }

    private void openLog(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            status = translate("messages.player_not_found");
            statusSeverity = LogSeverity.ERROR;
            refreshAll();
            return;
        }
        player.getPageManager().openCustomPage(
                ref,
                store,
                new StackWiseLogPage(viewerRef, plugin, page, searchQuery, activeTab)
        );
    }

    private void finish(OperationResult result) {
        status = result.translated(locale);
        statusSeverity = severity(result);
        clampPage(filteredIndexes().size());
        refreshAll();
    }

    private LogSeverity severity(OperationResult result) {
        if (!result.success()) return LogSeverity.ERROR;
        StackApplyReport report = result.report();
        if (report != null && report.failures > 0) return LogSeverity.ERROR;
        if (report != null && report.blockedCount() > 0) return LogSeverity.WARNING;
        if (result.validation() != null && !result.validation().warnings().isEmpty()) return LogSeverity.WARNING;
        return LogSeverity.SUCCESS;
    }

    private void bindEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClosePageButton", event("close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RulesTabActive", event("tab-rules"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RulesTabInactive", event("tab-rules"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SettingsTabActive", event("tab-settings"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SettingsTabInactive", event("tab-settings"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PreviousButton", event("previous"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton", event("next"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NewRuleButton", event("new"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ReloadButton", event("reload"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ViewLogButton", event("view-log"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearSearchButton", event("clear-search"), false);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SearchButton",
                searchEvent(),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Validating,
                "#SearchField",
                searchEvent(),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#GlobalStackModeDropdown",
                EventData.of("Action", "global-mode-changed")
                        .append("@GlobalStackMode", "#GlobalStackModeDropdown.Value"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SaveGeneralButton",
                EventData.of("Action", "save-general")
                        .append("@Enabled", "#EnabledCheck.Value")
                        .append("@GlobalLimitEnabled", "#GlobalLimitEnabledCheck.Value")
                        .append("@GlobalStackMode", "#GlobalStackModeDropdown.Value")
                        .append("@GlobalStackLimit", "#GlobalStackLimitInput.Value")
                        .append("@GlobalStackMultiplier", "#GlobalStackMultiplierInput.Value")
                        .append("@GlobalStackCap", "#GlobalStackCapInput.Value")
                        .append("@SafeMode", "#SafeModeCheck.Value")
                        .append("@AllowDecreases", "#AllowDecreasesCheck.Value")
                        .append("@AllowRuntimeDecreases", "#AllowRuntimeDecreasesCheck.Value")
                        .append("@RestoreUnmatched", "#RestoreUnmatchedCheck.Value")
                        .append("@RespectExternalChanges", "#RespectExternalChangesCheck.Value"),
                false
        );
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#EditButton" + slot, event("edit-slot:" + slot), false);
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#DeleteButton" + slot,
                    event("delete-request-slot:" + slot),
                    false
            );
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#ConfirmDeleteButton" + slot,
                    event("delete-confirm-slot:" + slot),
                    false
            );
            events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelDeleteButton" + slot, event("delete-cancel"), false);
        }
    }

    private void refreshAll() {
        UICommandBuilder commands = new UICommandBuilder();
        List<Integer> filtered = filteredIndexes();
        clampPage(filtered.size());
        render(commands, filtered);
        sendUpdate(commands, false);
    }

    private void render(UICommandBuilder commands, List<Integer> filtered) {
        renderStaticText(commands);
        renderTabs(commands);
        renderSettings(commands);
        renderRules(commands, filtered);
        renderStatus(commands);
    }

    private void renderTabs(UICommandBuilder commands) {
        boolean rules = TAB_RULES.equals(activeTab);
        commands.set("#RulesTabActive.Visible", rules);
        commands.set("#RulesTabInactive.Visible", !rules);
        commands.set("#SettingsTabActive.Visible", !rules);
        commands.set("#SettingsTabInactive.Visible", rules);
        commands.set("#RulesTabContent.Visible", rules);
        commands.set("#SettingsTabContent.Visible", !rules);
    }

    private void renderSettings(UICommandBuilder commands) {
        StackWiseConfig config = plugin.getConfig();
        commands.set("#EnabledCheck.Value", config.enabled);
        commands.set("#GlobalLimitEnabledCheck.Value", config.globalLimitEnabled);
        commands.set("#GlobalStackModeDropdown.Entries", List.of(
                entry(translate("ui.global_mode.fixed"), GlobalStackMode.FIXED.name()),
                entry(translate("ui.global_mode.multiplier"), GlobalStackMode.MULTIPLIER.name())
        ));
        commands.set("#GlobalStackModeDropdown.Value", config.globalStackMode.name());
        renderGlobalModeDetails(commands, config.globalStackMode);
        commands.set("#GlobalStackLimitInput.Value", config.globalStackLimit);
        commands.set("#GlobalStackMultiplierInput.Value", config.globalStackMultiplier);
        commands.set("#GlobalStackCapInput.Value", config.globalStackCap);
        commands.set("#SafeModeCheck.Value", config.safeMode);
        commands.set("#AllowDecreasesCheck.Value", config.allowDecreases);
        commands.set("#AllowRuntimeDecreasesCheck.Value", config.allowRuntimeDecreases);
        commands.set("#RestoreUnmatchedCheck.Value", config.restoreUnmatchedItems);
        commands.set("#RespectExternalChangesCheck.Value", config.respectExternalChanges);
    }

    private void renderGlobalModeDetails(UICommandBuilder commands, GlobalStackMode mode) {
        GlobalStackMode selected = mode == null ? GlobalStackMode.FIXED : mode;
        boolean fixed = selected == GlobalStackMode.FIXED;
        setText(
                commands,
                "#GlobalStackModeHintLabel",
                translate(fixed
                        ? "ui.admin.global_stack_mode_fixed_hint"
                        : "ui.admin.global_stack_mode_multiplier_hint")
        );
        commands.set("#FixedModeCard.Visible", fixed);
        commands.set("#MultiplierModeCard.Visible", !fixed);
    }

    private void renderRules(UICommandBuilder commands, List<Integer> filtered) {
        StackWiseConfig config = plugin.getConfig();
        commands.set("#SearchField.Value", searchQuery);
        int totalPages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        commands.set("#PageLabel.TextSpans", Message.raw(translate(
                "ui.admin.page",
                page + 1,
                totalPages,
                filtered.size(),
                config.rules.size()
        )));
        commands.set("#PreviousButton.Disabled", page == 0);
        commands.set("#NextButton.Disabled", page + 1 >= totalPages);

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int position = page * PAGE_SIZE + slot;
            boolean visible = position < filtered.size();
            commands.set("#RuleRow" + slot + ".Visible", visible);
            if (!visible) continue;
            int index = filtered.get(position);
            StackRule rule = config.rules.get(index);
            boolean confirming = Integer.valueOf(index).equals(pendingDeleteIndex);
            commands.set("#RuleActions" + slot + ".Visible", !confirming);
            commands.set("#DeleteConfirmation" + slot + ".Visible", confirming);
            renderRuleIcon(commands, slot, rule);
            commands.set("#RuleName" + slot + ".TextSpans", Message.raw(
                    rule.id + " - " + translate(rule.enabled ? "ui.common.enabled" : "ui.common.disabled")
            ));
            String target = actionLabel(rule.action) + " - " + matchTypeLabel(rule.matchType) + " - " + rule.value;
            String limit = rule.action == RuleAction.SET ? " => " + rule.maxStack : "";
            String matches = translate("ui.admin.matches", plugin.getApplyService().matchCount(rule.id));
            String description = target + limit + " - " + translate("ui.admin.priority", rule.priority) + " - " + matches;
            RuleCardLayout.Dimensions dimensions = RuleCardLayout.measure(description);
            applyRuleCardLayout(commands, slot, dimensions);
            commands.set("#RuleDescription" + slot + ".TextSpans", Message.raw(description));
            commands.set("#DeleteConfirmationLabel" + slot + ".TextSpans", Message.raw(
                    translate("ui.admin.delete_confirmation", rule.id)
            ));
        }
    }


    private void applyRuleCardLayout(
            UICommandBuilder commands,
            int slot,
            RuleCardLayout.Dimensions dimensions
    ) {
        commands.setObject(
                "#RuleRow" + slot + ".Anchor",
                anchor(null, dimensions.rowHeight(), null, null, RuleCardLayout.ROW_GAP)
        );
        commands.setObject(
                "#RuleIconPanel" + slot + ".Anchor",
                anchor(
                        RuleCardLayout.ICON_WIDTH,
                        RuleCardLayout.ICON_HEIGHT,
                        dimensions.centeredTop(RuleCardLayout.ICON_HEIGHT),
                        RuleCardLayout.CONTENT_GAP,
                        null
                )
        );
        commands.setObject(
                "#RuleText" + slot + ".Anchor",
                anchor(null, dimensions.textHeight(), null, RuleCardLayout.CONTENT_GAP, null)
        );
        commands.setObject(
                "#RuleDescription" + slot + ".Anchor",
                anchor(null, dimensions.descriptionHeight(), null, null, null)
        );
        commands.setObject(
                "#RuleActions" + slot + ".Anchor",
                anchor(
                        RuleCardLayout.ACTIONS_WIDTH,
                        RuleCardLayout.ACTIONS_HEIGHT,
                        dimensions.centeredTop(RuleCardLayout.ACTIONS_HEIGHT),
                        null,
                        null
                )
        );
        commands.setObject(
                "#DeleteConfirmation" + slot + ".Anchor",
                anchor(
                        RuleCardLayout.CONFIRMATION_WIDTH,
                        RuleCardLayout.CONFIRMATION_HEIGHT,
                        dimensions.centeredTop(RuleCardLayout.CONFIRMATION_HEIGHT),
                        null,
                        null
                )
        );
    }

    private Anchor anchor(Integer width, Integer height, Integer top, Integer right, Integer bottom) {
        Anchor anchor = new Anchor();
        if (width != null) anchor.setWidth(Value.of(width));
        if (height != null) anchor.setHeight(Value.of(height));
        if (top != null) anchor.setTop(Value.of(top));
        if (right != null) anchor.setRight(Value.of(right));
        if (bottom != null) anchor.setBottom(Value.of(bottom));
        return anchor;
    }

    private void renderRuleIcon(UICommandBuilder commands, int slot, StackRule rule) {
        String itemId = rule.iconItemId;
        boolean hasIcon = itemId != null && !itemId.isBlank();
        boolean validIcon = hasIcon && plugin.getItemIconCatalog().isValidItemId(itemId);
        commands.set("#RuleIcon" + slot + ".Visible", validIcon);
        if (validIcon) {
            commands.set("#RuleIcon" + slot + ".ItemId", itemId);
            commands.set("#RuleIcon" + slot + ".ShowItemTooltip", false);
        }
        commands.set("#RuleNoIcon" + slot + ".Visible", !hasIcon);
        commands.set("#RuleMissingIcon" + slot + ".Visible", hasIcon && !validIcon);
    }

    private void renderStatus(UICommandBuilder commands) {
        boolean visible = status != null && !status.isBlank();
        commands.set("#StatusGroup.Visible", visible);
        setSeverityLabels(commands, "Status", statusSeverity, visible ? status : "");
        commands.set("#ViewLogButton.Disabled", !plugin.getOperationLogService().hasEntries());
    }

    private void setSeverityLabels(UICommandBuilder commands, String prefix, LogSeverity severity, String text) {
        for (LogSeverity value : LogSeverity.values()) {
            String id = "#" + prefix + severitySuffix(value) + "Label";
            commands.set(id + ".Visible", value == severity);
            if (value == severity) commands.set(id + ".TextSpans", Message.raw(text));
        }
    }

    private String severitySuffix(LogSeverity severity) {
        return switch (severity) {
            case INFO -> "Info";
            case SUCCESS -> "Success";
            case WARNING -> "Warning";
            case ERROR -> "Error";
        };
    }

    private void renderStaticText(UICommandBuilder commands) {
        setText(commands, "#AdminTitle", translate("ui.admin.title"));
        setText(commands, "#RulesTabActive", translate("ui.admin.tab_rules"));
        setText(commands, "#RulesTabInactive", translate("ui.admin.tab_rules"));
        setText(commands, "#SettingsTabActive", translate("ui.admin.tab_settings"));
        setText(commands, "#SettingsTabInactive", translate("ui.admin.tab_settings"));
        setText(commands, "#ViewLogButton", translate("ui.admin.view_log"));
        setText(commands, "#GlobalSettingsLabel", translate("ui.admin.global_settings"));
        setText(commands, "#BehaviorSettingsLabel", translate("ui.admin.behavior_settings"));
        setText(commands, "#GlobalLimitSettingsLabel", translate("ui.admin.global_limit_settings"));
        setText(commands, "#EnabledLabel", translate("ui.admin.enabled"));
        setText(commands, "#EnabledHintLabel", translate("ui.admin.enabled_hint"));
        setText(commands, "#SafeModeLabel", translate("ui.admin.safe_mode"));
        setText(commands, "#RespectExternalChangesLabel", translate("ui.admin.respect_external"));
        setText(commands, "#AllowDecreasesLabel", translate("ui.admin.allow_decreases"));
        setText(commands, "#AllowRuntimeDecreasesLabel", translate("ui.admin.allow_runtime_decreases"));
        setText(commands, "#RestoreUnmatchedLabel", translate("ui.admin.restore_unmatched"));
        setText(commands, "#GlobalLimitEnabledLabel", translate("ui.admin.global_limit_enabled"));
        setText(commands, "#GlobalStackModeLabel", translate("ui.admin.global_stack_mode"));
        setText(commands, "#GlobalStackLimitLabel", translate("ui.admin.global_stack_limit"));
        setText(commands, "#GlobalStackMultiplierLabel", translate("ui.admin.global_stack_multiplier"));
        setText(commands, "#GlobalStackCapLabel", translate("ui.admin.global_stack_cap"));
        setText(commands, "#FixedModeCardLabel", translate("ui.global_mode.fixed"));
        setText(commands, "#MultiplierModeCardLabel", translate("ui.global_mode.multiplier"));
        setText(commands, "#LiveApplyHintLabel", translate("ui.admin.live_apply_hint"));
        setText(commands, "#SaveGeneralButton", translate("ui.admin.save_settings"));
        setText(commands, "#SearchLabel", translate("ui.admin.search_label"));
        commands.set("#SearchField.PlaceholderText", translate("ui.admin.search_placeholder"));
        setText(commands, "#SearchButton", translate("ui.admin.search"));
        setText(commands, "#ClearSearchButton", translate("ui.admin.clear_search"));
        setText(commands, "#NewRuleButton", translate("ui.admin.new_rule"));
        setText(commands, "#ReloadButton", translate("ui.admin.reload_json"));
        setText(commands, "#RulesLabel", translate("ui.admin.rules"));
        setText(commands, "#PreviousButton", translate("ui.common.previous"));
        setText(commands, "#NextButton", translate("ui.common.next"));
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            setText(commands, "#EditButton" + slot, translate("ui.common.edit"));
            setText(commands, "#DeleteButton" + slot, translate("ui.common.delete"));
            setText(commands, "#CancelDeleteButton" + slot, translate("ui.common.cancel"));
            setText(commands, "#ConfirmDeleteButton" + slot, translate("ui.common.confirm_delete"));
            setText(commands, "#RuleNoIcon" + slot, translate("ui.admin.no_icon"));
            setText(commands, "#RuleMissingIcon" + slot, translate("ui.admin.icon_unavailable"));
        }
    }

    private void setText(UICommandBuilder commands, String selector, String value) {
        commands.set(selector + ".TextSpans", Message.raw(value));
    }

    private List<Integer> filteredIndexes() {
        StackWiseConfig config = plugin.getConfig();
        List<Integer> indexes = new ArrayList<>();
        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        for (int index = 0; index < config.rules.size(); index++) {
            StackRule rule = config.rules.get(index);
            if (rule == null) continue;
            if (query.isBlank() || contains(rule.id, query) || contains(rule.value, query) || contains(rule.iconItemId, query)) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String actionLabel(RuleAction action) {
        return translate(action == RuleAction.EXCLUDE ? "ui.action.exclude" : "ui.action.set");
    }

    private String matchTypeLabel(MatchType matchType) {
        return translate("ui.match_type." + matchType.name().toLowerCase(Locale.ROOT));
    }

    private void clampPage(int size) {
        int totalPages = Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));
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

    private EventData event(String action) {
        return EventData.of("Action", action);
    }

    private EventData searchEvent() {
        return EventData.of("Action", "search").append("@SearchQuery", "#SearchField.Value");
    }

    private int parseIndex(String action, String prefix) {
        try {
            return Integer.parseInt(action.substring(prefix.length()));
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private boolean validIndex(int index, int size) {
        return index >= 0 && index < size;
    }

    private String translate(String key, Object... args) {
        return I18n.translate(locale, key, args);
    }

    private DropdownEntryInfo entry(String label, String value) {
        return new DropdownEntryInfo(LocalizableString.fromString(label), value);
    }

    private GlobalStackMode parseGlobalStackMode(String value, GlobalStackMode fallback) {
        try {
            return GlobalStackMode.valueOf(value);
        } catch (RuntimeException exception) {
            return fallback == null ? GlobalStackMode.FIXED : fallback;
        }
    }

    public static final class AdminEventData {
        public static final BuilderCodec<AdminEventData> CODEC = BuilderCodec.builder(AdminEventData.class, AdminEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action).add()
                .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (data, value) -> data.searchQuery = value, data -> data.searchQuery).add()
                .append(new KeyedCodec<>("@Enabled", Codec.BOOLEAN), (data, value) -> data.enabled = value, data -> data.enabled).add()
                .append(new KeyedCodec<>("@GlobalLimitEnabled", Codec.BOOLEAN), (data, value) -> data.globalLimitEnabled = value, data -> data.globalLimitEnabled).add()
                .append(new KeyedCodec<>("@GlobalStackMode", Codec.STRING), (data, value) -> data.globalStackMode = value, data -> data.globalStackMode).add()
                .append(new KeyedCodec<>("@GlobalStackLimit", Codec.INTEGER), (data, value) -> data.globalStackLimit = value, data -> data.globalStackLimit).add()
                .append(new KeyedCodec<>("@GlobalStackMultiplier", Codec.DOUBLE), (data, value) -> data.globalStackMultiplier = value, data -> data.globalStackMultiplier).add()
                .append(new KeyedCodec<>("@GlobalStackCap", Codec.INTEGER), (data, value) -> data.globalStackCap = value, data -> data.globalStackCap).add()
                .append(new KeyedCodec<>("@SafeMode", Codec.BOOLEAN), (data, value) -> data.safeMode = value, data -> data.safeMode).add()
                .append(new KeyedCodec<>("@AllowDecreases", Codec.BOOLEAN), (data, value) -> data.allowDecreases = value, data -> data.allowDecreases).add()
                .append(new KeyedCodec<>("@AllowRuntimeDecreases", Codec.BOOLEAN), (data, value) -> data.allowRuntimeDecreases = value, data -> data.allowRuntimeDecreases).add()
                .append(new KeyedCodec<>("@RestoreUnmatched", Codec.BOOLEAN), (data, value) -> data.restoreUnmatched = value, data -> data.restoreUnmatched).add()
                .append(new KeyedCodec<>("@RespectExternalChanges", Codec.BOOLEAN), (data, value) -> data.respectExternalChanges = value, data -> data.respectExternalChanges).add()
                .build();

        public String action = "";
        public String searchQuery = "";
        public boolean enabled;
        public boolean globalLimitEnabled;
        public String globalStackMode = GlobalStackMode.FIXED.name();
        public int globalStackLimit;
        public double globalStackMultiplier;
        public int globalStackCap;
        public boolean safeMode;
        public boolean allowDecreases;
        public boolean allowRuntimeDecreases;
        public boolean restoreUnmatched;
        public boolean respectExternalChanges;
    }
}
