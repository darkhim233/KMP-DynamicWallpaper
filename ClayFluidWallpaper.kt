package neko.cloud.wallpaper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

@Composable
fun ClayFluidWallpaper(
    scheme: ColorScheme,
    modifier: Modifier = Modifier,
    blurSize: Dp = 12.dp,
    onControllerReady: (DynamicWallpaper) -> Unit = {},
) {

    val wallpaper = remember { DynamicWallpaper() }
    LaunchedEffect(scheme)    { wallpaper.updateColorScheme(scheme) }
    LaunchedEffect(wallpaper) { onControllerReady(wallpaper) }

    val blobs   by wallpaper.blobsState
    val bgColor by wallpaper.backgroundColorState

    Canvas(
        modifier = modifier
            .blur(blurSize)
            .fillMaxSize()
            .onGloballyPositioned {
                wallpaper.updateSize(it.size.width.toFloat(), it.size.height.toFloat())
            },
    ) {
        drawRect(color = bgColor)
        blobs.forEach { blob ->
            withTransform({
                translate(blob.cx, blob.cy)
                rotate(toDegrees(blob.rotation.toDouble()).toFloat())
            }) {
                drawClayBlob(blob)
            }
        }
    }
}

fun toDegrees(toDouble: Double): Double {
    return toDouble*57.29577951308232
}
// ─────────────────────────────────────────────────────────────────────────────
// 控制器
// ─────────────────────────────────────────────────────────────────────────────

class DynamicWallpaper {
    internal val blobsState           = mutableStateOf<List<Blob>>(emptyList())
    internal val backgroundColorState = mutableStateOf(Color(0xFFFDF7EF))

    private var fluidColors: List<FluidColor> = emptyList()
    private var canvasW = 0f
    private var canvasH = 0f

    fun updateColorScheme(scheme: ColorScheme) {
        backgroundColorState.value = scheme.background
        fluidColors = listOf(
            FluidColor(scheme.primaryContainer,   scheme.primary),
            FluidColor(scheme.secondaryContainer, scheme.secondary),
            FluidColor(scheme.tertiaryContainer,  scheme.tertiary),
            FluidColor(scheme.errorContainer,     scheme.error),
            FluidColor(scheme.primaryContainer,   scheme.inversePrimary),
        )
        regenerate()
    }

    fun blur(size: Float) {

    }
    fun updateSize(w: Float, h: Float) {
        if (canvasW == w && canvasH == h) return
        canvasW = w; canvasH = h
        regenerate()
    }

    fun randomize() = regenerate()

    private fun regenerate() {
        if (fluidColors.isEmpty() || canvasW <= 0f || canvasH <= 0f) return
        val base = max(canvasW, canvasH)

        blobsState.value = (0 until 22).map { i ->
            val radius = when {
                i < 5  -> base * (0.6f + Random.nextFloat() * 0.4f)
                i < 14 -> base * (0.25f + Random.nextFloat() * 0.3f)
                else   -> base * (0.1f  + Random.nextFloat() * 0.15f)
            }
            val complexity = when {
                i < 5  -> 12 + Random.nextInt(6)
                i < 14 -> 15
                else   -> 10
            }
            Blob(
                path       = buildPath(radius, complexity),
                color      = fluidColors[Random.nextInt(fluidColors.size)],
                cx         = (Random.nextFloat() * 1.8f - 0.4f) * canvasW,
                cy         = (Random.nextFloat() * 1.8f - 0.4f) * canvasH,
                baseRadius = radius,
                rotation   = Random.nextFloat() * TAU,
            )
        }.sortedByDescending { it.baseRadius }
    }

