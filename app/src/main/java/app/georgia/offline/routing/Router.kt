package app.georgia.offline.routing

import app.georgia.offline.domain.GeoMath
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/**
 * A* road router on [RoadGraph]. Start and end are snapped onto the nearest usable edge, so a
 * route can begin and finish in the middle of a long road. Turn restrictions are not modelled.
 */
class Router(private val graph: RoadGraph) {
    private val index: SnapIndex by lazy { SnapIndex(graph) }

    /** Builds the snapping grid ahead of the first route (takes about a second on a phone). */
    fun prepare() {
        index
    }

    private class Piece(val edge: Int, val direction: Int, val points: DoubleArray, val endVertex: Int)

    @Synchronized
    fun route(profile: RoutingProfile, lat1: Double, lon1: Double, lat2: Double, lon2: Double): RouteResult {
        val a = index.snap(profile, lat1, lon1, MAX_SNAP_METERS) ?: return RouteResult.failed(RouteFailure.NO_ROAD_NEAR_START)
        val b = index.snap(profile, lat2, lon2, MAX_SNAP_METERS) ?: return RouteResult.failed(RouteFailure.NO_ROAD_NEAR_END)
        val ea = a.edge
        val eb = b.edge
        val speedA = speed(profile, ea)
        val speedB = speed(profile, eb)
        val lengthA = graph.edgeLengthMeters(ea)
        val lengthB = graph.edgeLengthMeters(eb)

        // The start point sits inside edge ea: it can leave towards either end the profile may use.
        var seedToVertex = -1
        var seedToCost = Double.MAX_VALUE
        var seedFromVertex = -1
        var seedFromCost = Double.MAX_VALUE
        if (graph.usable(profile, ea, 0)) {
            seedToVertex = graph.edgeTo(ea)
            seedToCost = (lengthA - a.alongMeters) / speedA
        }
        if (graph.usable(profile, ea, 1)) {
            seedFromVertex = graph.edgeFrom(ea)
            seedFromCost = a.alongMeters / speedA
        }
        // The end point sits inside edge eb: it is reached from one of its ends.
        var targetFromVertex = -1 // reached at `from`, then driven forward to the point
        var targetFromCost = 0.0
        var targetToVertex = -1 // reached at `to`, then driven backward to the point
        var targetToCost = 0.0
        if (graph.usable(profile, eb, 0)) {
            targetFromVertex = graph.edgeFrom(eb)
            targetFromCost = b.alongMeters / speedB
        }
        if (graph.usable(profile, eb, 1)) {
            targetToVertex = graph.edgeTo(eb)
            targetToCost = (lengthB - b.alongMeters) / speedB
        }

        // Both points on the same edge: driving straight along it may beat any detour.
        var sameEdgeSeconds = Double.MAX_VALUE
        var sameEdgeDirection = 0
        if (ea == eb) {
            val delta = b.alongMeters - a.alongMeters
            if (delta >= 0 && graph.usable(profile, ea, 0)) {
                sameEdgeSeconds = delta / speedA
                sameEdgeDirection = 0
            } else if (delta <= 0 && graph.usable(profile, ea, 1)) {
                sameEdgeSeconds = -delta / speedA
                sameEdgeDirection = 1
            }
        }

        val n = graph.vertexCount
        val g = DoubleArray(n) { Double.MAX_VALUE }
        val parentVertex = IntArray(n) { -1 }
        val parentArc = IntArray(n) { -1 }
        val closed = BooleanArray(n)
        val heap = MinHeap()
        val goalLat = b.lat
        val goalLon = b.lon
        val mLon = METERS_PER_DEGREE * cos(Math.toRadians(goalLat))
        fun heuristic(v: Int): Double =
            hypot((graph.vertexLon(v) - goalLon) * mLon, (graph.vertexLat(v) - goalLat) * METERS_PER_DEGREE) * 0.995 / profile.maxSpeedMps

        if (seedToVertex >= 0) {
            g[seedToVertex] = seedToCost
            heap.push(seedToCost + heuristic(seedToVertex), seedToVertex)
        }
        if (seedFromVertex >= 0 && seedFromCost < g[seedFromVertex]) {
            g[seedFromVertex] = seedFromCost
            heap.push(seedFromCost + heuristic(seedFromVertex), seedFromVertex)
        }

        var bestTotal = Double.MAX_VALUE
        var bestVertex = -1
        var bestSide = 0
        if ((targetFromVertex >= 0 || targetToVertex >= 0) && (seedToVertex >= 0 || seedFromVertex >= 0)) {
            while (heap.size > 0) {
                if (heap.peekKey() >= bestTotal) break
                val u = heap.pop()
                if (closed[u]) continue
                closed[u] = true
                val gu = g[u]
                if (u == targetFromVertex && gu + targetFromCost < bestTotal) {
                    bestTotal = gu + targetFromCost
                    bestVertex = u
                    bestSide = 0
                }
                if (u == targetToVertex && gu + targetToCost < bestTotal) {
                    bestTotal = gu + targetToCost
                    bestVertex = u
                    bestSide = 1
                }
                for (i in graph.arcStart(u) until graph.arcStart(u + 1)) {
                    val arc = graph.arc(i)
                    val e = arc ushr 1
                    val direction = arc and 1
                    if (!graph.usable(profile, e, direction)) continue
                    val w = if (direction == 0) graph.edgeTo(e) else graph.edgeFrom(e)
                    if (closed[w]) continue
                    val ng = gu + graph.edgeLengthMeters(e) / speed(profile, e)
                    if (ng < g[w]) {
                        g[w] = ng
                        parentVertex[w] = u
                        parentArc[w] = arc
                        heap.push(ng + heuristic(w), w)
                    }
                }
            }
        }

        val pieces = ArrayList<Piece>()
        val seconds: Double
        if (sameEdgeSeconds <= bestTotal && sameEdgeSeconds != Double.MAX_VALUE) {
            seconds = sameEdgeSeconds
            val pts = graph.edgePoints(ea)
            val buffer = PointBuffer()
            buffer.add(a.lat, a.lon)
            if (sameEdgeDirection == 0) {
                for (i in a.segment + 1..b.segment) buffer.add(pts[2 * i], pts[2 * i + 1])
            } else {
                for (i in a.segment downTo b.segment + 1) buffer.add(pts[2 * i], pts[2 * i + 1])
            }
            buffer.add(b.lat, b.lon)
            pieces.add(Piece(ea, sameEdgeDirection, buffer.toArray(), -1))
        } else if (bestVertex >= 0) {
            seconds = bestTotal
            val chain = ArrayList<Int>()
            var v = bestVertex
            while (parentArc[v] != -1) {
                chain.add(parentArc[v])
                v = parentVertex[v]
            }
            chain.reverse()
            val firstVertex = v
            val startDirection = if (firstVertex == seedToVertex && (firstVertex != seedFromVertex || seedToCost <= seedFromCost)) 0 else 1

            // Start piece: from the snapped start point to the first vertex.
            run {
                val pts = graph.edgePoints(ea)
                val buffer = PointBuffer()
                buffer.add(a.lat, a.lon)
                if (startDirection == 0) {
                    for (i in a.segment + 1 until pts.size / 2) buffer.add(pts[2 * i], pts[2 * i + 1])
                } else {
                    for (i in a.segment downTo 0) buffer.add(pts[2 * i], pts[2 * i + 1])
                }
                pieces.add(Piece(ea, startDirection, buffer.toArray(), firstVertex))
            }
            // Whole edges between the start and end edges.
            for (arc in chain) {
                val e = arc ushr 1
                val direction = arc and 1
                val pts = graph.edgePoints(e)
                val buffer = PointBuffer()
                if (direction == 0) {
                    for (i in 0 until pts.size / 2) buffer.add(pts[2 * i], pts[2 * i + 1])
                } else {
                    for (i in pts.size / 2 - 1 downTo 0) buffer.add(pts[2 * i], pts[2 * i + 1])
                }
                pieces.add(Piece(e, direction, buffer.toArray(), if (direction == 0) graph.edgeTo(e) else graph.edgeFrom(e)))
            }
            // End piece: from the last vertex to the snapped end point.
            run {
                val pts = graph.edgePoints(eb)
                val buffer = PointBuffer()
                val direction = bestSide // 0: arrived at `from`, travelling forward; 1: arrived at `to`, travelling backward
                if (direction == 0) {
                    for (i in 0..b.segment) buffer.add(pts[2 * i], pts[2 * i + 1])
                } else {
                    for (i in pts.size / 2 - 1 downTo b.segment + 1) buffer.add(pts[2 * i], pts[2 * i + 1])
                }
                buffer.add(b.lat, b.lon)
                pieces.add(Piece(eb, direction, buffer.toArray(), -1))
            }
        } else {
            return RouteResult.failed(RouteFailure.NO_PATH)
        }

        // Join the pieces; each later piece repeats the junction point, which is dropped.
        val all = PointBuffer()
        for ((pieceIndex, piece) in pieces.withIndex()) {
            val first = if (pieceIndex == 0) 0 else 1
            for (i in first until piece.points.size / 2) all.add(piece.points[2 * i], piece.points[2 * i + 1])
        }
        val steps = buildSteps(profile, pieces)
        return RouteResult.found(Route(profile, all.toArray(), seconds, steps))
    }

