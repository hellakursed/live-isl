package com.liveisl.app.ui.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liveisl.app.sign.AvatarPose
import com.liveisl.app.sign.AvatarSignRenderer
import com.liveisl.app.sign.HandShape
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Projected signer with readable palms and five curling fingers per hand.
 *
 * Arm convention (matches [com.liveisl.app.sign.AvatarPoseLibrary]):
 * pitch ≈ 0 hangs down; negative pitch raises the arm.
 */
@Composable
fun Avatar3DStage(
    renderer: AvatarSignRenderer,
    glossLabel: String?,
    modifier: Modifier = Modifier,
) {
    val pose by renderer.pose.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF071820), Color(0xFF123040), Color(0xFF0B1F2A))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSigner(pose)
        }

        if (glossLabel != null) {
            Text(
                text = glossLabel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xAA0B1F2A))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = "3D signer with hands ready",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9BB4C4),
            )
        }
    }
}

private data class ArmChain(
    val elbow: Offset,
    val wrist: Offset,
    val palm: Offset,
    val alongX: Float,
    val alongY: Float,
    val acrossX: Float,
    val acrossY: Float,
    val side: Float,
)

private fun DrawScope.drawSigner(pose: AvatarPose) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.58f
    val scale = minOf(w, h) * 0.30f

    val skin = Color(0xFFF0C9A8)
    val skinDark = Color(0xFFD4A47A)
    val shirt = Color(0xFF2EC4B6)
    val pants = Color(0xFF163D4C)
    val outline = Color(0xFF0B1F2A)

    fun project(x: Float, y: Float, z: Float): Offset {
        val yaw = pose.torsoYaw
        val xr = x * cos(yaw) - z * sin(yaw)
        val zr = x * sin(yaw) + z * cos(yaw)
        val depth = 3.4f + zr
        val f = 2.8f / depth
        return Offset(cx + xr * scale * f, cy - y * scale * f)
    }

    fun bone(a: Offset, b: Offset, width: Float, color: Color) {
        drawLine(color, a, b, strokeWidth = width, cap = StrokeCap.Round)
    }

    fun joint(p: Offset, r: Float, color: Color) {
        drawCircle(color, radius = r, center = p)
    }

    drawOval(
        color = Color(0x332EC4B6),
        topLeft = Offset(cx - scale * 1.15f, cy + scale * 0.85f),
        size = Size(scale * 2.3f, scale * 0.4f),
    )

    val hip = project(0f, 0f, 0f)
    val chest = project(0f, 1.05f, 0f)
    val neck = project(0f, 1.5f, 0f)
    val head = project(pose.headYaw * 0.18f, 2.0f, 0.05f)

    val lKnee = project(-0.22f, -0.65f, 0.04f)
    val rKnee = project(0.22f, -0.65f, 0.04f)
    val lFoot = project(-0.25f, -1.25f, 0.08f)
    val rFoot = project(0.25f, -1.25f, 0.08f)
    bone(hip, lKnee, scale * 0.15f, pants)
    bone(lKnee, lFoot, scale * 0.13f, pants)
    bone(hip, rKnee, scale * 0.15f, pants)
    bone(rKnee, rFoot, scale * 0.13f, pants)

    bone(hip, chest, scale * 0.3f, shirt)
    bone(chest, neck, scale * 0.18f, shirt)
    joint(head, scale * 0.3f, skin)
    drawCircle(outline, scale * 0.3f, head, style = Stroke(width = scale * 0.028f))
    drawCircle(outline, scale * 0.035f, Offset(head.x - scale * 0.08f, head.y - scale * 0.02f))
    drawCircle(outline, scale * 0.035f, Offset(head.x + scale * 0.08f, head.y - scale * 0.02f))

    fun solveArm(
        shoulderX: Float,
        pitch: Float,
        yaw: Float,
        elbowBend: Float,
        side: Float,
    ): ArmChain {
        // pitch 0 ≈ hanging down; negative pitch raises (matches pose library).
        val upper = 0.62f
        val lower = 0.58f
        val ux = side * 0.14f + sin(yaw) * upper * 0.85f
        val uy = -sin(pitch) * upper
        val uz = cos(pitch) * upper * 0.55f + cos(yaw) * 0.06f
        val shoulderY = 1.32f
        val elbow = project(shoulderX + ux, shoulderY + uy, uz)

        val pitch2 = pitch + elbowBend
        val yaw2 = yaw + side * elbowBend * 0.25f
        val fx = side * 0.06f + sin(yaw2) * lower * 0.9f
        val fy = -sin(pitch2) * lower
        val fz = cos(pitch2) * lower * 0.6f
        val wrist = project(shoulderX + ux + fx, shoulderY + uy + fy, uz + fz)

        val palm = project(
            shoulderX + ux + fx + sin(yaw2) * 0.12f,
            shoulderY + uy + fy - sin(pitch2) * 0.12f,
            uz + fz + cos(pitch2) * 0.08f,
        )

        var ax = palm.x - wrist.x
        var ay = palm.y - wrist.y
        val alen = sqrt(ax * ax + ay * ay).coerceAtLeast(1f)
        ax /= alen
        ay /= alen
        val acrossX = -ay * side
        val acrossY = ax * side

        return ArmChain(elbow, wrist, palm, ax, ay, acrossX, acrossY, side)
    }

    val lShoulder = project(-0.45f, 1.32f, 0f)
    val rShoulder = project(0.45f, 1.32f, 0f)
    joint(lShoulder, scale * 0.1f, shirt)
    joint(rShoulder, scale * 0.1f, shirt)

    val leftArm = solveArm(-0.45f, pose.leftArmPitch, pose.leftArmYaw, pose.leftElbow, -1f)
    val rightArm = solveArm(0.45f, pose.rightArmPitch, pose.rightArmYaw, pose.rightElbow, 1f)

    bone(lShoulder, leftArm.elbow, scale * 0.125f, skin)
    bone(leftArm.elbow, leftArm.wrist, scale * 0.11f, skin)
    bone(rShoulder, rightArm.elbow, scale * 0.125f, skin)
    bone(rightArm.elbow, rightArm.wrist, scale * 0.11f, skin)

    drawHand(leftArm, pose.leftWristRoll, pose.leftHand, scale, skin, skinDark, outline)
    drawHand(rightArm, pose.rightWristRoll, pose.rightHand, scale, skin, skinDark, outline)
}

