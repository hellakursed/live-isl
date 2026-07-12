package com.liveisl.app.sign

/**
 * Per-finger openness: 0 = fully curled, 1 = fully extended.
 */
data class HandShape(
    val thumb: Float = 0.85f,
    val index: Float = 1f,
    val middle: Float = 1f,
    val ring: Float = 1f,
    val pinky: Float = 1f,
    /** Finger abduction / fan-out. */
    val spread: Float = 0.55f,
) {
    companion object {
        val OPEN = HandShape(0.9f, 1f, 1f, 1f, 1f, 0.75f)
        val FLAT = HandShape(0.75f, 1f, 1f, 1f, 1f, 0.35f)
        val FIST = HandShape(0.35f, 0.12f, 0.1f, 0.1f, 0.12f, 0.08f)
        val POINT = HandShape(0.4f, 1f, 0.12f, 0.1f, 0.1f, 0.15f)
        val TWO = HandShape(0.4f, 1f, 1f, 0.12f, 0.1f, 0.45f)
        val THREE = HandShape(0.45f, 1f, 1f, 1f, 0.12f, 0.5f)
        val OK = HandShape(0.55f, 0.35f, 1f, 1f, 1f, 0.4f)
        val PINCH = HandShape(0.55f, 0.45f, 0.2f, 0.15f, 0.15f, 0.2f)
        val ILY = HandShape(0.95f, 1f, 0.15f, 0.15f, 1f, 0.55f) // I-L-Y / love
        val THUMBS_UP = HandShape(1f, 0.15f, 0.12f, 0.12f, 0.12f, 0.1f)
        val CUP = HandShape(0.7f, 0.55f, 0.55f, 0.55f, 0.55f, 0.25f)
        val HOOK_INDEX = HandShape(0.45f, 0.55f, 0.15f, 0.12f, 0.12f, 0.2f)
        val CALL_ME = HandShape(1f, 0.12f, 0.1f, 0.1f, 1f, 0.4f)

        fun lerp(a: HandShape, b: HandShape, t: Float): HandShape {
            fun f(x: Float, y: Float) = x + (y - x) * t
            return HandShape(
                thumb = f(a.thumb, b.thumb),
                index = f(a.index, b.index),
                middle = f(a.middle, b.middle),
                ring = f(a.ring, b.ring),
                pinky = f(a.pinky, b.pinky),
                spread = f(a.spread, b.spread),
            )
        }
    }
}

/**
 * Full-body signing pose including hand shapes for both hands.
 */
data class AvatarPose(
    val leftArmPitch: Float = 0.25f,
    val leftArmYaw: Float = 0.2f,
    val leftElbow: Float = 0.45f,
    val rightArmPitch: Float = 0.25f,
    val rightArmYaw: Float = -0.2f,
    val rightElbow: Float = 0.45f,
    val leftWristRoll: Float = 0f,
    val rightWristRoll: Float = 0f,
    val torsoYaw: Float = 0f,
    val headYaw: Float = 0f,
    val leftHand: HandShape = HandShape.OPEN,
    val rightHand: HandShape = HandShape.OPEN,
    val durationMs: Long = 1200,
) {
    companion object {
        val NEUTRAL = AvatarPose()

        fun lerp(a: AvatarPose, b: AvatarPose, t: Float): AvatarPose {
            fun f(x: Float, y: Float) = x + (y - x) * t
            return AvatarPose(
                leftArmPitch = f(a.leftArmPitch, b.leftArmPitch),
                leftArmYaw = f(a.leftArmYaw, b.leftArmYaw),
                leftElbow = f(a.leftElbow, b.leftElbow),
                rightArmPitch = f(a.rightArmPitch, b.rightArmPitch),
                rightArmYaw = f(a.rightArmYaw, b.rightArmYaw),
                rightElbow = f(a.rightElbow, b.rightElbow),
                leftWristRoll = f(a.leftWristRoll, b.leftWristRoll),
                rightWristRoll = f(a.rightWristRoll, b.rightWristRoll),
                torsoYaw = f(a.torsoYaw, b.torsoYaw),
                headYaw = f(a.headYaw, b.headYaw),
                leftHand = HandShape.lerp(a.leftHand, b.leftHand, t),
                rightHand = HandShape.lerp(a.rightHand, b.rightHand, t),
                durationMs = b.durationMs,
            )
        }
    }
}

object AvatarPoseLibrary {
    /** Multi-keyframe clips for signs that need motion (e.g. wave). */
    fun clipFor(glossId: String): List<AvatarPose> {
        val key = glossId.uppercase()
        when (key) {
            "HELLO", "MEET", "HI" -> return waveHello
            "YES", "ACCEPT", "OK", "OKAY" -> return nodYes
        }
        clips[key]?.let { return it }
        return listOf(poseFor(key))
    }

