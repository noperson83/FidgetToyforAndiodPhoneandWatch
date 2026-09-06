package bpm.munkz.pulse_wear.os.bpm.presentation

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class FidgetSyncState(
    val revision: Long,
    val actorId: String,
    val toyIndex: Int,
    val fidgetCount: Int,
    val mainColorArgb: Int,
    val backgroundColorArgb: Int,
    val ringColorArgb: Int,
    val hapticFeedbackEnabled: Boolean,
    val soundFeedbackEnabled: Boolean,
    val feedbackSoundMode: Int,
    val accentIntensityMode: Int,
    val appLanguage: Int,
    val keepScreenOn: Boolean,
    val cpuPercentVisible: Boolean,
    val pinnedToyIdsCsv: String,
    val donationCounts: Map<String, Int>,
    val motionGesturesEnabled: Boolean,
    val gestureControlMode: Int,
    val phoneMotionEnabled: Boolean,
    val tiltGestureEnabled: Boolean,
    val shakeGestureEnabled: Boolean,
    val motionSensitivity: Int,
    val neutralTiltX: Float,
    val neutralTiltY: Float,
)

internal fun fidgetSyncContentEquals(
    first: FidgetSyncState?,
    second: FidgetSyncState,
): Boolean {
    return first?.copy(revision = 0L, actorId = "") ==
        second.copy(revision = 0L, actorId = "")
}

internal fun shouldApplyFidgetSyncState(
    current: FidgetSyncState?,
    incoming: FidgetSyncState,
    localActorId: String,
): Boolean {
    if (incoming.actorId == localActorId) return false
    if (current == null) return true
    return shouldApplyFidgetSyncEnvelope(
        currentRevision = current.revision,
        currentActorId = current.actorId,
        incoming = incoming,
        localActorId = localActorId,
    )
}

internal fun shouldApplyFidgetSyncEnvelope(
    currentRevision: Long,
    currentActorId: String,
    incoming: FidgetSyncState,
    localActorId: String,
): Boolean {
    if (incoming.actorId == localActorId) return false
    return incoming.revision > currentRevision ||
        (incoming.revision == currentRevision && incoming.actorId > currentActorId)
}

internal fun initialFidgetSyncEnvelope(
    hasDeviceActor: Boolean,
    hasCurrentSchema: Boolean,
    storedRevision: Long,
    storedActorId: String,
): Pair<Long, String> {
    return if (hasDeviceActor && hasCurrentSchema) {
        storedRevision.coerceAtLeast(0L) to storedActorId
    } else {
        0L to ""
    }
}

internal suspend fun <T> debounceFidgetSyncPublish(
    delayMillis: Long = FIDGET_SYNC_DEBOUNCE_MILLIS,
    publish: suspend () -> T,
): T {
    delay(delayMillis.coerceAtLeast(0L))
    return publish()
}

internal suspend fun startOptionalFidgetSync(
    start: suspend () -> Unit,
    onUnavailable: (Exception) -> Unit = {},
): Boolean {
    return try {
        start()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        onUnavailable(failure)
        false
    }
}

