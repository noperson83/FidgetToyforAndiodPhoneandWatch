package bpm.munkz.pulse_wear.os.bpm.presentation

import androidx.compose.ui.geometry.Offset
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FidgetMazePhysicsTest {
    @Test
    fun motionTiltMazesLockTheCurrentScreenOrientation() {
        listOf(7, 13, 25, 26).forEach { toyIndex ->
            assertTrue(
                shouldLockFidgetMotionOrientation(
                    motionInputEnabled = true,
                    tiltGestureEnabled = true,
                    toyIndex = toyIndex,
                ),
            )
        }
        assertFalse(
            shouldLockFidgetMotionOrientation(
                motionInputEnabled = false,
                tiltGestureEnabled = true,
                toyIndex = 25,
            ),
        )
        assertFalse(
            shouldLockFidgetMotionOrientation(
                motionInputEnabled = true,
                tiltGestureEnabled = false,
                toyIndex = 25,
            ),
        )
        assertFalse(
            shouldLockFidgetMotionOrientation(
                motionInputEnabled = true,
                tiltGestureEnabled = true,
                toyIndex = 1,
            ),
        )
    }

    @Test
    fun centerDropGenerationCreatesThreeGatesAndOuterStart() {
        val maze = generateCenterDropMaze(Random(12))

        assertEquals(3, maze.gateAnglesDegrees.size)
        assertTrue(maze.startPosition.getDistance() in 48.9f..49.1f)
    }

    @Test
    fun centerDropRingBlocksBallAwayFromGate() {
        val maze = CenterDropMaze(
            gateAnglesDegrees = listOf(180f, 180f, 180f),
            startPosition = Offset(47f, 0f),
        )

        val moved = moveCenterDropBall(Offset(47f, 0f), Offset(-10f, 0f), maze)

        assertTrue(moved.getDistance() >= 46.9f)
    }

    @Test
    fun centerDropGateLetsBallCrossRing() {
        val maze = CenterDropMaze(
            gateAnglesDegrees = listOf(0f, 0f, 0f),
            startPosition = Offset(47f, 0f),
        )

        val moved = moveCenterDropBall(Offset(47f, 0f), Offset(-10f, 0f), maze)

        assertTrue(moved.getDistance() < 42f)
    }

    @Test
    fun ballSortLocksOnlyBallThatReachesMatchingPocket() {
        val maze = BallSortMaze(
            pocketPositions = listOf(Offset(10f, 0f), Offset(30f, 30f), Offset(-30f, 30f)),
            startPositions = listOf(Offset.Zero, Offset.Zero, Offset.Zero),
            obstaclePositions = emptyList(),
        )

        val moved = moveBallSortBalls(
            positions = maze.startPositions,
            locked = listOf(false, false, false),
            maze = maze,
            delta = Offset(8f, 0f),
        )

        assertTrue(moved.locked[0])
        assertFalse(moved.locked[1])
        assertFalse(moved.locked[2])
        assertEquals(maze.pocketPositions[0], moved.positions[0])
    }
}
