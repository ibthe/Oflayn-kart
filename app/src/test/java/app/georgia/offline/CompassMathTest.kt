package app.georgia.offline

import android.hardware.SensorManager
import android.view.Surface
import app.georgia.offline.ui.CompassMath
import org.junit.Assert.assertEquals
import org.junit.Test

class CompassMathTest {
    @Test
    fun screenAxesMatchNaturalOrientation() {
        assertEquals(
            SensorManager.AXIS_X to SensorManager.AXIS_Y,
            CompassMath.screenAxes(Surface.ROTATION_0)
        )
    }

    @Test
    fun screenAxesRemapQuarterTurns() {
        assertEquals(
            SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X,
            CompassMath.screenAxes(Surface.ROTATION_90)
        )
        assertEquals(
            SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X,
            CompassMath.screenAxes(Surface.ROTATION_270)
        )
    }

    @Test
    fun screenAxesRemapHalfTurn() {
        assertEquals(
            SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y,
            CompassMath.screenAxes(Surface.ROTATION_180)
        )
    }

    @Test
    fun unknownDisplayRotationFallsBackToNaturalAxes() {
        assertEquals(
            SensorManager.AXIS_X to SensorManager.AXIS_Y,
            CompassMath.screenAxes(-1)
        )
    }
}