    private fun buildPath(radius: Float, complexity: Int): Path {
        val p1 = Random.nextFloat() * TAU; val p2 = Random.nextFloat() * TAU; val p3 = Random.nextFloat() * TAU
        val f1 = (1 + Random.nextInt(3)).toFloat()
        val f2 = (2 + Random.nextInt(4)).toFloat()
        val f3 = (4 + Random.nextInt(4)).toFloat()
        val sx = 0.7f + Random.nextFloat() * 0.6f
        val sy = 0.7f + Random.nextFloat() * 0.6f

        val pts = Array(complexity) { i ->
            val t    = (i.toFloat() / complexity) * TAU
            val wave = 1f + 0.45f * sin(t * f1 + p1) + 0.25f * cos(t * f2 + p2) + 0.15f * sin(t * f3 + p3)
            val r    = radius * max(0.2f, wave)
            Offset(cos(t) * r * sx, sin(t) * r * sy)
        }
        return Path().apply {
            moveTo((pts[0].x + pts[complexity - 1].x) / 2f, (pts[0].y + pts[complexity - 1].y) / 2f)
            repeat(complexity) { i ->
                val p = pts[i]; val n = pts[(i + 1) % complexity]
                quadraticTo(p.x, p.y, (p.x + n.x) / 2f, (p.y + n.y) / 2f)
            }
            close()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 渲染
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawClayBlob(blob: Blob) {
    val r   = blob.baseRadius
    val lw  = max(40f, r * 0.18f)   // 描边宽度基准，越大光影越向内扩散
    val path = blob.path

    // ── 1. 外阴影：在路径外用 SrcOver 叠一层向右下偏移的暗色填充 ────────────
    //    HTML: ctx.shadowColor / shadowBlur / shadowOffsetX/Y
    //    Compose: 偏移 + 半透明填充，drawPath 在 clip 外，天然被上层遮住边缘
    val shadowPath = Path().apply {
        addPath(path, Offset(r * 0.04f, r * 0.06f))
    }
    drawPath(shadowPath, color = Color(0x1A000000))   // ~10% black

    // ── 2. 基础体积渐变填充（不透明） ────────────────────────────────────────
    drawPath(
        path  = path,
        brush = Brush.linearGradient(
            colors = listOf(blob.color.start, blob.color.end),
            start  = Offset(-r, -r),
            end    = Offset(r, r),
        ),
    )

    // ── 3-5. 进入 clip 区域，模拟 HTML 的 blur+stroke 光影 ───────────────────
    //    原理：HTML 在 clip 内对路径做偏移描边，描边宽度很大 + filter blur，
    //          形成从边缘向内渐淡的光/影。
    //    Compose 替代：用多层半透明大宽度 Stroke + SrcAtop BlendMode，
    //          偏移路径使描边只露出靠近边缘一侧，达到定向光效果。
    clipPath(path) {

        // 3. 漫反射高光（左上）—— 向左上偏移，描边只在左上边缘内侧可见
        val highlightPath = Path().apply { addPath(path, Offset(-r * 0.15f, -r * 0.18f)) }
        drawPath(
            path      = highlightPath,
            color     = Color.White.copy(alpha = 0.55f),
            style     = androidx.compose.ui.graphics.drawscope.Stroke(width = lw * 2.8f),
            blendMode = BlendMode.SrcAtop,
        )
        // 第二层高光，更窄更亮，模拟 specular
        drawPath(
            path      = highlightPath,
            color     = Color.White.copy(alpha = 0.30f),
            style     = androidx.compose.ui.graphics.drawscope.Stroke(width = lw * 1.2f),
            blendMode = BlendMode.SrcAtop,
        )

        // 4. 内阴影（右下）—— 向右下偏移，描边只在右下边缘内侧可见
        val shadowInPath = Path().apply { addPath(path, Offset(r * 0.18f, r * 0.22f)) }
        drawPath(
            path      = shadowInPath,
            color     = Color.Black.copy(alpha = 0.22f),
            style     = androidx.compose.ui.graphics.drawscope.Stroke(width = lw * 3.2f),
            blendMode = BlendMode.SrcAtop,
        )

        // 5. 哑光环境反射（边缘整圈，本体亮色）—— 不偏移，描边对称分布在内边缘
        drawPath(
            path      = path,
            color     = blob.color.start.copy(alpha = 0.25f),
            style     = androidx.compose.ui.graphics.drawscope.Stroke(width = lw * 1.5f),
            blendMode = BlendMode.SrcAtop,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 数据类
// ─────────────────────────────────────────────────────────────────────────────

internal data class FluidColor(val start: Color, val end: Color)

internal data class Blob(
    val path: Path,
    val color: FluidColor,
    val cx: Float,
    val cy: Float,
    val baseRadius: Float,
    val rotation: Float,
)

private val TAU = (PI * 2).toFloat()
