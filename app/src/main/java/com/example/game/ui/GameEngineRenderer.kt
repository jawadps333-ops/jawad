package com.example.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.audio.GameAudio
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Screen Point representation
data class Point2DProj(
    val x: Float,
    val y: Float,
    val zDepth: Float
)

// Painters algorithm task
sealed class RenderTask(val depth: Float) : Comparable<RenderTask> {
    override fun compareTo(other: RenderTask): Int {
        // Furthest first (draw background nodes first)
        return other.depth.compareTo(this.depth)
    }
    abstract fun draw(scope: DrawScope)
}

class PolygonRenderTask(
    depth: Float,
    private val points: List<Point2DProj>,
    private val fillColor: Color,
    private val strokeColor: Color? = null,
    private val strokeWidth: Float = 1.5f
) : RenderTask(depth) {
    override fun draw(scope: DrawScope) {
        if (points.size < 3) return
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
            close()
        }
        scope.drawPath(path, color = fillColor)
        if (strokeColor != null) {
            scope.drawPath(path, color = strokeColor, style = Stroke(width = strokeWidth))
        }
    }
}

class CircleRenderTask(
    depth: Float,
    private val center: Point2DProj,
    private val radius: Float,
    private val fillColor: Color,
    private val strokeColor: Color? = null
) : RenderTask(depth) {
    override fun draw(scope: DrawScope) {
        scope.drawCircle(
            color = fillColor,
            radius = radius,
            center = Offset(center.x, center.y)
        )
        if (strokeColor != null) {
            scope.drawCircle(
                color = strokeColor,
                radius = radius,
                center = Offset(center.x, center.y),
                style = Stroke(width = 1.5f)
            )
        }
    }
}

class LineRenderTask(
    depth: Float,
    private val p1: Point2DProj,
    private val p2: Point2DProj,
    private val color: Color,
    private val strokeWidth: Float = 2f
) : RenderTask(depth) {
    override fun draw(scope: DrawScope) {
        scope.drawLine(
            color = color,
            start = Offset(p1.x, p1.y),
            end = Offset(p2.x, p2.y),
            strokeWidth = strokeWidth
        )
    }
}

class CustomSpriteRenderTask(
    depth: Float,
    private val p: Point2DProj,
    private val drawBlock: DrawScope.(Offset) -> Unit
) : RenderTask(depth) {
    override fun draw(scope: DrawScope) {
        scope.drawBlock(Offset(p.x, p.y))
    }
}

