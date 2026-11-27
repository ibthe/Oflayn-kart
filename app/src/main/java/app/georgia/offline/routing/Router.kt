package app.georgia.offline.routing

import app.georgia.offline.domain.GeoMath
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/**
 * A* road router on [RoadGraph]. Start and end are snapped onto the nearest usable edge, so a
 * route can begin and finish in the middle of a long road. Node-via OSM turn restrictions are enforced.
 */
class Router(private val graph: RoadGraph) {
    private val index: SnapIndex by lazy { SnapIndex(graph) }

    /** Builds the snapping grid ahead of the first route (takes about a second on a phone). */
    fun prepare() {
        index
    }

    private class Piece(val edge: Int, val direction: Int, val points: DoubleArray, val endVertex: Int)
    private class Target(val arc: Int, val cost: Double)

    /** Snap a user-edited waypoint to the nearest road usable by the selected profile. */
    @Synchronized
    fun snapToRoad(profile: RoutingProfile, latitude: Double, longitude: Double): Snap? =
        index.snap(profile, latitude, longitude, MAX_SNAP_METERS)

    /**
     * Routes through an ordered list of points. Each adjacent pair is routed independently and
     * the resulting geometry/instructions are merged into one continuous route.
     */
    @Synchronized
    fun route(profile: RoutingProfile, points: List<Pair<Double, Double>>): RouteResult {
        if (points.size < 2) return RouteResult.failed(RouteFailure.NO_PATH)
        val geometry = ArrayList<Double>()
        val steps = ArrayList<RouteStep>()
        var duration = 0.0
        var distanceOffset = 0.0
        val warnings = linkedSetOf<String>()
        for (legIndex in 0 until points.lastIndex) {
            val from = points[legIndex]
            val to = points[legIndex + 1]
            val result = route(profile, from.first, from.second, to.first, to.second)
            val leg = result.route ?: return RouteResult.failed(result.failure ?: RouteFailure.NO_PATH)
            warnings.addAll(leg.warnings)
            val firstPoint = if (legIndex == 0) 0 else 1
            for (i in firstPoint until leg.pointCount) {
                geometry.add(leg.lat(i))
                geometry.add(leg.lon(i))
            }
            for (step in leg.steps) {
                if (legIndex > 0 && step.maneuver == Maneuver.START) continue
                if (legIndex < points.lastIndex - 1 && step.maneuver == Maneuver.ARRIVE) continue
                steps.add(RouteStep(
                    step.maneuver,
                    step.text,
                    distanceOffset + step.distanceFromStartMeters,
                    step.lat,
                    step.lon
                ))
            }
            distanceOffset += leg.distanceMeters
            duration += leg.durationSeconds
            if (legIndex < points.lastIndex - 1) {
                steps.add(RouteStep(
                    Maneuver.WAYPOINT,
                    "Промежуточная точка",
                    distanceOffset,
                    leg.lat(leg.pointCount - 1),
                    leg.lon(leg.pointCount - 1)
                ))
            }
        }
        return RouteResult.found(Route(profile, geometry.toDoubleArray(), duration, steps, warnings.toList()))
    }