    private fun speed(profile: RoutingProfile, e: Int): Double = profile.speedMps(graph.edgeClass(e), graph.edgeFlags(e))

    private fun pieceLength(points: DoubleArray): Double {
        var total = 0.0
        for (i in 1 until points.size / 2) {
            total += GeoMath.distanceMeters(points[2 * i - 2], points[2 * i - 1], points[2 * i], points[2 * i + 1])
        }
        return total
    }

    /** Heading of the last stretch of a piece, ignoring sub-8 m wiggles at snapped ends. */
    private fun headingAtEnd(points: DoubleArray): Double {
        val last = points.size / 2 - 1
        var j = last - 1
        while (j > 0 && GeoMath.distanceMeters(points[2 * j], points[2 * j + 1], points[2 * last], points[2 * last + 1]) < 8.0) j--
        return GeoMath.bearingDegrees(points[2 * j], points[2 * j + 1], points[2 * last], points[2 * last + 1])
    }

    private fun headingAtStart(points: DoubleArray): Double {
        val last = points.size / 2 - 1
        var j = 1
        while (j < last && GeoMath.distanceMeters(points[0], points[1], points[2 * j], points[2 * j + 1]) < 8.0) j++
        return GeoMath.bearingDegrees(points[0], points[1], points[2 * j], points[2 * j + 1])
    }

