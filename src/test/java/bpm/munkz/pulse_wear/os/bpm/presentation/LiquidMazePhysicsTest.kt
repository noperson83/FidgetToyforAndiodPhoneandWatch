package bpm.munkz.pulse_wear.os.bpm.presentation

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidMazePhysicsTest {
    @Test
    fun blobStaysInsideTheVisibleBoard() {
        val puzzle = lockedLiquidMazePuzzle()
        var position = Offset.Zero

        repeat(24) {
            position = moveLiquidMazeBlob(position, Offset(12f, 12f), puzzle)
        }

        assertTrue(position.x <= 50f)
        assertTrue(position.y <= 50f)
    }

    @Test
    fun blobDoesNotPassThroughAClosedMazeWall() {
        val puzzle = lockedLiquidMazePuzzle()
        var position = Offset.Zero

        repeat(12) {
            position = moveLiquidMazeBlob(position, Offset(5f, 0f), puzzle)
        }

        assertTrue(position.x <= 2.1f)
    }

    @Test
    fun blobCrossesOnlyAnOpenedMazePassage() {
        val openings = MutableList(FIDGET_MAZE_CELL_COUNT) { 0 }
        val centerCell = 12
        openings[centerCell] = MAZE_OPEN_RIGHT
        openings[centerCell + 1] = MAZE_OPEN_LEFT
        val puzzle = FidgetMazePuzzle(openings, startCell = centerCell, endCell = centerCell + 1)
        var position = liquidMazeStartPosition(puzzle)

        repeat(12) {
            position = moveLiquidMazeBlob(position, Offset(5f, 0f), puzzle)
        }

        assertTrue(position.x > 10f)
    }

    @Test
    fun generatedMazeGetsASafeStartingPosition() {
        val puzzle = generateFidgetMazePuzzle()

        val start = liquidMazeStartPosition(puzzle)
        val afterNoMovement = moveLiquidMazeBlob(start, Offset.Zero, puzzle)

        assertEquals(start, afterNoMovement)
        assertTrue(start.x in -40f..40f)
        assertTrue(start.y in -40f..40f)
    }

    private fun lockedLiquidMazePuzzle(): FidgetMazePuzzle = FidgetMazePuzzle(
        openings = List(FIDGET_MAZE_CELL_COUNT) { 0 },
        startCell = 12,
        endCell = 13,
    )
}
