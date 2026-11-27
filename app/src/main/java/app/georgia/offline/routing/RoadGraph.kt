package app.georgia.offline.routing

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Read-only view of `georgia-roads.bin` (layout: `scripts/road_graph_format.py`). Works directly on a
 * memory-mapped buffer, so opening a country-sized graph costs almost no heap.
 */
class RoadGraph(buffer: ByteBuffer) {
    private val buf: ByteBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    val vertexCount: Int
    val edgeCount: Int
    val arcCount: Int
    val minLat: Double
    val minLon: Double
    val maxLat: Double
    val maxLon: Double
    private val nameCount: Int
    private val vertexOffset = HEADER_SIZE
    private val edgeOffset: Int
    private val shapeOffset: Int
    private val arcStartOffset: Int
    private val arcsOffset: Int
    private val nameOffsetsOffset: Int
    private val nameBytesOffset: Int
    private val turnRestrictionCount: Int
    private val turnStartOffset: Int
    private val turnRecordsOffset: Int

    init {
        require(buf.capacity() >= HEADER_SIZE) { "Файл графа дорог повреждён: слишком мал." }
        require(buf.get(0) == 'G'.code.toByte() && buf.get(1) == 'R'.code.toByte() && buf.get(2) == 'T'.code.toByte() && buf.get(3) == '1'.code.toByte()) {
            "Это не файл графа дорог."
        }
        require(buf.getInt(4) == FORMAT_VERSION) { "Неподдерживаемая версия графа дорог." }
        vertexCount = buf.getInt(8)
        edgeCount = buf.getInt(12)
        val shapeBytes = buf.getInt(16)
        nameCount = buf.getInt(20)
        minLat = buf.getInt(28) / 1e6
        minLon = buf.getInt(32) / 1e6
        maxLat = buf.getInt(36) / 1e6
        maxLon = buf.getInt(40) / 1e6
        arcCount = buf.getInt(44)
        edgeOffset = vertexOffset + 8 * vertexCount
        shapeOffset = edgeOffset + EDGE_SIZE * edgeCount
        arcStartOffset = shapeOffset + shapeBytes
        arcsOffset = arcStartOffset + 4 * (vertexCount + 1)
        nameOffsetsOffset = arcsOffset + 4 * arcCount
        nameBytesOffset = nameOffsetsOffset + 4 * (nameCount + 1)
        turnRestrictionCount = buf.getInt(48)
        require(turnRestrictionCount >= 0) { "Некорректное число ограничений поворота." }
        turnStartOffset = nameBytesOffset + buf.getInt(24)
        turnRecordsOffset = turnStartOffset + 4 * (arcCount + 1)
        require(turnRecordsOffset.toLong() + 8L * turnRestrictionCount <= buf.capacity()) { "Файл графа дорог обрезан." }
        require(buf.getInt(turnStartOffset + 4 * arcCount) == turnRestrictionCount) { "Индекс ограничений поворота повреждён." }
    }

    fun vertexLat(v: Int): Double = buf.getInt(vertexOffset + 8 * v) / 1e6
    fun vertexLon(v: Int): Double = buf.getInt(vertexOffset + 8 * v + 4) / 1e6

    private fun edgeBase(e: Int) = edgeOffset + EDGE_SIZE * e
    fun edgeFrom(e: Int): Int = buf.getInt(edgeBase(e))
    fun edgeTo(e: Int): Int = buf.getInt(edgeBase(e) + 4)
    fun edgeLengthMeters(e: Int): Double = buf.getInt(edgeBase(e) + 8) / 10.0
    fun edgeClass(e: Int): Int = buf.get(edgeBase(e) + 18).toInt() and 0xFF
    fun edgeFlags(e: Int): Int = buf.get(edgeBase(e) + 19).toInt() and 0xFF

    /** Signed incline in the edge's digitised direction (0) or reverse direction (1). */
    fun edgeInclinePercent(e: Int, direction: Int): Int {
        val packed = buf.getShort(edgeBase(e) + 22).toInt() and 0xFFFF
        val value = if (direction == 0) packed and 0xFF else (packed ushr 8) and 0xFF
        return if (value >= 128) value - 256 else value
    }
    fun isRoundabout(e: Int): Boolean = edgeFlags(e) and FLAG_ROUNDABOUT != 0

    /** Whether [profile] may travel edge [e] in [direction] (0 = from to to, 1 = to to from). */
    fun usable(profile: RoutingProfile, e: Int, direction: Int): Boolean =
        (buf.get(edgeBase(e) + 20 + direction).toInt() and profile.bit) != 0

