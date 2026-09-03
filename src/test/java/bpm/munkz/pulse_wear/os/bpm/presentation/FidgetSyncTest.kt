package bpm.munkz.pulse_wear.os.bpm.presentation

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FidgetSyncTest {
    @Test
    fun codecRoundTripPreservesStateAndClampsDonationBadges() {
        val state = testState().copy(
            donationCounts = mapOf(
                FIDGET_DONATION_1_PRODUCT_ID to -2,
                FIDGET_DONATION_3_PRODUCT_ID to 2,
                FIDGET_DONATION_5_PRODUCT_ID to 5,
                FIDGET_DONATION_10_PRODUCT_ID to 9,
            ),
        )

        val decoded = FidgetSyncCodec.decode(FidgetSyncCodec.encode(state)).getOrThrow()

        assertEquals(state.copy(donationCounts = mapOf(
            FIDGET_DONATION_1_PRODUCT_ID to 0,
            FIDGET_DONATION_3_PRODUCT_ID to 2,
            FIDGET_DONATION_5_PRODUCT_ID to 5,
            FIDGET_DONATION_10_PRODUCT_ID to 5,
        )), decoded)
    }

    @Test
    fun malformedAndTrailingPayloadsAreRejected() {
        val encoded = FidgetSyncCodec.encode(testState())
        val badMagic = encoded.copyOf().also { bytes -> bytes[0] = 0 }

        assertTrue(FidgetSyncCodec.decode(badMagic).isFailure)
        assertTrue(FidgetSyncCodec.decode(encoded + byteArrayOf(1)).isFailure)
    }

    @Test
    fun versionOnePayloadUsesSafeMotionDefaults() {
        val payload = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { stream ->
                stream.writeInt(0x4D465347)
                stream.writeInt(1)
                stream.writeLong(3L)
                stream.writeUTF("00000000-0000-0000-0000-000000000003")
                stream.writeInt(7)
                stream.writeInt(21)
                stream.writeInt(0xFF56F1C8.toInt())
                stream.writeInt(0xFF061112.toInt())
                stream.writeInt(0xFFFFC857.toInt())
                stream.writeBoolean(true)
                stream.writeBoolean(false)
                stream.writeInt(0)
                stream.writeInt(1)
                stream.writeInt(0)
                stream.writeBoolean(false)
                stream.writeBoolean(false)
                stream.writeUTF("1,7")
                repeat(4) { stream.writeInt(0) }
            }
        }.toByteArray()

        val decoded = FidgetSyncCodec.decode(payload).getOrThrow()

        assertFalse(decoded.motionGesturesEnabled)
        assertEquals(FidgetControlMode.Touch.persistedValue, decoded.gestureControlMode)
        assertFalse(decoded.phoneMotionEnabled)
        assertTrue(decoded.tiltGestureEnabled)
        assertTrue(decoded.shakeGestureEnabled)
        assertEquals(FidgetMotionSensitivity.Medium.persistedValue, decoded.motionSensitivity)
    }

    @Test
    fun conflictResolutionIgnoresSelfAndUsesRevisionThenActorId() {
        val localActorId = "00000000-0000-0000-0000-000000000010"
        val lowActorId = "00000000-0000-0000-0000-000000000005"
        val highActorId = "00000000-0000-0000-0000-000000000020"
        val current = testState(actorId = localActorId, revision = 7L)

        assertFalse(shouldApplyFidgetSyncState(null, current.copy(revision = 8L), localActorId))
        assertFalse(shouldApplyFidgetSyncState(current, current.copy(revision = 8L), localActorId))
        assertFalse(shouldApplyFidgetSyncState(current, testState(lowActorId, 6L), localActorId))
        assertFalse(shouldApplyFidgetSyncState(current, testState(lowActorId, 7L), localActorId))
        assertTrue(shouldApplyFidgetSyncState(current, testState(highActorId, 7L), localActorId))
        assertTrue(shouldApplyFidgetSyncState(current, testState(lowActorId, 8L), localActorId))
    }

    @Test
    fun persistedEnvelopeRejectsStaleStateBeforeCurrentContentIsLoaded() {
        val localActorId = "00000000-0000-0000-0000-000000000010"
        val lowActorId = "00000000-0000-0000-0000-000000000005"
        val highActorId = "00000000-0000-0000-0000-000000000020"

        assertFalse(shouldApplyFidgetSyncEnvelope(7L, localActorId, testState(lowActorId, 6L), localActorId))
        assertFalse(shouldApplyFidgetSyncEnvelope(7L, localActorId, testState(lowActorId, 7L), localActorId))
        assertTrue(shouldApplyFidgetSyncEnvelope(7L, localActorId, testState(highActorId, 7L), localActorId))
        assertTrue(shouldApplyFidgetSyncEnvelope(7L, localActorId, testState(lowActorId, 8L), localActorId))
    }

    @Test
    fun firstDeviceIdentityResetsLegacyOrRestoredConflictEnvelope() {
        assertEquals(0L to "", initialFidgetSyncEnvelope(
            hasDeviceActor = false,
            hasCurrentSchema = false,
            storedRevision = 883L,
            storedActorId = "00000000-0000-0000-0000-000000000030",
        ))
        assertEquals(0L to "", initialFidgetSyncEnvelope(
            hasDeviceActor = true,
            hasCurrentSchema = false,
            storedRevision = 883L,
            storedActorId = "00000000-0000-0000-0000-000000000030",
        ))
        assertEquals(883L to "00000000-0000-0000-0000-000000000030", initialFidgetSyncEnvelope(
            hasDeviceActor = true,
            hasCurrentSchema = true,
            storedRevision = 883L,
            storedActorId = "00000000-0000-0000-0000-000000000030",
        ))
    }

    @Test
    fun contentComparisonPreventsEchoWhenOnlyEnvelopeChanges() {
        val state = testState()
        val sameContentFromPeer = state.copy(
            revision = state.revision + 4L,
            actorId = UUID.randomUUID().toString(),
        )

        assertTrue(fidgetSyncContentEquals(state, sameContentFromPeer))
        assertFalse(fidgetSyncContentEquals(state, sameContentFromPeer.copy(fidgetCount = 35)))
    }

    @Test
    fun cancelledDebouncePublishesOnlyTheLastSettledState() = runBlocking {
        val published = mutableListOf<Int>()
        val first = launch {
            debounceFidgetSyncPublish(delayMillis = 80L) { published += 1 }
        }
        delay(15L)
        first.cancelAndJoin()

        debounceFidgetSyncPublish(delayMillis = 15L) { published += 2 }

        assertEquals(listOf(2), published)
    }

    @Test
    fun unavailableDataLayerDoesNotFailAppStartup() = runBlocking {
        var reportedFailure: Exception? = null

        val started = startOptionalFidgetSync(
            start = { throw IllegalStateException("Wearable API unavailable") },
            onUnavailable = { reportedFailure = it },
        )

        assertFalse(started)
        assertEquals("Wearable API unavailable", reportedFailure?.message)
    }

    @Test(expected = CancellationException::class)
    fun syncStartupStillPropagatesCoroutineCancellation() {
        runBlocking {
            startOptionalFidgetSync(
                start = { throw CancellationException("cancelled") },
            )
        }
    }

    private fun testState(
        actorId: String = UUID.randomUUID().toString(),
        revision: Long = 7L,
    ): FidgetSyncState {
        return FidgetSyncState(
            revision = revision,
            actorId = actorId,
            toyIndex = 4,
            fidgetCount = 34,
            mainColorArgb = 0xFF56F1C8.toInt(),
            backgroundColorArgb = 0xFF061112.toInt(),
            ringColorArgb = 0xFFFFC857.toInt(),
            hapticFeedbackEnabled = true,
            soundFeedbackEnabled = true,
            feedbackSoundMode = 2,
            accentIntensityMode = 1,
            appLanguage = 1,
            keepScreenOn = true,
            cpuPercentVisible = false,
            pinnedToyIdsCsv = "1,4,8",
            donationCounts = mapOf(
                FIDGET_DONATION_1_PRODUCT_ID to 1,
                FIDGET_DONATION_3_PRODUCT_ID to 2,
                FIDGET_DONATION_5_PRODUCT_ID to 3,
                FIDGET_DONATION_10_PRODUCT_ID to 4,
            ),
            motionGesturesEnabled = true,
            gestureControlMode = FidgetControlMode.Both.persistedValue,
            phoneMotionEnabled = false,
            tiltGestureEnabled = true,
            shakeGestureEnabled = true,
            motionSensitivity = FidgetMotionSensitivity.High.persistedValue,
            neutralTiltX = 0.12f,
            neutralTiltY = -0.18f,
        )
    }
}