@Composable
fun Game3DCanvas(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    // Collect states
    val px by viewModel.playerX.collectAsState()
    val py by viewModel.playerY.collectAsState()
    val pAngle by viewModel.playerAngle.collectAsState()
    val drivenVehicleId by viewModel.drivenVehicleId.collectAsState()

    // Read static maps elements
    val buildings = viewModel.buildings
    val vehicles = viewModel.vehicles
    val npcs = viewModel.npcs
    val projectiles = viewModel.projectiles
    val moneyDrops = viewModel.moneyDrops
    val houses = viewModel.houses

    val config = LocalConfiguration.current
    val wWidth = config.screenWidthDp.dp
    val wHeight = config.screenHeightDp.dp

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFEAA164))) { // Nostalgic GTA SA sunset dust background
        Canvas(modifier = Modifier.fillMaxSize().testTag("3d_game_canvas")) {
            val width = size.width
            val height = size.height

            if (width < 100 || height < 100) return@Canvas

            // 1. Calculate camera parameters
            // Standard Third Person POV look parameters: Camera follows from behind
            val focalLength = width * 1.1f // Field of View (FOV) factor
            val camDist = 58f
            val camHeight = 35f // Elevated to peek streets
            val camPitch = 0.28f // Radiant tilt slant looking downwards

            // Follow player position, offset backward
            val camX = px - cos(pAngle) * camDist
            val camZ = py - sin(pAngle) * camDist
            val camY = camHeight

            // Yaw aligns so player's heading is facing 'up'
            val camYaw = 1.5707f - pAngle

            // Helper 3D Projection math localized
            fun project3D(wx: Float, wy: Float, wz: Float): Point2DProj? {
                // Translate relative to camera coordinates
                val tx = wx - camX
                val ty = wy - camY
                val tz = wz - camZ

                // Yaw transform (Z and X plane rotation)
                val cosY = cos(camYaw)
                val sinY = sin(camYaw)
                val rx = tx * cosY - tz * sinY
                val rz = tx * sinY + tz * cosY

                // Pitch transform (Y and Z slant rotation)
                val cosP = cos(camPitch)
                val sinP = sin(camPitch)
                val ryProj = ty * cosP - rz * sinP
                val rzProj = ty * sinP + rz * cosP

                // Avoid projection division by zero (objects behind the viewport camera)
                if (rzProj < 1.5f) return null

                val sx = (width / 2f) + (rx / rzProj) * focalLength
                val sy = (height / 2f) - (ryProj / rzProj) * focalLength

                return Point2DProj(sx, sy, rzProj)
            }

            // Painters algorithm tasks array
            val renderQueue = ArrayList<RenderTask>()

            // ------------------ 2. GRID / STREET RENDERING ------------------
            // Draw roads: Horizontal grid avenues and vertical boulevards
            val majorAvenues = listOf(0f, 240f, 480f, 720f, 960f, 1200f)
            val roadHalfWidth = 25f

            // Draw major intersections grid
            for (av in majorAvenues) {
                // Vertical roads (x = av, spanning z 0 to mapSize)
                val rvP1 = project3D(av - roadHalfWidth, 0f, 0f)
                val rvP2 = project3D(av + roadHalfWidth, 0f, 0f)
                val rvP3 = project3D(av + roadHalfWidth, 0f, viewModel.mapSize)
                val rvP4 = project3D(av - roadHalfWidth, 0f, viewModel.mapSize)

                if (rvP1 != null && rvP2 != null && rvP3 != null && rvP4 != null) {
                    val dAvg = (rvP1.zDepth + rvP2.zDepth + rvP3.zDepth + rvP4.zDepth) / 4f
                    renderQueue.add(
                        PolygonRenderTask(
                            depth = dAvg,
                            points = listOf(rvP1, rvP2, rvP3, rvP4),
                            fillColor = Color(0xFF323235), // Dark asphalt
                            strokeColor = Color(0xFF1E1E1E)
                        )
                    )
                }

                // Horizontal roads (z = av, spanning x 0 to mapSize)
                val rhP1 = project3D(0f, 0f, av - roadHalfWidth)
                val rhP2 = project3D(viewModel.mapSize, 0f, av - roadHalfWidth)
                val rhP3 = project3D(viewModel.mapSize, 0f, av + roadHalfWidth)
                val rhP4 = project3D(0f, 0f, av + roadHalfWidth)

                if (rhP1 != null && rhP2 != null && rhP3 != null && rhP4 != null) {
                    val dAvg = (rhP1.zDepth + rhP2.zDepth + rhP3.zDepth + rhP4.zDepth) / 4f
                    renderQueue.add(
                        PolygonRenderTask(
                            depth = dAvg,
                            points = listOf(rhP1, rhP2, rhP3, rhP4),
                            fillColor = Color(0xFF323235),
                            strokeColor = Color(0xFF1E1E1E)
                        )
                    )
                }
            }

            // ------------------ 3. HOUSES / PROPERTIES RENDER ------------------
            houses.forEach { h ->
                val pCenter = project3D(h.x, 0f, h.y)
                if (pCenter != null) {
                    val sizeScale = 400f / pCenter.zDepth
                    // Rotating pickup 3D diamond styled icon
                    val isOwned = h.isOwned
                    val markerColor = if (isOwned) Color(0xFF4CAF50) else Color(0xFF2196F3) // Green for owned, blue for buyable
                    val houseRadius = (16f * sizeScale).coerceIn(4f, 70f)

                    renderQueue.add(
                        CircleRenderTask(
                            depth = pCenter.zDepth + 0.1f, // draw on ground
                            center = pCenter,
                            radius = houseRadius,
                            fillColor = markerColor.copy(alpha = 0.5f),
                            strokeColor = markerColor
                        )
                    )

                    // Draw a nice classic floating ring & star above property
                    val pRing = project3D(h.x, 6f, h.y)
                    if (pRing != null) {
                        val ringRadius = (9f * (400f / pRing.zDepth)).coerceIn(2f, 40f)
                        renderQueue.add(
                            CircleRenderTask(
                                depth = pRing.zDepth,
                                center = pRing,
                                radius = ringRadius,
                                fillColor = Color.Yellow.copy(alpha = 0.35f),
                                strokeColor = Color.Yellow
                            )
                        )
                    }
                }
            }

            // ------------------ 2B. ROUTE 15 HIGHWAY OVERPASS ------------------
            // We draw elevated curved overpass segments crossing diagonally from bottom-left to top-right
            val segs = listOf(
                Pair(0f, 850f),
                Pair(200f, 740f),
                Pair(400f, 620f),
                Pair(600f, 500f),
                Pair(800f, 380f),
                Pair(1000f, 260f),
                Pair(1200f, 150f)
            )

            // Draw concrete columns down to the ground
            for (i in segs.indices) {
                val seg = segs[i]
                val pPillarBase = project3D(seg.first, 0f, seg.second)
                val pPillarTop = project3D(seg.first, 12f, seg.second)
                if (pPillarBase != null && pPillarTop != null) {
                    val scale = 400f / pPillarBase.zDepth
                    renderQueue.add(
                        LineRenderTask(
                            depth = pPillarBase.zDepth,
                            p1 = pPillarBase,
                            p2 = pPillarTop,
                            color = Color(0xFF78909C), // Steel grey concrete
                            strokeWidth = (14f * scale).coerceIn(4f, 40f)
                        )
                    )
                }
            }

            // Draw the elevated double-lane road deck segments
            for (i in 0 until segs.size - 1) {
                val s1 = segs[i]
                val s2 = segs[i + 1]

                val dx = s2.first - s1.first
                val dz = s2.second - s1.second
                val len = sqrt(dx * dx + dz * dz)
                val nx = (-dz / len) * 35f // Half-width of highway deck
                val nz = (dx / len) * 35f

                // Deck corners at y = 12f
                val h1 = project3D(s1.first - nx, 12f, s1.second - nz)
                val h2 = project3D(s1.first + nx, 12f, s1.second + nz)
                val h3 = project3D(s2.first + nx, 12f, s2.second + nz)
                val h4 = project3D(s2.first - nx, 12f, s2.second - nz)

                if (h1 != null && h2 != null && h3 != null && h4 != null) {
                    val dAvg = (h1.zDepth + h2.zDepth + h3.zDepth + h4.zDepth) / 4f
                    // Asphalt bridge road surface
                    renderQueue.add(
                        PolygonRenderTask(
                            depth = dAvg,
                            points = listOf(h1, h2, h3, h4),
                            fillColor = Color(0xFF263238), // Dark Charcoal
                            strokeColor = Color.Black,
                            strokeWidth = 3f
                        )
                    )

                    // Draw yellow dashed double line down the center of Route 15
                    val c1 = project3D(s1.first, 12.1f, s1.second)
                    val c2 = project3D(s2.first, 12.1f, s2.second)
                    if (c1 != null && c2 != null) {
                        renderQueue.add(
                            LineRenderTask(
                                depth = dAvg - 0.1f,
                                p1 = c1,
                                p2 = c2,
                                color = Color(0xFFFFEB3B), // Yellow center strip
                                strokeWidth = 3f
                            )
                        )
                    }

                    // Green "Route 15" signs on guardrails
                    if (i % 2 == 0) {
                        val signPos = project3D(s1.first - nx - 3f, 14f, s1.second - nz)
                        if (signPos != null) {
                            val sz = (13f * (400f / signPos.zDepth)).coerceIn(3f, 25f)
                            renderQueue.add(
                                CircleRenderTask(
                                    depth = signPos.zDepth - 0.2f,
                                    center = signPos,
                                    radius = sz,
                                    fillColor = Color(0xFF00695C), // Route 15 Green
                                    strokeColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // ------------------ 4. BUILDINGS AND LANDMARKS COMPONENT ------------------
            buildings.forEach { b ->
                // Project 8 corner coordinates of the rectangular block
                val s0 = project3D(b.x, 0f, b.z)
                val s1 = project3D(b.x + b.width, 0f, b.z)
                val s2 = project3D(b.x + b.width, 0f, b.z + b.depth)
                val s3 = project3D(b.x, 0f, b.z + b.depth)

                val s4 = project3D(b.x, b.height, b.z)
                val s5 = project3D(b.x + b.width, b.height, b.z)
                val s6 = project3D(b.x + b.width, b.height, b.z + b.depth)
                val s7 = project3D(b.x, b.height, b.z + b.depth)

                if (s0 != null && s1 != null && s2 != null && s3 != null &&
                    s4 != null && s5 != null && s6 != null && s7 != null
                ) {
                    val avgDist = (s0.zDepth + s1.zDepth + s2.zDepth + s3.zDepth) / 4f
                    val baseColor = Color(b.colorHex)
                    val shadowColor = Color((b.colorHex and 0xFEFEFEFE) shr 1) // darker shading for comic depth
                    val roofColor = Color(b.colorHex).copy(alpha = 0.95f)

                    // Draw solid walls with prominent black outline for cartoon cell shaded look
                    // Back wall (s0-s1-s5-s4)
                    renderQueue.add(PolygonRenderTask(avgDist, listOf(s0, s1, s5, s4), shadowColor, Color.Black, strokeWidth = 2.5f))
                    // Side wall (s1-s2-s6-s5)
                    renderQueue.add(PolygonRenderTask(avgDist, listOf(s1, s2, s6, s5), baseColor, Color.Black, strokeWidth = 2.5f))
                    // Front wall (s2-s3-s7-s6)
                    renderQueue.add(PolygonRenderTask(avgDist, listOf(s2, s3, s7, s6), shadowColor, Color.Black, strokeWidth = 2.5f))
                    // Side wall (s3-s0-s4-s7)
                    renderQueue.add(PolygonRenderTask(avgDist, listOf(s3, s0, s4, s7), baseColor, Color.Black, strokeWidth = 2.5f))
                    // Roof top
                    renderQueue.add(PolygonRenderTask(avgDist - 0.5f, listOf(s4, s5, s6, s7), roofColor, Color.Black, strokeWidth = 2.5f))

                    // SPECIAL LANDMARK DECORATIONS
                    when (b.r) {
                        10 -> { // Al Maha Hall (Wedding Ballroom): Pink neon accents, floating signage
                            val signCenter = project3D(b.x + b.width / 2f, b.height + 4f, b.z + b.depth / 2f)
                            if (signCenter != null) {
                                val sSize = (18f * (320f / signCenter.zDepth)).coerceIn(4f, 45f)
                                renderQueue.add(
                                    CircleRenderTask(
                                        depth = signCenter.zDepth - 0.8f,
                                        center = signCenter,
                                        radius = sSize,
                                        fillColor = Color(0xFFC2185B), // Deep pink neon marquee
                                        strokeColor = Color.White
                                    )
                                )
                            }
                            // Vertical neon column light shafts on front wall corners
                            val c1 = project3D(b.x + b.width, 0f, b.z + b.depth)
                            val c2 = project3D(b.x + b.width, b.height, b.z + b.depth)
                            if (c1 != null && c2 != null) {
                                renderQueue.add(
                                    LineRenderTask(
                                        depth = avgDist - 0.2f,
                                        p1 = c1,
                                        p2 = c2,
                                        color = Color(0xFFFF4081), // Pink pillar trace
                                        strokeWidth = 4f
                                    )
                                )
                            }
                        }
                        11 -> { // Al Quds Bakery: Striped cartoon awning overhang over entrance
                            val frontLeft = project3D(b.x, b.height * 0.6f, b.z + b.depth)
                            val frontRight = project3D(b.x + b.width, b.height * 0.6f, b.z + b.depth)
                            val awningLeft = project3D(b.x, b.height * 0.4f, b.z + b.depth + 6f)
                            val awningRight = project3D(b.x + b.width, b.height * 0.4f, b.z + b.depth + 6f)
                            if (frontLeft != null && frontRight != null && awningLeft != null && awningRight != null) {
                                renderQueue.add(
                                    PolygonRenderTask(
                                        depth = avgDist - 1f,
                                        points = listOf(frontLeft, frontRight, awningRight, awningLeft),
                                        fillColor = Color(0xFFE53935), // Vibrant red striped awning
                                        strokeColor = Color.White
                                    )
                                )
                            }
                        }
                        12 -> { // Tansal Turkish Restaurant: neon signage & cozy highlights
                            val bannerStart = project3D(b.x, b.height * 0.8f, b.z + b.depth + 0.1f)
                            val bannerEnd = project3D(b.x + b.width, b.height * 0.8f, b.z + b.depth + 0.1f)
                            if (bannerStart != null && bannerEnd != null) {
                                renderQueue.add(
                                    LineRenderTask(
                                        depth = avgDist - 0.4f,
                                        p1 = bannerStart,
                                        p2 = bannerEnd,
                                        color = Color(0xFFFFB300), // Neon Orange/Gold banner
                                        strokeWidth = 6f
                                    )
                                )
                            }
                        }
                        14 -> { // Masjid Al Johny: Golden domes & towering tall Minaret
                            val dome = project3D(b.x + b.width / 2f, b.height + 6f, b.z + b.depth / 2f)
                            if (dome != null) {
                                val domeRadius = (16f * (400f / dome.zDepth)).coerceIn(4f, 60f)
                                renderQueue.add(
                                    CircleRenderTask(
                                        depth = dome.zDepth - 0.9f,
                                        center = dome,
                                        radius = domeRadius,
                                        fillColor = Color(0xFFFFD54F), // Majestic gold dome
                                        strokeColor = Color.Black
                                    )
                                )
                            }
                            // Majestic Minaret Tower next to mosque
                            val minBase = project3D(b.x + b.width + 12f, 0f, b.z + b.depth / 2f)
                            val minTop = project3D(b.x + b.width + 12f, 34f, b.z + b.depth / 2f)
                            if (minBase != null && minTop != null) {
                                val scale = 400f / minBase.zDepth
                                renderQueue.add(
                                    LineRenderTask(
                                        depth = minBase.zDepth + 0.5f,
                                        p1 = minBase,
                                        p2 = minTop,
                                        color = Color(0xFF00897B), // Beautiful Emerald-green minaret
                                        strokeWidth = (9f * scale).coerceIn(3f, 25f)
                                    )
                                )
                                // Golden peak spire circle for minaret
                                renderQueue.add(
                                    CircleRenderTask(
                                        depth = minTop.zDepth - 0.1f,
                                        center = minTop,
                                        radius = (4.5f * scale).coerceIn(1.5f, 15f),
                                        fillColor = Color(0xFFFFD54F) // Gold crest minaret cap
                                    )
                                )
                            }
                        }
                        15 -> { // Adil's Safehouse Apartment: Beaming intense emerald cylinder marker beacon pointing to the heavens
                            val startP = project3D(b.x + b.width / 2f, b.height, b.z + b.depth / 2f)
                            val skyP = project3D(b.x + b.width / 2f, b.height + 120f, b.z + b.depth / 2f)
                            if (startP != null && skyP != null) {
                                val scaleWidth = 400f / startP.zDepth
                                // Translucent safety column light shaft
                                renderQueue.add(
                                    LineRenderTask(
                                        depth = startP.zDepth - 2f,
                                        p1 = startP,
                                        p2 = skyP,
                                        color = Color(0x7F4CAF50), // Emerald transparent glow
                                        strokeWidth = (28f * scaleWidth).coerceIn(4f, 50f)
                                    )
                                )
                                // Dense hot core beam
                                renderQueue.add(
                                    LineRenderTask(
                                        depth = startP.zDepth - 2.1f,
                                        p1 = startP,
                                        p2 = skyP,
                                        color = Color(0xEECCFF90), // Searing lime core
                                        strokeWidth = (7f * scaleWidth).coerceIn(1.5f, 15f)
                                    )
                                )
                            }
                        }
                    }

                    // Standard window grids panel decoration for non-special high buildings
                    if (b.r < 10 && b.height > 25f && avgDist < 400f) {
                        val midWindow = project3D(b.x + b.width / 2f, b.height / 2f, b.z + b.depth + 0.1f)
                        if (midWindow != null) {
                            val wSize = (14f * (400f / midWindow.zDepth)).coerceIn(1f, 30f)
                            renderQueue.add(
                                CircleRenderTask(
                                    depth = midWindow.zDepth - 0.2f,
                                    center = midWindow,
                                    radius = wSize,
                                    fillColor = Color(0xFFFFD54F) // Cozy yellow window lights
                                )
                            )
                        }
                    }
                }
            }

            // ------------------ 5. TRAFFIC VECHICLES ------------------
            vehicles.forEach { car ->
                if (car.health <= 0) {
                    // Exploded debris flat block
                    val pBase = project3D(car.x, 0f, car.y)
                    if (pBase != null) {
                        renderQueue.add(
                            CircleRenderTask(
                                depth = pBase.zDepth,
                                center = pBase,
                                radius = 18f * (300f / pBase.zDepth),
                                fillColor = Color(0xFF3E2723), // Burnt chassis grey charcoal
                                strokeColor = Color.Black
                            )
                        )
                    }
                    return@forEach
                }

                // Calculate rotated corners for correct 3D heading
                val sizeW = 4.2f
                val sizeL = 8.5f
                val sizeH = 3.6f

                val cosCar = cos(car.angle)
                val sinCar = sin(car.angle)

                fun localToWorld(lx: Float, lz: Float, ly: Float): Point2DProj? {
                    // Turn coordinate
                    val wx = car.x + (lx * sinCar - lz * cosCar)
                    val wz = car.y + (lx * cosCar + lz * sinCar)
                    return project3D(wx, ly, wz)
                }

                val c0 = localToWorld(-sizeW, -sizeL, 0f)
                val c1 = localToWorld(sizeW, -sizeL, 0f)
                val c2 = localToWorld(sizeW, sizeL, 0f)
                val c3 = localToWorld(-sizeW, sizeL, 0f)

                val c4 = localToWorld(-sizeW, -sizeL, sizeH)
                val c5 = localToWorld(sizeW, -sizeL, sizeH)
                val c6 = localToWorld(sizeW, sizeL, sizeH)
                val c7 = localToWorld(-sizeW, sizeL, sizeH)

                if (c0 != null && c1 != null && c2 != null && c3 != null &&
                    c4 != null && c5 != null && c6 != null && c7 != null
                ) {
                    val carColor = Color(car.colorHex)
                    val winColor = Color(0xFFB3E5FC).copy(alpha = 0.7f) // Glassy bluish window
                    val dAvg = (c0.zDepth + c1.zDepth + c2.zDepth + c3.zDepth) / 4f

                    // Render solid color parts
                    renderQueue.add(PolygonRenderTask(dAvg, listOf(c0, c1, c5, c4), carColor.copy(alpha = 0.8f), Color.Black))
                    // Front windshield windshield window side (s2-s3-s7-s6)
                    renderQueue.add(PolygonRenderTask(dAvg, listOf(c2, c3, c7, c6), Color.Black, Color.White))
                    // Back side window (s1-s2-s6-s5)
                    renderQueue.add(PolygonRenderTask(dAvg, listOf(c1, c2, c6, c5), carColor, Color.Black))
                    // Left side window (s3-s0-s4-s7)
                    renderQueue.add(PolygonRenderTask(dAvg, listOf(c3, c0, c4, c7), carColor, Color.Black))
                    // Car Roof Hood
                    renderQueue.add(PolygonRenderTask(dAvg - 0.2f, listOf(c4, c5, c6, c7), carColor, Color.White))

                    // Drawn bright headlight cones pointing forward on traffic lane
                    val lightPos = localToWorld(0f, sizeL + 4f, 0.4f)
                    if (lightPos != null) {
                        val headSz = (11f * (300f / lightPos.zDepth)).coerceIn(2f, 25f)
                        renderQueue.add(
                            CircleRenderTask(
                                depth = lightPos.zDepth - 0.5f,
                                center = lightPos,
                                radius = headSz,
                                fillColor = Color.White.copy(alpha = 0.35f)
                            )
                        )
                    }
                }
            }

            // ------------------ 6. NPCs PEPE / DEAD STATE RENDER ------------------
            npcs.forEach { npc ->
                val pBase = project3D(npc.x, 0f, npc.y)
                if (pBase != null) {
                    val baseScale = 400f / pBase.zDepth
                    if (npc.isDead) {
                        // Rendering Splat crimson puddle pool
                        val poolRad = (13f * baseScale).coerceIn(3f, 40f)
                        renderQueue.add(
                            CircleRenderTask(
                                depth = pBase.zDepth + 0.05f,
                                center = pBase,
                                radius = poolRad,
                                fillColor = Color(0xFFD32F2F).copy(alpha = 0.65f)
                            )
                        )
                        // flat body
                        val pBody = project3D(npc.x + 3f, 0f, npc.y + 1f)
                        if (pBody != null) {
                            renderQueue.add(
                                LineRenderTask(
                                    depth = pBase.zDepth,
                                    p1 = pBase,
                                    p2 = pBody,
                                    color = Color(0xFF2E1C0C),
                                    strokeWidth = (5f * baseScale).coerceIn(1f, 15f)
                                )
                            )
                        }
                    } else {
                        // Draw vertical 3D capsule for alive pedestrians
                        val pHead = project3D(npc.x, 5.5f, npc.y)
                        if (pHead != null) {
                            val bodyColor = when (npc.type) {
                                "gangster" -> Color(0xFF7B1FA2) // Ballas gang lavender
                                "cop" -> Color(0xFF1A237E) // Blue patrol uniform shirt
                                else -> Color(0xFF4CAF50) // Average citizen teal green
                            }
                            val headWidth = (4.5f * baseScale).coerceIn(1f, 18f)

                            // Draw body uniform trace line
                            renderQueue.add(
                                LineRenderTask(
                                    depth = pBase.zDepth,
                                    p1 = pBase,
                                    p2 = pHead,
                                    color = bodyColor,
                                    strokeWidth = (9f * baseScale).coerceIn(1.5f, 28f)
                                )
                            )

                            // Head cap node
                            renderQueue.add(
                                CircleRenderTask(
                                    depth = pHead.zDepth - 0.1f,
                                    center = pHead,
                                    radius = headWidth,
                                    fillColor = Color(0xFFD7CCC8), // Pale/brown node
                                    strokeColor = Color.Black
                                )
                            )
                        }
                    }
                }
            }

            // ------------------ 7. PROJECTILES RENDER ------------------
            projectiles.forEach { proj ->
                val pBall = project3D(proj.x, 1.2f, proj.y)
                if (pBall != null) {
                    val pSize = if (proj.isRpg) 11f else 5f
                    val bColor = if (proj.isRpg) Color.Red else Color.Yellow
                    renderQueue.add(
                        CircleRenderTask(
                            depth = pBall.zDepth,
                            center = pBall,
                            radius = pSize * (350f / pBall.zDepth),
                            fillColor = bColor,
                            strokeColor = Color.White
                        )
                    )
                }
            }

            // ------------------ 8. CASH MONEY DROPS ------------------
            moneyDrops.forEach { drop ->
                val pCash = project3D(drop.x, 0f, drop.y)
                if (pCash != null) {
                    val sc = 400f / pCash.zDepth
                    val rSize = (10f * sc).coerceIn(2f, 30f)
                    renderQueue.add(
                        CircleRenderTask(
                            depth = pCash.zDepth + 0.1f,
                            center = pCash,
                            radius = rSize,
                            fillColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                            strokeColor = Color.Yellow
                        )
                    )
                }
            }

            // ------------------ 9. THE CHARACTER: ADIL RENDER ------------------
            // If the player is driving inside a car, do not draw him separately as a roaming node
            if (drivenVehicleId == null) {
                // Ground center of player Adil
                val pBase = project3D(px, 0f, py)
                val pHead = project3D(px, 5.5f, py) // Height of Adil is 5.5 units on ground

                if (pBase != null && pHead != null) {
                    val scale = 400f / pBase.zDepth
                    val beardColor = Color(0xFF1E1E1E)
                    val skinColor = Color(0xFF5D4037) // Dark/brown skin average tall guy
                    val shirtColor = Color(0xFF00C853) // Green CJ crew shirt color
                    val jeansColor = Color(0xFF0D47A1) // Blue retro denim jeans

                    // Draw legs structure (slightly animate back-and-forth swing based on movement tick)
                    // Torso block
                    renderQueue.add(
                        LineRenderTask(
                            depth = pBase.zDepth,
                            p1 = pBase,
                            p2 = pHead,
                            color = shirtColor,
                            strokeWidth = (14f * scale).coerceIn(2f, 38f)
                        )
                    )

                    // Jeans tint
                    val pCenterLegs = project3D(px, 2.5f, py)
                    if (pCenterLegs != null) {
                        renderQueue.add(
                            LineRenderTask(
                                depth = pBase.zDepth + 0.05f,
                                p1 = pBase,
                                p2 = pCenterLegs,
                                color = jeansColor,
                                strokeWidth = (13f * scale).coerceIn(1.8f, 32f)
                            )
                        )
                    }

                    // Head details: beard, sunglasses and taper fade hair
                    val headCenter = pHead
                    val hRadius = (5.5f * scale).coerceIn(1.5f, 20f)
                    renderQueue.add(
                        CircleRenderTask(
                            depth = pHead.zDepth - 0.1f,
                            center = headCenter,
                            radius = hRadius,
                            fillColor = skinColor,
                            strokeColor = Color.Black
                        )
                    )

                    // Large Black beard wrapping nose and chin (lower half semicircle)
                    val pBeard = project3D(px, 5.0f, py)
                    if (pBeard != null) {
                        renderQueue.add(
                            CircleRenderTask(
                                depth = pBeard.zDepth - 0.15f,
                                center = pBeard,
                                radius = hRadius * 0.9f,
                                fillColor = beardColor
                            )
                        )
                    }

                    // Cool retro black square glasses covering eyes zone!
                    val pGlasses = project3D(px + cos(pAngle) * 0.4f, 5.7f, py + sin(pAngle) * 0.4f)
                    if (pGlasses != null) {
                        val glassOffset = (3f * scale).coerceIn(1f, 10f)
                        renderQueue.add(
                            CustomSpriteRenderTask(
                                depth = pGlasses.zDepth - 0.2f,
                                p = pGlasses,
                                drawBlock = { offset ->
                                    // Left eye lens
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = Offset(offset.x - glassOffset, offset.y - glassOffset / 2),
                                        size = Size(glassOffset, glassOffset * 0.6f)
                                    )
                                    // Right eye lens
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = Offset(offset.x + 2f, offset.y - glassOffset / 2),
                                        size = Size(glassOffset, glassOffset * 0.6f)
                                    )
                                    // Thin bridge line
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(offset.x - 1f, offset.y),
                                        end = Offset(offset.x + 2f, offset.y),
                                        strokeWidth = 2f
                                    )
                                }
                            )
                        )
                    }

                    // Low Taper Fade curly black hair on top
                    val pFadeHair = project3D(px, 6.2f, py)
                    if (pFadeHair != null) {
                        val hairFadeRadius = (4.8f * scale).coerceIn(1f, 18f)
                        renderQueue.add(
                            CircleRenderTask(
                                depth = pFadeHair.zDepth - 0.12f,
                                center = pFadeHair,
                                radius = hairFadeRadius,
                                fillColor = Color(0xFF1E1E1E) // Dark black curly curly top
                            )
                        )
                        // shaved side gradients grey
                        val pSideHairLeft = project3D(px - 0.4f, 5.8f, py)
                        val pSideHairRight = project3D(px + 0.4f, 5.8f, py)
                        if (pSideHairLeft != null && pSideHairRight != null) {
                            renderQueue.add(
                                CircleRenderTask(
                                    depth = pSideHairLeft.zDepth - 0.11f,
                                    center = pSideHairLeft,
                                    radius = hairFadeRadius * 0.6f,
                                    fillColor = Color(0xFF757575) // Shaved fade sides
                                )
                            )
                            renderQueue.add(
                                CircleRenderTask(
                                    depth = pSideHairRight.zDepth - 0.11f,
                                    center = pSideHairRight,
                                    radius = hairFadeRadius * 0.6f,
                                    fillColor = Color(0xFF757575)
                                )
                            )
                        }
                    }
                }
            }

            // ------------------ 10. DRIVEN MARKER (DIAMOND DECK FOR VEHICLES) ------------------
            if (drivenVehicleId == null && vehicles.isNotEmpty()) {
                // Draw floating green marker over closest hijackable car within range
                val closestCar = vehicles.filter { !it.isPlayerDriven && it.health > 0 }
                    .minByOrNull { sqrt((it.x - px) * (it.x - px) + (it.y - py) * (it.y - py)) }

                if (closestCar != null) {
                    val dist = sqrt((closestCar.x - px) * (closestCar.x - px) + (closestCar.y - py) * (closestCar.y - py))
                    if (dist < 40f) {
                        val pCarMarker = project3D(closestCar.x, 8.5f, closestCar.y)
                        if (pCarMarker != null) {
                            val markHeight = (12f * (300f / pCarMarker.zDepth)).coerceIn(3f, 35f)
                            renderQueue.add(
                                CircleRenderTask(
                                    depth = pCarMarker.zDepth - 0.8f,
                                    center = pCarMarker,
                                    radius = markHeight,
                                    fillColor = Color.Yellow.copy(alpha = 0.5f),
                                    strokeColor = Color.Yellow
                                )
                            )
                        }
                    }
                }
            }

            // ------------------ 11. STORY DESTINATION MARKER ------------------
            // Green classic marker glowing cylinder for Adil's home apartment
            if (!viewModel.isMissionCompleted) {
                val pMarkerApt = project3D(180f, 0f, 180f)
                if (pMarkerApt != null) {
                    val rad = (24f * (450f / pMarkerApt.zDepth)).coerceIn(4f, 90f)
                    renderQueue.add(
                        CircleRenderTask(
                            depth = pMarkerApt.zDepth + 0.15f,
                            center = pMarkerApt,
                            radius = rad,
                            fillColor = Color(0xFF4CAF50).copy(alpha = 0.45f),
                            strokeColor = Color.White
                        )
                    )
                }
            }

            // Draw all compiled and sorted tasks back to front
            renderQueue.sort()
            renderQueue.forEach { task ->
                task.draw(this)
            }
        }

        // Radar Mini-Map in bottom left corner of HUD screen
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
                .size(110.dp)
                .background(Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(10.dp))
                .border(2.dp, Color.White, shape = RoundedCornerShape(10.dp))
                .testTag("radar_minimap")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scale = 0.08f // relative zoom out
                val radCenter = size.width / 2f

                // Draw central roads lines in radar
                val majorAvs = listOf(0f, 240f, 480f, 720f, 960f, 1200f)
                for (av in majorAvs) {
                    // Vertical road translate
                    val vrx = radCenter + (av - px) * scale
                    if (vrx in 0f..size.width) {
                        drawLine(
                            color = Color.DarkGray,
                            start = Offset(vrx, 0f),
                            end = Offset(vrx, size.height),
                            strokeWidth = 4f
                        )
                    }

                    // Horizontal road translate
                    val hry = radCenter + (av - py) * scale
                    if (hry in 0f..size.height) {
                        drawLine(
                            color = Color.DarkGray,
                            start = Offset(0f, hry),
                            end = Offset(size.width, hry),
                            strokeWidth = 4f
                        )
                    }
                }

                // Draw houses/buy spots in radar
                houses.forEach { h ->
                    val rx = radCenter + (h.x - px) * scale
                    val ry = radCenter + (h.y - py) * scale
                    if (rx in 0f..size.width && ry in 0f..size.height) {
                        drawCircle(
                            color = if (h.isOwned) Color.Green else Color.Blue,
                            radius = 4f,
                            center = Offset(rx, ry)
                        )
                    }
                }

                // Draw destination target mark (apartments safety spot at 180, 180)
                if (!viewModel.isMissionCompleted) {
                    val dx = radCenter + (180f - px) * scale
                    val dy = radCenter + (180f - py) * scale
                    if (dx in 0f..size.width && dy in 0f..size.height) {
                        drawCircle(
                            color = Color.Yellow,
                            radius = 6f,
                            center = Offset(dx, dy)
                        )
                    }
                }

                // Draw players center node pointer facing angle direction
                val arrowLen = 10f
                val dx = cos(pAngle) * arrowLen
                val dy = sin(pAngle) * arrowLen
                drawLine(
                    color = Color.Red,
                    start = Offset(radCenter, radCenter),
                    end = Offset(radCenter + dx, radCenter + dy),
                    strokeWidth = 3f
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5f,
                    center = Offset(radCenter, radCenter)
                )
            }
        }
    }
}

