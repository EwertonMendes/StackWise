# Architecture

## Responsibilities

`StackWisePlugin` owns the Hytale lifecycle, command registration, asset event registration, and service composition.

`ConfigManager` owns JSON persistence, normalization, validation, invalid file backups, and atomic writes inside the data directory supplied by `JavaPluginInit`.

`RuleCompiler` converts mutable configuration rules into an ordered immutable rule set.

`StackApplyService` owns the complete loaded item registry, original limits, conflict detection, safety checks, runtime protection, match counts, catalog diagnostics, and application reports.

`ItemStackLimitAdapter` isolates interaction with the Hytale item implementation.

`PermissionService` combines native permissions with optional LuckPerms access.

`I18n` resolves player locale and loads localized command, validation, and interface text.

`StackWiseAdminPage` and `StackWiseRulePage` expose the same configuration managed by `ConfigManager`.

## Asset flow

1. Hytale emits an item `LoadedAssetsEvent`.
2. StackWise reads the complete registry from `event.getAssetMap().getAssetMap()`.
3. The service snapshots original limits for newly observed item objects.
4. Rules are compiled once for the application cycle.
5. Items are evaluated in stable item id order.
6. A matching rule wins; otherwise the optional global limit is selected for originally stackable items.
7. The selected value is safety checked.
8. Only changed values are written.
9. The item packet cache is invalidated.
10. The write is read back and verified.
11. Match counts and a bounded diagnostic report are retained.

Using the complete asset map is required because a loaded asset event may contain only a small changed batch. Runtime configuration changes must still evaluate every loaded item.

## Runtime reload

A reload is accepted only after parsing, normalization, and validation. The current in-memory configuration remains active when a new file is invalid.

Live reductions are deferred by default. The configuration is saved and the report indicates that a restart is required.

## Localization

Internal configuration values are language independent. UI dropdowns display translated labels while submitting stable values such as `SET`, `EXCLUDE`, `EXACT`, and `PREFIX`.

The Java interface layer translates dynamic strings after resolving the player's locale. Command descriptions use Hytale server translation keys.

## Performance

StackWise performs no work per tick. Rules are compiled once per application cycle. Glob and regular expression patterns are compiled once. Exact, prefix, and suffix rules use direct string matching.

## Custom UI

UI files are located under `Common/UI/Custom/StackWise` and import `../Common.ui`.

Interactive controls use Hytale Common Styling templates. UI documents contain literal fallback text, while Java applies localized text at runtime through `TextSpans` and targets only explicitly named elements. Checkboxes and their labels are separate named controls, so no command depends on a template child. The UI contains unique element ids, typed event codecs, searchable pagination, global-limit controls, and two-step destructive actions.