    private val waveHello = listOf(
        AvatarPose(
            rightArmPitch = -1.35f, rightArmYaw = -0.65f, rightElbow = 0.08f,
            rightHand = HandShape.OPEN, leftHand = HandShape.FLAT, durationMs = 240,
        ),
        AvatarPose(
            rightArmPitch = -1.35f, rightArmYaw = 0.55f, rightElbow = 0.08f,
            rightHand = HandShape.OPEN, durationMs = 240,
        ),
        AvatarPose(
            rightArmPitch = -1.35f, rightArmYaw = -0.5f, rightElbow = 0.08f,
            rightHand = HandShape.OPEN, durationMs = 240,
        ),
        AvatarPose(
            rightArmPitch = -1.25f, rightArmYaw = 0.35f, rightElbow = 0.12f,
            rightHand = HandShape.OPEN, durationMs = 300,
        ),
    )

    private val nodYes = listOf(
        AvatarPose(
            rightArmPitch = -0.95f, rightElbow = 0.95f, rightHand = HandShape.FIST, durationMs = 300,
        ),
        AvatarPose(
            rightArmPitch = -0.7f, rightElbow = 1.25f, rightHand = HandShape.FIST,
            headYaw = 0.3f, durationMs = 340,
        ),
        AvatarPose(
            rightArmPitch = -0.95f, rightElbow = 0.95f, rightHand = HandShape.FIST, durationMs = 300,
        ),
    )

    private val clips: Map<String, List<AvatarPose>> = mapOf(
        "LOVE" to listOf(
            AvatarPose(
                leftArmPitch = -0.7f, rightArmPitch = -0.7f,
                leftElbow = 0.9f, rightElbow = 0.9f,
                leftHand = HandShape.ILY, rightHand = HandShape.ILY, durationMs = 350,
            ),
            AvatarPose(
                leftArmPitch = -1.15f, rightArmPitch = -1.15f,
                leftElbow = 1.2f, rightElbow = 1.2f,
                leftHand = HandShape.ILY, rightHand = HandShape.ILY, durationMs = 700,
            ),
            AvatarPose(
                leftArmPitch = -1.0f, rightArmPitch = -1.0f,
                leftElbow = 1.15f, rightElbow = 1.15f,
                leftHand = HandShape.ILY, rightHand = HandShape.ILY, durationMs = 400,
            ),
        ),
        "DRINK" to listOf(
            AvatarPose(
                rightArmPitch = -0.85f, rightElbow = 0.9f, rightHand = HandShape.CUP, durationMs = 350,
            ),
            AvatarPose(
                rightArmPitch = -1.45f, rightElbow = 1.45f, rightArmYaw = 0.2f,
                rightHand = HandShape.CUP, headYaw = -0.2f, durationMs = 700,
            ),
            AvatarPose(
                rightArmPitch = -1.2f, rightElbow = 1.3f, rightHand = HandShape.CUP, durationMs = 300,
            ),
        ),
        "HELP" to listOf(
            AvatarPose(
                leftArmPitch = -0.55f, rightArmPitch = -0.55f,
                leftElbow = 0.35f, rightElbow = 0.35f,
                leftHand = HandShape.OPEN, rightHand = HandShape.OPEN, durationMs = 350,
            ),
            AvatarPose(
                leftArmPitch = -1.15f, rightArmPitch = -1.15f,
                leftElbow = 0.65f, rightElbow = 0.65f,
                leftHand = HandShape.OPEN, rightHand = HandShape.OPEN, durationMs = 700,
            ),
        ),
        "NO" to listOf(
            AvatarPose(
                rightArmPitch = -1.0f, rightElbow = 0.2f,
                rightHand = HandShape.TWO, rightWristRoll = -0.4f, durationMs = 280,
            ),
            AvatarPose(
                rightArmPitch = -1.0f, rightElbow = 0.2f,
                rightHand = HandShape.TWO, rightWristRoll = 0.55f, durationMs = 280,
            ),
            AvatarPose(
                rightArmPitch = -1.0f, rightElbow = 0.2f,
                rightHand = HandShape.TWO, rightWristRoll = -0.35f, durationMs = 280,
            ),
        ),
        "YOU" to listOf(
            AvatarPose(
                rightArmPitch = -0.55f, rightArmYaw = 0.2f, rightElbow = 0.2f,
                rightHand = HandShape.POINT, durationMs = 280,
            ),
            AvatarPose(
                rightArmPitch = -1.05f, rightArmYaw = 0.65f, rightElbow = 0.05f,
                rightHand = HandShape.POINT, durationMs = 650,
            ),
        ),
    )

