package com.spela.player.domain.model

/**
 * Android gamepad input is now positional (#1334): physical key codes are
 * normalized to canonical [GamepadPosition]s and mapped to RetroPad ids via the
 * synced positional mapping layer. The old per-brand key-code presets
 * (xbox/playstation/nintendo-standard) only differed by an A/B face-button swap,
 * which the positional model handles by normalization — so there are no Android
 * key-code presets anymore.
 *
 * One-time behavior change: users previously on "nintendo-standard" (A=East,
 * B=South) now get the positional default (bottom button = RetroPad B) like
 * every other pad. They can rebind per console in the positional editor.
 */
actual fun getPlatformPresets(): List<KeyMappingPreset> = emptyList()
