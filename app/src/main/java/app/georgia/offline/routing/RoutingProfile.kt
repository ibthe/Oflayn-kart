package app.georgia.offline.routing

import kotlin.math.max

// Top-level on purpose: a companion object of an enum is not yet initialised when the entries are built.
private const val CLASS_COUNT = 22

/**
 * How the person travels. [bit] matches the access masks stored in the road graph and the speed
 * tables mirror `scripts/road_graph_format.py`, which is the specification of the data file.
 */
enum class RoutingProfile(
    val bit: Int,
    val label: String,
    val maxSpeedMps: Double,
    private val unpavedFactor: Double,
    kmhByClass: Map<Int, Int>
) {
    CAR(
        1, "Авто", 25.0, 0.6,
        mapOf(1 to 90, 2 to 50, 3 to 75, 4 to 45, 5 to 60, 6 to 40, 7 to 50, 8 to 35, 9 to 40, 10 to 30, 11 to 35, 12 to 25, 13 to 10, 14 to 15, 15 to 15, 21 to 30)
    ),
    OFFROAD(
        2, "4×4", 25.0, 0.85,
        mapOf(1 to 90, 2 to 50, 3 to 75, 4 to 45, 5 to 60, 6 to 40, 7 to 50, 8 to 35, 9 to 40, 10 to 30, 11 to 35, 12 to 25, 13 to 10, 14 to 15, 15 to 20, 21 to 30)
    ),
    BIKE(
        4, "Велосипед", 5.0, 0.7,
        mapOf(3 to 14, 4 to 14, 5 to 15, 6 to 15, 7 to 16, 8 to 16, 9 to 17, 10 to 17, 11 to 16, 12 to 17, 13 to 15, 14 to 15, 15 to 12, 16 to 10, 17 to 12, 18 to 18, 21 to 15)
    ),
    FOOT(
        8, "Пешком", 1.5, 0.9,
        (3..21).associateWith { 5 } + mapOf(16 to 4, 19 to 3)
    );

    private val pavedMps = DoubleArray(CLASS_COUNT) { (kmhByClass[it] ?: 10) / 3.6 }
    private val unpavedMps = DoubleArray(CLASS_COUNT) { max((kmhByClass[it] ?: 10) * unpavedFactor, 3.0) / 3.6 }

    fun speedMps(roadClass: Int, flags: Int): Double {
        val index = if (roadClass in 0 until CLASS_COUNT) roadClass else 0
        return if (flags and RoadGraph.FLAG_UNPAVED != 0) unpavedMps[index] else pavedMps[index]
    }
}