    private val poses: Map<String, AvatarPose> = mapOf(
        "YOU" to AvatarPose(
            rightArmPitch = -0.95f, rightArmYaw = 0.55f, rightElbow = 0.08f,
            rightHand = HandShape.POINT, leftHand = HandShape.FLAT, durationMs = 950,
        ),
        "I" to AvatarPose(
            rightArmPitch = -0.75f, rightArmYaw = 0.05f, rightElbow = 1.25f,
            rightHand = HandShape.POINT, durationMs = 900,
        ),
        "ME" to AvatarPose(
            rightArmPitch = -0.75f, rightArmYaw = 0.05f, rightElbow = 1.25f,
            rightHand = HandShape.POINT, durationMs = 900,
        ),
        "HELP" to AvatarPose(
            leftArmPitch = -1.05f, rightArmPitch = -1.05f,
            leftElbow = 0.55f, rightElbow = 0.55f,
            leftHand = HandShape.OPEN, rightHand = HandShape.OPEN, durationMs = 1300,
        ),
        "NEED" to bothForward(-0.9f, 0.45f, HandShape.FLAT, HandShape.FLAT, 1100),
        "LOVE" to AvatarPose(
            leftArmPitch = -1.0f, rightArmPitch = -1.0f,
            leftElbow = 1.15f, rightElbow = 1.15f,
            leftHand = HandShape.ILY, rightHand = HandShape.ILY, durationMs = 1400,
        ),
        "LIKE" to AvatarPose(
            rightArmPitch = -0.85f, rightElbow = 0.95f,
            rightHand = HandShape.THUMBS_UP, headYaw = 0.12f, durationMs = 1000,
        ),
        "FRIEND" to AvatarPose(
            leftArmPitch = -0.75f, rightArmPitch = -0.75f,
            leftElbow = 0.85f, rightElbow = 0.85f,
            leftHand = HandShape.HOOK_INDEX, rightHand = HandShape.HOOK_INDEX,
            torsoYaw = 0.12f, durationMs = 1200,
        ),
        "HOME" to bothForward(-0.55f, 1.05f, HandShape.FLAT, HandShape.FLAT, 1100),
        "DRINK" to AvatarPose(
            rightArmPitch = -1.35f, rightElbow = 1.45f, rightArmYaw = 0.15f,
            rightHand = HandShape.CUP, headYaw = -0.15f, durationMs = 1250,
        ),
        "LUNCH" to AvatarPose(
            rightArmPitch = -1.05f, rightElbow = 1.35f,
            rightHand = HandShape.PINCH, durationMs = 1100,
        ),
        "HOW" to bothForward(-0.95f, 0.35f, HandShape.OPEN, HandShape.OPEN, 1100)
            .copy(headYaw = 0.22f),
        "WHY" to AvatarPose(
            rightArmPitch = -1.2f, rightElbow = 0.25f,
            rightHand = HandShape.OPEN, headYaw = 0.3f, durationMs = 1000,
        ),
        "WHO" to AvatarPose(
            rightArmPitch = -1.05f, rightArmYaw = 0.35f, rightElbow = 0.15f,
            rightHand = HandShape.POINT, durationMs = 900,
        ),
        "QUESTION" to AvatarPose(
            rightArmPitch = -1.2f, rightElbow = 0.2f,
            rightHand = HandShape.POINT, headYaw = 0.28f, durationMs = 1000,
        ),
        "SORRY" to AvatarPose(
            rightArmPitch = -0.65f, rightElbow = 1.25f, torsoYaw = 0.18f,
            rightHand = HandShape.FIST, headYaw = -0.18f, durationMs = 1300,
        ),
        "REJECT" to bothForward(-0.45f, 0.2f, HandShape.OPEN, HandShape.OPEN, 900)
            .copy(torsoYaw = -0.22f),
        "PERFECT" to AvatarPose(
            rightArmPitch = -1.0f, rightElbow = 0.95f,
            rightHand = HandShape.OK, durationMs = 1000,
        ),
        "SMILE" to AvatarPose(
            leftArmPitch = -0.35f, rightArmPitch = -0.35f,
            leftHand = HandShape.OPEN, rightHand = HandShape.OPEN,
            headYaw = 0.12f, durationMs = 1000,
        ),
        "ENJOY" to bothForward(-1.0f, 0.45f, HandShape.OPEN, HandShape.OPEN, 1200),
        "WALK" to AvatarPose(
            leftArmPitch = -0.65f, rightArmPitch = 0.25f, leftElbow = 0.5f,
            leftHand = HandShape.FLAT, rightHand = HandShape.FLAT,
            torsoYaw = 0.1f, durationMs = 1000,
        ),
        "GIVE" to bothForward(-0.8f, 0.28f, HandShape.FLAT, HandShape.FLAT, 1100),
        "KNOW" to AvatarPose(
            rightArmPitch = -1.25f, rightElbow = 1.15f, rightArmYaw = 0.12f,
            rightHand = HandShape.FLAT, durationMs = 1000,
        ),
        "TALK" to AvatarPose(
            rightArmPitch = -0.9f, rightElbow = 1.05f, rightArmYaw = 0.22f,
            rightHand = HandShape.FLAT, durationMs = 1100,
        ),
        "STOP" to AvatarPose(
            rightArmPitch = -1.15f, rightElbow = 0.05f,
            rightHand = HandShape.FLAT, rightWristRoll = 0.4f, durationMs = 900,
        ),
        "THANK" to AvatarPose(
            rightArmPitch = -0.95f, rightElbow = 0.85f,
            rightHand = HandShape.FLAT, headYaw = 0.18f, durationMs = 1100,
        ),
        "PLEASE" to bothForward(-0.75f, 0.7f, HandShape.FLAT, HandShape.FLAT, 1100),
        "INDIA" to AvatarPose(
            rightArmPitch = -1.05f, rightElbow = 0.55f, leftArmPitch = -0.45f,
            rightHand = HandShape.FLAT, leftHand = HandShape.FLAT, durationMs = 1200,
        ),
        "TIME" to AvatarPose(
            leftArmPitch = -0.55f, leftElbow = 1.05f,
            rightArmPitch = -0.95f, rightElbow = 0.35f,
            leftHand = HandShape.FIST, rightHand = HandShape.POINT, durationMs = 1100,
        ),
        "MONEY" to AvatarPose(
            rightArmPitch = -0.85f, rightElbow = 1.05f,
            rightHand = HandShape.PINCH, durationMs = 1000,
        ),
        "CALL" to AvatarPose(
            rightArmPitch = -1.3f, rightElbow = 1.4f, rightArmYaw = 0.3f,
            rightHand = HandShape.CALL_ME, durationMs = 1100,
        ),
        "NO" to AvatarPose(
            rightArmPitch = -0.9f, rightElbow = 0.3f,
            rightHand = HandShape.TWO, rightWristRoll = 0.5f, durationMs = 900,
        ),
        "GOOD" to AvatarPose(
            rightArmPitch = -0.8f, rightElbow = 0.85f,
            rightHand = HandShape.THUMBS_UP, durationMs = 950,
        ),
        "WATER" to AvatarPose(
            rightArmPitch = -1.1f, rightElbow = 0.7f,
            rightHand = HandShape.THREE, durationMs = 1000,
        ),
    )

