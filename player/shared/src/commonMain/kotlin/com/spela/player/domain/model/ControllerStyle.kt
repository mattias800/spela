package com.spela.player.domain.model

/**
 * Normalized physical-controller layout family. Drives Android input
 * normalization and the identity display only — never the positional bindings.
 */
enum class ControllerStyle {
    Xbox,
    Nintendo,
    PlayStation,
    Generic,
    ;

    /** Human-readable identity shown to the user (e.g. "Player 1: Xbox Controller"). */
    val displayName: String
        get() = when (this) {
            Xbox -> "Xbox Controller"
            Nintendo -> "Nintendo Controller"
            PlayStation -> "PlayStation Controller"
            Generic -> "Gamepad"
        }

    /** Compact label for chips/affordances (e.g. the "Type: …" override button). */
    val shortLabel: String
        get() = when (this) {
            Xbox -> "Xbox"
            Nintendo -> "Nintendo"
            PlayStation -> "PlayStation"
            Generic -> "Gamepad"
        }
}

/**
 * Classifies a connected controller into a [ControllerStyle] from USB vendor/
 * product IDs (both Android InputDevice and SDL expose these), with a name-
 * substring fallback. Pure + platform-agnostic so both platforms share it.
 */
object ControllerClassifier {
    // USB vendor IDs.
    private const val VENDOR_SONY = 0x054C
    private const val VENDOR_MICROSOFT = 0x045E
    private const val VENDOR_NINTENDO = 0x057E

    fun fromVendorProduct(vendorId: Int, productId: Int, name: String): ControllerStyle {
        when (vendorId) {
            VENDOR_SONY -> return ControllerStyle.PlayStation
            VENDOR_MICROSOFT -> return ControllerStyle.Xbox
            VENDOR_NINTENDO -> return ControllerStyle.Nintendo
        }
        val n = name.lowercase()
        return when {
            "dualsense" in n || "dualshock" in n || "playstation" in n || "wireless controller" in n -> ControllerStyle.PlayStation
            "xbox" in n || "xinput" in n -> ControllerStyle.Xbox
            "switch" in n || "joy-con" in n || "joycon" in n || "pro controller" in n || "nintendo" in n -> ControllerStyle.Nintendo
            else -> ControllerStyle.Generic
        }
    }
}