    @Synchronized
    fun route(profile: RoutingProfile, lat1: Double, lon1: Double, lat2: Double, lon2: Double): RouteResult {
        val a = index.snap(profile, lat1, lon1, MAX_SNAP_METERS) ?: return RouteResult.failed(RouteFailure.NO_ROAD_NEAR_START)
        val b = index.snap(profile, lat2, lon2, MAX_SNAP_METERS) ?: return RouteResult.failed(RouteFailure.NO_ROAD_NEAR_END)
        val ea = a.edge
        val eb = b.edge
        val speedAForward = speed(profile, ea, 0)
        val speedABackward = speed(profile, ea, 1)
        val speedBForward = speed(profile, eb, 0)
        val speedBBackward = speed(profile, eb, 1)
        val lengthA = graph.edgeLengthMeters(ea)
        val lengthB = graph.edgeLengthMeters(eb)

        // Search state is the directed edge just traversed, not merely the vertex.
        // This is essential: OSM turn restrictions depend on the incoming road.
        val seedForwardArc = ea shl 1
        val seedBackwardArc = seedForwardArc or 1
        val targetForwardArc = eb shl 1
        val targetBackwardArc = targetForwardArc or 1

        var seedForwardCost = Double.MAX_VALUE
        var seedBackwardCost = Double.MAX_VALUE
        if (graph.usable(profile, ea, 0)) seedForwardCost = (lengthA - a.alongMeters) / speedAForward
        if (graph.usable(profile, ea, 1)) seedBackwardCost = a.alongMeters / speedABackward

        var targetFromVertex = -1 // arrive at from endpoint, then travel forward along eb
        var targetFromCost = 0.0
        var targetToVertex = -1 // arrive at to endpoint, then travel backward along eb
        var targetToCost = 0.0
        if (graph.usable(profile, eb, 0)) {
            targetFromVertex = graph.edgeFrom(eb)
            targetFromCost = b.alongMeters / speedBForward
        }
        if (graph.usable(profile, eb, 1)) {
            targetToVertex = graph.edgeTo(eb)
            targetToCost = (lengthB - b.alongMeters) / speedBBackward
        }

        // A same-edge trip has no junction transition, so it is unaffected by turn restrictions.
        var sameEdgeSeconds = Double.MAX_VALUE
        var sameEdgeDirection = 0
        if (ea == eb) {
            val delta = b.alongMeters - a.alongMeters
            if (delta >= 0 && graph.usable(profile, ea, 0)) {
                sameEdgeSeconds = delta / speedAForward
                sameEdgeDirection = 0
            } else if (delta <= 0 && graph.usable(profile, ea, 1)) {
                sameEdgeSeconds = -delta / speedABackward
                sameEdgeDirection = 1
            }
        }

        val stateCount = graph.arcCount
        val g = DoubleArray(stateCount) { Double.MAX_VALUE }
        val parentState = IntArray(stateCount) { -1 }
        val closed = BooleanArray(stateCount)
        val heap = MinHeap()
        val goalLat = b.lat
        val goalLon = b.lon
        val mLon = METERS_PER_DEGREE * cos(Math.toRadians(goalLat))
        fun endVertex(arc: Int): Int {
            val edge = arc ushr 1
            return if ((arc and 1) == 0) graph.edgeTo(edge) else graph.edgeFrom(edge)
        }
        fun heuristic(vertex: Int): Double =
            hypot((graph.vertexLon(vertex) - goalLon) * mLon, (graph.vertexLat(vertex) - goalLat) * METERS_PER_DEGREE) *
                0.995 / profile.maxSpeedMps

        if (seedForwardCost < Double.MAX_VALUE) {
            g[seedForwardArc] = seedForwardCost
            heap.push(seedForwardCost + heuristic(endVertex(seedForwardArc)), seedForwardArc)
        }
        if (seedBackwardCost < g[seedBackwardArc]) {
            g[seedBackwardArc] = seedBackwardCost
            heap.push(seedBackwardCost + heuristic(endVertex(seedBackwardArc)), seedBackwardArc)
        }

        var bestTotal = Double.MAX_VALUE
        var bestState = -1
        var bestSide = 0
        if ((targetFromVertex >= 0 || targetToVertex >= 0) && heap.size > 0) {
            while (heap.size > 0) {
                if (heap.peekKey() >= bestTotal) break
                val incomingArc = heap.pop()
                if (closed[incomingArc]) continue
                closed[incomingArc] = true
                val u = endVertex(incomingArc)
                val gu = g[incomingArc]

                if (u == targetFromVertex && graph.turnAllowed(profile, incomingArc, targetForwardArc) &&
                    gu + targetFromCost < bestTotal
                ) {
                    bestTotal = gu + targetFromCost
                    bestState = incomingArc
                    bestSide = 0
                }
                if (u == targetToVertex && graph.turnAllowed(profile, incomingArc, targetBackwardArc) &&
                    gu + targetToCost < bestTotal
                ) {
                    bestTotal = gu + targetToCost
                    bestState = incomingArc
                    bestSide = 1
                }

                for (i in graph.arcStart(u) until graph.arcStart(u + 1)) {
                    val outgoingArc = graph.arc(i)
                    val edge = outgoingArc ushr 1
                    val direction = outgoingArc and 1
                    if (!graph.usable(profile, edge, direction) ||
                        !graph.turnAllowed(profile, incomingArc, outgoingArc) ||
                        closed[outgoingArc]
                    ) continue
                    val nextVertex = endVertex(outgoingArc)
                    val nextCost = gu + graph.edgeLengthMeters(edge) / speed(profile, edge, direction)
                    if (nextCost < g[outgoingArc]) {
                        g[outgoingArc] = nextCost
                        parentState[outgoingArc] = incomingArc
                        heap.push(nextCost + heuristic(nextVertex), outgoingArc)
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
        } else if (bestState >= 0) {
            seconds = bestTotal
            val chain = ArrayList<Int>()
            var state = bestState
            while (state >= 0) {
                chain.add(state)
                state = parentState[state]
            }
            chain.reverse()
            val firstArc = chain.first()
            val startDirection = firstArc and 1

            // Start piece: from the snapped start point to the endpoint reached by the seed arc.
            run {
                val pts = graph.edgePoints(ea)
                val buffer = PointBuffer()
                buffer.add(a.lat, a.lon)
                if (startDirection == 0) {
                    for (i in a.segment + 1 until pts.size / 2) buffer.add(pts[2 * i], pts[2 * i + 1])
                } else {
                    for (i in a.segment downTo 0) buffer.add(pts[2 * i], pts[2 * i + 1])
                }
                pieces.add(Piece(ea, startDirection, buffer.toArray(), endVertex(firstArc)))
            }
            // The first arc is already represented by the start piece.
            for (arc in chain.drop(1)) {
                val edge = arc ushr 1
                val direction = arc and 1
                val pts = graph.edgePoints(edge)
                val buffer = PointBuffer()
                if (direction == 0) {
                    for (i in 0 until pts.size / 2) buffer.add(pts[2 * i], pts[2 * i + 1])
                } else {
                    for (i in pts.size / 2 - 1 downTo 0) buffer.add(pts[2 * i], pts[2 * i + 1])
                }
                pieces.add(Piece(edge, direction, buffer.toArray(), endVertex(arc)))
            }
            // End piece follows the checked transition onto the destination edge.
            run {
                val pts = graph.edgePoints(eb)
                val buffer = PointBuffer()
                if (bestSide == 0) {
                    for (i in 0..b.segment) buffer.add(pts[2 * i], pts[2 * i + 1])
                } else {
                    for (i in pts.size / 2 - 1 downTo b.segment + 1) buffer.add(pts[2 * i], pts[2 * i + 1])
                }
                buffer.add(b.lat, b.lon)
                pieces.add(Piece(eb, bestSide, buffer.toArray(), -1))
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
        val usedFlags = pieces.fold(0) { flags, piece -> flags or graph.edgeFlags(piece.edge) }
        val warnings = buildList {
            if (usedFlags and RoadGraph.FLAG_UNPAVED != 0) {
                add("Маршрут включает грунтовые или неасфальтированные участки.")
            }
            if (usedFlags and RoadGraph.FLAG_ROUGH != 0) {
                add("Есть участки с плохим качеством покрытия.")
            }
            if (usedFlags and RoadGraph.FLAG_SEVERE != 0) {
                add("Есть очень сложные участки. Даже 4×4 не гарантирует фактическую проходимость.")
            }
            if (usedFlags and RoadGraph.FLAG_STEEP != 0) {
                add("Есть участки с крутым уклоном; расчётное время увеличено с учётом рельефа.")
            }
        }
        return RouteResult.found(Route(profile, all.toArray(), seconds, steps, warnings))
    }

    private fun speed(profile: RoutingProfile, e: Int, direction: Int): Double =
        profile.speedMps(graph.edgeClass(e), graph.edgeFlags(e), graph.edgeInclinePercent(e, direction))

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

    private fun hasExitFromRoundabout(profile: RoutingProfile, vertex: Int, incomingArc: Int): Boolean {
        for (i in graph.arcStart(vertex) until graph.arcStart(vertex + 1)) {
            val arc = graph.arc(i)
            val e = arc ushr 1
            if (graph.usable(profile, e, arc and 1) && graph.turnAllowed(profile, incomingArc, arc) && !graph.isRoundabout(e)) return true
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
                if (vertex >= 0 && hasExitFromRoundabout(profile, vertex, (from.edge shl 1) or from.direction)) exits++
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
            val incomingArc = (from.edge shl 1) or from.direction
            if (graph.choicesAt(profile, incomingArc, vertex) < 3 || size < 25.0) continue
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

    /** Binary min-heap of (key, directed incoming arc); stale duplicates are skipped by the caller. */
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
