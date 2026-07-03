
## SpGroupLabel — extract the group-subheader Text pattern
`Text(style = SpTypography.LabelMedium, color = SpColor.OnBackgroundSecondary, Modifier.fillMaxWidth())` used as a radio-group / field-group header now appears in ShowcaseEditorDialog (×2) and both Wii picker surfaces (InGameWiiControlSchemeDialog, WiiControlSchemeSection). Per "one visual pattern = one shared component," extract an `SpGroupLabel`/`SpFieldLabel`. Flagged in the #1560 ui-agent review.
