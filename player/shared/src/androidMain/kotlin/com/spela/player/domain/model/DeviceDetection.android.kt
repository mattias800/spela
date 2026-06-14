package com.spela.player.domain.model

/**
 * Android gamepad input is positional (#1334), so there is no key-code preset to
 * seed — returning null makes [com.spela.player.domain.repository.KeyMappingRepository.ensureDefaultsApplied]
 * a no-op on Android. The positional mapping layer's defaults provide the
 * standard behavior; the keyboard-preset path is desktop-only.
 */
actual fun detectDevicePreset(): String? = null
