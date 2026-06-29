# Changelog

## 1.0.0

- Split the administration interface into Rules and Global settings tabs.
- Added a translated, paginated, color-coded operation log for information, success, warning, and error diagnostics.
- Added Enter-key submission to rule search.
- Changed rule-editor dropdown panels to open below their fields at the field width.
- Replaced routine administration page rebuilds with in-place updates and retained scrolling positions.
- Added diagnostics for validation failures, blocked changes, restart requirements, external conflicts, verification failures, and item exceptions.
- Added cleanup for obsolete temporary Custom UI files when updating over the same IntelliJ project directory.
- Rebuilt both Custom UI documents with direct named Common controls and literal fallback text.
- Moved every translated interface string to runtime `TextSpans` updates, matching the proven VoidVault approach.
- Removed internal template-child selectors and markup translation expressions from the UI documents.
- Added an enabled-by-default global stack limit of 1000 for unmatched stackable items.
- Added global-limit controls to JSON and the administration interface.
- Added explicit rule and exclusion precedence over the global limit.
- Added safe restoration when the global limit is disabled.
- Added automatic cleanup for obsolete source files when updating over an existing project directory.
- Removed the deprecated direct `Player.getPlayerRef()` call from locale detection.
- Added deterministic exact, prefix, suffix, glob, and regular expression rules.
- Added set and exclusion actions.
- Added an administrative Custom UI based on Hytale Common Styling.
- Added searchable and paginated rules by rule id or configured match value.
- Added two-step irreversible deletion confirmation in both administration pages.
- Increased destructive button widths and removed unsupported decorative separators.
- Added localized commands, responses, validation, interface labels, and dropdown entries.
- Added English, German, Spanish, French, Brazilian Portuguese, Russian, Ukrainian, and Simplified Chinese resources.
- Added JSON and UI configuration synchronization.
- Added active first-run examples for every action and match type.
- Added complete item registry processing instead of retaining only the latest asset event batch.
- Fixed runtime rules such as `Weapon_Arrow` not applying after startup.
- Updated Safe Mode so stackable ammunition is not blocked by state-related asset definitions.
- Added a complete item reference and diagnostics export.
- Removed automatic legacy configuration import and backup behavior.
- Added native permission and optional LuckPerms integration.
- Added runtime decrease protection and external change protection.
- Added atomic configuration writes, invalid configuration backups, and validation.
- Added item packet cache invalidation after stack limit changes.
- Added Common Styling, localization, full registry, catalog, arrow, and UI contract regression tests.