internal object FidgetSyncLink {
    private const val TAG = "MunkzFidgetSync"
    private const val DATA_PATH = "/munkz-fidget/state/v2"
    private const val MESSAGE_PATH = "/munkz-fidget/state-event/v2"
    fun events(context: Context): Flow<FidgetSyncState> = callbackFlow {
        val appContext = context.applicationContext
        val dataClient = Wearable.getDataClient(appContext)
        val messageClient = Wearable.getMessageClient(appContext)

        fun emitPayload(payload: ByteArray?) {
            if (payload == null) return
            FidgetSyncCodec.decode(payload)
                .onSuccess { trySend(it) }
                .onFailure { failure -> Log.w(TAG, "Ignoring invalid sync payload.", failure) }
        }

        val messageListener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == MESSAGE_PATH) emitPayload(event.data)
        }
        val dataListener = DataClient.OnDataChangedListener { changes ->
            changes.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == DATA_PATH) {
                    emitPayload(event.dataItem.data)
                }
            }
        }

        var messageListenerRegistered = false
        var dataListenerRegistered = false
        val syncStarted = startOptionalFidgetSync(
            start = {
                GoogleApiAvailability.getInstance()
                    .checkApiAvailability(dataClient)
                    .awaitFidgetSyncResult()
                messageClient.addListener(messageListener).awaitFidgetSyncResult()
                messageListenerRegistered = true
                dataClient.addListener(
                    dataListener,
                    fidgetSyncUri(DATA_PATH),
                    DataClient.FILTER_LITERAL,
                ).awaitFidgetSyncResult()
                dataListenerRegistered = true
            },
            onUnavailable = { failure ->
                Log.w(TAG, "Wearable Data Layer unavailable; continuing without sync.", failure)
            },
        )
        if (!syncStarted) {
            if (messageListenerRegistered) messageClient.removeListener(messageListener)
            if (dataListenerRegistered) dataClient.removeListener(dataListener)
            close()
            return@callbackFlow
        }

        try {
            val items = dataClient.getDataItems(
                fidgetSyncUri(DATA_PATH),
                DataClient.FILTER_LITERAL,
            ).awaitFidgetSyncResult()
            try {
                items.forEach { emitPayload(it.data) }
            } finally {
                items.release()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The listeners still provide live sync if the initial read is unavailable.
        }

        awaitClose {
            if (messageListenerRegistered) messageClient.removeListener(messageListener)
            if (dataListenerRegistered) dataClient.removeListener(dataListener)
        }
    }

    suspend fun publish(context: Context, state: FidgetSyncState): Result<Unit> {
        return try {
            val payload = FidgetSyncCodec.encode(state)
            Wearable.getDataClient(context.applicationContext)
                .putDataItem(
                    PutDataRequest.create(DATA_PATH)
                        .setData(payload)
                        .setUrgent(),
                )
                .awaitFidgetSyncResult()
            val nodes = Wearable.getNodeClient(context.applicationContext)
                .connectedNodes
                .awaitFidgetSyncResult()
            nodes.forEach { node ->
                try {
                    Wearable.getMessageClient(context.applicationContext)
                        .sendMessage(node.id, MESSAGE_PATH, payload)
                        .awaitFidgetSyncResult()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w(TAG, "Immediate sync message failed; durable state remains available.", failure)
                }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }
}

internal class FidgetSyncCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val identityPreferences = appContext.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)
    private val savedActorId = identityPreferences
        .getString(ACTOR_KEY, null)
        ?.takeIf { saved -> runCatching { UUID.fromString(saved) }.isSuccess }
    private val actorId = savedActorId
        ?: UUID.randomUUID().toString().also { created ->
            identityPreferences.edit { putString(ACTOR_KEY, created) }
        }
    private val initialEnvelope = initialFidgetSyncEnvelope(
        hasDeviceActor = savedActorId != null,
        hasCurrentSchema = identityPreferences.getInt(ENVELOPE_SCHEMA_KEY, 0) == ENVELOPE_SCHEMA_VERSION,
        storedRevision = identityPreferences.getLong(REVISION_KEY, 0L),
        storedActorId = identityPreferences.getString(LAST_ACTOR_KEY, "").orEmpty(),
    )
    private var revision = initialEnvelope.first
    private var lastAcceptedActorId = initialEnvelope.second
    private var lastState: FidgetSyncState? = null

    init {
        if (initialEnvelope.first == 0L && initialEnvelope.second.isEmpty()) {
            persistEnvelope()
        }
    }

    fun events(context: Context): kotlinx.coroutines.flow.Flow<FidgetSyncState> {
        return FidgetSyncLink.events(context)
    }

    suspend fun publish(stateWithoutRevision: FidgetSyncState): Result<Unit> {
        if (fidgetSyncContentEquals(lastState, stateWithoutRevision)) {
            return Result.success(Unit)
        }
        revision = maxOf(revision, stateWithoutRevision.revision) + 1L
        val state = stateWithoutRevision.copy(revision = revision, actorId = actorId)
        lastAcceptedActorId = actorId
        persistEnvelope()
        return FidgetSyncLink.publish(appContext, state).also { result ->
            if (result.isSuccess) {
                lastState = state
            } else {
                Log.w(TAG, "Unable to publish fidget sync state.", result.exceptionOrNull())
            }
        }
    }

    fun shouldApply(incoming: FidgetSyncState): Boolean {
        return lastState?.let { current ->
            shouldApplyFidgetSyncState(current, incoming, actorId)
        } ?: shouldApplyFidgetSyncEnvelope(
            currentRevision = revision,
            currentActorId = lastAcceptedActorId,
            incoming = incoming,
            localActorId = actorId,
        )
    }

    fun accept(incoming: FidgetSyncState) {
        lastState = incoming
        revision = maxOf(revision, incoming.revision)
        lastAcceptedActorId = incoming.actorId
        persistEnvelope()
    }

    private fun persistEnvelope() {
        identityPreferences.edit {
            putInt(ENVELOPE_SCHEMA_KEY, ENVELOPE_SCHEMA_VERSION)
            putLong(REVISION_KEY, revision)
            putString(LAST_ACTOR_KEY, lastAcceptedActorId)
        }
    }

    companion object {
        private const val TAG = "MunkzFidgetSync"
        private const val DEVICE_PREFS = "munkz_fidget_device_sync"
        private const val ACTOR_KEY = "actor_id"
        private const val ENVELOPE_SCHEMA_KEY = "schema_version"
        private const val ENVELOPE_SCHEMA_VERSION = 1
        private const val REVISION_KEY = "revision"
        private const val LAST_ACTOR_KEY = "last_actor_id"
    }
}

