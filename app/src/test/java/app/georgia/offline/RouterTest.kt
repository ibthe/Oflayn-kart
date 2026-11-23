package app.georgia.offline

import app.georgia.offline.domain.GeoMath
import app.georgia.offline.routing.Maneuver
import app.georgia.offline.routing.RoadGraph
import app.georgia.offline.routing.Route
import app.georgia.offline.routing.RouteFailure
import app.georgia.offline.routing.Router
import app.georgia.offline.routing.RoutingProfile
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the Kotlin router on a tiny graph written by the Python builder, so the file format is tested end to end. */
class RouterTest {
    private val graph: RoadGraph by lazy {
        val bytes = javaClass.classLoader!!.getResourceAsStream("tiny-roads.bin")!!.readBytes()
        RoadGraph(ByteBuffer.wrap(bytes))
    }
    private val router: Router by lazy { Router(graph) }

    private fun route(profile: RoutingProfile, lat1: Double, lon1: Double, lat2: Double, lon2: Double): Route {
        val result = router.route(profile, lat1, lon1, lat2, lon2)
        org.junit.Assert.assertNotNull("route failed: ${result.failure}", result.route)
        return result.route!!
    }

    @Test
    fun readsTheFileWrittenByThePythonBuilder() {
        assertEquals(5, graph.vertexCount)
        assertEquals(5, graph.edgeCount)
        assertEquals(10, graph.arcCount)
    }

    @Test
    fun straightRouteThroughTheJunction() {
        val route = route(RoutingProfile.CAR, 41.7001, 44.8002, 41.6999, 44.8198)
        val expected = GeoMath.distanceMeters(41.7, 44.8002, 41.7, 44.8198)
        assertEquals(expected, route.distanceMeters, 20.0)
        assertEquals(Maneuver.START, route.steps.first().maneuver)
        assertEquals(Maneuver.ARRIVE, route.steps.last().maneuver)
    }

    @Test
    fun turnLeftAtTheJunctionIsAnnouncedWithTheStreetName() {
        val route = route(RoutingProfile.CAR, 41.7001, 44.8002, 41.705, 44.810)
        val turn = route.steps.firstOrNull { it.maneuver == Maneuver.LEFT }
        org.junit.Assert.assertNotNull(turn)
        assertTrue(turn!!.text, turn.text.contains("налево") && turn.text.contains("Cross street"))
        assertEquals(830.0, turn.distanceFromStartMeters, 40.0)
    }

    @Test
    fun oneWayRoadForcesACarDetourButNotAPedestrian() {
        val carBack = route(RoutingProfile.CAR, 41.7099, 44.8101, 41.7, 44.8199)
        val carForward = route(RoutingProfile.CAR, 41.7, 44.8199, 41.7099, 44.8101)
        val foot = route(RoutingProfile.FOOT, 41.7099, 44.8101, 41.7, 44.8199)
        assertTrue("car forward ${carForward.distanceMeters}", carForward.distanceMeters < 1500.0)
        assertTrue("car back ${carBack.distanceMeters}", carBack.distanceMeters > 1800.0)
        assertTrue("foot ${foot.distanceMeters}", foot.distanceMeters < 1500.0)
    }

    @Test
    fun routeOnASingleEdge() {
        val route = route(RoutingProfile.CAR, 41.7, 44.8020, 41.7, 44.8060)
        assertEquals(GeoMath.distanceMeters(41.7, 44.802, 41.7, 44.806), route.distanceMeters, 10.0)
    }

    @Test
    fun farFromAnyRoadIsReportedNotRouted() {
        val result = router.route(RoutingProfile.CAR, 42.5, 44.8, 41.7, 44.81)
        assertNull(result.route)
        assertEquals(RouteFailure.NO_ROAD_NEAR_START, result.failure)
    }

    @Test
    fun projectionFollowsTheRoute() {
        val route = route(RoutingProfile.CAR, 41.7001, 44.8002, 41.6999, 44.8198)
        val onRoute = route.project(41.7, 44.81)
        assertTrue(onRoute.offRouteMeters < 15.0)
        assertEquals(route.distanceMeters / 2, onRoute.alongMeters, route.distanceMeters * 0.1)
        val off = route.project(41.7060, 44.81)
        assertTrue(off.offRouteMeters > 500.0)
    }
}