    fun usableAny(profile: RoutingProfile, e: Int): Boolean =
        usable(profile, e, 0) || usable(profile, e, 1)

    fun arcStart(v: Int): Int = buf.getInt(arcStartOffset + 4 * v)
    fun arc(index: Int): Int = buf.getInt(arcsOffset + 4 * index)

    /** Enforces OSM no_* and only_* transitions after arriving along [incomingArc]. */
    fun turnAllowed(profile: RoutingProfile, incomingArc: Int, outgoingArc: Int): Boolean {
        var onlyFound = false
        var onlyAllowed = false
        var prohibited = false
        val start = buf.getInt(turnStartOffset + 4 * incomingArc)
        val end = buf.getInt(turnStartOffset + 4 * (incomingArc + 1))
        for (index in start until end) {
            val offset = turnRecordsOffset + 8 * index
            val mask = buf.get(offset + 4).toInt() and 0xFF
            if (mask and profile.bit == 0) continue
            val candidate = buf.getInt(offset)
            when (buf.get(offset + 5).toInt() and 0xFF) {
                TURN_ONLY -> {
                    onlyFound = true
                    if (candidate == outgoingArc) onlyAllowed = true
                }
                TURN_NO -> if (candidate == outgoingArc) prohibited = true
            }
        }
        return (!onlyFound || onlyAllowed) && !prohibited
    }

    /** Number of ways [profile] can leave vertex [v], counting the way it arrived by. */
    fun choicesAt(profile: RoutingProfile, v: Int): Int {
        var count = 0
        for (i in arcStart(v) until arcStart(v + 1)) {
            val arc = arc(i)
            if (usable(profile, arc ushr 1, arc and 1)) count++
        }
        return count
    }

    /** Edge geometry in the digitised direction as interleaved lat, lon degrees (endpoints included). */
    fun edgePoints(e: Int): DoubleArray {
        val base = edgeBase(e)
        val count = buf.getShort(base + 16).toInt() and 0xFFFF
        val out = DoubleArray(2 * (count + 2))
        val from = buf.getInt(base)
        val to = buf.getInt(base + 4)
        out[0] = vertexLat(from)
        out[1] = vertexLon(from)
        val shapeBase = shapeOffset + buf.getInt(base + 12)
        for (k in 0 until count) {
            out[2 + 2 * k] = buf.getInt(shapeBase + 8 * k) / 1e6
            out[3 + 2 * k] = buf.getInt(shapeBase + 8 * k + 4) / 1e6
        }
        out[2 * (count + 1)] = vertexLat(to)
        out[2 * (count + 1) + 1] = vertexLon(to)
        return out
    }

    fun edgeName(e: Int): String? {
        val index = buf.getInt(edgeBase(e) + 24)
        if (index < 0 || index >= nameCount) return null
        val start = buf.getInt(nameOffsetsOffset + 4 * index)
        val end = buf.getInt(nameOffsetsOffset + 4 * index + 4)
        val bytes = ByteArray(end - start) { buf.get(nameBytesOffset + start + it) }
        return String(bytes, Charsets.UTF_8)
    }

    companion object {
        const val HEADER_SIZE = 64
        const val EDGE_SIZE = 28
        const val FORMAT_VERSION = 3
        private const val TURN_NO = 0
        private const val TURN_ONLY = 1
        const val FLAG_UNPAVED = 1
        const val FLAG_ROUNDABOUT = 2
        const val FLAG_ROUGH = 4
        const val FLAG_SEVERE = 8
        const val FLAG_STEEP = 16
    }
}

/** A point on the road network closest to a requested location. [alongMeters] is measured from the edge start. */
class Snap(
    val edge: Int,
    val segment: Int,
    val lat: Double,
    val lon: Double,
    val alongMeters: Double,
    val distanceMeters: Double
)

/** Grid of edges by geometry (about 1 km cells), built once, used to snap locations to the nearest usable road. */
class SnapIndex(private val graph: RoadGraph) {
    private val minLat = graph.minLat - CELL
    private val minLon = graph.minLon - CELL
    private val rows = ((graph.maxLat - minLat) / CELL).toInt() + 2
    private val cols = ((graph.maxLon - minLon) / CELL).toInt() + 2
    private val cellStart = IntArray(rows * cols + 1)
    private val items: IntArray = buildItems()
    private val stamp = IntArray(graph.edgeCount)
    private var queryId = 0

