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
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import tblack.stackwise.StackWisePlugin;
import tblack.stackwise.i18n.I18n;
import tblack.stackwise.icon.ItemIconEntry;

import javax.annotation.Nonnull;
import java.util.List;

public final class StackWiseIconPickerPage extends InteractiveCustomUIPage<StackWiseIconPickerPage.IconPickerEventData> {
    private static final String LAYOUT = "StackWise/IconPicker.ui";
    private static final int COLUMNS = 6;
    private static final int ROWS = 4;
    private static final int PAGE_SIZE = COLUMNS * ROWS;

    private final StackWisePlugin plugin;
    private final PlayerRef viewerRef;
    private final String locale;
    private final int ruleIndex;
    private final int returnPage;
    private final String returnSearch;
    private final RuleEditorDraft draft;
    private String query = "";
    private String searchDraft = "";
    private String status = "";
    private List<ItemIconEntry> matches;
    private int page;

    public StackWiseIconPickerPage(
            @Nonnull PlayerRef playerRef,
            StackWisePlugin plugin,
            int ruleIndex,
            int returnPage,
            String returnSearch,
            RuleEditorDraft draft
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, IconPickerEventData.CODEC);
        this.plugin = plugin;
        this.viewerRef = playerRef;
        this.locale = I18n.localeFromPlayerRef(playerRef);
        this.ruleIndex = ruleIndex;
        this.returnPage = Math.max(0, returnPage);
        this.returnSearch = returnSearch == null ? "" : returnSearch;
        this.draft = draft;
        this.matches = searchIcons("");
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
            IconPickerEventData data
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
            case "back" -> openEditor(ref, store, draft);
            case "search-value-changed" -> {
                searchDraft = data.searchValue == null ? "" : data.searchValue;
                sendUpdate();
            }
            case "search" -> applySearch(data.searchValue == null ? searchDraft : data.searchValue);
            case "clear-search" -> applySearch("");
            case "previous" -> {
                page = Math.max(0, page - 1);
                refreshPicker();
            }
            case "next" -> {
                page = Math.min(lastPage(), page + 1);
                refreshPicker();
            }
            case "remove-icon" -> openEditor(ref, store, draft.withIconItemId(null));
            default -> {
                if (data.action.startsWith("select:")) select(ref, store, data.action);
                else refreshPicker();
            }
        }
    }

    private void bindEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", event("close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", event("back"), false);
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchField",
                EventData.of("@SearchField", "#SearchField.Value").append("Action", "search-value-changed"),
                false
        );
        events.addEventBinding(CustomUIEventBindingType.Validating, "#SearchField", searchEvent(), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SearchButton", searchEvent(), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearSearchButton", event("clear-search"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PreviousButton", event("previous"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton", event("next"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RemoveIconButton", event("remove-icon"), false);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#IconButton" + slot,
                    event("select:" + slot),
                    false
            );
        }
    }

    private EventData event(String action) {
        return EventData.of("Action", action);
    }

    private EventData searchEvent() {
        return EventData.of("Action", "search").append("@SearchField", "#SearchField.Value");
    }

    private void applySearch(String rawQuery) {
        query = rawQuery == null ? "" : rawQuery.trim();
        searchDraft = query;
        matches = searchIcons(query);
        page = 0;
        refreshPicker();
    }

    private List<ItemIconEntry> searchIcons(String value) {
        try {
            status = "";
            return plugin.getItemIconCatalog().search(value, locale);
        } catch (RuntimeException exception) {
            status = translate("ui.icon_picker.error");
            return List.of();
        }
    }

    private void select(Ref<EntityStore> ref, Store<EntityStore> store, String action) {
        int slot;
        try {
            slot = Integer.parseInt(action.substring("select:".length()));
        } catch (NumberFormatException | IndexOutOfBoundsException exception) {
            refreshPicker();
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            refreshPicker();
            return;
        }
        int index = page * PAGE_SIZE + slot;
        if (index < 0 || index >= matches.size()) {
            refreshPicker();
            return;
        }
        openEditor(ref, store, draft.withIconItemId(matches.get(index).itemId()));
    }

    private void openEditor(Ref<EntityStore> ref, Store<EntityStore> store, RuleEditorDraft updatedDraft) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            status = translate("messages.player_not_found");
            refreshPicker();
            return;
        }
        player.getPageManager().openCustomPage(
                ref,
                store,
                new StackWiseRulePage(
                        viewerRef,
                        plugin,
                        ruleIndex,
                        returnPage,
                        returnSearch,
                        updatedDraft
                )
        );
    }

    private void render(UICommandBuilder commands) {
        setText(commands, "#PickerTitle", translate("ui.icon_picker.title"));
        setText(commands, "#Subtitle", translate("ui.icon_picker.subtitle"));
        commands.set("#SearchField.Value", searchDraft);
        commands.set("#SearchField.PlaceholderText", translate("ui.icon_picker.search_placeholder"));
        setText(commands, "#SearchButton", translate("ui.icon_picker.search"));
        setText(commands, "#ClearSearchButton", translate("ui.icon_picker.clear"));
        setText(commands, "#RemoveIconButton", translate("ui.icon_picker.remove_icon"));
        setText(commands, "#BackButton", translate("ui.icon_picker.back"));
        setText(commands, "#CloseButton", translate("ui.common.close"));
        commands.set("#ResultLabel.TextSpans", Message.raw(translate("ui.icon_picker.results", matches.size())));
        commands.set("#PageLabel.TextSpans", Message.raw(translate(
                "ui.icon_picker.page",
                page + 1,
                lastPage() + 1
        )));
        commands.set("#PreviousButton.Disabled", page <= 0);
        commands.set("#NextButton.Disabled", page >= lastPage());
        commands.set("#StatusLabel.TextSpans", Message.raw(status));

        boolean empty = matches.isEmpty();
        commands.set("#NoResultsLabel.Visible", empty);
        if (empty) {
            String key = query.isBlank() ? "ui.icon_picker.unavailable" : "ui.icon_picker.no_results";
            commands.set("#NoResultsLabel.TextSpans", Message.raw(translate(key)));
        }

        int start = page * PAGE_SIZE;
        for (int row = 0; row < ROWS; row++) {
            commands.set("#IconRow" + row + ".Visible", start + row * COLUMNS < matches.size());
        }
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            boolean visible = index < matches.size();
            commands.set("#IconCell" + slot + ".Visible", visible);
            commands.set("#IconButton" + slot + ".Disabled", !visible);
            if (!visible) continue;

            ItemIconEntry entry = matches.get(index);
            commands.set("#IconSlot" + slot + ".ItemId", entry.itemId());
            commands.set("#IconSlot" + slot + ".ShowItemTooltip", false);
            String name = entry.hasDisplayName() ? entry.displayName() : translate("ui.icon_picker.unknown_item");
            commands.set("#IconName" + slot + ".TextSpans", Message.raw(shorten(name, 34)));
            commands.set("#IconId" + slot + ".TextSpans", Message.raw(shorten(entry.itemId(), 38)));
        }
    }

    private void refreshPicker() {
        UICommandBuilder commands = new UICommandBuilder();
        render(commands);
        sendUpdate(commands, false);
    }

    private int lastPage() {
        return Math.max(0, (matches.size() - 1) / PAGE_SIZE);
    }

    private String shorten(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) return value == null ? "" : value;
        return value.substring(0, maximumLength - 1) + "…";
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

    public static final class IconPickerEventData {
        public static final BuilderCodec<IconPickerEventData> CODEC = BuilderCodec.builder(IconPickerEventData.class, IconPickerEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action).add()
                .append(new KeyedCodec<>("@SearchField", Codec.STRING), (data, value) -> data.searchValue = value, data -> data.searchValue).add()
                .build();

        public String action = "";
        public String searchValue;
    }
}
