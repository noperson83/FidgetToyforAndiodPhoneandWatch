package bpm.munkz.pulse_wear.os.bpm.presentation

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GearJamGeometryTest {
    @Test fun allThreeGearsStayMeshed() {
        val sizes = GearJamSizes(primary = 48f, secondary = 38f, tertiary = 30f)
        val layout = gearJamLayoutFor(sizes)

        assertEquals(
            meshedDistance(sizes.primary, sizes.secondary),
            distance(layout.primaryOffset, layout.secondaryOffset),
            0.001f,
        )
        assertEquals(
            meshedDistance(sizes.primary, sizes.tertiary),
            distance(layout.primaryOffset, layout.tertiaryOffset),
            0.001f,
        )
        assertEquals(
            meshedDistance(sizes.secondary, sizes.tertiary),
            distance(layout.secondaryOffset, layout.tertiaryOffset),
            0.001f,
        )
    }

    @Test fun largestRandomizedClusterFitsInsideTheFidgetStage() {
        val sizes = GearJamSizes(primary = 48f, secondary = 44f, tertiary = 40f)
        val layout = gearJamLayoutFor(sizes)
        val stageRadius = 59f

        listOf(
            layout.primaryOffset to sizes.primary,
            layout.secondaryOffset to sizes.secondary,
            layout.tertiaryOffset to sizes.tertiary,
        ).forEach { (offset, diameter) ->
            val radius = diameter / 2f
            assertTrue(offset.x - radius >= -stageRadius)
            assertTrue(offset.x + radius <= stageRadius)
            assertTrue(offset.y - radius >= -stageRadius)
            assertTrue(offset.y + radius <= stageRadius)
        }
    }

    @Test fun resizeProducesNewSizesWithinTheDesignedRanges() {
        val previous = GearJamSizes(primary = 41f, secondary = 35f, tertiary = 31f)
        val resized = randomGearJamSizes(random = Random(42), previous = previous)

        assertNotEquals(previous, resized)
        assertTrue(resized.primary in 34f..48f)
        assertTrue(resized.secondary in 28f..44f)
        assertTrue(resized.tertiary in 24f..40f)
    }

    private fun meshedDistance(firstDiameter: Float, secondDiameter: Float): Float =
        (firstDiameter + secondDiameter) / 2f - GEAR_JAM_MESH_OVERLAP_DP

    private fun distance(first: Offset, second: Offset): Float =
        hypot(first.x - second.x, first.y - second.y)
}
