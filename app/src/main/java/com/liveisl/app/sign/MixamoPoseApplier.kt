package com.liveisl.app.sign

import com.google.android.filament.gltfio.Animator
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.RotationsOrder
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.toQuaternion
import io.github.sceneview.node.Node
import kotlin.math.PI

/**
 * Drives a Mixamo skeleton from [AvatarPose] relative to bind (T-pose).
 *
 * Mixamo local arms: Z drops from T-pose; ±X swings forward/back.
 * Because Blender→glTF axis conversion varies, we auto-detect which X sign
 * puts the hand in front of the chest (toward the camera) on first raise.
 */
class MixamoPoseApplier private constructor(
    private val nodesByName: Map<String, Node>,
    private val restQuaternions: Map<String, Quaternion>,
    private val animator: Animator?,
) {
    /** +1 or -1: multiplied onto the forward (X) arm swing. */
    private var forwardSign = -1f
    private var calibrated = false

    fun apply(pose: AvatarPose) {
        if (nodesByName.isEmpty()) return

        addDelta("Spine", Rotation(y = radToDeg(pose.torsoYaw) * 0.8f))
        addDelta("Spine1", Rotation(y = radToDeg(pose.torsoYaw) * 0.55f))
        addDelta("Spine2", Rotation(y = radToDeg(pose.torsoYaw) * 0.4f, x = -6f))
        addDelta("Neck", Rotation(y = radToDeg(pose.headYaw) * 0.5f))
        addDelta("Head", Rotation(y = radToDeg(pose.headYaw) * 0.85f))

        applyArm("Right", 1f, pose.rightArmPitch, pose.rightArmYaw, pose.rightElbow, pose.rightWristRoll, pose.rightHand)
        applyArm("Left", -1f, pose.leftArmPitch, pose.leftArmYaw, pose.leftElbow, pose.leftWristRoll, pose.leftHand)
        animator?.updateBoneMatrices()

        maybeCalibrateForwardSign(pose)
    }

    /**
     * If a raised right hand sits behind the chest along +Z (camera axis),
     * flip [forwardSign] and re-apply once.
     */
    private fun maybeCalibrateForwardSign(pose: AvatarPose) {
        if (calibrated) return
        val raiseT = ((-pose.rightArmPitch).coerceIn(0f, 1.6f) / 1.6f)
        if (raiseT < 0.35f) return

        val hand = node("RightHand") ?: return
        val chest = node("Spine2") ?: node("Spine1") ?: return
        val handZ = hand.worldPosition.z
        val chestZ = chest.worldPosition.z
        // Camera looks from +Z: front of body ⇒ larger Z than chest.
        if (handZ < chestZ - 0.02f) {
            forwardSign = -forwardSign
            calibrated = true
            // Re-apply with corrected sign
            applyArm("Right", 1f, pose.rightArmPitch, pose.rightArmYaw, pose.rightElbow, pose.rightWristRoll, pose.rightHand)
            applyArm("Left", -1f, pose.leftArmPitch, pose.leftArmYaw, pose.leftElbow, pose.leftWristRoll, pose.leftHand)
            animator?.updateBoneMatrices()
        } else {
            calibrated = true
        }
    }

    private fun applyArm(
        sideLabel: String,
        side: Float,
        pitch: Float,
        yaw: Float,
        elbow: Float,
        wristRoll: Float,
        hand: HandShape,
    ) {
        val raiseT = ((-pitch).coerceIn(0f, 1.6f) / 1.6f)
        val swing = radToDeg(yaw).coerceIn(-50f, 50f)
        val elbowDeg = (radToDeg(elbow) * 0.95f).coerceIn(8f, 130f)

        val forwardX = forwardSign * 95f * raiseT
        val hangZ = (82f * (1f - raiseT * 0.72f) + 5f) * side
        val aimY = swing * 0.55f * side

        addDelta("${sideLabel}Arm", Rotation(x = forwardX, y = aimY, z = hangZ))
        addDelta("${sideLabel}ForeArm", Rotation(x = -elbowDeg * 0.15f, y = 0f, z = -elbowDeg))
        addDelta(
            "${sideLabel}Hand",
            Rotation(
                x = radToDeg(wristRoll) * 0.6f + forwardSign * 12f * raiseT,
                y = swing * 0.15f * side,
                z = radToDeg(wristRoll) * 0.35f * side,
            ),
        )
        applyFingers(sideLabel, hand)
    }

    private fun applyFingers(sideLabel: String, shape: HandShape) {
        val opens = floatArrayOf(shape.thumb, shape.index, shape.middle, shape.ring, shape.pinky)
        val names = arrayOf("HandThumb", "HandIndex", "HandMiddle", "HandRing", "HandPinky")
        for (i in names.indices) {
            val open = opens[i].coerceIn(0f, 1f)
            val curl = (1f - open) * 85f
            val spread = (shape.spread - 0.35f) * 22f * (i - 2)
            val isThumb = i == 0
            for (joint in 1..3) {
                val jointCurl = curl * (0.5f + joint * 0.22f)
                val delta = if (isThumb) {
                    Rotation(
                        x = jointCurl * 0.35f,
                        y = -jointCurl * 0.55f * if (sideLabel == "Right") 1f else -1f,
                        z = spread * 0.45f + jointCurl * 0.2f,
                    )
                } else {
                    Rotation(x = jointCurl, y = spread * (0.18f / joint), z = 0f)
                }
                addDelta("$sideLabel${names[i]}$joint", delta)
            }
        }
    }

    private fun addDelta(shortName: String, deltaDeg: Rotation) {
        val node = node(shortName) ?: return
        val key = node.name ?: shortName
        val rest = restQuaternions[key]
            ?: restQuaternions["$PREFIX$shortName"]
            ?: return
        val delta = Float3(deltaDeg.x, deltaDeg.y, deltaDeg.z)
            .toQuaternion(RotationsOrder.ZYX)
        node.quaternion = rest * delta
    }

    private fun node(short: String): Node? =
        nodesByName["$PREFIX$short"]
            ?: nodesByName[short]
            ?: nodesByName["mixamorig$short"]

    companion object {
        private const val PREFIX = "mixamorig:"

        fun capture(
            nodesByName: Map<String, Node>,
            animator: Animator?,
        ): MixamoPoseApplier {
            val rest = LinkedHashMap<String, Quaternion>(nodesByName.size)
            nodesByName.forEach { (name, node) ->
                val q = node.quaternion
                rest[name] = Quaternion(q.x, q.y, q.z, q.w)
            }
            return MixamoPoseApplier(nodesByName, rest, animator)
        }
    }
}

private fun radToDeg(r: Float): Float = r * 180f / PI.toFloat()