    private fun hasExitFromRoundabout(profile: RoutingProfile, vertex: Int): Boolean {
        for (i in graph.arcStart(vertex) until graph.arcStart(vertex + 1)) {
            val arc = graph.arc(i)
            val e = arc ushr 1
            if (graph.usable(profile, e, arc and 1) && !graph.isRoundabout(e)) return true
        }
        return false
    }

    private fun buildSteps(profile: RoutingProfile, pieces: List<Piece>): List<RouteStep> {
        val steps = ArrayList<RouteStep>()
        val firstName = graph.edgeName(pieces[0].edge)
        steps.add(
            RouteStep(
                Maneuver.START,
                if (firstName != null) "Начните движение по $firstName" else "Начните движение",
                0.0, pieces[0].points[0], pieces[0].points[1]
            )
        )
        var distance = 0.0
        var inRoundabout = false
        var exits = 0
        var entryDistance = 0.0
        var entryLat = 0.0
        var entryLon = 0.0
        for (i in 0 until pieces.size - 1) {
            val from = pieces[i]
            val to = pieces[i + 1]
            distance += pieceLength(from.points)
            val junctionLat = from.points[from.points.size - 2]
            val junctionLon = from.points[from.points.size - 1]
            val fromRound = graph.isRoundabout(from.edge)
            val toRound = graph.isRoundabout(to.edge)
            val vertex = from.endVertex
            if (!fromRound && toRound) {
                inRoundabout = true
                exits = 0
                entryDistance = distance
                entryLat = junctionLat
                entryLon = junctionLon
                continue
            }
            if (fromRound && toRound) {
                if (vertex >= 0 && hasExitFromRoundabout(profile, vertex)) exits++
                continue
            }
            if (fromRound && !toRound) {
                exits++
                if (inRoundabout) {
                    val name = graph.edgeName(to.edge)
                    steps.add(
                        RouteStep(
                            Maneuver.ROUNDABOUT,
                            "На кольце выезжайте на $exits-й съезд" + (if (name != null) " на $name" else ""),
                            entryDistance, entryLat, entryLon
                        )
                    )
                }
                inRoundabout = false
                continue
            }
            if (vertex < 0) continue
            val angle = ((headingAtStart(to.points) - headingAtEnd(from.points) + 540.0) % 360.0) - 180.0
            val size = abs(angle)
            if (graph.choicesAt(profile, vertex) < 3 || size < 25.0) continue
            val right = angle > 0
            val maneuver = when {
                size < 60.0 -> if (right) Maneuver.SLIGHT_RIGHT else Maneuver.SLIGHT_LEFT
                size < 135.0 -> if (right) Maneuver.RIGHT else Maneuver.LEFT
                size < 170.0 -> if (right) Maneuver.SHARP_RIGHT else Maneuver.SHARP_LEFT
                else -> Maneuver.UTURN
            }
            val phrase = when (maneuver) {
                Maneuver.SLIGHT_RIGHT -> "Держитесь правее"
                Maneuver.SLIGHT_LEFT -> "Держитесь левее"
                Maneuver.RIGHT -> "Поверните направо"
                Maneuver.LEFT -> "Поверните налево"
                Maneuver.SHARP_RIGHT -> "Резко направо"
                Maneuver.SHARP_LEFT -> "Резко налево"
                else -> "Развернитесь"
            }
            val name = graph.edgeName(to.edge)
            steps.add(RouteStep(maneuver, phrase + (if (name != null) " на $name" else ""), distance, junctionLat, junctionLon))
        }
        val last = pieces.last()
        distance += pieceLength(last.points)
        steps.add(
            RouteStep(
                Maneuver.ARRIVE, "Вы прибыли в пункт назначения", distance,
                last.points[last.points.size - 2], last.points[last.points.size - 1]
            )
        )
        return steps
    }

