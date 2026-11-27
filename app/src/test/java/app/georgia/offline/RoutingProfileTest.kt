package app.georgia.offline

import app.georgia.offline.routing.RoadGraph
import app.georgia.offline.routing.RoutingProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingProfileTest {
    @Test
    fun roughAndSevereSurfaceFlagsReduceEstimatedSpeed() {
        val paved = RoutingProfile.OFFROAD.speedMps(15, 0)
        val rough = RoutingProfile.OFFROAD.speedMps(15, RoadGraph.FLAG_ROUGH)
        val severe = RoutingProfile.OFFROAD.speedMps(15, RoadGraph.FLAG_SEVERE)
        assertTrue(paved > rough)
        assertTrue(rough > severe)
    }

    @Test
    fun smoothnessFlagsNeverProduceZeroOrNegativeSpeed() {
        for (profile in RoutingProfile.values()) {
            assertTrue(profile.speedMps(15, RoadGraph.FLAG_SEVERE) > 0.0)
        }
    }

    @Test
    fun motorcycleProfileHasIndependentAccessBitAndRoadSpeeds() {
        assertTrue(RoutingProfile.MOTORCYCLE.bit == 16)
        assertTrue(RoutingProfile.MOTORCYCLE.maxSpeedMps > RoutingProfile.CAR.maxSpeedMps)
        val paved = RoutingProfile.MOTORCYCLE.speedMps(12, 0)
        val rough = RoutingProfile.MOTORCYCLE.speedMps(12, RoadGraph.FLAG_ROUGH)
        val severe = RoutingProfile.MOTORCYCLE.speedMps(12, RoadGraph.FLAG_SEVERE)
        assertTrue(paved > rough)
        assertTrue(rough > severe)
    }

    @Test
    fun steepGradeReducesSpeedForEveryProfile() {
        for (profile in RoutingProfile.values()) {
            val normal = profile.speedMps(12, 0)
            val steep = profile.speedMps(12, RoadGraph.FLAG_STEEP)
            assertTrue(normal > steep)
            assertTrue(steep > 0.0)
        }
    }

    @Test
    fun newQualityFlagsDoNotOverlapExistingFlags() {
        assertFalse(RoadGraph.FLAG_ROUGH and RoadGraph.FLAG_UNPAVED != 0)
        assertFalse(RoadGraph.FLAG_SEVERE and RoadGraph.FLAG_ROUNDABOUT != 0)
        assertFalse(RoadGraph.FLAG_STEEP and RoadGraph.FLAG_SEVERE != 0)
        assertFalse(RoadGraph.FLAG_STEEP and RoadGraph.FLAG_ROUNDABOUT != 0)
    }
}