internal object FidgetSyncCodec {
    private const val MAGIC = 0x4D465347
    private const val VERSION = 2
    private const val MAX_TEXT = 2048

    fun encode(state: FidgetSyncState): ByteArray {
        require(state.revision >= 0L) { "Fidget sync revision cannot be negative." }
        require(state.fidgetCount >= 0) { "Fidget count cannot be negative." }
        UUID.fromString(state.actorId)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeInt(MAGIC)
            stream.writeInt(VERSION)
            stream.writeLong(state.revision)
            stream.writeUTF(state.actorId)
            stream.writeInt(state.toyIndex)
            stream.writeInt(state.fidgetCount)
            stream.writeInt(state.mainColorArgb)
            stream.writeInt(state.backgroundColorArgb)
            stream.writeInt(state.ringColorArgb)
            stream.writeBoolean(state.hapticFeedbackEnabled)
            stream.writeBoolean(state.soundFeedbackEnabled)
            stream.writeInt(state.feedbackSoundMode)
            stream.writeInt(state.accentIntensityMode)
            stream.writeInt(state.appLanguage)
            stream.writeBoolean(state.keepScreenOn)
            stream.writeBoolean(state.cpuPercentVisible)
            stream.writeBoundedText(state.pinnedToyIdsCsv)
            val donationIds = listOf(
                FIDGET_DONATION_1_PRODUCT_ID,
                FIDGET_DONATION_3_PRODUCT_ID,
                FIDGET_DONATION_5_PRODUCT_ID,
                FIDGET_DONATION_10_PRODUCT_ID,
            )
            donationIds.forEach { id -> stream.writeInt(state.donationCounts.getOrDefault(id, 0).coerceIn(0, 5)) }
            stream.writeBoolean(state.motionGesturesEnabled)
            stream.writeInt(state.gestureControlMode)
            stream.writeBoolean(state.phoneMotionEnabled)
            stream.writeBoolean(state.tiltGestureEnabled)
            stream.writeBoolean(state.shakeGestureEnabled)
            stream.writeInt(state.motionSensitivity)
            stream.writeFloat(state.neutralTiltX.coerceIn(-1f, 1f))
            stream.writeFloat(state.neutralTiltY.coerceIn(-1f, 1f))
        }
        return output.toByteArray()
    }

    fun decode(payload: ByteArray): Result<FidgetSyncState> = runCatching {
        DataInputStream(ByteArrayInputStream(payload)).use { stream ->
            require(stream.readInt() == MAGIC) { "Invalid fidget sync payload." }
            val version = stream.readInt()
            require(version in 1..VERSION) { "Unsupported fidget sync version." }
            val revision = stream.readLong().coerceAtLeast(0L)
            val actorId = stream.readUTF().also { UUID.fromString(it) }
            val toyIndex = stream.readInt()
            val fidgetCount = stream.readInt().coerceAtLeast(0)
            val mainColorArgb = stream.readInt()
            val backgroundColorArgb = stream.readInt()
            val ringColorArgb = stream.readInt()
            val hapticFeedbackEnabled = stream.readBoolean()
            val soundFeedbackEnabled = stream.readBoolean()
            val feedbackSoundMode = stream.readInt()
            val accentIntensityMode = stream.readInt()
            val appLanguage = stream.readInt()
            val keepScreenOn = stream.readBoolean()
            val cpuPercentVisible = stream.readBoolean()
            val pinnedToyIdsCsv = stream.readBoundedText()
            val donationValues = listOf(
                FIDGET_DONATION_1_PRODUCT_ID,
                FIDGET_DONATION_3_PRODUCT_ID,
                FIDGET_DONATION_5_PRODUCT_ID,
                FIDGET_DONATION_10_PRODUCT_ID,
            ).associateWith { stream.readInt().coerceIn(0, 5) }
            val motionGesturesEnabled = if (version >= 2) stream.readBoolean() else false
            val gestureControlMode = if (version >= 2) stream.readInt() else FidgetControlMode.Touch.persistedValue
            val phoneMotionEnabled = if (version >= 2) stream.readBoolean() else false
            val tiltGestureEnabled = if (version >= 2) stream.readBoolean() else true
            val shakeGestureEnabled = if (version >= 2) stream.readBoolean() else true
            val motionSensitivity = if (version >= 2) {
                stream.readInt()
            } else {
                FidgetMotionSensitivity.Medium.persistedValue
            }
            val neutralTiltX = if (version >= 2) stream.readFloat().coerceIn(-1f, 1f) else 0f
            val neutralTiltY = if (version >= 2) stream.readFloat().coerceIn(-1f, 1f) else 0f
            require(stream.available() == 0) { "Unexpected trailing fidget sync data." }
            FidgetSyncState(
                revision = revision,
                actorId = actorId,
                toyIndex = toyIndex,
                fidgetCount = fidgetCount,
                mainColorArgb = mainColorArgb,
                backgroundColorArgb = backgroundColorArgb,
                ringColorArgb = ringColorArgb,
                hapticFeedbackEnabled = hapticFeedbackEnabled,
                soundFeedbackEnabled = soundFeedbackEnabled,
                feedbackSoundMode = feedbackSoundMode,
                accentIntensityMode = accentIntensityMode,
                appLanguage = appLanguage,
                keepScreenOn = keepScreenOn,
                cpuPercentVisible = cpuPercentVisible,
                pinnedToyIdsCsv = pinnedToyIdsCsv,
                donationCounts = donationValues,
                motionGesturesEnabled = motionGesturesEnabled,
                gestureControlMode = gestureControlMode,
                phoneMotionEnabled = phoneMotionEnabled,
                tiltGestureEnabled = tiltGestureEnabled,
                shakeGestureEnabled = shakeGestureEnabled,
                motionSensitivity = motionSensitivity,
                neutralTiltX = neutralTiltX,
                neutralTiltY = neutralTiltY,
            )
        }
    }

    private fun DataOutputStream.writeBoundedText(value: String) {
        writeUTF(value.take(MAX_TEXT))
    }

    private fun DataInputStream.readBoundedText(): String {
        return readUTF().take(MAX_TEXT)
    }
}

private fun fidgetSyncUri(path: String): Uri {
    return Uri.Builder().scheme("wear").authority("*").path(path).build()
}

private suspend fun <T> Task<T>.awaitFidgetSyncResult(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { completed ->
            if (!continuation.isActive) return@addOnCompleteListener
            when {
                completed.isSuccessful -> continuation.resume(completed.result)
                completed.isCanceled -> continuation.cancel(
                    CancellationException("Fidget sync Data Layer task was cancelled."),
                )
                else -> continuation.resumeWithException(
                    completed.exception ?: IllegalStateException("Fidget sync Data Layer task failed."),
                )
            }
        }
    }
}