    fun poseFor(glossId: String): AvatarPose {
        val key = glossId.uppercase()
        poses[key]?.let { return it }
        if (key.length == 1 && key[0].isLetter()) {
            return letterPose(key[0])
        }
        val h = key.hashCode()
        return AvatarPose(
            leftArmPitch = -0.25f,
            rightArmPitch = -0.55f - (h % 5) * 0.08f,
            rightElbow = 0.35f + (h % 4) * 0.12f,
            rightArmYaw = ((h % 7) - 3) * 0.08f,
            headYaw = ((h % 9) - 4) * 0.04f,
            rightHand = HandShape.OPEN,
            leftHand = HandShape.FLAT,
            durationMs = 900,
        )
    }

    private fun bothForward(
        pitch: Float,
        elbow: Float,
        left: HandShape,
        right: HandShape,
        ms: Long,
    ) = AvatarPose(
        leftArmPitch = pitch,
        rightArmPitch = pitch,
        leftElbow = elbow,
        rightElbow = elbow,
        leftHand = left,
        rightHand = right,
        durationMs = ms,
    )

    private fun letterPose(ch: Char): AvatarPose {
        val shape = when (ch.uppercaseChar()) {
            'A' -> HandShape.FIST.copy(thumb = 0.85f)
            'B' -> HandShape.FLAT.copy(thumb = 0.35f)
            'C' -> HandShape.CUP
            'D' -> HandShape.POINT.copy(thumb = 0.55f, middle = 0.45f)
            'E' -> HandShape.FIST.copy(thumb = 0.25f)
            'F' -> HandShape.OK
            'I' -> HandShape(0.35f, 0.12f, 0.1f, 0.1f, 1f, 0.2f)
            'L' -> HandShape(1f, 1f, 0.12f, 0.1f, 0.1f, 0.35f)
            'O' -> HandShape.OK.copy(middle = 0.4f, ring = 0.4f, pinky = 0.4f)
            'V' -> HandShape.TWO
            'W' -> HandShape.THREE
            'Y' -> HandShape(1f, 0.12f, 0.1f, 0.1f, 1f, 0.45f)
            else -> HandShape.OPEN
        }
        return AvatarPose(
            rightArmPitch = -1.05f,
            rightArmYaw = 0.15f,
            rightElbow = 0.2f,
            rightHand = shape,
            leftHand = HandShape.FLAT,
            durationMs = 850,
        )
    }
}