private fun DrawScope.drawHand(
    arm: ArmChain,
    wristRoll: Float,
    shape: HandShape,
    scale: Float,
    skin: Color,
    skinDark: Color,
    outline: Color,
) {
    val palmLen = scale * 0.16f
    val palmWid = scale * 0.13f
    val cx = arm.palm.x
    val cy = arm.palm.y
    val palmCenter = Offset(cx + arm.alongX * palmLen * 0.25f, cy + arm.alongY * palmLen * 0.25f)

    drawLine(skin, arm.wrist, arm.palm, strokeWidth = scale * 0.07f, cap = StrokeCap.Round)
    drawCircle(skin, palmWid * 0.72f, palmCenter)
    drawCircle(outline, palmWid * 0.72f, palmCenter, style = Stroke(width = scale * 0.016f))

    val opens = floatArrayOf(shape.thumb, shape.index, shape.middle, shape.ring, shape.pinky)
    val acrossSlots = floatArrayOf(-0.95f, -0.55f, -0.1f, 0.35f, 0.75f)
    val fingerLens = floatArrayOf(
        scale * 0.17f,
        scale * 0.24f,
        scale * 0.26f,
        scale * 0.24f,
        scale * 0.20f,
    )
    val roll = wristRoll * arm.side

    for (i in 0 until 5) {
        val open = opens[i].coerceIn(0f, 1f)
        val spread = shape.spread * acrossSlots[i]
        val baseAcross = acrossSlots[i] * palmWid * 0.95f + roll * palmWid * 0.15f

        val base = if (i == 0) {
            Offset(
                cx - arm.alongX * palmLen * 0.05f + arm.acrossX * arm.side * palmWid * 0.7f,
                cy - arm.alongY * palmLen * 0.05f + arm.acrossY * arm.side * palmWid * 0.7f,
            )
        } else {
            Offset(
                cx + arm.alongX * palmLen * 0.55f + arm.acrossX * baseAcross,
                cy + arm.alongY * palmLen * 0.55f + arm.acrossY * baseAcross,
            )
        }

        val fan = spread * 0.55f
        var dirX = arm.alongX + arm.acrossX * fan
        var dirY = arm.alongY + arm.acrossY * fan
        if (i == 0) {
            dirX = arm.acrossX * arm.side * 0.75f + arm.alongX * 0.45f
            dirY = arm.acrossY * arm.side * 0.75f + arm.alongY * 0.45f
        }
        val dlen = sqrt(dirX * dirX + dirY * dirY).coerceAtLeast(0.001f)
        dirX /= dlen
        dirY /= dlen

        val segments = 3
        var px = base.x
        var py = base.y
        val segLen = fingerLens[i] / segments
        val curl = 1f - open

        for (s in 0 until segments) {
            val bend = curl * (0.55f + s * 0.35f)
            val bx = dirX * (1f - bend) - arm.alongX * bend * 0.85f
            val by = dirY * (1f - bend) - arm.alongY * bend * 0.85f
            val bl = sqrt(bx * bx + by * by).coerceAtLeast(0.001f)
            val nx = px + (bx / bl) * segLen * (0.55f + open * 0.45f)
            val ny = py + (by / bl) * segLen * (0.55f + open * 0.45f)
            val width = scale * (0.052f - s * 0.009f)
            drawLine(
                color = if (s == 0) skinDark else skin,
                start = Offset(px, py),
                end = Offset(nx, ny),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
            drawCircle(skin, width * 0.58f, Offset(nx, ny))
            px = nx
            py = ny
        }
    }
}
