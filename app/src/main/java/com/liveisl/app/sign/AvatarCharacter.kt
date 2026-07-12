package com.liveisl.app.sign

/**
 * Which 3D signer body to show when [SignOutputMode.AVATAR_3D] is active.
 * Armature keeps the procedural Canvas mannequin; worker uses the Mixamo GLB.
 */
enum class AvatarCharacter(
    val storageKey: String,
    val label: String,
    val description: String,
) {
    ARMATURE(
        storageKey = "armature",
        label = "Armature",
        description = "Procedural stick-figure signer with finger shapes",
    ),
    CONSTRUCTION_WORKER(
        storageKey = "construction_worker",
        label = "Construction worker",
        description = "Rigged Mixamo character (full hands & fingers)",
    );

    companion object {
        fun fromStorage(key: String?): AvatarCharacter =
            entries.firstOrNull { it.storageKey == key } ?: ARMATURE
    }
}
