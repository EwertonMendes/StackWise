# StackWise

StackWise is a server-side Hytale mod for safe, deterministic, and configurable item stack limits. It replaces broad one-value overrides with ordered rules, a global fallback, runtime safeguards, and an in-game administration interface.

## Main features

- Exact item id, prefix, suffix, glob, and regular-expression matching
- Deterministic rule priority and specificity
- `SET` and `EXCLUDE` actions
- Editable global stack limit, enabled by default at `1000`
- Safe mode for originally non-stackable items
- Protection against live decreases and external mod conflicts
- Restoration when StackWise is disabled or an item stops matching
- Searchable, paginated, localized Custom UI
- Actionable player-facing operation log and full technical server logging
- Manual Overstacked migration through `/stackwise import overstacked`
- Atomic configuration writes, backups, and validation
- No per-tick processing

## Requirements

- Current Hytale server installation
- Java 25 or newer
- LuckPerms is optional

## Installation

1. Run `./gradlew clean build`.
2. Copy `build/libs/StackWise-1.0.0.jar` to the server `mods` directory.
3. Remove older StackWise JARs.
4. Start the server.
5. Edit `mods/Tblack_StackWise/config.json` or open `/stackwise` in game.

StackWise is enabled by default on the first server start.

## Commands

| Command | Purpose |
|---|---|
| `/stackwise` | Open the administration interface |
| `/stackwise ui` | Open the administration interface |
| `/stackwise reload` | Reload and validate `config.json` |
| `/stackwise validate` | Validate the active configuration |
| `/stackwise import overstacked` | Import `MaxStackSizes.json` manually |
| `/stackwise status` | Show the latest application report |

The default alias is `/sw`. The default administration permission is `stackwise.admin`.

## Configuration

The generated schema starts at `configVersion: 1`. The public JSON contains only values that change StackWise behavior. Validation limits are internal constants:

- Minimum configurable stack: `1`
- Maximum configurable stack: `999999`
- Default global stack limit: `1000`

`globalStackLimit` remains editable. Values outside the supported range are rejected before the active configuration is replaced.

A complete example is available in `config.example.json`.

### Global limit

When `globalLimitEnabled` is true, every otherwise-unmatched item that was originally stackable receives `globalStackLimit`. Specific rules take precedence. `EXCLUDE` restores or preserves the baseline limit for matching items. Originally non-stackable items are not changed by the global fallback.

### Rule precedence

Rules are evaluated by:

1. Higher priority
2. More specific match type
3. Earlier position in the configuration

Specificity order: `EXACT`, `PREFIX`, `SUFFIX`, `GLOB`, `REGEX`.

### Safety and runtime behavior

- `safeMode` blocks increases for items whose baseline limit is one unless the matching rule enables `allowUnsafe`.
- `allowDecreases` controls values below the captured baseline.
- `allowRuntimeDecreases` controls ordinary live reductions while StackWise remains enabled.
- `restoreUnmatchedItems` restores items that stop matching a rule.
- `respectExternalChanges` prevents StackWise from overwriting a value changed later by another mod or server component.

When `enabled` becomes false, StackWise immediately attempts to restore only values it owns. This shutdown restoration is not blocked by `allowRuntimeDecreases`. Existing oversized inventory stacks, asset reload order, or values controlled by another mod can still require a server restart to fully normalize. The administration interface shows this warning below the enable checkbox.

## Overstacked migration

Migration is never automatic. Keep the original `MaxStackSizes.json` available and run:

```text
/stackwise import overstacked
```

The importer:

- Reads `ItemIds` and `Patterns`
- Validates integer limits from `1` through `999999`
- Preserves Overstacked regular expressions as `REGEX` rules
- Converts compatible item families into `PREFIX` rules only when at least two entries share the same two-segment prefix and the same limit
- Keeps entries separate when values differ
- Replaces the StackWise rule list, creates a timestamped configuration backup, and disables the global fallback to preserve the imported behavior
- Uses deterministic rule ids and localized result messages

Examples:

- `Ingredient_Crystal_Blue = 1000` and `Ingredient_Crystal_Green = 1000` become one `Ingredient_Crystal_` prefix rule.
- If one crystal uses a different limit, those crystal entries remain exact rules.
- `Ore_Onyxium` remains exact while variants such as `Ore_Onyxium_Basalt` may be grouped under `Ore_Onyxium_`.

The command structure intentionally uses `/stackwise import <source>` so additional migration sources can be added without changing the root command design.

## Operation log

The main administration page displays only the result of the action just performed. `View log` opens details for the latest operation without duplicating the summary.

Player-facing logs contain only actionable messages such as invalid configuration, safe-mode blocks, restart requirements, external conflicts, and migration problems. Java exception details and stack traces remain in the server log.

## Building and testing

```bash
./gradlew clean build
```

The local Hytale server JAR and assets are discovered from the official installation or can be overridden with Gradle properties.

```bash
./gradlew runServer
```

See `docs/VALIDATION.md` for the in-game verification checklist.

## License

StackWise is licensed under the PolyForm Noncommercial License 1.0.0.
