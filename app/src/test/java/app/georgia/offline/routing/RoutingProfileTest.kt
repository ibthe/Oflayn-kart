package app.georgia.offline.routing

import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingProfileTest {
    @Test
    fun uphillIsSlowerThanDownhillAndFlatForEveryProfile() {
        RoutingProfile.entries.forEach { profile ->
            val flat = profile.speedMps(12, 0, 0)
            val uphill = profile.speedMps(12, 0, 12)
            val downhill = profile.speedMps(12, 0, -12)
            assertTrue("$profile should slow on uphill grades", flat > uphill)
            assertTrue("$profile should apply a smaller penalty downhill", downhill > uphill)
            assertTrue("$profile should remain slower downhill than flat", flat > downhill)
        }
    }

    @Test
    fun steepFlagRemainsFallbackWhenNoDirectionalGradeIsAvailable() {
        RoutingProfile.entries.forEach { profile ->
            assertTrue(
                "$profile should retain conservative fallback for unmeasured steep roads",
                profile.speedMps(12, RoadGraph.FLAG_STEEP) < profile.speedMps(12, 0)
            )
        }
    }
}