    private fun buildItems(): IntArray {
        // Pass 1 counts how many edges touch each cell, pass 2 fills the cells (counting sort).
        for (e in 0 until graph.edgeCount) {
            forEachCell(graph.edgePoints(e)) { cell -> cellStart[cell + 1]++ }
        }
        for (c in 0 until rows * cols) cellStart[c + 1] += cellStart[c]
        val fill = cellStart.copyOf()
        val storage = IntArray(cellStart[rows * cols])
        for (e in 0 until graph.edgeCount) {
            forEachCell(graph.edgePoints(e)) { cell -> storage[fill[cell]++] = e }
        }
        return storage
    }

    private inline fun forEachCell(pts: DoubleArray, action: (Int) -> Unit) {
        var last = -1
        for (i in 0 until pts.size / 2) {
            val cell = cellOf(pts[2 * i], pts[2 * i + 1])
            if (cell != last) {
                last = cell
                action(cell)
            }
        }
    }

    private fun row(lat: Double) = ((lat - minLat) / CELL).toInt().coerceIn(0, rows - 1)
    private fun col(lon: Double) = ((lon - minLon) / CELL).toInt().coerceIn(0, cols - 1)
    private fun cellOf(lat: Double, lon: Double) = row(lat) * cols + col(lon)

    @Synchronized
    fun snap(profile: RoutingProfile, lat: Double, lon: Double, maxMeters: Double): Snap? {
        queryId++
        val r0 = row(lat)
        val c0 = col(lon)
        val mLat = METERS_PER_DEGREE
        val mLon = METERS_PER_DEGREE * cos(Math.toRadians(lat))
        var bestEdge = -1
        var bestSegment = 0
        var bestT = 0.0
        var bestDistance = Double.MAX_VALUE
        val maxRing = (maxMeters / MIN_CELL_METERS).toInt() + 1
        var ring = 0
        while (ring <= maxRing && !(bestEdge >= 0 && (ring - 1) * MIN_CELL_METERS > bestDistance)) {
            for (r in max(0, r0 - ring)..min(rows - 1, r0 + ring)) {
                for (c in max(0, c0 - ring)..min(cols - 1, c0 + ring)) {
                    if (max(kotlin.math.abs(r - r0), kotlin.math.abs(c - c0)) != ring) continue
                    val cell = r * cols + c
                    for (k in cellStart[cell] until cellStart[cell + 1]) {
                        val e = items[k]
                        if (stamp[e] == queryId) continue
                        stamp[e] = queryId
                        if (!graph.usableAny(profile, e)) continue
                        val pts = graph.edgePoints(e)
                        for (i in 0 until pts.size / 2 - 1) {
                            val ax = (pts[2 * i + 1] - lon) * mLon
                            val ay = (pts[2 * i] - lat) * mLat
                            val bx = (pts[2 * i + 3] - lon) * mLon
                            val by = (pts[2 * i + 2] - lat) * mLat
                            val dx = bx - ax
                            val dy = by - ay
                            val denom = dx * dx + dy * dy
                            val t = if (denom == 0.0) 0.0 else (-(ax * dx + ay * dy) / denom).coerceIn(0.0, 1.0)
                            val d = hypot(ax + dx * t, ay + dy * t)
                            if (d < bestDistance) {
                                bestDistance = d
                                bestEdge = e
                                bestSegment = i
                                bestT = t
                            }
                        }
                    }
                }
            }
            ring++
        }
        if (bestEdge < 0 || bestDistance > maxMeters) return null
        val pts = graph.edgePoints(bestEdge)
        val segments = pts.size / 2 - 1
        val lengths = DoubleArray(segments) {
            hypot((pts[2 * it + 3] - pts[2 * it + 1]) * mLon, (pts[2 * it + 2] - pts[2 * it]) * mLat)
        }
        val total = lengths.sum()
        var along = 0.0
        for (i in 0 until bestSegment) along += lengths[i]
        along += lengths[bestSegment] * bestT
        val scale = if (total > 0.0) graph.edgeLengthMeters(bestEdge) / total else 1.0
        val a = bestSegment
        val plat = pts[2 * a] + (pts[2 * a + 2] - pts[2 * a]) * bestT
        val plon = pts[2 * a + 1] + (pts[2 * a + 3] - pts[2 * a + 1]) * bestT
        return Snap(bestEdge, bestSegment, plat, plon, along * scale, bestDistance)
    }

    private companion object {
        const val CELL = 0.01
        const val METERS_PER_DEGREE = 111_195.0
        const val MIN_CELL_METERS = 750.0
    }
}
