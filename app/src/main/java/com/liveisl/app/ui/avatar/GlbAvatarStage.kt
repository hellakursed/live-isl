package com.liveisl.app.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liveisl.app.sign.AvatarSignRenderer
import com.liveisl.app.sign.MixamoPoseApplier
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WORKER_ASSET = "models/Construction_Worker.glb"

/**
 * Filament / SceneView stage for the Mixamo construction-worker GLB.
 * Framed waist-up and centered; poses push Filament bone matrices each frame.
 */
@Composable
fun GlbAvatarStage(
    renderer: AvatarSignRenderer,
    glossLabel: String?,
    modifier: Modifier = Modifier,
) {
    val pose by renderer.pose.collectAsStateWithLifecycle()

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    // Waist-up signing frame — camera looks at mid-torso, centered.
    val cameraNode = rememberCameraNode(engine) {
        position = Position(x = 0f, y = 1.35f, z = 2.05f)
        lookAt(Position(x = 0f, y = 1.2f, z = 0f))
    }

    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var modelNode by remember { mutableStateOf<ModelNode?>(null) }
    var poseApplier by remember { mutableStateOf<MixamoPoseApplier?>(null) }

    LaunchedEffect(modelLoader) {
        loading = true
        loadError = null
        try {
            val instance = withContext(Dispatchers.Main.immediate) {
                modelLoader.createModelInstance(WORKER_ASSET)
            }
            val node = ModelNode(
                modelInstance = instance,
                autoAnimate = false,
                scaleToUnits = 2.05f,
                centerOrigin = Position(x = 0f, y = 0f, z = 0f),
            )
            // Bounding-box center → world origin, then drop slightly for waist-up crop.
            val c = node.center
            node.position = Position(x = -c.x, y = -c.y - 0.2f, z = -c.z)
            node.rotation = Rotation(y = 0f)

            modelNode = node
            val named = collectNamedNodes(node)
            poseApplier = MixamoPoseApplier.capture(named, node.animator)
            poseApplier?.apply(pose)
            loading = false
        } catch (t: Throwable) {
            loading = false
            loadError = t.message ?: "Failed to load character"
        }
    }

    LaunchedEffect(pose, poseApplier) {
        val applier = poseApplier ?: return@LaunchedEffect
        withContext(Dispatchers.Main.immediate) {
            applier.apply(pose)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF071820), Color(0xFF123040), Color(0xFF0B1F2A))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            childNodes = listOfNotNull(modelNode),
            isOpaque = false,
        )

        when {
            loading -> CircularProgressIndicator(color = Color(0xFF2EC4B6))
            loadError != null -> Text(
                text = loadError ?: "Load error",
                color = Color(0xFFFF8A80),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
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
        } else if (!loading && loadError == null) {
            Text(
                text = "Construction worker ready",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9BB4C4),
            )
        }
    }
}

private fun collectNamedNodes(model: ModelNode): Map<String, Node> {
    val out = LinkedHashMap<String, Node>()
    fun index(n: Node) {
        val name = n.name
        if (!name.isNullOrBlank()) {
            out[name] = n
            val short = name.substringAfterLast('/')
            if (short != name) out[short] = n
        }
    }
    model.nodes.forEach(::index)
    model.emptyNodes.forEach(::index)
    if (out.isEmpty()) {
        fun walk(n: Node) {
            index(n)
            n.childNodes.forEach { walk(it) }
        }
        walk(model)
    }
    return out
}
