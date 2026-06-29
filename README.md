# StackWise

StackWise is a server-side Hytale mod for safe, deterministic, and configurable item stack limits.

It provides two synchronized configuration paths:

- `config.json` in the Hytale-provided StackWise data directory
- An in-game administration interface opened with `/stackwise` or `/sw`

Changes saved in the interface are written to the same JSON file. Manual JSON changes can be loaded with `/stackwise reload` or the `Reload JSON` button.

## Features

- Exact item id, prefix, suffix, glob, and regular expression matching
- Deterministic priority and conflict resolution
- Set and exclusion actions
- Optional global limit for every otherwise-unmatched stackable item
- Searchable and paginated rule management with Enter-key submission
- Explicit irreversible deletion confirmation
- Safe mode for originally non-stackable items
- Runtime decrease protection
- Restoration of items that stop matching a rule
- Conflict protection for values changed by another mod
- Full loaded item registry processing
- Item reference catalog export
- Atomic configuration writes and invalid configuration backups
- Native Hytale permissions and optional LuckPerms integration
- Two-tab administrative Custom UI built with Hytale Common Styling
- Color-coded operation log for load, reload, save, export, validation, and item application diagnostics
- Scroll-preserving in-place interface updates for routine actions
- Localized commands, messages, interface labels, and dropdown entries
- No per-tick processing
- Item packet cache invalidation after stack limit changes

## Requirements

- A current Hytale server installation
- Java 25
- LuckPerms is optional

## Installation

1. Install or update Hytale through the official launcher.
2. Run `./gradlew clean build`.
3. Copy `StackWise-1.0.0.jar` from `build/libs` into the server `mods` directory.
4. Remove older StackWise JAR files from the same directory.
5. Start the server.
6. Edit the generated `config.json` or open `/stackwise` in game.
7. Restart the server after reducing an active stack limit unless live decreases were explicitly enabled.

## Building

StackWise follows the same local Hytale dependency strategy as VoidVault. It automatically reads the server JAR and assets from the official installation directory on Windows, Linux, and macOS.

No Hytale JAR or assets file needs to be copied into the project.

Project updates may be extracted directly over the existing project directory. The build removes obsolete StackWise source files from earlier packages before compilation, so the IntelliJ project directory does not need to be recreated.

```bash
./gradlew clean build
```

The output is created at:

```text
build/libs/StackWise-1.0.0.jar
```

A local development server can be started with:

```bash
./gradlew runServer
```

## Commands

| Command | Purpose |
|---|---|
| `/stackwise` | Open the administration interface |
| `/stackwise ui` | Open the administration interface |
| `/stackwise reload` | Reload `config.json` and apply safe live changes |
| `/stackwise validate` | Validate the active configuration |
| `/stackwise export` | Export the complete loaded item reference catalog |
| `/stackwise status` | Show the latest application report |

The default alias is `/sw`.

## Permissions

The default administration permission is:

```text
stackwise.admin
```

Native Hytale permission providers and LuckPerms are supported. Wildcard administrators can also access the interface.

Example LuckPerms command:

```text
/lp group admin permission set stackwise.admin true
```

Permission is checked when the interface opens and before every write operation.

## Configuration

The configuration is created in the data directory supplied by Hytale to StackWise.

A complete example is available in `config.example.json`.

The first generated configuration contains active and functional examples for every action and match type:

- `SET` with `EXACT`
- `SET` with `PREFIX`
- `SET` with `SUFFIX`
- `SET` with `GLOB`
- `SET` with `REGEX`
- `EXCLUDE`

Existing configurations are never replaced during an update. Back up and remove the existing `config.json` before startup only when a new first-run configuration is intentionally required.

### Global limit

`globalLimitEnabled` is enabled by default. When enabled, every originally stackable item that does not match a rule receives `globalStackLimit`, which defaults to `1000`.

Specific rules always win over the global limit. An `EXCLUDE` rule keeps matching items at their original limits. Originally non-stackable items are never changed by the global limit.

When the global limit is disabled, otherwise-unmatched items keep or return to their original limits. A live reduction may still require a server restart unless `allowRuntimeDecreases` is enabled.

`maximumStack` remains the validation ceiling for both rule limits and the global limit.

### Rule precedence

Rules are evaluated in this order:

1. Higher priority
2. More specific match type
3. Earlier position in the configuration

Specificity order:

1. Exact
2. Prefix
3. Suffix
4. Glob
5. Regular expression

The first matching compiled rule wins.

### Match types

| Type | Example | Matches |
|---|---|---|
| `EXACT` | `Ingredient_Stick` | One exact item id |
| `PREFIX` | `Weapon_Arrow` | Every id starting with the value |
| `SUFFIX` | `_White` | Every id ending with the value |
| `GLOB` | `Plant_Seeds_*` | Simple `*` and `?` wildcard patterns |
| `REGEX` | `^Ingredient_Crystal_(Cyan\|Red)$` | Advanced regular expressions |

