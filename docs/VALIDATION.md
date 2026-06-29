# Validation

## Automated checks

- Default configuration validation
- Every action and match type represented in the first-run configuration
- Rule priority and specificity
- Exclusion behavior
- Glob escaping and regular expression validation
- Arrow prefix regression using `Weapon_Arrow_Iron`
- Safe Mode protection for original stack size one
- Stackable state-related items remain eligible
- Runtime decrease protection
- External modification conflict protection
- Original limit preservation
- Restoration of unmatched items
- Complete item catalog diagnostics
- Item packet cache invalidation
- Full asset registry usage in the plugin event handler
- No legacy migration implementation or references
- Localization key parity for every included locale
- Command, interface, validation, action, and match type translation coverage
- Common Styling policy
- Common Styling expression validation against the installed `Assets.zip`
- UI named expression order
- Unique UI element ids
- Java event binding to UI element contracts
- Rules and Global settings tab controls
- Bottom-aligned rule-editor dropdown panels
- Search button and Enter-key validation events
- Operation-log page controls and severity presentation
- Scroll-retention declarations and in-place administration updates
- Search controls and deletion confirmation controls
- ASCII-only rule list separators
- VoidVault-equivalent Hytale server dependency paths

## Real-server verification

The delivery environment does not include the proprietary current Hytale server binary or a Hytale client session. Complete the following checks before publishing:

1. Run `./gradlew clean build --no-build-cache --rerun-tasks`.
2. Install only `StackWise-1.0.0.jar` in the server mod directory.
3. Start the server and confirm `/stackwise status` reports the adapter as available.
4. Confirm `Scanned` represents the complete item registry rather than a small asset batch.
5. Open `/stackwise` as an administrator.
6. Confirm a player without `stackwise.admin` cannot open or save through the interface.
7. Confirm a LuckPerms user with `stackwise.admin` can open and save through the interface.
8. Switch between Rules and Global settings and confirm the active tab changes without resetting the active tab scroll.
9. Search for `arrows` and `Weapon_Arrow` using both the Search button and the Enter key.
10. Open both action and match-type dropdowns and confirm their panels appear below the fields.
11. Set a `PREFIX` rule for `Weapon_Arrow` to `1000` and confirm its match count is greater than zero.
12. Confirm arrows can form stacks above 100 after acquiring or combining enough arrows.
13. Reload JSON, save settings, and open View log. Confirm successes, warnings, and errors use distinct colors.
14. Export the item reference and confirm `itemCount` is substantially larger than a single mod batch.
15. Confirm `Weapon_Arrow_Crude` or another loaded arrow appears with the expected rule and target.
16. Test edit and delete confirmation in both administration pages.
17. Switch client language and verify labels, dropdown entries, and responses change while JSON enum values remain unchanged.
18. Test inventory transfers, containers, crafting, drops, death, logout, and restart.
19. Confirm an originally non-stackable item is blocked unless `allowUnsafe` is enabled.
20. Confirm a live decrease reports a restart requirement by default.
21. Restart and verify saved rules apply consistently.
