package bpm.munkz.pulse_wear.os.bpm.presentation

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidMazePhysicsTest {
    @Test
    fun blobStaysInsideTheVisibleBoard() {
        val walls = emptyList<LiquidMazeWall>()
        var position = Offset.Zero

        repeat(24) {
            position = moveLiquidMazeBlob(position, Offset(12f, 12f), walls)
        }

        assertTrue(position.x <= 35f)
        assertTrue(position.y <= 35f)
    }

    @Test
    fun blobDoesNotPassThroughAWall() {
        val walls = listOf(LiquidMazeWall(Offset(-30f, 0f), Offset(30f, 0f)))
        val position = moveLiquidMazeBlob(Offset(0f, -20f), Offset(0f, 20f), walls)

        assertTrue(position.y < -13f)
    }

    @Test
    fun generatedMazeGetsASafeStartingPosition() {
        val walls = listOf(
            LiquidMazeWall(Offset(-32f, -12f), Offset(8f, -12f)),
            LiquidMazeWall(Offset(12f, 12f), Offset(32f, 12f)),
        )

        val start = liquidMazeStartPosition(walls)
        val afterNoMovement = moveLiquidMazeBlob(start, Offset.Zero, walls)

        assertEquals(start, afterNoMovement)
        assertTrue(start.x in -35f..35f)
        assertTrue(start.y in -35f..35f)
    }
}