### Actions

| Action | Behavior |
|---|---|
| `SET` | Assign the configured maximum stack |
| `EXCLUDE` | Prevent lower-priority rules from changing the item |

### Safety settings

`safeMode` blocks increases for items whose original stack limit is one unless that rule enables `allowUnsafe`.

Stackable items remain eligible even when their asset contains weapon, tool, armor, or other state-related definitions. This allows ammunition such as `Weapon_Arrow_*` to be changed while still protecting originally non-stackable equipment.

`allowDecreases` controls whether a configured value may be lower than the original item limit.

`allowRuntimeDecreases` controls whether an active value may be reduced without restarting. It is disabled by default because existing oversized stacks may become invalid.

`restoreUnmatchedItems` restores the original value when a previously applied item no longer matches an active rule.

`respectExternalChanges` stops StackWise from overwriting a value that another mod changed after StackWise applied it.

The hard configurable limit is `9999`.

## Administration interface

The main administration page is separated into `Rules` and `Global settings` tabs. The tabs use Common Styling buttons rather than the currently problematic default `TabNavigation` styles.

Routine actions update the mounted document in place. The rule, settings, editor, and log scrolling areas enable `KeepScrollPosition`, preventing ordinary searches, pagination, delete confirmation, saves, and tab changes from rebuilding the page and returning to the top.

The rule editor uses a Common Styling dropdown style with `PanelAlign: Bottom`, so action and match-type entries open below their fields.

### Rule search

The administration interface can search case-insensitively by:

- Rule id
- Exact item id stored in `value`
- Prefix, suffix, glob, or regular expression text stored in `value`

The current search remains active when a rule is edited and the interface returns to the previous page. Search can be submitted with the button or the Enter key.


## Operation log

The `View log` button opens diagnostics for the most recent asset load, JSON reload, settings save, rule save, deletion, or catalog export. Entries are grouped visually by severity:

- Information
- Success
- Warning
- Error

The log includes validation paths, blocked unsafe items, restart-required reductions, conflicts with other mods, packet verification failures, exceptions, and a limited sample of successful changes. Errors and warnings are listed before successful item changes.

## Item reference catalog

Run `/stackwise export` or use `Export item reference` to create:

```text
<StackWise data directory>/item-catalog.json
```

The catalog is a rule-authoring and diagnostics reference. It is not imported automatically and does not change gameplay.

Use it to:

- Discover exact item ids for `EXACT` rules
- Identify shared prefixes and suffixes
- Verify how many items are available to StackWise
- See original, current, and requested stack limits
- See which rule controls an item
- Diagnose exclusions, unmatched items, and Safe Mode blocks

The export contains:

- `schemaVersion`
- `itemCount`
- `items`
- `itemId`
- `originalStack`
- `currentStack`
- `targetStack`
- `ruleId`
- `action`
- `matchType`
- `matchValue`
- `status`
- `unsafeReason`

After a normal server startup, `itemCount` should represent the complete loaded item registry rather than only the most recent asset update batch.

## Localization

StackWise includes the same localization architecture used by VoidVault.

Included locales:

- English (`en-US`)
- German (`de-DE`)
- Spanish (`es-ES`)
- French (`fr-FR`)
- Brazilian Portuguese (`pt-BR`)
- Russian (`ru-RU`)
- Ukrainian (`uk-UA`)
- Simplified Chinese (`zh-CN`)

Interface labels, rule actions, match types, command descriptions, validation messages, and command responses are translated. JSON enum values remain stable in English, such as `SET`, `EXCLUDE`, `PREFIX`, and `REGEX`, regardless of the player's language.

## Compatibility behavior

StackWise listens for Hytale item asset events and applies rules against the complete asset map. This ensures that rules created after startup can affect all loaded items instead of only the last event batch.

The item compatibility layer reads the public stack accessor when available, writes a supported field or setter, invalidates the cached item packet, and verifies the result.

When no compatible mutator is available, StackWise leaves assets unchanged and reports the adapter as unavailable in `/stackwise status`.

## Tests

```bash
./gradlew test
```

The test suite covers rule precedence, global-limit precedence and restoration, validation, localization key parity, Common Styling references, Java-to-UI selector contracts, unique UI element ids, bottom-aligned dropdowns, safe tab controls, Enter-key search, scroll retention declarations, operation-log controls, deletion confirmations, arrow rule regression, full catalog diagnostics, Safe Mode, runtime reductions, external conflicts, restoration, repeated asset loading, packet cache invalidation, and the VoidVault-equivalent Gradle dependency contract.

See `docs/VALIDATION.md` for the real-server release checklist.

## License

StackWise is licensed under the PolyForm Noncommercial License 1.0.0.