// Retro Vintage HUD overlay representing status bars and cash
@Composable
fun RetroHUDOverlay(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val money by viewModel.money.collectAsState()
    val health by viewModel.health.collectAsState()
    val armor by viewModel.armor.collectAsState()
    val respect by viewModel.respect.collectAsState()
    val currentWeapon by viewModel.currentWeapon.collectAsState()
    val wantedLevel by viewModel.wantedLevel.collectAsState()
    val missionText by viewModel.missionText.collectAsState()

    val formattedMoney = remember(money) {
        String.format("$%08d", money)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("retro_hud_overlay")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left HUD: Health, Armor, Respect meters
            Column(
                modifier = Modifier
                    .width(170.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                // Health Bar (Sturdy Red/Crimson color)
                Text(
                    text = "HEALTH: $health%",
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                LinearProgressIndicator(
                    progress = { health / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .padding(vertical = 1.dp),
                    color = Color(0xFFD32F2F),
                    trackColor = Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Armor Bar (Clean white/silver badge style)
                Text(
                    text = "ARMOR: $armor%",
                    color = Color(0xFFECEFF1),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                LinearProgressIndicator(
                    progress = { armor / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .padding(vertical = 1.dp),
                    color = Color(0xFF90A4AE),
                    trackColor = Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Respect Bar (Bouncing yellow star meter)
                Text(
                    text = "RESPECT: $respect",
                    color = Color(0xFFFFD54F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Right HUD: Cash indicator & active weapon selector box
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Bold green digits representing bank cache stats
                Text(
                    text = formattedMoney,
                    color = Color(0xFF4CAF50),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("hud_money")
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Active Weapon Slot selection
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(6.dp))
                        .border(1.5.dp, Color(0xFFFFD54F), shape = RoundedCornerShape(6.dp))
                        .clickable { viewModel.selectNextWeapon() }
                        .testTag("weapon_slot"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = currentWeapon,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "TAP",
                            color = Color.Gray,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Wanted level star indicator
                Row {
                    for (i in 1..5) {
                        val active = i <= wantedLevel
                        Text(
                            text = "★",
                            color = if (active) Color(0xFFFF9800) else Color.DarkGray,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Mission announcement instructions (Orange vintage GTA banner)
        if (missionText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .background(Color(0xFFE65100).copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp))
                    .border(1.5.dp, Color.Yellow, shape = RoundedCornerShape(8.dp))
                    .padding(8.0.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = missionText,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.testTag("mission_banner")
                )
            }
        }
    }
}

// Virtual Gamepad Buttons overlay for controlling vehicles and player
@Composable
fun VirtualGamepadOverlay(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val drivenVehicleId by viewModel.drivenVehicleId.collectAsState()
    val currentWeapon by viewModel.currentWeapon.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        // -------- LEFT SIDE: MOBILE DYNAMIC ANALOG JOYSTICK --------
        Box(
            modifier = Modifier
                .size(130.dp)
                .testTag("joystick_pad"),
            contentAlignment = Alignment.Center
        ) {
            var dragOffset by remember { mutableStateOf(Offset.Zero) }
            val maxRadiusPx = with(LocalDensity.current) { 45.dp.toPx() }

            // Outer ring
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape)
                    .border(2.5.dp, Color.White, shape = CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { /* Start */ },
                            onDragEnd = {
                                dragOffset = Offset.Zero
                                viewModel.updateJoystick(0f, 0f)
                            },
                            onDragCancel = {
                                dragOffset = Offset.Zero
                                viewModel.updateJoystick(0f, 0f)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val raw = dragOffset + dragAmount
                                val dist = sqrt(raw.x * raw.x + raw.y * raw.y)
                                dragOffset = if (dist <= maxRadiusPx) {
                                    raw
                                } else {
                                    Offset(
                                        (raw.x / dist) * maxRadiusPx,
                                        (raw.y / dist) * maxRadiusPx
                                    )
                                }
                                val jx = dragOffset.x / maxRadiusPx
                                val jy = dragOffset.y / maxRadiusPx
                                viewModel.updateJoystick(jx, jy)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Internal guideline text
                Text(
                    text = "L  🕹️  R",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                // Drag Knob
                Box(
                    modifier = Modifier
                        .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                        .size(46.dp)
                        .background(Color.White.copy(alpha = 0.95f), shape = CircleShape)
                        .border(3.0.dp, Color.DarkGray, shape = CircleShape)
                )
            }
        }

        // -------- RIGHT SIDE: ERGONOMIC ACTION BUTTON CLUSTER --------
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Action: Buy house spot marker trigger
                GamepadButton(
                    text = "🏢 BUY",
                    tag = "btn_buy_house",
                    color = Color(0xFF1976D2),
                    width = 76f,
                    onClick = { viewModel.tryBuyClosestHouse() }
                )

                // Action: Action trigger: Hijack car doors or eject
                GamepadButton(
                    text = if (drivenVehicleId != null) "🚪 EXIT" else "🚘 STEAL",
                    tag = "btn_hijack_car",
                    color = Color(0xFFFF9800),
                    width = 86f,
                    onClick = { viewModel.hijackVehicle() }
                )
            }

            // Action: Attack/Punch trigger bullet projectile
            GamepadButton(
                text = if (currentWeapon == "FISTS") "👊 PUNCH / ATTACK" else "🔫 FIRE WEAPON",
                tag = "btn_action_fire",
                color = Color(0xFFD32F2F),
                width = 172f,
                onClick = { viewModel.fireWeapon() }
            )
        }
    }
}

@Composable
fun GamepadButton(
    text: String,
    tag: String,
    color: Color = Color.Black.copy(alpha = 0.65f),
    width: Float = 54f,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = width.dp, height = 54.dp)
            .background(color, shape = RoundedCornerShape(12.dp))
            .border(2.dp, Color.White, shape = RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}