    private class PointBuffer {
        private var data = DoubleArray(64)
        private var size = 0

        fun add(lat: Double, lon: Double) {
            if (size + 2 > data.size) data = data.copyOf(max(data.size * 2, size + 2))
            data[size++] = lat
            data[size++] = lon
        }

        fun toArray(): DoubleArray = data.copyOf(size)
    }

    /** Binary min-heap of (key, vertex); stale duplicates are skipped by the caller. */
    private class MinHeap {
        private var keys = DoubleArray(1024)
        private var values = IntArray(1024)
        var size = 0
            private set

        fun peekKey(): Double = keys[0]

        fun push(key: Double, value: Int) {
            if (size == keys.size) {
                keys = keys.copyOf(size * 2)
                values = values.copyOf(size * 2)
            }
            var i = size++
            while (i > 0) {
                val parent = (i - 1) / 2
                if (keys[parent] <= key) break
                keys[i] = keys[parent]
                values[i] = values[parent]
                i = parent
            }
            keys[i] = key
            values[i] = value
        }

        fun pop(): Int {
            val top = values[0]
            size--
            if (size > 0) {
                val key = keys[size]
                val value = values[size]
                var i = 0
                while (true) {
                    var child = 2 * i + 1
                    if (child >= size) break
                    if (child + 1 < size && keys[child + 1] < keys[child]) child++
                    if (keys[child] >= key) break
                    keys[i] = keys[child]
                    values[i] = values[child]
                    i = child
                }
                keys[i] = key
                values[i] = value
            }
            return top
        }
    }

    private companion object {
        const val MAX_SNAP_METERS = 5_000.0
        const val METERS_PER_DEGREE = 111_195.0
    }
}
