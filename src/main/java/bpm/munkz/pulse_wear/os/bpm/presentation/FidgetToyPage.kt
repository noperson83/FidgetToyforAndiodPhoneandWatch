package bpm.munkz.pulse_wear.os.bpm.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.ImageDecoder
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.net.Uri
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import bpm.munkz.pulse_wear.os.bpm.BuildConfig
import bpm.munkz.pulse_wear.os.bpm.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FidgetToyPage(
    requestedToyIndex: Int? = null,
    requestedToyRequestId: Long = 0L,
) {
    val context = LocalContext.current
    val wearEdition = BuildConfig.APP_EDITION == "fidgettoy"
    val phoneEdition = BuildConfig.APP_EDITION == "fidgetphone"
    val savedSettings = remember(context) { context.loadFidgetSettings() }
    val directLaunchToyIndex = requestedToyIndex?.takeIf { requestedId ->
        FIDGET_TOY_INFOS.any { toy -> toy.id == requestedId }
    }
    var toyIndex by rememberSaveable {
        mutableIntStateOf(directLaunchToyIndex ?: FIDGET_SPINNER_INDEX)
    }
    var lastPlayableToyIndex by rememberSaveable {
        mutableIntStateOf(directLaunchToyIndex ?: FIDGET_SPINNER_INDEX)
    }
    var directLaunchGuardUntilMs by remember { mutableLongStateOf(0L) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var spinVelocityDegreesPerSecond by remember { mutableFloatStateOf(0f) }
    var touchPulse by remember { mutableFloatStateOf(0f) }
    var rewardPulse by remember { mutableFloatStateOf(0f) }
    var rewardFlash by remember { mutableFloatStateOf(0f) }
    var rewardProgressPopupOpen by rememberSaveable { mutableStateOf(false) }
    var rewardMomentMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var fidgetCount by rememberSaveable { mutableIntStateOf(savedSettings.fidgetCount) }
    var mainColorArgb by rememberSaveable { mutableIntStateOf(savedSettings.mainColorArgb) }
    var backgroundColorArgb by rememberSaveable { mutableIntStateOf(savedSettings.backgroundColorArgb) }
    var backgroundImageUri by rememberSaveable { mutableStateOf(savedSettings.backgroundImageUri) }
    var ringColorArgb by rememberSaveable { mutableIntStateOf(savedSettings.ringColorArgb) }
    var spinnerStyleIndex by rememberSaveable { mutableIntStateOf(savedSettings.spinnerStyleIndex) }
    var spinnerMultiEnabled by rememberSaveable { mutableStateOf(savedSettings.spinnerMultiEnabled) }
    var switchMask by remember { mutableIntStateOf(0) }
    var mazePosition by remember { mutableIntStateOf(0) }
    var freeButtonPositions by remember {
        mutableStateOf(
            listOf(
                Offset(-32f, -32f),
                Offset(32f, -32f),
                Offset(-32f, 32f),
                Offset(32f, 32f),
            ),
        )
    }
    var whackButtonPositions by remember {
        mutableStateOf(listOf(0, 5, 10, 15))
    }
    var squishyPull by remember { mutableStateOf(Offset.Zero) }
    var squishyPressure by remember { mutableFloatStateOf(0f) }
    var magSnapPosition by remember { mutableIntStateOf(1) }
    var popGridMask by remember { mutableIntStateOf(0) }
    var infinityFold by remember { mutableIntStateOf(0) }
    var infinityCardFlipMask by remember { mutableIntStateOf(0) }
    var ratchetStep by remember { mutableIntStateOf(0) }
    var liquidMazePuzzle by remember { mutableStateOf(generateLiquidMazePuzzle()) }
    var liquidBlobPosition by remember { mutableStateOf(liquidMazeStartPosition(liquidMazePuzzle)) }
    var liquidBlobTrail by remember { mutableStateOf(emptyList<Offset>()) }
    var gearRotation by remember { mutableFloatStateOf(0f) }
    var worryStoneRub by remember { mutableFloatStateOf(0f) }
    var worryStoneTouchPoint by remember { mutableStateOf<Offset?>(null) }
    var keyClickMask by remember { mutableIntStateOf(0) }
    var dockPadStates by remember { mutableStateOf(context.loadFidgetDockPadStates()) }
    var zenTracePoints by remember { mutableStateOf(emptyList<Offset>()) }
    var activeBeatPad by remember { mutableIntStateOf(-1) }
    var slingshotPosition by remember { mutableStateOf(Offset.Zero) }
    var slingshotVelocity by remember { mutableStateOf(Offset.Zero) }
    var slingshotPulling by remember { mutableStateOf(false) }
    var homeWindowPaneMask by remember { mutableIntStateOf(0) }
    var homeDoorOpen by remember { mutableStateOf(false) }
    var homeLightOn by remember { mutableStateOf(false) }
    var homeFanOn by remember { mutableStateOf(false) }
    var homeFanRotation by remember { mutableFloatStateOf(0f) }
    var homeSinkTapMask by remember { mutableIntStateOf(0) }
    var mazePuzzle by remember { mutableStateOf(generateFidgetMazePuzzle()) }
    var mazePlayerCell by remember { mutableIntStateOf(mazePuzzle.startCell) }
    var centerDropMaze by remember { mutableStateOf(generateCenterDropMaze()) }
    var centerDropBallPosition by remember { mutableStateOf(centerDropMaze.startPosition) }
    var centerDropSolved by remember { mutableStateOf(false) }
    var ballSortMaze by remember { mutableStateOf(generateBallSortMaze()) }
    var ballSortPositions by remember { mutableStateOf(ballSortMaze.startPositions) }
    var ballSortLocked by remember { mutableStateOf(List(BALL_SORT_BALL_COUNT) { false }) }
    var lastTiltMazeMoveAtMs by remember { mutableLongStateOf(0L) }
    var handledShakeToken by remember { mutableIntStateOf(0) }
    var hapticFeedbackEnabled by rememberSaveable { mutableStateOf(savedSettings.hapticFeedbackEnabled) }
    var soundFeedbackEnabled by rememberSaveable { mutableStateOf(savedSettings.soundFeedbackEnabled) }
    var feedbackSoundMode by rememberSaveable { mutableStateOf(savedSettings.feedbackSoundMode) }
    var accentIntensityMode by rememberSaveable { mutableStateOf(savedSettings.accentIntensityMode) }
    var rewardStyle by rememberSaveable { mutableStateOf(savedSettings.rewardStyle) }
    var appLanguage by rememberSaveable { mutableStateOf(savedSettings.appLanguage) }
    var keepScreenOn by rememberSaveable { mutableStateOf(savedSettings.keepScreenOn) }
    var cpuPercentVisible by rememberSaveable { mutableStateOf(savedSettings.cpuPercentVisible) }
    var pinnedToyIdsCsv by rememberSaveable { mutableStateOf(savedSettings.pinnedToyIdsCsv) }
    var motionGesturesEnabled by rememberSaveable { mutableStateOf(savedSettings.motionGesturesEnabled) }
    var gestureControlMode by rememberSaveable { mutableStateOf(savedSettings.gestureControlMode) }
    var phoneMotionEnabled by rememberSaveable { mutableStateOf(savedSettings.phoneMotionEnabled) }
    var tiltGestureEnabled by rememberSaveable { mutableStateOf(savedSettings.tiltGestureEnabled) }
    var shakeGestureEnabled by rememberSaveable { mutableStateOf(savedSettings.shakeGestureEnabled) }
    var motionSensitivity by rememberSaveable { mutableStateOf(savedSettings.motionSensitivity) }
    var neutralTiltX by rememberSaveable { mutableFloatStateOf(savedSettings.neutralTiltX) }
    var neutralTiltY by rememberSaveable { mutableFloatStateOf(savedSettings.neutralTiltY) }
    var wallResetToken by remember { mutableIntStateOf(0) }
    var reviewPopupOpen by remember { mutableStateOf(false) }
    var donationPopupOpen by remember { mutableStateOf(false) }
    var colorPopupOpen by remember { mutableStateOf(false) }
    var intensityPopupOpen by remember { mutableStateOf(false) }
    var reviewStatusText by remember { mutableStateOf("") }
    var donationThanksText by remember { mutableStateOf("") }
    var donationCounts by remember { mutableStateOf(context.loadFidgetDonationCounts()) }
    var fidgetSyncReady by remember { mutableStateOf(false) }
    val fidgetSync = remember(context) { FidgetSyncCoordinator(context.applicationContext) }
    val isInstalledFromPlay = remember(context) { context.isInstalledFromPlay() }
    val mainColor = colorFromChoice(mainColorArgb)
    val backgroundColor = colorFromChoice(backgroundColorArgb)
    val ringColor = colorFromChoice(ringColorArgb)
    val ringIsRainbow = isRainbowColor(ringColorArgb)
    val rainbowRotationDegrees = if (ringIsRainbow) {
        val rainbowTransition = rememberInfiniteTransition(label = "fidget rainbow ring")
        val rotation by rainbowTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "fidget rainbow rotation",
        )
        rotation
    } else {
        0f
    }
    val spinnerStyle = fidgetSpinnerStyle(spinnerStyleIndex)
    val fidgetText = fidgetTextFor(appLanguage)
    val donationBadge = fidgetDonationBadgeFor(donationCounts)
    val motionInputEnabled = motionGesturesEnabled &&
        gestureControlMode != FidgetControlMode.Touch &&
        (!phoneEdition || phoneMotionEnabled)
    val motionSnapshot = rememberFidgetMotionSnapshot(
        enabled = motionInputEnabled,
        sensitivity = motionSensitivity,
        neutralTilt = Offset(neutralTiltX, neutralTiltY),
    )

    LaunchedEffect(toyIndex, wearEdition) {
        if (wearEdition && toyIndex != FIDGET_MENU_INDEX) {
            donationPopupOpen = false
        }
    }
    LaunchedEffect(toyIndex) {
        if (FIDGET_TOY_INFOS.any { toy -> toy.id == toyIndex }) {
            lastPlayableToyIndex = toyIndex
        }
    }
    val pinnedToyIds = remember(pinnedToyIdsCsv) { pinnedToyIdsCsv.toPinnedToyIds() }
    val favoriteToyId = pinnedToyIds.firstOrNull() ?: FIDGET_SPINNER_INDEX
    val fidgetPageOrder = remember(pinnedToyIds) { fidgetPageOrderFor(pinnedToyIds) }
    val currentToyName = fidgetToyNameFor(toyIndex, appLanguage, fidgetText)
    val nextRewardCount = nextFibonacciTarget(fidgetCount)
    val cpuUsagePercent = rememberFidgetCpuUsagePercent(enabled = cpuPercentVisible)
    val feedbackController = remember(context) {
        FidgetFeedbackController(context.applicationContext)
    }
    val hostActivity = remember(context) { context.findActivity() }
    val effectiveKeepScreenOn = shouldKeepFidgetScreenOn(
        manualKeepScreenOn = keepScreenOn,
        wearEdition = wearEdition,
        motionInputEnabled = motionInputEnabled,
    )
    val donationCoordinator = remember(context, isInstalledFromPlay) {
        if (!isInstalledFromPlay) {
            null
        } else {
            BillingUnlockCoordinator(
                context = context,
                productIds = FIDGET_DONATION_PRODUCTS.map { it.productId }.toSet(),
                consumableProductIds = FIDGET_DONATION_PRODUCTS.map { it.productId }.toSet(),
                onProductOwned = { productId ->
                    donationThanksText = donationThanksTextFor(productId, appLanguage)
                    donationCounts = context.recordFidgetDonation(productId)
                },
                onProductConsumed = { productId ->
                    donationThanksText = donationThanksTextFor(productId, appLanguage)
                    donationCounts = context.recordFidgetDonation(productId)
                },
            )
        }
    }
    val backgroundImageBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        context,
        backgroundImageUri,
    ) {
        value = withContext(Dispatchers.IO) {
            context.loadFidgetBackgroundImage(backgroundImageUri)
        }
    }
    val backgroundImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            context.persistFidgetBackgroundImageAccess(uri)
            backgroundImageUri = uri.toString()
        }
    }

    LaunchedEffect(requestedToyRequestId, directLaunchToyIndex) {
        directLaunchToyIndex?.let { requestedId ->
            toyIndex = requestedId
            directLaunchGuardUntilMs = SystemClock.elapsedRealtime() + 2_500L
        }
    }

    DisposableEffect(feedbackController) {
        onDispose {
            feedbackController.release()
        }
    }

    DisposableEffect(context) {
        val preferences = context.getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                FIDGET_COUNT_KEY -> {
                    fidgetCount = preferences.getInt(FIDGET_COUNT_KEY, 0).coerceAtLeast(0)
                }
                FIDGET_DOCK_PAD_STATES_KEY -> {
                    dockPadStates = context.loadFidgetDockPadStates()
                }
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    DisposableEffect(donationCoordinator) {
        donationCoordinator?.start()
        onDispose {
            donationCoordinator?.stop()
        }
    }

    DisposableEffect(hostActivity, effectiveKeepScreenOn) {
        val window = hostActivity?.window
        if (effectiveKeepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(activeBeatPad) {
        if (activeBeatPad >= 0) {
            delay(120L)
            activeBeatPad = -1
        }
    }

    LaunchedEffect(rewardMomentMessage) {
        if (rewardMomentMessage != null) {
            delay(1_700L)
            rewardMomentMessage = null
        }
    }

    LaunchedEffect(toyIndex) {
        if (toyIndex == FIDGET_LIQUID_MAZE_INDEX) {
            liquidMazePuzzle = generateLiquidMazePuzzle()
            liquidBlobPosition = liquidMazeStartPosition(liquidMazePuzzle)
            liquidBlobTrail = emptyList()
        }
    }

    fun triggerRewardMoment(count: Int) {
        rewardPulse = 1f
        touchPulse = 1f
        when (rewardStyle) {
            FidgetRewardStyle.Calm -> Unit
            FidgetRewardStyle.Glow -> rewardFlash = 0.32f
            FidgetRewardStyle.Celebrate -> {
                rewardFlash = 1f
                rewardMomentMessage = positiveRewardMessageFor(count, appLanguage)
                feedbackController.playReward(
                    hapticEnabled = hapticFeedbackEnabled,
                    soundEnabled = soundFeedbackEnabled,
                    beatSoundMode = feedbackSoundMode,
                )
            }
        }
    }

    fun triggerFeedback(countFidget: Boolean = true) {
        if (countFidget) {
            val nextCount = fidgetCount + 1
            fidgetCount = nextCount
            context.saveFidgetCount(nextCount)
            if (isFibonacciReward(nextCount)) {
                triggerRewardMoment(nextCount)
            }
        }
        feedbackController.play(
            hapticEnabled = hapticFeedbackEnabled,
            soundEnabled = soundFeedbackEnabled,
            beatSoundMode = feedbackSoundMode,
            accentIntensityMode = accentIntensityMode,
        )
    }

    fun triggerBeatPad(index: Int) {
        val nextCount = fidgetCount + 1
        fidgetCount = nextCount
        context.saveFidgetCount(nextCount)
        if (isFibonacciReward(nextCount)) {
            triggerRewardMoment(nextCount)
        }
        feedbackController.playBeatPad(
            padIndex = index,
            hapticEnabled = hapticFeedbackEnabled,
            soundEnabled = soundFeedbackEnabled,
            accentIntensityMode = accentIntensityMode,
        )
    }

    fun refreshMazeShuffle() {
        triggerFeedback()
        mazePuzzle = generateFidgetMazePuzzle()
        mazePlayerCell = mazePuzzle.startCell
    }

    fun refreshCenterDropMaze() {
        triggerFeedback()
        centerDropMaze = generateCenterDropMaze()
        centerDropBallPosition = centerDropMaze.startPosition
        centerDropSolved = false
    }

    fun refreshBallSortMaze() {
        triggerFeedback()
        ballSortMaze = generateBallSortMaze()
        ballSortPositions = ballSortMaze.startPositions
        ballSortLocked = List(BALL_SORT_BALL_COUNT) { false }
    }

    LaunchedEffect(
        toyIndex,
        motionSnapshot.tilt,
        motionInputEnabled,
        tiltGestureEnabled,
        motionSensitivity,
    ) {
        if (!motionInputEnabled || !tiltGestureEnabled) return@LaunchedEffect
        val tilt = motionSnapshot.tilt
        if (tilt.vectorLength() < 0.07f) return@LaunchedEffect
        val motionDelta = tilt * (2.2f * motionSensitivity.tiltScale)
        when (toyIndex) {
            FIDGET_LIQUID_MAZE_INDEX -> {
                val nextPosition = moveLiquidMazeBlob(
                    position = liquidBlobPosition,
                    delta = motionDelta,
                    puzzle = liquidMazePuzzle,
                )
                if (nextPosition != liquidBlobPosition) {
                    liquidBlobTrail = (liquidBlobTrail + liquidBlobPosition).takeLast(14)
                    liquidBlobPosition = nextPosition
                    touchPulse = 1f
                }
            }
            FIDGET_MAZE_INDEX -> {
                val now = SystemClock.elapsedRealtime()
                val cooldown = when (motionSensitivity) {
                    FidgetMotionSensitivity.Low -> 260L
                    FidgetMotionSensitivity.Medium -> 190L
                    FidgetMotionSensitivity.High -> 135L
                }
                if (now - lastTiltMazeMoveAtMs >= cooldown) {
                    (tilt * 100f).toMazeDirection()?.let { direction ->
                        val nextCell = mazePuzzle.nextCell(mazePlayerCell, direction)
                        if (nextCell != mazePlayerCell) {
                            lastTiltMazeMoveAtMs = now
                            mazePlayerCell = nextCell
                            triggerFeedback()
                            if (nextCell == mazePuzzle.endCell) rewardPulse = 1f
                        }
                    }
                }
            }
            FIDGET_CENTER_DROP_MAZE_INDEX -> {
                if (!centerDropSolved) {
                    val nextPosition = moveCenterDropBall(
                        position = centerDropBallPosition,
                        delta = motionDelta,
                        maze = centerDropMaze,
                    )
                    centerDropBallPosition = nextPosition
                    if (nextPosition.vectorLength() <= CENTER_DROP_HOLE_RADIUS_DP) {
                        centerDropSolved = true
                        rewardPulse = 1f
                        triggerFeedback()
                    }
                }
            }
            FIDGET_BALL_SORT_MAZE_INDEX -> {
                val next = moveBallSortBalls(
                    positions = ballSortPositions,
                    locked = ballSortLocked,
                    maze = ballSortMaze,
                    delta = motionDelta,
                )
                if (next.positions != ballSortPositions || next.locked != ballSortLocked) {
                    val newlyLocked = next.locked.count { it } > ballSortLocked.count { it }
                    ballSortPositions = next.positions
                    ballSortLocked = next.locked
                    if (newlyLocked) triggerFeedback()
                    if (next.locked.all { it }) rewardPulse = 1f
                }
            }
        }
    }

    LaunchedEffect(
        motionSnapshot.shakeToken,
        toyIndex,
        motionInputEnabled,
        shakeGestureEnabled,
    ) {
        val shakeToken = motionSnapshot.shakeToken
        if (
            !motionInputEnabled ||
            !shakeGestureEnabled ||
            shakeToken == 0 ||
            shakeToken == handledShakeToken
        ) {
            return@LaunchedEffect
        }
        handledShakeToken = shakeToken
        when (toyIndex) {
            FIDGET_MAZE_INDEX -> refreshMazeShuffle()
            FIDGET_CENTER_DROP_MAZE_INDEX -> refreshCenterDropMaze()
            FIDGET_BALL_SORT_MAZE_INDEX -> refreshBallSortMaze()
        }
    }

    fun saveSettingsAndCloseMenu() {
        context.saveFidgetSettings(
            FidgetSettingsState(
                fidgetCount = fidgetCount,
                mainColorArgb = mainColorArgb,
                backgroundColorArgb = backgroundColorArgb,
                backgroundImageUri = backgroundImageUri,
                ringColorArgb = ringColorArgb,
                spinnerStyleIndex = spinnerStyleIndex,
                spinnerMultiEnabled = spinnerMultiEnabled,
                hapticFeedbackEnabled = hapticFeedbackEnabled,
                soundFeedbackEnabled = soundFeedbackEnabled,
                feedbackSoundMode = feedbackSoundMode,
                accentIntensityMode = accentIntensityMode,
                rewardStyle = rewardStyle,
                appLanguage = appLanguage,
                keepScreenOn = keepScreenOn,
                cpuPercentVisible = cpuPercentVisible,
                pinnedToyIdsCsv = pinnedToyIdsCsv,
                motionGesturesEnabled = motionGesturesEnabled,
                gestureControlMode = gestureControlMode,
                phoneMotionEnabled = phoneMotionEnabled,
                tiltGestureEnabled = tiltGestureEnabled,
                shakeGestureEnabled = shakeGestureEnabled,
                motionSensitivity = motionSensitivity,
                neutralTiltX = neutralTiltX,
                neutralTiltY = neutralTiltY,
            ),
        )
        triggerFeedback(countFidget = false)
        toyIndex = fidgetMenuReturnToyIndex(lastPlayableToyIndex)
    }

    fun togglePinnedToy(toyId: Int) {
        val nextPinnedToyIds = if (toyId in pinnedToyIds) {
            pinnedToyIds - toyId
        } else {
            pinnedToyIds + toyId
        }
        pinnedToyIdsCsv = nextPinnedToyIds.joinToString(",")
        context.saveFidgetPinnedToyIds(pinnedToyIdsCsv)
        triggerFeedback(countFidget = false)
    }

    fun setFavoriteToy(toyId: Int) {
        val nextPinnedToyIds = listOf(toyId) + pinnedToyIds.filterNot { it == toyId }
        pinnedToyIdsCsv = nextPinnedToyIds.joinToString(",")
        context.saveFidgetPinnedToyIds(pinnedToyIdsCsv)
        triggerFeedback(countFidget = false)
    }

    fun moveInFidgetOrder(delta: Int) {
        val currentIndex = fidgetPageOrder.indexOf(toyIndex).takeIf { it >= 0 } ?: 0
        toyIndex = fidgetPageOrder[(currentIndex + delta).wrapFidgetIndex(fidgetPageOrder.size)]
    }

    fun currentFidgetSyncState(): FidgetSyncState {
        return FidgetSyncState(
            revision = 0L,
            actorId = "",
            toyIndex = toyIndex,
            fidgetCount = fidgetCount,
            mainColorArgb = mainColorArgb,
            backgroundColorArgb = backgroundColorArgb,
            ringColorArgb = ringColorArgb,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            soundFeedbackEnabled = soundFeedbackEnabled,
            feedbackSoundMode = feedbackSoundMode.persistedValue,
            accentIntensityMode = accentIntensityMode.persistedValue,
            appLanguage = AppLanguages.indexOf(appLanguage).coerceAtLeast(0),
            keepScreenOn = keepScreenOn,
            cpuPercentVisible = cpuPercentVisible,
            pinnedToyIdsCsv = pinnedToyIdsCsv,
            donationCounts = donationCounts,
            motionGesturesEnabled = motionGesturesEnabled,
            gestureControlMode = gestureControlMode.persistedValue,
            phoneMotionEnabled = phoneMotionEnabled,
            tiltGestureEnabled = tiltGestureEnabled,
            shakeGestureEnabled = shakeGestureEnabled,
            motionSensitivity = motionSensitivity.persistedValue,
            neutralTiltX = neutralTiltX,
            neutralTiltY = neutralTiltY,
        )
    }

    LaunchedEffect(fidgetSync) {
        fidgetSync.events(context).collect { incoming ->
            if (!fidgetSync.shouldApply(incoming)) return@collect
            fidgetSync.accept(incoming)
            fidgetSyncReady = true
            if (SystemClock.elapsedRealtime() >= directLaunchGuardUntilMs) {
                toyIndex = incoming.toyIndex.coerceIn(FIDGET_WALL_INDEX, FIDGET_MENU_INDEX)
            }
            fidgetCount = incoming.fidgetCount
            context.saveFidgetCount(fidgetCount)
            mainColorArgb = incoming.mainColorArgb
            backgroundColorArgb = incoming.backgroundColorArgb
            ringColorArgb = incoming.ringColorArgb
            hapticFeedbackEnabled = incoming.hapticFeedbackEnabled
            soundFeedbackEnabled = incoming.soundFeedbackEnabled
            feedbackSoundMode = BeatSoundMode.fromPersistedValue(incoming.feedbackSoundMode)
            accentIntensityMode = AccentIntensityMode.fromPersistedValue(incoming.accentIntensityMode)
            appLanguage = AppLanguages.getOrElse(incoming.appLanguage) { AppLanguage.English }
            keepScreenOn = incoming.keepScreenOn
            cpuPercentVisible = incoming.cpuPercentVisible
            pinnedToyIdsCsv = incoming.pinnedToyIdsCsv
            motionGesturesEnabled = incoming.motionGesturesEnabled
            gestureControlMode = FidgetControlMode.fromPersistedValue(incoming.gestureControlMode)
            phoneMotionEnabled = incoming.phoneMotionEnabled
            tiltGestureEnabled = incoming.tiltGestureEnabled
            shakeGestureEnabled = incoming.shakeGestureEnabled
            motionSensitivity = FidgetMotionSensitivity.fromPersistedValue(incoming.motionSensitivity)
            neutralTiltX = incoming.neutralTiltX
            neutralTiltY = incoming.neutralTiltY
            donationCounts = context.saveFidgetDonationCounts(incoming.donationCounts)
            context.saveFidgetSettings(
                FidgetSettingsState(
                    fidgetCount = fidgetCount,
                    mainColorArgb = mainColorArgb,
                    backgroundColorArgb = backgroundColorArgb,
                    backgroundImageUri = backgroundImageUri,
                    ringColorArgb = ringColorArgb,
                    spinnerStyleIndex = spinnerStyleIndex,
                    spinnerMultiEnabled = spinnerMultiEnabled,
                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                    soundFeedbackEnabled = soundFeedbackEnabled,
                    feedbackSoundMode = feedbackSoundMode,
                    accentIntensityMode = accentIntensityMode,
                    rewardStyle = rewardStyle,
                    appLanguage = appLanguage,
                    keepScreenOn = keepScreenOn,
                    cpuPercentVisible = cpuPercentVisible,
                    pinnedToyIdsCsv = pinnedToyIdsCsv,
                    motionGesturesEnabled = motionGesturesEnabled,
                    gestureControlMode = gestureControlMode,
                    phoneMotionEnabled = phoneMotionEnabled,
                    tiltGestureEnabled = tiltGestureEnabled,
                    shakeGestureEnabled = shakeGestureEnabled,
                    motionSensitivity = motionSensitivity,
                    neutralTiltX = neutralTiltX,
                    neutralTiltY = neutralTiltY,
                ),
            )
        }
    }

    LaunchedEffect(
        fidgetSyncReady,
        toyIndex,
        fidgetCount,
        mainColorArgb,
        backgroundColorArgb,
        ringColorArgb,
        hapticFeedbackEnabled,
        soundFeedbackEnabled,
        feedbackSoundMode,
        accentIntensityMode,
        appLanguage,
        keepScreenOn,
        cpuPercentVisible,
        pinnedToyIdsCsv,
        donationCounts,
        motionGesturesEnabled,
        gestureControlMode,
        phoneMotionEnabled,
        tiltGestureEnabled,
        shakeGestureEnabled,
        motionSensitivity,
        neutralTiltX,
        neutralTiltY,
    ) {
        if (!fidgetSyncReady) return@LaunchedEffect
        debounceFidgetSyncPublish {
            fidgetSync.publish(currentFidgetSyncState())
        }
    }

    LaunchedEffect(Unit) {
        delay(1_500L)
        fidgetSyncReady = true
    }

    LaunchedEffect(Unit) {
        var previousFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (previousFrameNanos != 0L) {
                    val elapsedSeconds = (frameNanos - previousFrameNanos) / 1_000_000_000f
                    rotationDegrees += spinVelocityDegreesPerSecond * elapsedSeconds
                    spinVelocityDegreesPerSecond *= 0.992f
                    if (spinVelocityDegreesPerSecond in -3f..3f) {
                        spinVelocityDegreesPerSecond = 0f
                    }
                    if (!slingshotPulling && slingshotVelocity.vectorLength() > 0f) {
                        val nextPosition = slingshotPosition + slingshotVelocity * elapsedSeconds
                        var nextX = nextPosition.x
                        var nextY = nextPosition.y
                        var nextVelocityX = slingshotVelocity.x
                        var nextVelocityY = slingshotVelocity.y

                        if (nextX > SLINGSHOT_BOUNCE_LIMIT_DP) {
                            nextX = SLINGSHOT_BOUNCE_LIMIT_DP
                            nextVelocityX = -abs(nextVelocityX) * SLINGSHOT_BOUNCE_DAMPING
                        } else if (nextX < -SLINGSHOT_BOUNCE_LIMIT_DP) {
                            nextX = -SLINGSHOT_BOUNCE_LIMIT_DP
                            nextVelocityX = abs(nextVelocityX) * SLINGSHOT_BOUNCE_DAMPING
                        }

                        if (nextY > SLINGSHOT_BOUNCE_LIMIT_DP) {
                            nextY = SLINGSHOT_BOUNCE_LIMIT_DP
                            nextVelocityY = -abs(nextVelocityY) * SLINGSHOT_BOUNCE_DAMPING
                        } else if (nextY < -SLINGSHOT_BOUNCE_LIMIT_DP) {
                            nextY = -SLINGSHOT_BOUNCE_LIMIT_DP
                            nextVelocityY = abs(nextVelocityY) * SLINGSHOT_BOUNCE_DAMPING
                        }

                        slingshotPosition = Offset(nextX, nextY)
                        slingshotVelocity = Offset(nextVelocityX, nextVelocityY) * SLINGSHOT_ROLLING_DAMPING
                        if (slingshotVelocity.vectorLength() < SLINGSHOT_STOP_SPEED) {
                            slingshotVelocity = Offset.Zero
                        }
                    }
                    if (homeFanOn) {
                        homeFanRotation += elapsedSeconds * 420f
                    }
                    if (squishyPressure > 0f || squishyPull.vectorLength() > 0.1f) {
                        squishyPressure *= 0.9f
                        squishyPull *= 0.84f
                        if (squishyPressure < 0.02f) {
                            squishyPressure = 0f
                        }
                        if (squishyPull.vectorLength() < 0.4f) {
                            squishyPull = Offset.Zero
                        }
                    }
                    if (worryStoneRub > 0f) {
                        worryStoneRub *= 0.965f
                        if (worryStoneRub < 0.01f) {
                            worryStoneRub = 0f
                            worryStoneTouchPoint = null
                        }
                    }
                    if (zenTracePoints.isNotEmpty()) {
                        zenTracePoints = zenTracePoints.takeLast(ZEN_TRACE_MAX_POINTS)
                    }
                    touchPulse *= 0.9f
                    rewardPulse *= 0.94f
                    rewardFlash = (rewardFlash - 0.035f).coerceAtLeast(0f)
                }
                previousFrameNanos = frameNanos
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val compactWatch = minOf(maxWidth, maxHeight) <= 205.dp
        val phoneLayout = maxHeight >= 360.dp || maxWidth >= 360.dp
        val phoneLandscape = phoneLayout && maxWidth > maxHeight
        val phonePortrait = phoneLayout && !phoneLandscape
        val systemFontScale = LocalDensity.current.fontScale
        val wearLargeFontTopExtra = if (phoneLayout) {
            0.dp
        } else {
            ((systemFontScale - 1f).coerceIn(0f, 0.5f) * 24f).dp
        }
        val navButtonWidth = when {
            compactWatch -> 28.dp
            phonePortrait -> 88.dp
            phoneLayout -> 54.dp
            else -> 36.dp
        }
        val navButtonHeight = when {
            compactWatch -> 48.dp
            phonePortrait -> 50.dp
            phoneLayout -> 76.dp
            else -> 56.dp
        }
        val navButtonPadding = when {
            phoneLandscape -> 78.dp
            phoneLayout -> 26.dp
            compactWatch -> 16.dp
            else -> 18.dp
        }
        val bottomContentPadding = when {
            phoneLandscape -> 10.dp
            phoneLayout -> 16.dp
            else -> 18.dp
        }
        val pageHorizontalPadding = if (phoneLandscape) 48.dp else 10.dp
        val pageTopPadding = when {
            phoneLandscape -> 10.dp
            phoneLayout -> 34.dp
            toyIndex == FIDGET_MENU_INDEX -> 24.dp + wearLargeFontTopExtra
            else -> 34.dp + wearLargeFontTopExtra
        }
        val phoneToyScale = when {
            phoneLandscape -> 1.72f
            phoneLayout -> 1.8f
            else -> 1f
        }
        val spinnerClusterEnabled = phoneEdition && phoneLayout && spinnerMultiEnabled
        val stageToyScale = when {
            spinnerClusterEnabled && toyIndex == FIDGET_SPINNER_INDEX && phoneLandscape -> 1.58f
            spinnerClusterEnabled && toyIndex == FIDGET_SPINNER_INDEX -> 1.42f
            else -> phoneToyScale
        }
        val stageOffsetY = when {
            phoneLandscape -> 0.dp
            phoneLayout -> 10.dp
            else -> 0.dp
        }
        // Keep the watch arrows centered on the measured stage trim, not the screen midpoint.
        val navOffsetY = if (phoneLayout) 0.dp else (-2).dp

        if (phoneLayout && backgroundImageBitmap != null) {
            Image(
                bitmap = backgroundImageBitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.78f,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = if (phoneLayout && backgroundImageBitmap != null) {
                            listOf(
                                backgroundColor.copy(alpha = 0.24f),
                                backgroundColor.copy(alpha = 0.46f),
                                Color.Black.copy(alpha = 0.76f),
                            )
                        } else {
                            listOf(
                                backgroundColor.copy(alpha = 0.98f),
                                backgroundColor.copy(alpha = 0.78f),
                                Color.Black,
                            )
                        },
                    ),
                )
                .then(
                    if (phoneLandscape) {
                        Modifier.navigationBarsPadding()
                    } else {
                        Modifier
                    },
                ),
        ) {

        if (!phoneLayout) {
            FidgetOuterRing(
                ringColor = ringColor,
                rainbow = ringIsRainbow,
                rainbowRotationDegrees = rainbowRotationDegrees,
                touchPulse = touchPulse,
                rewardPulse = rewardPulse,
                edgeInset = if (compactWatch) 11.dp else 13.dp,
            )
        }

        if (!phonePortrait && toyIndex != FIDGET_MENU_INDEX && toyIndex != FIDGET_WALL_INDEX) {
            FidgetTrackNavButton(
                isNext = false,
                accentColor = mainColor,
                accentColorArgb = mainColorArgb,
                width = navButtonWidth,
                height = navButtonHeight,
                arrowRotationDegrees = 0f,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(y = navOffsetY)
                    .padding(start = navButtonPadding),
                onClick = {
                    triggerFeedback()
                    moveInFidgetOrder(-1)
                },
            )

            FidgetTrackNavButton(
                isNext = true,
                accentColor = mainColor,
                accentColorArgb = mainColorArgb,
                width = navButtonWidth,
                height = navButtonHeight,
                arrowRotationDegrees = 0f,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(y = navOffsetY)
                    .padding(end = navButtonPadding),
                onClick = {
                    triggerFeedback()
                    moveInFidgetOrder(1)
                },
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (phoneLayout) {
                        Modifier.navigationBarsPadding()
                    } else {
                        Modifier
                    },
                )
                .padding(
                    start = pageHorizontalPadding,
                    top = pageTopPadding,
                    end = pageHorizontalPadding,
                    bottom = bottomContentPadding,
                ),
        ) {
            if (toyIndex != FIDGET_MENU_INDEX) {
                FidgetTitleBar(
                    title = fidgetText.title,
                    toyName = currentToyName,
                    accentColor = mainColor,
                    accentColorArgb = mainColorArgb,
                    phoneLayout = phoneLayout,
                    phoneLandscape = phoneLandscape,
                    showDonateButton = !wearEdition && phoneLayout && toyIndex != FIDGET_WALL_INDEX,
                    donateLabel = fidgetText.donate,
                    onDonateClick = {
                        triggerFeedback(countFidget = false)
                        donationPopupOpen = true
                    },
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            Box(
                contentAlignment = if (toyIndex == FIDGET_MENU_INDEX) {
                    Alignment.TopCenter
                } else {
                    Alignment.Center
                },
                modifier = Modifier
                    .then(
                        when {
                            phoneLayout && toyIndex != FIDGET_MENU_INDEX && toyIndex != FIDGET_WALL_INDEX -> {
                                Modifier
                                    .fillMaxWidth(0.94f)
                                    .weight(1f)
                            }
                            toyIndex != FIDGET_MENU_INDEX && toyIndex != FIDGET_WALL_INDEX -> {
                                // Keep every toy stage inside the round-display safe zone.
                                Modifier
                                    .fillMaxWidth(0.86f)
                                    .weight(1f)
                            }
                            else -> {
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            }
                        },
                    )
                    .offset {
                        IntOffset(
                            x = 0,
                            y = if (
                                toyIndex != FIDGET_MENU_INDEX &&
                                toyIndex != FIDGET_WALL_INDEX
                            ) {
                                stageOffsetY.roundToPx()
                            } else {
                                0
                            },
                        )
                    }
                    .then(
                        if (toyIndex != FIDGET_MENU_INDEX && toyIndex != FIDGET_WALL_INDEX) {
                            Modifier
                                .padding(
                                    start = 2.dp,
                                    top = if (phoneLayout) 6.dp else 1.dp,
                                    end = 2.dp,
                                    bottom = if (phoneLayout) 6.dp else 4.dp,
                                )
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.16f))
                                .border(
                                    1.dp,
                                    mainColor.copy(alpha = 0.18f),
                                    RoundedCornerShape(20.dp),
                                )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (toyIndex != FIDGET_MENU_INDEX && toyIndex != FIDGET_WALL_INDEX) {
                    FidgetStageBackdrop(
                        ringColor = ringColor,
                        rainbow = ringIsRainbow,
                        rainbowRotationDegrees = rainbowRotationDegrees,
                        touchPulse = touchPulse,
                        rewardPulse = rewardPulse,
                        toyScale = phoneToyScale,
                    )
                }

                Box(
                    modifier = if (phoneLayout && toyIndex != FIDGET_MENU_INDEX && toyIndex != FIDGET_WALL_INDEX) {
                        Modifier.scale(stageToyScale)
                    } else {
                        Modifier
                    },
                ) {
                when (toyIndex) {
                    FIDGET_WALL_INDEX -> {
                        FidgetSelectionWallPage(
                            pinnedToyIds = pinnedToyIds,
                            favoriteToyId = favoriteToyId,
                            language = appLanguage,
                            text = fidgetText,
                            accentColor = mainColor,
                            accentColorArgb = mainColorArgb,
                            resetKey = wallResetToken,
                            onToySelected = { toyId ->
                                triggerFeedback(countFidget = false)
                                toyIndex = toyId
                            },
                            onPinToggle = ::togglePinnedToy,
                            onFavoriteSelected = ::setFavoriteToy,
                        )
                    }

                    FIDGET_SPINNER_INDEX -> {
                        val spinnerShellShape = if (spinnerClusterEnabled) {
                            RoundedCornerShape(28.dp)
                        } else {
                            CircleShape
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(if (spinnerClusterEnabled) 214.dp else 118.dp)
                                .height(118.dp)
                                .clip(spinnerShellShape)
                                .background(Color.White.copy(alpha = 0.07f))
                                .border(1.dp, mainColor.copy(alpha = 0.7f), spinnerShellShape)
                                .combinedClickable(
                                    onClick = {},
                                onLongClick = {
                                    triggerFeedback()
                                    spinVelocityDegreesPerSecond = 0f
                                    touchPulse = 1f
                                },
                                )
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                        spinVelocityDegreesPerSecond = 0f
                                        touchPulse = 1f
                                        triggerFeedback()
                                    },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val center = androidx.compose.ui.geometry.Offset(
                                                x = size.width / 2f,
                                                y = size.height / 2f,
                                            )
                                            val previousAngle = angleDegrees(change.previousPosition, center)
                                            val currentAngle = angleDegrees(change.position, center)
                                            val deltaDegrees = shortestAngleDelta(previousAngle, currentAngle)
                                            val elapsedMillis = (change.uptimeMillis - change.previousUptimeMillis)
                                                .coerceAtLeast(1L)

                                            rotationDegrees += deltaDegrees
                                            spinVelocityDegreesPerSecond = (deltaDegrees / elapsedMillis * 1_000f)
                                                .coerceIn(-2_700f, 2_700f)
                                            touchPulse = 1f
                                        },
                                        onDragEnd = {
                                            spinVelocityDegreesPerSecond *= 1.35f
                                        },
                                        onDragCancel = {
                                            spinVelocityDegreesPerSecond = 0f
                                        },
                                    )
                                },
                        ) {
                            FidgetSpinner(
                                rotation = rotationDegrees,
                                style = spinnerStyle,
                                mainColor = mainColor,
                                ringColor = ringColor,
                                showSatellites = spinnerClusterEnabled,
                            )
                        }
                    }

                    FIDGET_SWITCH_INDEX -> {
                        SwitchFidgetToy(
                            switchMask = switchMask,
                            onSwitchToggle = { index ->
                                triggerFeedback()
                                switchMask = switchMask xor (1 shl index)
                            },
                        )
                    }

                    else -> {
                        if (toyIndex == FIDGET_SWITCH_MAZE_INDEX) {
                            SwitchMazeToy(
                                mazePosition = mazePosition,
                                onMove = { deltaColumn, deltaRow ->
                                    triggerFeedback()
                                    val column = (mazePosition % SWITCH_MAZE_COLUMNS + deltaColumn)
                                        .coerceIn(0, SWITCH_MAZE_COLUMNS - 1)
                                    val row = (mazePosition / SWITCH_MAZE_COLUMNS + deltaRow)
                                        .coerceIn(0, SWITCH_MAZE_ROWS - 1)
                                    mazePosition = row * SWITCH_MAZE_COLUMNS + column
                                },
                            )
                        } else if (toyIndex == FIDGET_FREE_BUTTON_INDEX) {
                            FreeMoveButtonToy(
                                buttonPositions = freeButtonPositions,
                                onButtonDragStart = {
                                    triggerFeedback()
                                },
                                onButtonMove = { index, delta ->
                                    freeButtonPositions = freeButtonPositions.mapIndexed { positionIndex, position ->
                                        if (positionIndex == index) {
                                            Offset(
                                                x = (position.x + delta.x).coerceIn(-45f, 45f),
                                                y = (position.y + delta.y).coerceIn(-45f, 45f),
                                            )
                                        } else {
                                            position
                                        }
                                    }
                                },
                            )
                        } else if (toyIndex == FIDGET_WHACK_BUTTON_INDEX) {
                            WhackColorButtonToy(
                                buttonPositions = whackButtonPositions,
                                onButtonTap = { index ->
                                    triggerFeedback()
                                    whackButtonPositions = whackButtonPositions.mapIndexed { positionIndex, position ->
                                        if (positionIndex == index) {
                                            randomOpenWhackPosition(
                                                currentPosition = position,
                                                occupiedPositions = whackButtonPositions.filterIndexed { otherIndex, _ ->
                                                    otherIndex != index
                                                }.toSet(),
                                            )
                                        } else {
                                            position
                                        }
                                    }
                                },
                            )
                        } else if (toyIndex == FIDGET_SQUISHY_INDEX) {
                            SquishyFidgetToy(
                                pullOffset = squishyPull,
                                pressure = squishyPressure,
                                accentColor = mainColor,
                                onPress = {
                                    triggerFeedback()
                                    squishyPressure = 1f
                                },
                                onPull = { pullOffset, pressure ->
                                    squishyPull = pullOffset
                                    squishyPressure = pressure
                                    touchPulse = 1f
                                },
                                onRelease = {
                                    triggerFeedback()
                                    squishyPressure = 0.7f
                                },
                            )
                        } else if (toyIndex == FIDGET_MAG_SNAP_INDEX) {
                            MagSnapFidgetToy(
                                position = magSnapPosition,
                                onMove = { delta ->
                                    val nextPosition = (magSnapPosition + delta).coerceIn(0, 2)
                                    if (nextPosition != magSnapPosition) {
                                        triggerFeedback()
                                        magSnapPosition = nextPosition
                                    }
                                },
                            )
                        } else if (toyIndex == FIDGET_POP_GRID_INDEX) {
                            PopGridFidgetToy(
                                popMask = popGridMask,
                                onPop = { index ->
                                    triggerFeedback()
                                    popGridMask = popGridMask xor (1 shl index)
                                    if (popGridMask == (1 shl POP_GRID_COUNT) - 1) {
                                        rewardPulse = 1f
                                    }
                                },
                                onReset = {
                                    triggerFeedback()
                                    popGridMask = 0
                                },
                            )
                        } else if (toyIndex == FIDGET_INFINITY_CUBE_INDEX) {
                            InfinityFlipFidgetToy(
                                fold = infinityFold,
                                onFlip = {
                                    triggerFeedback()
                                    infinityFold = (infinityFold + 1).wrapFidgetIndex(4)
                                },
                                flippedCardMask = infinityCardFlipMask,
                                onCardFlip = { index ->
                                    triggerFeedback()
                                    infinityCardFlipMask = infinityCardFlipMask xor (1 shl index)
                                    infinityFold = (infinityFold + 1).wrapFidgetIndex(4)
                                },
                            )
                        } else if (toyIndex == FIDGET_RATCHET_RING_INDEX) {
                            RatchetRingFidgetToy(
                                step = ratchetStep,
                                onStep = { delta ->
                                    triggerFeedback()
                                    ratchetStep = (ratchetStep + delta).wrapFidgetIndex(RATCHET_STEP_COUNT)
                                    touchPulse = 1f
                                },
                            )
                        } else if (toyIndex == FIDGET_LIQUID_MAZE_INDEX) {
                            LiquidMazeFidgetToy(
                                blobPosition = liquidBlobPosition,
                                blobTrail = liquidBlobTrail,
                                puzzle = liquidMazePuzzle,
                                onMove = { delta ->
                                    val nextPosition = moveLiquidMazeBlob(
                                        position = liquidBlobPosition,
                                        delta = delta,
                                        puzzle = liquidMazePuzzle,
                                    )
                                    liquidBlobTrail = (liquidBlobTrail + liquidBlobPosition).takeLast(14)
                                    liquidBlobPosition = nextPosition
                                    touchPulse = 1f
                                },
                                onRelease = {
                                    triggerFeedback()
                                },
                                onRefresh = {
                                    triggerFeedback()
                                    liquidMazePuzzle = generateLiquidMazePuzzle()
                                    liquidBlobPosition = liquidMazeStartPosition(liquidMazePuzzle)
                                    liquidBlobTrail = emptyList()
                                },
                            )
                        } else if (toyIndex == FIDGET_GEAR_JAM_INDEX) {
                            GearJamFidgetToy(
                                rotation = gearRotation,
                                onTurn = { delta ->
                                    triggerFeedback()
                                    gearRotation += delta
                                    touchPulse = 1f
                                },
                                onResize = {
                                    triggerFeedback()
                                    touchPulse = 1f
                                },
                            )
                        } else if (toyIndex == FIDGET_WORRY_STONE_INDEX) {
                            WorryStoneFidgetToy(
                                rub = worryStoneRub,
                                touchPoint = worryStoneTouchPoint,
                                accentColor = mainColor,
                                onRub = { position, delta ->
                                    worryStoneTouchPoint = position
                                    worryStoneRub = (worryStoneRub + delta.vectorLength() / 38f).coerceIn(0f, 1f)
                                    triggerFeedback()
                                },
                            )
                        } else if (toyIndex == FIDGET_KEY_CLICKS_INDEX) {
                            KeyClicksFidgetToy(
                                keyMask = keyClickMask,
                                wearEdition = wearEdition,
                                onKeyPress = { index ->
                                    triggerFeedback()
                                    keyClickMask = keyClickMask xor (1 shl index)
                                },
                            )
                        } else if (toyIndex == FIDGET_SYMBOL_DOCK_INDEX) {
                            SymbolDockFidgetToy(
                                padStates = dockPadStates,
                                accentColor = mainColor,
                                ringColor = ringColor,
                                onPadPress = { padIndex ->
                                    dockPadStates = context.advanceFidgetDockPad(padIndex)
                                    triggerFeedback()
                                    context.refreshFidgetDockWidget()
                                },
                            )
                        } else if (toyIndex == FIDGET_ZEN_TRACE_INDEX) {
                            ZenTraceFidgetToy(
                                tracePoints = zenTracePoints,
                                accentColor = mainColor,
                                onTrace = { point ->
                                    zenTracePoints = (zenTracePoints + point).takeLast(ZEN_TRACE_MAX_POINTS)
                                    touchPulse = 1f
                                },
                                onTraceStart = {
                                    triggerFeedback()
                                },
                                onClear = {
                                    triggerFeedback()
                                    zenTracePoints = emptyList()
                                },
                            )
                        } else if (toyIndex == FIDGET_BEAT_MACHINE_INDEX) {
                            BeatMachineFidgetToy(
                                activePad = activeBeatPad,
                                text = fidgetText,
                                accentColor = mainColor,
                                accentColorArgb = mainColorArgb,
                                onPadPress = { index ->
                                    activeBeatPad = index
                                    touchPulse = 1f
                                    rewardPulse = rewardPulse.coerceAtLeast(0.48f)
                                    triggerBeatPad(index)
                                },
                            )
                        } else if (toyIndex == FIDGET_SLINGSHOT_INDEX) {
                            SlingshotFidgetToy(
                                ballPosition = slingshotPosition,
                                pullLimit = SLINGSHOT_PULL_LIMIT_DP,
                                onPullStart = {
                                    triggerFeedback()
                                    slingshotPulling = true
                                    slingshotVelocity = Offset.Zero
                                },
                                onPullMove = { pullOffset ->
                                    slingshotPosition = pullOffset
                                },
                                onRelease = { pullOffset ->
                                    triggerFeedback()
                                    slingshotPulling = false
                                    slingshotPosition = pullOffset
                                    slingshotVelocity = if (pullOffset.vectorLength() > 2f) {
                                        -pullOffset * SLINGSHOT_LAUNCH_MULTIPLIER
                                    } else {
                                        Offset.Zero
                                    }
                                },
                            )
                        } else if (toyIndex == FIDGET_MAZE_INDEX) {
                            MazeFidgetToy(
                                puzzle = mazePuzzle,
                                playerCell = mazePlayerCell,
                                onMove = { direction ->
                                    val nextCell = mazePuzzle.nextCell(mazePlayerCell, direction)
                                    if (nextCell != mazePlayerCell) {
                                        triggerFeedback()
                                        mazePlayerCell = nextCell
                                        if (nextCell == mazePuzzle.endCell) {
                                            rewardPulse = 1f
                                        }
                                    }
                                },
                                onRefresh = ::refreshMazeShuffle,
                            )
                        } else if (toyIndex == FIDGET_CENTER_DROP_MAZE_INDEX) {
                            CenterDropMazeFidgetToy(
                                maze = centerDropMaze,
                                ballPosition = centerDropBallPosition,
                                solved = centerDropSolved,
                                onMove = { delta ->
                                    if (!centerDropSolved) {
                                        val nextPosition = moveCenterDropBall(
                                            position = centerDropBallPosition,
                                            delta = delta,
                                            maze = centerDropMaze,
                                        )
                                        centerDropBallPosition = nextPosition
                                        touchPulse = 1f
                                        if (nextPosition.vectorLength() <= CENTER_DROP_HOLE_RADIUS_DP) {
                                            centerDropSolved = true
                                            rewardPulse = 1f
                                            triggerFeedback()
                                        }
                                    }
                                },
                                onRelease = { triggerFeedback() },
                                onRefresh = ::refreshCenterDropMaze,
                            )
                        } else if (toyIndex == FIDGET_BALL_SORT_MAZE_INDEX) {
                            BallSortMazeFidgetToy(
                                maze = ballSortMaze,
                                positions = ballSortPositions,
                                locked = ballSortLocked,
                                onMove = { delta ->
                                    val next = moveBallSortBalls(
                                        positions = ballSortPositions,
                                        locked = ballSortLocked,
                                        maze = ballSortMaze,
                                        delta = delta,
                                    )
                                    val newlyLocked = next.locked.count { it } > ballSortLocked.count { it }
                                    ballSortPositions = next.positions
                                    ballSortLocked = next.locked
                                    touchPulse = 1f
                                    if (newlyLocked) triggerFeedback()
                                    if (next.locked.all { it }) rewardPulse = 1f
                                },
                                onRelease = { triggerFeedback() },
                                onRefresh = ::refreshBallSortMaze,
                            )
                        } else if (toyIndex == FIDGET_WINDOW_INDEX) {
                            WindowFidgetToy(
                                leftOpen = homeWindowPaneMask and 1 != 0,
                                rightOpen = homeWindowPaneMask and 2 != 0,
                                text = fidgetText,
                                accentColor = mainColor,
                                onPaneToggle = { pane ->
                                    triggerFeedback()
                                    homeWindowPaneMask = homeWindowPaneMask xor (1 shl pane)
                                },
                            )
                        } else if (toyIndex == FIDGET_DOOR_INDEX) {
                            DoorFidgetToy(
                                open = homeDoorOpen,
                                text = fidgetText,
                                accentColor = mainColor,
                                onToggle = {
                                    triggerFeedback()
                                    homeDoorOpen = !homeDoorOpen
                                },
                            )
                        } else if (toyIndex == FIDGET_LIGHT_INDEX) {
                            LightFidgetToy(
                                on = homeLightOn,
                                accentColor = mainColor,
                                onToggle = {
                                    triggerFeedback()
                                    homeLightOn = !homeLightOn
                                },
                            )
                        } else if (toyIndex == FIDGET_FAN_INDEX) {
                            FanFidgetToy(
                                on = homeFanOn,
                                rotation = homeFanRotation,
                                accentColor = mainColor,
                                accentColorArgb = mainColorArgb,
                                onToggle = {
                                    triggerFeedback()
                                    homeFanOn = !homeFanOn
                                },
                            )
                        } else if (toyIndex == FIDGET_SINK_INDEX) {
                            SinkFidgetToy(
                                hotOn = homeSinkTapMask and 1 != 0,
                                coldOn = homeSinkTapMask and 2 != 0,
                                text = fidgetText,
                                accentColor = mainColor,
                                onKnobToggle = { knob ->
                                    triggerFeedback()
                                    homeSinkTapMask = homeSinkTapMask xor (1 shl knob)
                                },
                            )
                        } else {
                            FidgetMenuPage(
                                appLanguage = appLanguage,
                                text = fidgetText,
                                hapticFeedbackEnabled = hapticFeedbackEnabled,
                                soundFeedbackEnabled = soundFeedbackEnabled,
                                feedbackSoundMode = feedbackSoundMode,
                                accentIntensityMode = accentIntensityMode,
                                rewardStyle = rewardStyle,
                                keepScreenOn = keepScreenOn,
                                cpuPercentVisible = cpuPercentVisible,
                                cpuUsagePercent = cpuUsagePercent,
                                mainColorArgb = mainColorArgb,
                                backgroundColorArgb = backgroundColorArgb,
                                ringColorArgb = ringColorArgb,
                                spinnerStyleIndex = spinnerStyleIndex,
                                spinnerMultiEnabled = spinnerMultiEnabled,
                                backgroundImageSelected = backgroundImageUri != null,
                                onReviewClick = {
                                    triggerFeedback(countFidget = false)
                                    reviewStatusText = ""
                                    reviewPopupOpen = true
                                },
                                onDonateClick = {
                                    triggerFeedback(countFidget = false)
                                    donationPopupOpen = true
                                },
                                phoneSurfacesEnabled = phoneEdition,
                                onAddDockClick = {
                                    triggerFeedback(countFidget = false)
                                    context.requestFidgetDockPin()
                                },
                                onSpinnerWallpaperClick = {
                                    triggerFeedback(countFidget = false)
                                    context.openFidgetSpinnerWallpaper()
                                },
                                motionGesturesEnabled = motionGesturesEnabled,
                                phoneMotionEnabled = phoneMotionEnabled,
                                tiltGestureEnabled = tiltGestureEnabled,
                                shakeGestureEnabled = shakeGestureEnabled,
                                motionSensitivity = motionSensitivity,
                                motionSensorsAvailable = motionSnapshot.sensorsAvailable,
                                onMotionGesturesEnabledChange = { enabled ->
                                    motionGesturesEnabled = enabled
                                    gestureControlMode = if (enabled) {
                                        FidgetControlMode.Both
                                    } else {
                                        FidgetControlMode.Touch
                                    }
                                    if (phoneEdition) phoneMotionEnabled = enabled
                                    triggerFeedback(countFidget = false)
                                },
                                onTiltGestureToggle = {
                                    tiltGestureEnabled = !tiltGestureEnabled
                                    triggerFeedback(countFidget = false)
                                },
                                onShakeGestureToggle = {
                                    shakeGestureEnabled = !shakeGestureEnabled
                                    triggerFeedback(countFidget = false)
                                },
                                onMotionSensitivityChoice = { sensitivity ->
                                    motionSensitivity = sensitivity
                                    triggerFeedback(countFidget = false)
                                },
                                onCalibrateTilt = {
                                    if (motionSnapshot.sensorsAvailable) {
                                        neutralTiltX = motionSnapshot.rawTilt.x
                                        neutralTiltY = motionSnapshot.rawTilt.y
                                        triggerFeedback(countFidget = false)
                                    }
                                },
                                onDoneClick = ::saveSettingsAndCloseMenu,
                                onRewardReset = {
                                    fidgetCount = 0
                                    context.saveFidgetCount(0)
                                    rewardPulse = 0f
                                    touchPulse = 0f
                                    triggerFeedback(countFidget = false)
                                },
                                onKeepScreenToggle = {
                                    keepScreenOn = !keepScreenOn
                                    triggerFeedback(countFidget = false)
                                },
                                onCpuToggle = {
                                    cpuPercentVisible = !cpuPercentVisible
                                    triggerFeedback(countFidget = false)
                                },
                                onLanguageChoice = { language ->
                                    appLanguage = language
                                    donationThanksText = ""
                                    triggerFeedback(countFidget = false)
                                },
                                onAccentIntensityModeChoice = { mode ->
                                    accentIntensityMode = mode
                                    triggerFeedback(countFidget = false)
                                },
                                onRewardStyleChoice = { style ->
                                    rewardStyle = style
                                    triggerFeedback(countFidget = false)
                                },
                                onMainColorChoice = { colorArgb ->
                                    mainColorArgb = colorArgb
                                    triggerFeedback(countFidget = false)
                                },
                                onBackgroundColorChoice = { colorArgb ->
                                    backgroundColorArgb = colorArgb
                                    triggerFeedback(countFidget = false)
                                },
                                onRingColorChoice = { colorArgb ->
                                    ringColorArgb = colorArgb
                                    rewardPulse = 1f
                                    triggerFeedback(countFidget = false)
                                },
                                onSpinnerStyleChoice = { styleIndex ->
                                    spinnerStyleIndex = fidgetSpinnerStyle(styleIndex).index
                                    touchPulse = 1f
                                    triggerFeedback(countFidget = false)
                                },
                                onSpinnerMultiChoice = { multiEnabled ->
                                    spinnerMultiEnabled = multiEnabled
                                    touchPulse = 1f
                                    triggerFeedback(countFidget = false)
                                },
                                onBackgroundImagePick = {
                                    backgroundImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                onBackgroundImageClear = {
                                    backgroundImageUri = null
                                    triggerFeedback(countFidget = false)
                                },
                                onHapticToggle = {
                                    val nextEnabled = !hapticFeedbackEnabled
                                    hapticFeedbackEnabled = nextEnabled
                                    feedbackController.play(
                                        hapticEnabled = nextEnabled,
                                        soundEnabled = false,
                                        beatSoundMode = feedbackSoundMode,
                                        accentIntensityMode = accentIntensityMode,
                                    )
                                },
                                onSoundToggle = {
                                    val nextEnabled = !soundFeedbackEnabled
                                    soundFeedbackEnabled = nextEnabled
                                    feedbackController.play(
                                        hapticEnabled = false,
                                        soundEnabled = nextEnabled,
                                        beatSoundMode = feedbackSoundMode,
                                        accentIntensityMode = accentIntensityMode,
                                    )
                                },
                                onSoundModeChoice = { mode ->
                                    feedbackSoundMode = mode
                                    feedbackController.play(
                                        hapticEnabled = hapticFeedbackEnabled,
                                        soundEnabled = true,
                                        beatSoundMode = mode,
                                        accentIntensityMode = accentIntensityMode,
                                    )
                                },
                            )
                        }
                    }
                }
                }

                if (phonePortrait && toyIndex != FIDGET_MENU_INDEX && toyIndex != FIDGET_WALL_INDEX) {
                    FidgetTrackNavButton(
                        isNext = false,
                        accentColor = mainColor,
                        accentColorArgb = mainColorArgb,
                        width = navButtonWidth,
                        height = navButtonHeight,
                        arrowRotationDegrees = 90f,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp)
                            .zIndex(5f),
                        onClick = {
                            triggerFeedback()
                            moveInFidgetOrder(-1)
                        },
                    )
                    FidgetTrackNavButton(
                        isNext = true,
                        accentColor = mainColor,
                        accentColorArgb = mainColorArgb,
                        width = navButtonWidth,
                        height = navButtonHeight,
                        arrowRotationDegrees = 90f,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                            .zIndex(5f),
                        onClick = {
                            triggerFeedback()
                            moveInFidgetOrder(1)
                        },
                    )
                }
            }

            if (toyIndex != FIDGET_MENU_INDEX && toyIndex != FIDGET_WALL_INDEX) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            when {
                                phoneLandscape -> 46.dp
                                phoneLayout -> 104.dp
                                else -> 56.dp
                            },
                        )
                        .then(
                            if (phoneLayout && !phoneLandscape) {
                                Modifier.background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            mainColor.copy(alpha = 0.06f),
                                            mainColor.copy(alpha = 0.14f),
                                        ),
                                    ),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(
                            start = if (phoneLayout && !phoneLandscape) 12.dp else 0.dp,
                            top = if (phoneLayout && !phoneLandscape) 5.dp else 0.dp,
                            end = if (phoneLayout && !phoneLandscape) 12.dp else 0.dp,
                            bottom = 3.dp,
                        ),
                ) {
                    if (phoneLayout && !phoneLandscape) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth(0.76f)
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            mainColor.copy(alpha = 0.58f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            if (phoneLayout && !phoneLandscape) 12.dp else 8.dp,
                            Alignment.CenterHorizontally,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = if (phoneLayout && !phoneLandscape) 4.dp else 0.dp),
                    ) {
                        FidgetNavButton(
                            text = fidgetText.menu,
                            wide = true,
                            phoneLayout = phoneLayout,
                            phoneLandscape = phoneLandscape,
                            accentColor = mainColor,
                            accentColorArgb = mainColorArgb,
                            onClick = {
                                triggerFeedback(countFidget = false)
                                toyIndex = FIDGET_MENU_INDEX
                            },
                        )
                        FidgetNavButton(
                            text = fidgetText.toys,
                            wide = true,
                            primary = true,
                            phoneLayout = phoneLayout,
                            phoneLandscape = phoneLandscape,
                            accentColor = mainColor,
                            accentColorArgb = mainColorArgb,
                            onClick = {
                                triggerFeedback(countFidget = false)
                                wallResetToken += 1
                                toyIndex = FIDGET_WALL_INDEX
                            },
                        )
                    }
                    FidgetRewardChip(
                        text = fidgetText.rewardLine(fidgetCount, nextRewardCount),
                        ringColor = ringColor,
                        rainbow = ringIsRainbow,
                        phoneLayout = phoneLayout,
                        phoneLandscape = phoneLandscape,
                        onClick = {
                            rewardProgressPopupOpen = true
                            triggerFeedback(countFidget = false)
                        },
                        modifier = if (phoneLandscape) {
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp)
                        } else {
                            Modifier
                                .align(Alignment.BottomCenter)
                        },
                    )
                }
            }
        }

        if (rewardFlash > 0f && rewardStyle != FidgetRewardStyle.Calm) {
            val flashAlpha = when (rewardStyle) {
                FidgetRewardStyle.Glow -> rewardFlash * 0.16f
                FidgetRewardStyle.Celebrate -> {
                    (0.08f + abs(sin((1f - rewardFlash) * 32f)) * 0.34f) * rewardFlash
                }
                FidgetRewardStyle.Calm -> 0f
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha)),
            )
        }

        if (rewardMomentMessage != null) {
            FidgetRewardMomentToast(
                message = rewardMomentMessage.orEmpty(),
                ringColor = ringColor,
                phoneLayout = phoneLayout,
                phoneLandscape = phoneLandscape,
            )
        }

        if (rewardProgressPopupOpen) {
            FidgetRewardProgressPopup(
                count = fidgetCount,
                nextReward = nextRewardCount,
                appLanguage = appLanguage,
                ringColor = ringColor,
                onDismiss = { rewardProgressPopupOpen = false },
            )
        }

        if (reviewPopupOpen) {
            PlayStoreReviewPopup(
                text = fidgetText,
                statusText = reviewStatusText,
                onOpenReview = {
                    triggerFeedback()
                    if (!isInstalledFromPlay) {
                        reviewStatusText = fidgetText.installFromPlay
                        return@PlayStoreReviewPopup
                    }
                    context.openFidgetPlayStoreListing()
                    reviewPopupOpen = false
                },
                onOpenPrivacyPolicy = {
                    triggerFeedback()
                    context.openFidgetPrivacyPolicy()
                },
                onDismiss = {
                    triggerFeedback()
                    reviewStatusText = ""
                    reviewPopupOpen = false
                },
            )
        }

        if (donationPopupOpen && (!wearEdition || toyIndex == FIDGET_MENU_INDEX)) {
            FidgetDonationPopup(
                text = fidgetText,
                badge = donationBadge,
                statusText = donationThanksText.ifBlank {
                    donationCoordinator?.statusText ?: fidgetText.installFromPlay
                },
                onDonate = { productId ->
                    triggerFeedback()
                    if (!isInstalledFromPlay) {
                        donationThanksText = fidgetText.installFromPlay
                        return@FidgetDonationPopup
                    }
                    donationThanksText = ""
                    donationCoordinator?.buy(hostActivity, productId)
                },
                onDismiss = {
                    triggerFeedback()
                    donationPopupOpen = false
                },
            )
        }

        if (colorPopupOpen) {
            FidgetColorPopup(
                text = fidgetText,
                mainColorArgb = mainColorArgb,
                backgroundColorArgb = backgroundColorArgb,
                ringColorArgb = ringColorArgb,
                onMainColorChoice = { colorArgb ->
                    mainColorArgb = colorArgb
                    triggerFeedback(countFidget = false)
                },
                onBackgroundColorChoice = { colorArgb ->
                    backgroundColorArgb = colorArgb
                    triggerFeedback(countFidget = false)
                },
                onRingColorChoice = { colorArgb ->
                    ringColorArgb = colorArgb
                    rewardPulse = 1f
                    triggerFeedback(countFidget = false)
                },
                onDismiss = {
                    triggerFeedback(countFidget = false)
                    colorPopupOpen = false
                },
            )
        }

        if (intensityPopupOpen) {
            FidgetIntensityPopup(
                appLanguage = appLanguage,
                text = fidgetText,
                selectedMode = accentIntensityMode,
                onModeChoice = { mode ->
                    accentIntensityMode = mode
                    triggerFeedback(countFidget = false)
                },
                onDismiss = {
                    triggerFeedback(countFidget = false)
                    intensityPopupOpen = false
                },
            )
        }
        }
    }
}

internal const val FIDGET_WALL_INDEX = 0
internal const val FIDGET_SPINNER_INDEX = 1
private const val FIDGET_SWITCH_INDEX = 2
private const val FIDGET_SWITCH_MAZE_INDEX = 3
private const val FIDGET_FREE_BUTTON_INDEX = 4
private const val FIDGET_WHACK_BUTTON_INDEX = 5
private const val FIDGET_SLINGSHOT_INDEX = 6
private const val FIDGET_MAZE_INDEX = 7
private const val FIDGET_SQUISHY_INDEX = 8
private const val FIDGET_MAG_SNAP_INDEX = 9
private const val FIDGET_POP_GRID_INDEX = 10
private const val FIDGET_INFINITY_CUBE_INDEX = 11
private const val FIDGET_RATCHET_RING_INDEX = 12
private const val FIDGET_LIQUID_MAZE_INDEX = 13
private const val FIDGET_GEAR_JAM_INDEX = 14
private const val FIDGET_WORRY_STONE_INDEX = 15
private const val FIDGET_KEY_CLICKS_INDEX = 16
private const val FIDGET_ZEN_TRACE_INDEX = 17
private const val ZEN_TRACE_MAX_POINTS = 96
internal const val FIDGET_BEAT_MACHINE_INDEX = 18
private const val FIDGET_WINDOW_INDEX = 19
private const val FIDGET_DOOR_INDEX = 20
private const val FIDGET_LIGHT_INDEX = 21
private const val FIDGET_FAN_INDEX = 22
private const val FIDGET_SINK_INDEX = 23
internal const val FIDGET_SYMBOL_DOCK_INDEX = 24
private const val FIDGET_CENTER_DROP_MAZE_INDEX = 25
private const val FIDGET_BALL_SORT_MAZE_INDEX = 26
private const val FIDGET_MENU_INDEX = 27
private const val SWITCH_MAZE_COLUMNS = 4
private const val SWITCH_MAZE_ROWS = 4
private const val SWITCH_MAZE_CELL_COUNT = SWITCH_MAZE_COLUMNS * SWITCH_MAZE_ROWS
internal const val FIDGET_MAZE_COLUMNS = 5
internal const val FIDGET_MAZE_ROWS = 5
internal const val FIDGET_MAZE_CELL_COUNT = FIDGET_MAZE_COLUMNS * FIDGET_MAZE_ROWS
private const val FIDGET_MAZE_RESET_CELL = FIDGET_MAZE_COLUMNS - 1

private const val LIQUID_MAZE_BLOB_RADIUS_DP = 8f
private const val LIQUID_MAZE_BOARD_LIMIT_DP = 50f
private const val LIQUID_MAZE_CELL_SIZE_DP = 20f
private const val LIQUID_MAZE_MAX_STEP_DP = 5f

private fun generateLiquidMazePuzzle(): FidgetMazePuzzle = generateFidgetMazePuzzle()

internal fun moveLiquidMazeBlob(
    position: Offset,
    delta: Offset,
    puzzle: FidgetMazePuzzle,
): Offset {
    val movement = delta.limitedToLength(LIQUID_MAZE_MAX_STEP_DP)
    val stepCount = (movement.vectorLength() / 1.25f).toInt().coerceIn(1, 5)
    val step = movement * (1f / stepCount)
    var current = position.limitedToBox(LIQUID_MAZE_BOARD_LIMIT_DP, LIQUID_MAZE_BOARD_LIMIT_DP)

    repeat(stepCount) {
        val candidates = listOf(
            current + step,
            Offset(current.x + step.x, current.y),
            Offset(current.x, current.y + step.y),
        ).mapNotNull { candidate ->
            constrainLiquidMazeMove(current, candidate, puzzle)
        }

        // A blocked diagonal keeps whichever open passage best matches the pull,
        // so the liquid naturally glides along a maze wall instead of sticking.
        current = candidates.maxByOrNull { candidate ->
            (candidate.x - current.x) * step.x + (candidate.y - current.y) * step.y
        } ?: current
    }
    return current
}

internal fun liquidMazeStartPosition(puzzle: FidgetMazePuzzle): Offset =
    liquidMazeCellCenter(puzzle.startCell)

private fun constrainLiquidMazeMove(
    current: Offset,
    requested: Offset,
    puzzle: FidgetMazePuzzle,
): Offset? {
    val fromCell = liquidMazeCellAt(current)
    val bounded = requested.limitedToBox(LIQUID_MAZE_BOARD_LIMIT_DP, LIQUID_MAZE_BOARD_LIMIT_DP)
    val targetCell = liquidMazeCellAt(bounded)
    val fromColumn = fromCell % FIDGET_MAZE_COLUMNS
    val fromRow = fromCell / FIDGET_MAZE_COLUMNS
    val targetColumn = targetCell % FIDGET_MAZE_COLUMNS
    val targetRow = targetCell / FIDGET_MAZE_COLUMNS

    if (fromCell == targetCell) {
        return liquidMazeKeepInsideClosedWalls(bounded, fromCell, puzzle)
    }
    if (abs(targetColumn - fromColumn) + abs(targetRow - fromRow) != 1) return null

    val direction = when {
        targetColumn > fromColumn -> MazeDirection.Right
        targetColumn < fromColumn -> MazeDirection.Left
        targetRow > fromRow -> MazeDirection.Down
        else -> MazeDirection.Up
    }
    if (!liquidMazeCellHasOpening(puzzle, fromCell, direction)) return null
    return liquidMazeKeepInsideClosedWalls(bounded, targetCell, puzzle)
}

private fun liquidMazeKeepInsideClosedWalls(
    position: Offset,
    cell: Int,
    puzzle: FidgetMazePuzzle,
): Offset {
    val column = cell % FIDGET_MAZE_COLUMNS
    val row = cell / FIDGET_MAZE_COLUMNS
    val left = -LIQUID_MAZE_BOARD_LIMIT_DP + column * LIQUID_MAZE_CELL_SIZE_DP
    val top = -LIQUID_MAZE_BOARD_LIMIT_DP + row * LIQUID_MAZE_CELL_SIZE_DP
    val right = left + LIQUID_MAZE_CELL_SIZE_DP
    val bottom = top + LIQUID_MAZE_CELL_SIZE_DP
    val openings = puzzle.openings.getOrElse(cell) { 0 }
    val minimumX = if (openings and MAZE_OPEN_LEFT == 0) left + LIQUID_MAZE_BLOB_RADIUS_DP else left
    val maximumX = if (openings and MAZE_OPEN_RIGHT == 0) right - LIQUID_MAZE_BLOB_RADIUS_DP else right
    val minimumY = if (openings and MAZE_OPEN_UP == 0) top + LIQUID_MAZE_BLOB_RADIUS_DP else top
    val maximumY = if (openings and MAZE_OPEN_DOWN == 0) bottom - LIQUID_MAZE_BLOB_RADIUS_DP else bottom
    return Offset(
        x = position.x.coerceIn(minimumX, maximumX),
        y = position.y.coerceIn(minimumY, maximumY),
    )
}

private fun liquidMazeCellHasOpening(
    puzzle: FidgetMazePuzzle,
    cell: Int,
    direction: MazeDirection,
): Boolean =
    cell != FIDGET_MAZE_RESET_CELL &&
        puzzle.openings.getOrElse(cell) { 0 } and direction.openMask() != 0

private fun liquidMazeCellAt(position: Offset): Int {
    val column = ((position.x + LIQUID_MAZE_BOARD_LIMIT_DP) / LIQUID_MAZE_CELL_SIZE_DP)
        .toInt()
        .coerceIn(0, FIDGET_MAZE_COLUMNS - 1)
    val row = ((position.y + LIQUID_MAZE_BOARD_LIMIT_DP) / LIQUID_MAZE_CELL_SIZE_DP)
        .toInt()
        .coerceIn(0, FIDGET_MAZE_ROWS - 1)
    return row * FIDGET_MAZE_COLUMNS + column
}

private fun liquidMazeCellCenter(cell: Int): Offset {
    val column = cell % FIDGET_MAZE_COLUMNS
    val row = cell / FIDGET_MAZE_COLUMNS
    return Offset(
        x = -LIQUID_MAZE_BOARD_LIMIT_DP + (column + 0.5f) * LIQUID_MAZE_CELL_SIZE_DP,
        y = -LIQUID_MAZE_BOARD_LIMIT_DP + (row + 0.5f) * LIQUID_MAZE_CELL_SIZE_DP,
    )
}
internal const val MAZE_OPEN_UP = 1
internal const val MAZE_OPEN_RIGHT = 2
internal const val MAZE_OPEN_DOWN = 4
internal const val MAZE_OPEN_LEFT = 8
private const val SLINGSHOT_PULL_LIMIT_DP = 42f
private const val SLINGSHOT_BOUNCE_LIMIT_DP = 47f
private const val SLINGSHOT_LAUNCH_MULTIPLIER = 16f
private const val SLINGSHOT_BOUNCE_DAMPING = 0.92f
private const val SLINGSHOT_ROLLING_DAMPING = 0.992f
private const val SLINGSHOT_STOP_SPEED = 8f
private const val SQUISHY_PULL_LIMIT_DP = 38f
private const val POP_GRID_COUNT = 12
private const val RATCHET_STEP_COUNT = 16
private const val FIDGET_PRIVACY_POLICY_URL = "https://labmunkz.com/MunkzFidgetToy/privacy/"
internal const val FIDGET_SYNC_DEBOUNCE_MILLIS = 250L
internal const val FIDGET_DONATION_1_PRODUCT_ID = "fidget_donation_1"
internal const val FIDGET_DONATION_3_PRODUCT_ID = "fidget_donation_3"
internal const val FIDGET_DONATION_5_PRODUCT_ID = "fidget_donation_5"
internal const val FIDGET_DONATION_10_PRODUCT_ID = "fidget_donation_10"
internal const val FIDGET_SETTINGS_PREFS = "munkz_fidget_toy_settings"
internal const val FIDGET_MAIN_COLOR_KEY = "main_color"
internal const val FIDGET_BACKGROUND_COLOR_KEY = "background_color"
private const val FIDGET_BACKGROUND_IMAGE_URI_KEY = "background_image_uri"
internal const val FIDGET_RING_COLOR_KEY = "ring_color"
internal const val FIDGET_SPINNER_STYLE_KEY = "spinner_style"
internal const val FIDGET_SPINNER_MULTI_KEY = "spinner_multi"
internal const val FIDGET_HAPTIC_ENABLED_KEY = "haptic_enabled"
private const val FIDGET_SOUND_ENABLED_KEY = "sound_enabled"
private const val FIDGET_SOUND_MODE_KEY = "sound_mode"
private const val FIDGET_ACCENT_INTENSITY_KEY = "accent_intensity"
private const val FIDGET_REWARD_STYLE_KEY = "reward_style"
internal const val FIDGET_LANGUAGE_KEY = "language"
private const val FIDGET_KEEP_SCREEN_ON_KEY = "keep_screen_on"
private const val FIDGET_CPU_VISIBLE_KEY = "cpu_visible"
internal const val FIDGET_PINNED_TOYS_KEY = "pinned_toys"
internal const val FIDGET_COUNT_KEY = "fidget_count"
private const val FIDGET_MOTION_GESTURES_KEY = "motion_gestures"
private const val FIDGET_GESTURE_CONTROL_MODE_KEY = "gesture_control_mode"
private const val FIDGET_PHONE_MOTION_KEY = "phone_motion"
private const val FIDGET_TILT_GESTURE_KEY = "tilt_gesture"
private const val FIDGET_SHAKE_GESTURE_KEY = "shake_gesture"
private const val FIDGET_MOTION_SENSITIVITY_KEY = "motion_sensitivity"
private const val FIDGET_NEUTRAL_TILT_X_KEY = "neutral_tilt_x"
private const val FIDGET_NEUTRAL_TILT_Y_KEY = "neutral_tilt_y"
private const val FIDGET_DONATION_COUNT_PREFIX = "donation_count_"

internal fun shouldKeepFidgetScreenOn(
    manualKeepScreenOn: Boolean,
    wearEdition: Boolean,
    motionInputEnabled: Boolean,
): Boolean {
    return manualKeepScreenOn || (wearEdition && motionInputEnabled)
}

internal data class FidgetToyInfo(
    val id: Int,
    val englishName: String,
    val spanishName: String,
    val englishStyle: String,
    val spanishStyle: String,
) {
    fun nameFor(language: AppLanguage): String = when (language) {
        AppLanguage.English -> englishName
        AppLanguage.Spanish -> spanishName
    }

    fun styleFor(language: AppLanguage): String = when (language) {
        AppLanguage.English -> englishStyle
        AppLanguage.Spanish -> spanishStyle
    }
}

private data class FidgetDonationProduct(
    val label: String,
    val productId: String,
)

private data class FidgetDonationBadge(
    val label: String,
    val count: Int,
    val colorArgb: Int,
)

internal enum class MazeDirection {
    Up,
    Right,
    Down,
    Left,
}

internal data class FidgetMazePuzzle(
    val openings: List<Int>,
    val startCell: Int,
    val endCell: Int,
) {
    fun nextCell(currentCell: Int, direction: MazeDirection): Int {
        val column = currentCell % FIDGET_MAZE_COLUMNS
        val row = currentCell / FIDGET_MAZE_COLUMNS
        val openMask = openings.getOrElse(currentCell) { 0 }
        val nextCell = when (direction) {
            MazeDirection.Up -> if (row > 0 && openMask and MAZE_OPEN_UP != 0) {
                currentCell - FIDGET_MAZE_COLUMNS
            } else {
                currentCell
            }
            MazeDirection.Right -> if (column < FIDGET_MAZE_COLUMNS - 1 && openMask and MAZE_OPEN_RIGHT != 0) {
                currentCell + 1
            } else {
                currentCell
            }
            MazeDirection.Down -> if (row < FIDGET_MAZE_ROWS - 1 && openMask and MAZE_OPEN_DOWN != 0) {
                currentCell + FIDGET_MAZE_COLUMNS
            } else {
                currentCell
            }
            MazeDirection.Left -> if (column > 0 && openMask and MAZE_OPEN_LEFT != 0) {
                currentCell - 1
            } else {
                currentCell
            }
        }
        return nextCell.takeUnless { it == FIDGET_MAZE_RESET_CELL } ?: currentCell
    }
}

private data class FidgetSettingsState(
    val fidgetCount: Int,
    val mainColorArgb: Int,
    val backgroundColorArgb: Int,
    val backgroundImageUri: String?,
    val ringColorArgb: Int,
    val spinnerStyleIndex: Int,
    val spinnerMultiEnabled: Boolean,
    val hapticFeedbackEnabled: Boolean,
    val soundFeedbackEnabled: Boolean,
    val feedbackSoundMode: BeatSoundMode,
    val accentIntensityMode: AccentIntensityMode,
    val rewardStyle: FidgetRewardStyle,
    val appLanguage: AppLanguage,
    val keepScreenOn: Boolean,
    val cpuPercentVisible: Boolean,
    val pinnedToyIdsCsv: String,
    val motionGesturesEnabled: Boolean,
    val gestureControlMode: FidgetControlMode,
    val phoneMotionEnabled: Boolean,
    val tiltGestureEnabled: Boolean,
    val shakeGestureEnabled: Boolean,
    val motionSensitivity: FidgetMotionSensitivity,
    val neutralTiltX: Float,
    val neutralTiltY: Float,
)

internal val FIDGET_TOY_INFOS = listOf(
    FidgetToyInfo(FIDGET_SPINNER_INDEX, "Spin Storm", "Tormenta Giratoria", "Motion", "Movimiento"),
    FidgetToyInfo(FIDGET_SWITCH_INDEX, "Flip Stack", "Pila de Palancas", "Switches", "Interruptores"),
    FidgetToyInfo(FIDGET_SWITCH_MAZE_INDEX, "Grid Stepper", "Pasos en Cuadrícula", "Switches", "Interruptores"),
    FidgetToyInfo(FIDGET_FREE_BUTTON_INDEX, "Button Drift", "Botones Libres", "Touch", "Tacto"),
    FidgetToyInfo(FIDGET_WHACK_BUTTON_INDEX, "Color Pop Hunt", "Caza de Colores", "Touch", "Tacto"),
    FidgetToyInfo(FIDGET_SLINGSHOT_INDEX, "Bounce Shot", "Tiro Rebotador", "Motion", "Movimiento"),
    FidgetToyInfo(FIDGET_MAZE_INDEX, "Maze Shuffle", "Laberinto Aleatorio", "Puzzle", "Rompecabezas"),
    FidgetToyInfo(FIDGET_SQUISHY_INDEX, "Squish Pop", "Pop Aplastable", "Soft", "Suave"),
    FidgetToyInfo(FIDGET_MAG_SNAP_INDEX, "Mag Snap", "Imán Clic", "Click", "Clic"),
    FidgetToyInfo(FIDGET_POP_GRID_INDEX, "Pop Grid", "Cuadrícula Pop", "Touch", "Tacto"),
    FidgetToyInfo(FIDGET_INFINITY_CUBE_INDEX, "Infinity Flip", "Giro Infinito", "Motion", "Movimiento"),
    FidgetToyInfo(FIDGET_RATCHET_RING_INDEX, "Ratchet Ring", "Aro de Trinquete", "Click", "Clic"),
    FidgetToyInfo(FIDGET_LIQUID_MAZE_INDEX, "Liquid Maze", "Laberinto Líquido", "Flow", "Flujo"),
    FidgetToyInfo(FIDGET_GEAR_JAM_INDEX, "Gear Jam", "Engranajes", "Motion", "Movimiento"),
    FidgetToyInfo(FIDGET_WORRY_STONE_INDEX, "Worry Stone", "Piedra Calmante", "Soft", "Suave"),
    FidgetToyInfo(FIDGET_KEY_CLICKS_INDEX, "Key Clicks", "Teclas Clic", "Click", "Clic"),
    FidgetToyInfo(FIDGET_ZEN_TRACE_INDEX, "Zen Trace", "Trazo Zen", "Flow", "Flujo"),
    FidgetToyInfo(FIDGET_BEAT_MACHINE_INDEX, "Beat Machine", "Máquina de Ritmos", "Sound", "Sonido"),
    FidgetToyInfo(FIDGET_WINDOW_INDEX, "Window Slide", "Ventana Corrediza", "Everyday", "Cotidiano"),
    FidgetToyInfo(FIDGET_DOOR_INDEX, "Door Swing", "Puerta Abatible", "Everyday", "Cotidiano"),
    FidgetToyInfo(FIDGET_LIGHT_INDEX, "Light Flick", "Toque de Luz", "Everyday", "Cotidiano"),
    FidgetToyInfo(FIDGET_FAN_INDEX, "Fan Breeze", "Brisa de Ventilador", "Everyday", "Cotidiano"),
    FidgetToyInfo(FIDGET_SINK_INDEX, "Sink Flow", "Flujo del Lavabo", "Everyday", "Cotidiano"),
    FidgetToyInfo(FIDGET_SYMBOL_DOCK_INDEX, "Symbol Dock", "Dock de Símbolos", "Click", "Clic"),
    FidgetToyInfo(FIDGET_CENTER_DROP_MAZE_INDEX, "Center Drop", "Caída Central", "Puzzle", "Rompecabezas"),
    FidgetToyInfo(FIDGET_BALL_SORT_MAZE_INDEX, "Ball Sort", "Ordena Bolas", "Puzzle", "Rompecabezas"),
)

internal data class FidgetText(
    val title: String,
    val menu: String,
    val toys: String,
    val toyWall: String,
    val pinnedFidgets: String,
    val pin: String,
    val pinned: String,
    val soon: String,
    val rewardLine: (count: Int, nextReward: Int) -> String,
    val links: String,
    val review: String,
    val donate: String,
    val phoneSurfaces: String,
    val addDock: String,
    val spinnerWallpaper: String,
    val gestures: String,
    val motionGestures: String,
    val controlMode: String,
    val touchControl: String,
    val motionControl: String,
    val bothControl: String,
    val phoneMotion: String,
    val tilt: String,
    val shake: String,
    val sensitivity: String,
    val low: String,
    val medium: String,
    val high: String,
    val calibrateTilt: String,
    val feedback: String,
    val vibe: String,
    val sound: String,
    val click: String,
    val wood: String,
    val bell: String,
    val bigBeep: String,
    val watch: String,
    val rewards: String,
    val resetRewards: String,
    val keepOn: String,
    val keepOff: String,
    val theme: String,
    val spinnerStyle: String,
    val spinnerLayout: String,
    val singleSpinner: String,
    val multiSpinner: String,
    val mainColor: String,
    val backgroundColor: String,
    val backgroundImage: String,
    val addImage: String,
    val changeImage: String,
    val clearImage: String,
    val ringColor: String,
    val language: String,
    val cpu: String,
    val on: String,
    val off: String,
    val done: String,
    val addReviewTitle: String,
    val openPlayStore: String,
    val no: String,
    val privacyPolicy: String,
    val colors: String,
    val intensityHelp: String,
    val installFromPlay: String,
    val thanksFor: (label: String) -> String,
    val day: String,
    val night: String,
    val open: String,
    val closed: String,
    val shut: String,
    val flash: String,
    val beatPadLabels: List<String>,
    val hotInitial: String,
    val coldInitial: String,
)

internal fun fidgetTextFor(language: AppLanguage): FidgetText {
    return when (language) {
        AppLanguage.English -> FidgetText(
            title = "Fidget Toy",
            menu = "Menu",
            toys = "Toys",
            toyWall = "Toy Wall",
            pinnedFidgets = "Pinned Fidgets",
            pin = "Pin",
            pinned = "Pinned",
            soon = "Soon",
            rewardLine = { count, nextReward ->
                when {
                    count < 1_000 -> "${formatFidgetCount(count)} taps | ${formatFidgetCount(nextReward)} reward"
                    count < 10_000 -> "${formatFidgetCount(count)} | ${formatFidgetCount(nextReward)}"
                    else -> "${formatFidgetCount(count)} taps"
                }
            },
            links = "Links",
            review = "Review",
            donate = "Donate",
            phoneSurfaces = "Phone Fidgets",
            addDock = "Add Fidget Dock",
            spinnerWallpaper = "Spinner Wallpaper",
            gestures = "Gestures",
            motionGestures = "Motion gestures",
            controlMode = "Control",
            touchControl = "Touch",
            motionControl = "Motion",
            bothControl = "Both",
            phoneMotion = "Phone motion",
            tilt = "Tilt",
            shake = "Shake",
            sensitivity = "Sensitivity",
            low = "Low",
            medium = "Medium",
            high = "High",
            calibrateTilt = "Calibrate tilt",
            feedback = "Feedback",
            vibe = "Vibe",
            sound = "Sound",
            click = "Click",
            wood = "Wood",
            bell = "Bell",
            bigBeep = "Big Beep",
            watch = "Screen",
            rewards = "Rewards",
            resetRewards = "Reset Rewards",
            keepOn = "Keep On",
            keepOff = "Time Out",
            theme = "Theme",
            spinnerStyle = "Spinner Style",
            spinnerLayout = "Spinner Layout",
            singleSpinner = "Single",
            multiSpinner = "Multi",
            mainColor = "Main",
            backgroundColor = "BG",
            backgroundImage = "BG Image",
            addImage = "Add image",
            changeImage = "Change",
            clearImage = "Clear",
            ringColor = "Ring",
            language = "Language",
            cpu = "CPU",
            on = "On",
            off = "Off",
            done = "Done",
            addReviewTitle = "Add a review?",
            openPlayStore = "Open Play Store",
            no = "No",
            privacyPolicy = "Privacy Policy",
            colors = "Colors",
            intensityHelp = "Beep + vibe strength",
            installFromPlay = "Install from Play to use",
            thanksFor = { label -> "Thanks for $label" },
            day = "DAY",
            night = "NIGHT",
            open = "OPEN",
            closed = "CLOSED",
            shut = "SHUT",
            flash = "FLASH",
            beatPadLabels = listOf("Kick", "Snr", "Hat", "Tom", "Clap", "Bell"),
            hotInitial = "H",
            coldInitial = "C",
        )
        AppLanguage.Spanish -> FidgetText(
            title = "Juguete Fidget",
            menu = "Menú",
            toys = "Juguetes",
            toyWall = "Muro de Juguetes",
            pinnedFidgets = "Juguetes Fijados",
            pin = "Fijar",
            pinned = "Fijado",
            soon = "Pronto",
            rewardLine = { count, nextReward ->
                when {
                    count < 1_000 -> "${formatFidgetCount(count)} toques | ${formatFidgetCount(nextReward)} premio"
                    count < 10_000 -> "${formatFidgetCount(count)} | ${formatFidgetCount(nextReward)}"
                    else -> "${formatFidgetCount(count)} toques"
                }
            },
            links = "Enlaces",
            review = "Reseña",
            donate = "Donar",
            phoneSurfaces = "Fidgets del Teléfono",
            addDock = "Agregar Dock Fidget",
            spinnerWallpaper = "Fondo Giratorio",
            gestures = "Gestos",
            motionGestures = "Gestos de movimiento",
            controlMode = "Control",
            touchControl = "Tacto",
            motionControl = "Movimiento",
            bothControl = "Ambos",
            phoneMotion = "Movimiento del teléfono",
            tilt = "Inclinación",
            shake = "Agitar",
            sensitivity = "Sensibilidad",
            low = "Baja",
            medium = "Media",
            high = "Alta",
            calibrateTilt = "Calibrar inclinación",
            feedback = "Efectos",
            vibe = "Vibración",
            sound = "Sonido",
            click = "Clic",
            wood = "Madera",
            bell = "Campana",
            bigBeep = "Pitido fuerte",
            watch = "Pantalla",
            rewards = "Premios",
            resetRewards = "Reiniciar premios",
            keepOn = "Mantener activa",
            keepOff = "Tiempo de espera",
            theme = "Tema",
            spinnerStyle = "Estilo del spinner",
            spinnerLayout = "Distribución del spinner",
            singleSpinner = "Uno",
            multiSpinner = "Múltiple",
            mainColor = "Principal",
            backgroundColor = "Fondo",
            backgroundImage = "Imagen de fondo",
            addImage = "Agregar",
            changeImage = "Cambiar",
            clearImage = "Quitar",
            ringColor = "Aro",
            language = "Idioma",
            cpu = "CPU",
            on = "Sí",
            off = "No",
            done = "Listo",
            addReviewTitle = "¿Agregar una reseña?",
            openPlayStore = "Abrir Play Store",
            no = "No",
            privacyPolicy = "Política de Privacidad",
            colors = "Colores",
            intensityHelp = "Fuerza de pitido y vibración",
            installFromPlay = "Instala desde Play para usarlo",
            thanksFor = { label -> "Gracias por donar $label" },
            day = "DÍA",
            night = "NOCHE",
            open = "ABIERTA",
            closed = "CERRADA",
            shut = "CERRADA",
            flash = "DESTELLO",
            beatPadLabels = listOf("Bombo", "Caja", "Hat", "Tom", "Palma", "Camp."),
            hotInitial = "C",
            coldInitial = "F",
        )
    }
}

private val FIDGET_DONATION_PRODUCTS = listOf(
    FidgetDonationProduct("$1", FIDGET_DONATION_1_PRODUCT_ID),
    FidgetDonationProduct("$3", FIDGET_DONATION_3_PRODUCT_ID),
    FidgetDonationProduct("$5", FIDGET_DONATION_5_PRODUCT_ID),
    FidgetDonationProduct("$10", FIDGET_DONATION_10_PRODUCT_ID),
)

private val FIDGET_DONATION_BADGE_COLORS = mapOf(
    FIDGET_DONATION_1_PRODUCT_ID to 0xFFB8FF00.toInt(),
    FIDGET_DONATION_3_PRODUCT_ID to 0xFF56F1C8.toInt(),
    FIDGET_DONATION_5_PRODUCT_ID to 0xFFFFC857.toInt(),
    FIDGET_DONATION_10_PRODUCT_ID to 0xFFFF2AD4.toInt(),
)

private fun fidgetDonationBadgeFor(donationCounts: Map<String, Int>): FidgetDonationBadge? {
    val highestDonation = FIDGET_DONATION_PRODUCTS
        .lastOrNull { donation -> donationCounts.getOrDefault(donation.productId, 0) > 0 }
        ?: return null
    val totalDonationCount = FIDGET_DONATION_PRODUCTS
        .sumOf { donation -> donationCounts.getOrDefault(donation.productId, 0) }
        .coerceIn(1, 5)
    return FidgetDonationBadge(
        label = highestDonation.label,
        count = totalDonationCount,
        colorArgb = FIDGET_DONATION_BADGE_COLORS.getValue(highestDonation.productId),
    )
}

@Composable
private fun FidgetTitleBar(
    title: String,
    toyName: String,
    accentColor: Color,
    accentColorArgb: Int,
    phoneLayout: Boolean,
    phoneLandscape: Boolean,
    showDonateButton: Boolean,
    donateLabel: String,
    onDonateClick: () -> Unit,
) {
    val systemFontScale = LocalDensity.current.fontScale
    val largeFontHeightExtra = ((systemFontScale - 1f).coerceIn(0f, 0.5f) * 16f).dp
    val titleColor = if (phoneLayout) {
        MaterialTheme.colorScheme.onBackground
    } else {
        Color.White
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(3f)
            .height(
                when {
                    phoneLandscape -> 36.dp + largeFontHeightExtra
                    phoneLayout -> 48.dp + largeFontHeightExtra
                    else -> 34.dp + largeFontHeightExtra
                },
            ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(
                if (phoneLandscape) Alignment.Center else Alignment.TopCenter,
            ),
        )
        {
            Image(
                painter = painterResource(id = R.drawable.munkz_fidget_toy_logo),
                contentDescription = null,
                modifier = Modifier.size(if (phoneLayout) 22.dp else 18.dp),
            )
            Text(
                text = title,
                color = titleColor,
                fontSize = if (phoneLayout) 16.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = toyName,
            color = accentColor,
            fontSize = when {
                phoneLandscape -> 13.sp
                phoneLayout -> 11.sp
                else -> 9.sp
            },
            fontWeight = FontWeight.Black,
            textAlign = if (phoneLandscape) TextAlign.Start else TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (phoneLandscape) {
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            },
        )

        if (showDonateButton) {
            FidgetThemeButton(
                text = donateLabel,
                modifier = Modifier
                    .align(
                        if (phoneLandscape) Alignment.CenterEnd else Alignment.TopEnd,
                    )
                    .width(72.dp)
                    .height(32.dp),
                fontSize = 11.sp,
                selected = true,
                prominent = true,
                accentColor = accentColor,
                accentColorArgb = accentColorArgb,
                onClick = onDonateClick,
            )
        }
    }
}

@Composable
private fun FidgetDonationBadgePill(
    badge: FidgetDonationBadge,
    phoneLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val badgeColor = colorFromChoice(badge.colorArgb)
    val badgeText = if (badge.count > 1) {
        "${badge.label} x${badge.count}"
    } else {
        badge.label
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(
                when {
                    phoneLayout && badge.count > 1 -> 68.dp
                    phoneLayout -> 44.dp
                    badge.count > 1 -> 45.dp
                    else -> 30.dp
                },
            )
            .height(if (phoneLayout) 24.dp else 17.dp)
            .clip(RoundedCornerShape(50))
            .background(badgeColor.copy(alpha = 0.9f))
            .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(50)),
    ) {
        Text(
            text = badgeText,
            color = readableTextColorFor(badge.colorArgb),
            fontSize = if (phoneLayout) 11.sp else 8.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FidgetMenuPage(
    appLanguage: AppLanguage,
    text: FidgetText,
    hapticFeedbackEnabled: Boolean,
    soundFeedbackEnabled: Boolean,
    feedbackSoundMode: BeatSoundMode,
    accentIntensityMode: AccentIntensityMode,
    rewardStyle: FidgetRewardStyle,
    keepScreenOn: Boolean,
    cpuPercentVisible: Boolean,
    cpuUsagePercent: Float?,
    mainColorArgb: Int,
    backgroundColorArgb: Int,
    ringColorArgb: Int,
    spinnerStyleIndex: Int,
    spinnerMultiEnabled: Boolean,
    backgroundImageSelected: Boolean,
    onReviewClick: () -> Unit,
    onDonateClick: () -> Unit,
    phoneSurfacesEnabled: Boolean,
    onAddDockClick: () -> Unit,
    onSpinnerWallpaperClick: () -> Unit,
    motionGesturesEnabled: Boolean,
    phoneMotionEnabled: Boolean,
    tiltGestureEnabled: Boolean,
    shakeGestureEnabled: Boolean,
    motionSensitivity: FidgetMotionSensitivity,
    motionSensorsAvailable: Boolean,
    onMotionGesturesEnabledChange: (Boolean) -> Unit,
    onTiltGestureToggle: () -> Unit,
    onShakeGestureToggle: () -> Unit,
    onMotionSensitivityChoice: (FidgetMotionSensitivity) -> Unit,
    onCalibrateTilt: () -> Unit,
    onDoneClick: () -> Unit,
    onRewardReset: () -> Unit,
    onKeepScreenToggle: () -> Unit,
    onCpuToggle: () -> Unit,
    onLanguageChoice: (AppLanguage) -> Unit,
    onAccentIntensityModeChoice: (AccentIntensityMode) -> Unit,
    onRewardStyleChoice: (FidgetRewardStyle) -> Unit,
    onMainColorChoice: (Int) -> Unit,
    onBackgroundColorChoice: (Int) -> Unit,
    onRingColorChoice: (Int) -> Unit,
    onSpinnerStyleChoice: (Int) -> Unit,
    onSpinnerMultiChoice: (Boolean) -> Unit,
    onBackgroundImagePick: () -> Unit,
    onBackgroundImageClear: () -> Unit,
    onHapticToggle: () -> Unit,
    onSoundToggle: () -> Unit,
    onSoundModeChoice: (BeatSoundMode) -> Unit,
) {
    val settingsScrollState = rememberScrollState()
    val accentColor = colorFromChoice(mainColorArgb)
    var gestureSectionExpanded by rememberSaveable { mutableStateOf(false) }
    val motionControlsActive = motionGesturesEnabled &&
        (!phoneSurfacesEnabled || phoneMotionEnabled)

    LaunchedEffect(Unit) {
        settingsScrollState.scrollTo(0)
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val watchSClass = minOf(maxWidth, maxHeight) <= 200.dp
        val phoneLayout = maxHeight >= 360.dp || maxWidth >= 360.dp
        val phoneLandscape = phoneLayout && maxWidth > maxHeight
        val horizontalPadding = when {
            phoneLandscape -> 46.dp
            phoneLayout -> 24.dp
            watchSClass -> 14.dp
            else -> 18.dp
        }
        val topPadding = when {
            phoneLandscape -> 8.dp
            phoneLayout -> 12.dp
            watchSClass -> 8.dp
            else -> 10.dp
        }
        val bottomPadding = when {
            phoneLandscape -> 24.dp
            phoneLayout -> 32.dp
            else -> 10.dp
        }
        val sectionSpacing = when {
            phoneLayout -> 16.dp
            watchSClass -> 7.dp
            else -> 9.dp
        }
        val tightSpacing = when {
            phoneLayout -> 8.dp
            watchSClass -> 4.dp
            else -> 5.dp
        }
        val labelFontSize = when {
            phoneLayout -> 16.sp
            watchSClass -> 9.sp
            else -> 10.sp
        }
        val scrollBarHeight = when {
            phoneLayout -> maxHeight * 0.58f
            watchSClass -> 104.dp
            else -> 132.dp
        }
        val doneButtonText = when (appLanguage) {
            AppLanguage.English -> "Save"
            AppLanguage.Spanish -> "Guardar"
        }
        val doneButtonModifier = when {
            phoneLayout -> Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 14.dp)
                .width(96.dp)
                .height(42.dp)
            else -> Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = if (watchSClass) 15.dp else 18.dp,
                    end = if (watchSClass) 14.dp else 16.dp,
                )
                .zIndex(4f)
                .rotate(38f)
                .width(if (watchSClass) 54.dp else 58.dp)
                .height(24.dp)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(settingsScrollState)
                .padding(start = horizontalPadding, top = topPadding, end = horizontalPadding, bottom = bottomPadding),
        ) {
            Image(
                painter = painterResource(id = R.drawable.munkz_fidget_toy_logo),
                contentDescription = null,
                modifier = Modifier.size(if (phoneLayout) 68.dp else 44.dp),
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "Munkz",
                color = accentColor,
                fontSize = if (phoneLayout) 23.sp else 15.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(sectionSpacing))

            FidgetMenuSectionTitle(text.language, labelFontSize, accentColor = accentColor)
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = tightSpacing),
            ) {
                AppLanguages.forEach { language ->
                    FidgetSettingsButton(
                        text = when (language) {
                            AppLanguage.English -> "EN"
                            AppLanguage.Spanish -> "ES"
                        },
                        selected = appLanguage == language,
                        accentColor = accentColor,
                        accentColorArgb = mainColorArgb,
                        phoneLayout = phoneLayout,
                        onClick = { onLanguageChoice(language) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            FidgetMenuSectionTitle(text.links, labelFontSize, accentColor = accentColor)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = tightSpacing),
            ) {
                FidgetSettingsButton(
                    text = text.review,
                    selected = false,
                    accentColor = accentColor,
                    accentColorArgb = mainColorArgb,
                    phoneLayout = phoneLayout,
                    onClick = onReviewClick,
                )
                FidgetSettingsButton(
                    text = text.donate,
                    selected = false,
                    accentColor = accentColor,
                    accentColorArgb = mainColorArgb,
                    phoneLayout = phoneLayout,
                    onClick = onDonateClick,
                )
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            if (phoneSurfacesEnabled) {
                FidgetMenuSectionTitle(text.phoneSurfaces, labelFontSize, accentColor = accentColor)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = tightSpacing),
                ) {
                    FidgetSettingsButton(
                        text = text.addDock,
                        selected = false,
                        accentColor = accentColor,
                        accentColorArgb = mainColorArgb,
                        phoneLayout = true,
                        onClick = onAddDockClick,
                    )
                    FidgetSettingsButton(
                        text = text.spinnerWallpaper,
                        selected = false,
                        accentColor = accentColor,
                        accentColorArgb = mainColorArgb,
                        phoneLayout = true,
                        onClick = onSpinnerWallpaperClick,
                    )
                }

                Spacer(modifier = Modifier.height(sectionSpacing))
            }

            FidgetSettingsButton(
                text = "${text.gestures}: ${if (motionControlsActive) text.on else text.off}",
                selected = motionControlsActive,
                accentColor = accentColor,
                accentColorArgb = mainColorArgb,
                phoneLayout = phoneLayout,
                onClick = { gestureSectionExpanded = !gestureSectionExpanded },
            )
            if (gestureSectionExpanded) {
                FidgetMenuSectionTitle(
                    text.motionGestures,
                    labelFontSize,
                    modifier = Modifier.padding(top = tightSpacing),
                    accentColor = accentColor,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = tightSpacing),
                ) {
                    FidgetSettingsButton(
                        text = text.off,
                        selected = !motionControlsActive,
                        accentColor = accentColor,
                        accentColorArgb = mainColorArgb,
                        phoneLayout = phoneLayout,
                        onClick = { onMotionGesturesEnabledChange(false) },
                    )
                    FidgetSettingsButton(
                        text = text.on,
                        selected = motionControlsActive,
                        accentColor = accentColor,
                        accentColorArgb = mainColorArgb,
                        phoneLayout = phoneLayout,
                        onClick = { onMotionGesturesEnabledChange(true) },
                    )
                }

                if (motionControlsActive) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = tightSpacing),
                    ) {
                        FidgetSettingsButton(
                            text = text.tilt,
                            selected = tiltGestureEnabled,
                            accentColor = accentColor,
                            accentColorArgb = mainColorArgb,
                            phoneLayout = phoneLayout,
                            onClick = onTiltGestureToggle,
                        )
                        FidgetSettingsButton(
                            text = text.shake,
                            selected = shakeGestureEnabled,
                            accentColor = accentColor,
                            accentColorArgb = mainColorArgb,
                            phoneLayout = phoneLayout,
                            onClick = onShakeGestureToggle,
                        )
                    }

                    FidgetMenuSectionTitle(
                        text.sensitivity,
                        labelFontSize,
                        modifier = Modifier.padding(top = tightSpacing),
                        accentColor = accentColor,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = tightSpacing),
                    ) {
                        FidgetSettingsButton(
                            text = text.low,
                            selected = motionSensitivity == FidgetMotionSensitivity.Low,
                            accentColor = accentColor,
                            accentColorArgb = mainColorArgb,
                            phoneLayout = phoneLayout,
                            onClick = { onMotionSensitivityChoice(FidgetMotionSensitivity.Low) },
                        )
                        FidgetSettingsButton(
                            text = text.medium,
                            selected = motionSensitivity == FidgetMotionSensitivity.Medium,
                            accentColor = accentColor,
                            accentColorArgb = mainColorArgb,
                            phoneLayout = phoneLayout,
                            onClick = { onMotionSensitivityChoice(FidgetMotionSensitivity.Medium) },
                        )
                        FidgetSettingsButton(
                            text = text.high,
                            selected = motionSensitivity == FidgetMotionSensitivity.High,
                            accentColor = accentColor,
                            accentColorArgb = mainColorArgb,
                            phoneLayout = phoneLayout,
                            onClick = { onMotionSensitivityChoice(FidgetMotionSensitivity.High) },
                        )
                    }
                    if (motionSensorsAvailable) {
                        FidgetSettingsButton(
                            text = text.calibrateTilt,
                            selected = false,
                            accentColor = accentColor,
                            accentColorArgb = mainColorArgb,
                            phoneLayout = phoneLayout,
                            onClick = onCalibrateTilt,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            FidgetMenuSectionTitle(text.feedback, labelFontSize, accentColor = accentColor)
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = tightSpacing),
            ) {
                FidgetSettingsButton(
                    text = text.vibe,
                    selected = hapticFeedbackEnabled,
                    accentColor = accentColor,
                    accentColorArgb = mainColorArgb,
                    phoneLayout = phoneLayout,
                    onClick = onHapticToggle,
                )
                FidgetSettingsButton(
                    text = text.sound,
                    selected = soundFeedbackEnabled,
                    accentColor = accentColor,
                    accentColorArgb = mainColorArgb,
                    phoneLayout = phoneLayout,
                    onClick = onSoundToggle,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = tightSpacing),
            ) {
                FidgetSettingsButton(
                    text = text.click,
                    selected = feedbackSoundMode == BeatSoundMode.Clicks,
                    accentColor = accentColor,
                    accentColorArgb = mainColorArgb,
                    phoneLayout = phoneLayout,
                    onClick = { onSoundModeChoice(BeatSoundMode.Clicks) },
                )
                FidgetSettingsButton(
                    text = text.wood,
                    selected = feedbackSoundMode == BeatSoundMode.Wood,
                    accentColor = accentColor,
                    accentColorArgb = mainColorArgb,
                    phoneLayout = phoneLayout,
                    onClick = { onSoundModeChoice(BeatSoundMode.Wood) },
                )
                FidgetSettingsButton(
                    text = text.bell,
                    selected = feedbackSoundMode == BeatSoundMode.Bell,
                    accentColor = accentColor,
                    accentColorArgb = mainColorArgb,
                    phoneLayout = phoneLayout,
                    onClick = { onSoundModeChoice(BeatSoundMode.Bell) },
                )
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            FidgetMenuSectionTitle(text.bigBeep, labelFontSize, accentColor = accentColor)
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = tightSpacing),
            ) {
                AccentIntensityChoices.forEach { choice ->
                    FidgetMiniChoiceButton(
                        text = choice.labelFor(appLanguage),
                        selected = accentIntensityMode == choice.mode,
                        accentColor = accentColor,
                        accentColorArgb = mainColorArgb,
                        phoneLayout = phoneLayout,
                        onClick = { onAccentIntensityModeChoice(choice.mode) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            FidgetMenuSectionTitle(text.watch, labelFontSize, accentColor = accentColor)
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = tightSpacing),
            ) {
                FidgetSettingsButton(
                    text = if (keepScreenOn) text.keepOn else text.keepOff,
                    selected = keepScreenOn,
                    accentColor = accentColor,
                    accentColorArgb = mainColorArgb,
                    phoneLayout = phoneLayout,
                    onClick = onKeepScreenToggle,
                )
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            FidgetMenuSectionTitle(text.theme, labelFontSize, accentColor = accentColor)
            Spacer(modifier = Modifier.height(tightSpacing))
            FidgetMenuSectionTitle(
                text = text.spinnerStyle,
                fontSize = if (phoneLayout) 13.sp else labelFontSize,
                accentColor = accentColor,
            )
            FidgetSpinnerStyleSelector(
                selectedStyleIndex = spinnerStyleIndex,
                appLanguage = appLanguage,
                mainColor = accentColor,
                ringColor = colorFromChoice(ringColorArgb),
                phoneLayout = phoneLayout,
                onStyleChoice = onSpinnerStyleChoice,
            )
            if (phoneSurfacesEnabled) {
                Spacer(modifier = Modifier.height(tightSpacing))
                FidgetMenuSectionTitle(text.spinnerLayout, 13.sp, accentColor = accentColor)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = tightSpacing),
                ) {
                    FidgetSettingsButton(
                        text = text.singleSpinner,
                        selected = !spinnerMultiEnabled,
                        accentColor = accentColor,
                        accentColorArgb = mainColorArgb,
                        phoneLayout = true,
                        onClick = { onSpinnerMultiChoice(false) },
                    )
                    FidgetSettingsButton(
                        text = text.multiSpinner,
                        selected = spinnerMultiEnabled,
                        accentColor = accentColor,
                        accentColorArgb = mainColorArgb,
                        phoneLayout = true,
                        onClick = { onSpinnerMultiChoice(true) },
                    )
                }
                Spacer(modifier = Modifier.height(tightSpacing))
            }
            FidgetColorRow(
                label = text.mainColor,
                selectedColorArgb = mainColorArgb,
                choices = ThemeMainColorOptions,
                phoneLayout = phoneLayout,
                onColorChoice = onMainColorChoice,
            )
            FidgetColorRow(
                label = text.backgroundColor,
                selectedColorArgb = backgroundColorArgb,
                choices = ThemeBackgroundColorOptions,
                phoneLayout = phoneLayout,
                onColorChoice = onBackgroundColorChoice,
            )
            FidgetColorRow(
                label = text.ringColor,
                selectedColorArgb = ringColorArgb,
                choices = PulseColorOptions,
                phoneLayout = phoneLayout,
                onColorChoice = onRingColorChoice,
            )

            if (phoneLayout) {
                Spacer(modifier = Modifier.height(tightSpacing))
                FidgetMenuSectionTitle(text.backgroundImage, labelFontSize, accentColor = accentColor)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = tightSpacing),
                ) {
                    FidgetSettingsButton(
                        text = if (backgroundImageSelected) text.changeImage else text.addImage,
                        selected = backgroundImageSelected,
                        accentColor = accentColor,
                        accentColorArgb = mainColorArgb,
                        phoneLayout = true,
                        onClick = onBackgroundImagePick,
                    )
                    if (backgroundImageSelected) {
                        FidgetSettingsButton(
                            text = text.clearImage,
                            selected = false,
                            accentColor = accentColor,
                            accentColorArgb = mainColorArgb,
                            phoneLayout = true,
                            onClick = onBackgroundImageClear,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            FidgetMenuSectionTitle(text.rewards, labelFontSize, accentColor = accentColor)
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = tightSpacing),
            ) {
                FidgetRewardStyle.entries.forEach { style ->
                    FidgetMiniChoiceButton(
                        text = style.labelFor(appLanguage),
                        selected = rewardStyle == style,
                        accentColor = accentColor,
                        accentColorArgb = mainColorArgb,
                        phoneLayout = phoneLayout,
                        onClick = { onRewardStyleChoice(style) },
                    )
                }
            }
            FidgetSettingsButton(
                text = text.resetRewards,
                selected = false,
                accentColor = accentColor,
                accentColorArgb = mainColorArgb,
                phoneLayout = phoneLayout,
                onClick = onRewardReset,
            )

            Spacer(modifier = Modifier.height(sectionSpacing))

            FidgetMenuSectionTitle(text.cpu, labelFontSize, accentColor = accentColor)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = tightSpacing),
            ) {
                FidgetSettingsButton(
                    text = if (cpuPercentVisible) text.on else text.off,
                    selected = cpuPercentVisible,
                    accentColor = accentColor,
                    accentColorArgb = mainColorArgb,
                    phoneLayout = phoneLayout,
                    onClick = onCpuToggle,
                )
                Text(
                    text = cpuUsagePercent.formatFidgetCpuPercent(),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(48.dp),
                )
            }

            Spacer(modifier = Modifier.height(if (phoneLayout) 28.dp else 48.dp))
        }

            if (settingsScrollState.maxValue > 0) {
                FidgetMenuScrollBar(
                scrollState = settingsScrollState,
                accentColor = accentColor,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = if (phoneLayout) 8.dp else 20.dp)
                    .width(if (phoneLayout) 6.dp else 4.dp)
                    .height(scrollBarHeight),
                )
            }

            FidgetThemeButton(
            text = doneButtonText,
            modifier = doneButtonModifier,
            fontSize = if (phoneLayout) 15.sp else 9.sp,
            selected = true,
            prominent = true,
            accentColor = accentColor,
            accentColorArgb = mainColorArgb,
            onClick = onDoneClick,
        )
    }
}

@Composable
private fun FidgetMenuScrollBar(
    scrollState: ScrollState,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(accentColor.copy(alpha = 0.16f)),
    ) {
        val thumbHeight = (maxHeight * 0.34f).coerceAtLeast(24.dp)

        Box(
            modifier = Modifier
                .offset {
                    val maxScroll = scrollState.maxValue
                    val progress = if (maxScroll > 0) {
                        (scrollState.value.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    IntOffset(
                        x = 0,
                        y = ((maxHeight - thumbHeight) * progress).roundToPx(),
                    )
                }
                .fillMaxWidth()
                .height(thumbHeight)
                .clip(RoundedCornerShape(50))
                .background(accentColor.copy(alpha = 0.82f)),
        )
    }
}

@Composable
private fun FidgetMenuSectionTitle(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text,
        color = accentColor,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun FidgetSpinnerStyleSelector(
    selectedStyleIndex: Int,
    appLanguage: AppLanguage,
    mainColor: Color,
    ringColor: Color,
    phoneLayout: Boolean,
    onStyleChoice: (Int) -> Unit,
) {
    val stylesPerRow = if (phoneLayout) 4 else 2
    val itemWidth = if (phoneLayout) 68.dp else 70.dp
    val itemHeight = if (phoneLayout) 70.dp else 58.dp
    val previewSize = if (phoneLayout) 40.dp else 30.dp
    val labelSize = if (phoneLayout) 9.sp else 7.sp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (phoneLayout) 6.dp else 4.dp),
        modifier = Modifier.padding(top = if (phoneLayout) 8.dp else 5.dp),
    ) {
        FIDGET_SPINNER_STYLES.chunked(stylesPerRow).forEach { rowStyles ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(
                    if (phoneLayout) 6.dp else 4.dp,
                    Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowStyles.forEach { style ->
                    val selected = selectedStyleIndex == style.index
                    val styleName = style.nameFor(appLanguage)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .width(itemWidth)
                            .height(itemHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) {
                                    mainColor.copy(alpha = 0.18f)
                                } else {
                                    Color.Black.copy(alpha = 0.28f)
                                },
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    mainColor.copy(alpha = 0.92f)
                                } else {
                                    Color.White.copy(alpha = 0.16f)
                                },
                                shape = RoundedCornerShape(8.dp),
                            )
                            .semantics { contentDescription = styleName }
                            .clickable { onStyleChoice(style.index) }
                            .padding(vertical = if (phoneLayout) 5.dp else 3.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(previewSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            FidgetSpinner(
                                rotation = 18f,
                                style = style,
                                mainColor = mainColor,
                                ringColor = ringColor,
                                showSatellites = false,
                            )
                        }
                        Text(
                            text = styleName,
                            color = if (selected) mainColor else Color.White.copy(alpha = 0.82f),
                            fontSize = labelSize,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun Boolean.thenCpuLabel(cpuUsagePercent: Float?): String {
    return if (this) {
        cpuUsagePercent.formatFidgetCpuPercent()
    } else {
        "--%"
    }
}

@Composable
private fun FidgetRewardProgressPopup(
    count: Int,
    nextReward: Int,
    appLanguage: AppLanguage,
    ringColor: Color,
    onDismiss: () -> Unit,
) {
    var fibonacciInfoOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler {
        if (fibonacciInfoOpen) {
            fibonacciInfoOpen = false
        } else {
            onDismiss()
        }
    }
    val previousReward = previousFibonacciTarget(count)
    val progress = if (nextReward <= previousReward) 1f else {
        ((count - previousReward).toFloat() / (nextReward - previousReward).toFloat()).coerceIn(0f, 1f)
    }
    val (title, tapsLabel, nextLabel) = when (appLanguage) {
        AppLanguage.English -> Triple("REWARD PATH", "Taps", "Next")
        AppLanguage.Spanish -> Triple("RUTA DE PREMIOS", "Toques", "Siguiente")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Color.Black.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(170.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF061112))
                .border(1.dp, ringColor.copy(alpha = 0.76f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(title, color = ringColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(
                    text = "${formatFidgetCount(count)} $tapsLabel",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "$nextLabel ${formatFidgetCount(nextReward)}",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(CircleShape)
                            .background(ringColor),
                    )
                }
                Text(
                    text = "${(progress * 100f).roundToInt()}%",
                    color = ringColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 5.dp),
                )
                FidgetMenuChoiceButton(
                    text = if (appLanguage == AppLanguage.English) "Done" else "Listo",
                    selected = false,
                    onClick = onDismiss,
                )
            }
            FidgetFibonacciInfoButton(
                ringColor = ringColor,
                onClick = { fibonacciInfoOpen = true },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        if (fibonacciInfoOpen) {
            FidgetFibonacciInfoPopup(
                appLanguage = appLanguage,
                ringColor = ringColor,
                onDismiss = { fibonacciInfoOpen = false },
            )
        }
    }
}

@Composable
private fun FidgetFibonacciInfoButton(
    ringColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(ringColor.copy(alpha = 0.18f))
            .border(1.dp, ringColor.copy(alpha = 0.8f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "i",
            color = ringColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun FidgetFibonacciInfoPopup(
    appLanguage: AppLanguage,
    ringColor: Color,
    onDismiss: () -> Unit,
) {
    val (title, description, closeLabel) = when (appLanguage) {
        AppLanguage.English -> Triple(
            "FIBONACCI REWARDS",
            "Each number is made from the two before it: 1, 2, 3, 5, 8, 13... Reach a milestone to trigger a reward moment.",
            "Got it",
        )
        AppLanguage.Spanish -> Triple(
            "RECOMPENSAS FIBONACCI",
            "Cada número se forma con los dos anteriores: 1, 2, 3, 5, 8, 13... Llega a una meta para activar una recompensa.",
            "OK",
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A191A))
                .border(1.dp, ringColor.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(
                text = title,
                color = ringColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            FidgetMenuChoiceButton(
                text = closeLabel,
                selected = false,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun FidgetRewardMomentToast(
    message: String,
    ringColor: Color,
    phoneLayout: Boolean,
    phoneLandscape: Boolean,
) {
    val rewardOffset = when {
        !phoneLayout -> 0.dp
        phoneLandscape -> (-34).dp
        else -> (-78).dp
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(156.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.82f))
                .border(1.dp, ringColor.copy(alpha = 0.86f), RoundedCornerShape(14.dp))
                .offset(y = rewardOffset)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun FidgetIntensityPopup(
    appLanguage: AppLanguage,
    text: FidgetText,
    selectedMode: AccentIntensityMode,
    onModeChoice: (AccentIntensityMode) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(150.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF061112))
                .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), RoundedCornerShape(16.dp))
                .padding(horizontal = 9.dp, vertical = 10.dp),
        ) {
            Text(
                text = text.bigBeep,
                color = Color(0xFFFFC857),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                AccentIntensityChoices.forEach { choice ->
                    FidgetMiniChoiceButton(
                        text = choice.labelFor(appLanguage),
                        selected = selectedMode == choice.mode,
                        onClick = { onModeChoice(choice.mode) },
                    )
                }
            }
            Text(
                text = text.intensityHelp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 7.dp),
            )
            FidgetMenuChoiceButton(
                text = text.done,
                selected = false,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun FidgetColorPopup(
    text: FidgetText,
    mainColorArgb: Int,
    backgroundColorArgb: Int,
    ringColorArgb: Int,
    onMainColorChoice: (Int) -> Unit,
    onBackgroundColorChoice: (Int) -> Unit,
    onRingColorChoice: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(162.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF061112))
                .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), RoundedCornerShape(16.dp))
                .padding(horizontal = 9.dp, vertical = 8.dp),
        ) {
            Text(
                text = text.colors,
                color = Color(0xFFFFC857),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            FidgetColorRow(
                label = text.mainColor,
                selectedColorArgb = mainColorArgb,
                choices = ThemeMainColorOptions,
                onColorChoice = onMainColorChoice,
            )
            FidgetColorRow(
                label = text.backgroundColor,
                selectedColorArgb = backgroundColorArgb,
                choices = ThemeBackgroundColorOptions,
                onColorChoice = onBackgroundColorChoice,
            )
            FidgetColorRow(
                label = text.ringColor,
                selectedColorArgb = ringColorArgb,
                choices = PulseColorOptions,
                onColorChoice = onRingColorChoice,
            )
            FidgetMenuChoiceButton(
                text = text.done,
                selected = false,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun FidgetColorRow(
    label: String,
    selectedColorArgb: Int,
    choices: List<Int>,
    phoneLayout: Boolean = false,
    onColorChoice: (Int) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(if (phoneLayout) 8.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = if (phoneLayout) 10.dp else 6.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
            fontSize = if (phoneLayout) 14.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        choices.chunked(4).forEach { colorRow ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(
                    if (phoneLayout) 8.dp else 4.dp,
                    Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                colorRow.forEach { colorArgb ->
                    FidgetColorSwatch(
                        colorArgb = colorArgb,
                        selected = selectedColorArgb == colorArgb,
                        phoneLayout = phoneLayout,
                        onClick = { onColorChoice(colorArgb) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FidgetColorSwatch(
    colorArgb: Int,
    selected: Boolean,
    phoneLayout: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    val swatchColor = colorFromChoice(colorArgb)
    Box(
        modifier = Modifier
            .width(if (phoneLayout) 62.dp else 36.dp)
            .height(if (phoneLayout) 38.dp else 22.dp)
            .clip(shape)
            .then(
                if (isRainbowColor(colorArgb)) {
                    Modifier.background(Brush.horizontalGradient(RainbowColors), shape)
                } else {
                    Modifier.background(swatchColor, shape)
                },
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                text = "*",
                fontSize = if (phoneLayout) 16.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color = selectedSwatchMarkColor(colorArgb),
            )
        }
    }
}

@Composable
private fun FidgetMiniChoiceButton(
    text: String,
    selected: Boolean,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    accentColorArgb: Int = NEON_GREEN_COLOR,
    phoneLayout: Boolean = false,
    onClick: () -> Unit,
) {
    FidgetThemeButton(
        text = text,
        modifier = Modifier
            .width(if (phoneLayout) 68.dp else 29.dp)
            .height(if (phoneLayout) 40.dp else 24.dp),
        fontSize = if (phoneLayout) 13.sp else 8.sp,
        selected = selected,
        prominent = selected,
        accentColor = accentColor,
        accentColorArgb = accentColorArgb,
        onClick = onClick,
    )
}

@Composable
private fun FidgetDonationPopup(
    text: FidgetText,
    badge: FidgetDonationBadge?,
    statusText: String,
    onDonate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        val phoneLayout = maxHeight >= 360.dp || maxWidth >= 360.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(if (phoneLayout) 330.dp else 154.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF061112))
                .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), RoundedCornerShape(16.dp))
                .padding(
                    horizontal = if (phoneLayout) 24.dp else 10.dp,
                    vertical = if (phoneLayout) 22.dp else 11.dp,
                ),
        ) {
            Text(
                text = text.donate,
                color = Color(0xFFFFC857),
                fontSize = if (phoneLayout) 22.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (badge != null) {
                FidgetDonationBadgePill(
                    badge = badge,
                    phoneLayout = phoneLayout,
                    modifier = Modifier.padding(top = if (phoneLayout) 8.dp else 4.dp),
                )
            }
            Text(
                text = statusText,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                fontSize = if (phoneLayout) 14.sp else 8.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 3.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                FidgetDonationChoiceButton(
                    text = FIDGET_DONATION_PRODUCTS[0].label,
                    selected = true,
                    phoneLayout = phoneLayout,
                    onClick = { onDonate(FIDGET_DONATION_PRODUCTS[0].productId) },
                )
                FidgetDonationChoiceButton(
                    text = FIDGET_DONATION_PRODUCTS[1].label,
                    selected = false,
                    phoneLayout = phoneLayout,
                    onClick = { onDonate(FIDGET_DONATION_PRODUCTS[1].productId) },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                FidgetDonationChoiceButton(
                    text = FIDGET_DONATION_PRODUCTS[2].label,
                    selected = false,
                    phoneLayout = phoneLayout,
                    onClick = { onDonate(FIDGET_DONATION_PRODUCTS[2].productId) },
                )
                FidgetDonationChoiceButton(
                    text = FIDGET_DONATION_PRODUCTS[3].label,
                    selected = false,
                    phoneLayout = phoneLayout,
                    onClick = { onDonate(FIDGET_DONATION_PRODUCTS[3].productId) },
                )
            }
            FidgetMenuChoiceButton(
                text = text.done,
                selected = false,
                phoneLayout = phoneLayout,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun FidgetDonationChoiceButton(
    text: String,
    selected: Boolean,
    phoneLayout: Boolean = false,
    onClick: () -> Unit,
) {
    GlassCommandButton(
        text = text,
        modifier = Modifier
            .width(if (phoneLayout) 120.dp else 50.dp)
            .height(if (phoneLayout) 46.dp else 24.dp),
        fontSize = if (phoneLayout) 16.sp else 9.sp,
        selected = selected,
        prominent = selected,
        onClick = onClick,
    )
}

@Composable
private fun PlayStoreReviewPopup(
    text: FidgetText,
    statusText: String,
    onOpenReview: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.width(146.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF061112))
                    .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            ) {
                Text(
                    text = text.addReviewTitle,
                    color = Color(0xFFFFC857),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = statusText.ifBlank { text.openPlayStore },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    FidgetMenuChoiceButton(
                        text = text.no,
                        selected = false,
                        onClick = onDismiss,
                    )
                    FidgetMenuChoiceButton(
                        text = text.review,
                        selected = true,
                        onClick = onOpenReview,
                    )
                }
            }
            Text(
                text = text.privacyPolicy,
                color = Color(0xFF56F1C8),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenPrivacyPolicy)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun FidgetMenuChoiceButton(
    text: String,
    selected: Boolean,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    accentColorArgb: Int = NEON_GREEN_COLOR,
    phoneLayout: Boolean = false,
    onClick: () -> Unit,
) {
    FidgetThemeButton(
        text = text,
        modifier = Modifier
            .width(
                if (phoneLayout) {
                    120.dp
                } else if (text.length > 5) {
                    50.dp
                } else {
                    38.dp
                },
            )
            .height(if (phoneLayout) 42.dp else 24.dp),
        fontSize = if (phoneLayout) 14.sp else 8.sp,
        selected = selected,
        prominent = selected,
        accentColor = accentColor,
        accentColorArgb = accentColorArgb,
        onClick = onClick,
    )
}

@Composable
private fun FidgetSettingsButton(
    text: String,
    selected: Boolean,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    accentColorArgb: Int = NEON_GREEN_COLOR,
    phoneLayout: Boolean = false,
    onClick: () -> Unit,
) {
    FidgetThemeButton(
        text = text,
        modifier = Modifier
            .width(
                when {
                    phoneLayout && text.length > 12 -> 150.dp
                    phoneLayout && text.length > 7 -> 122.dp
                    phoneLayout && text.length > 5 -> 108.dp
                    phoneLayout -> 92.dp
                    text.length > 12 -> 92.dp
                    text.length > 7 -> 64.dp
                    text.length > 5 -> 54.dp
                    else -> 44.dp
                },
            )
            .height(if (phoneLayout) 42.dp else 25.dp),
        fontSize = if (phoneLayout) 14.sp else 8.sp,
        selected = selected,
        prominent = selected,
        accentColor = accentColor,
        accentColorArgb = accentColorArgb,
        onClick = onClick,
    )
}

@Composable
private fun FidgetThemeButton(
    text: String,
    modifier: Modifier,
    fontSize: TextUnit,
    selected: Boolean,
    prominent: Boolean,
    accentColor: Color,
    accentColorArgb: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val buttonColor = if (prominent) {
        accentColor.copy(alpha = if (selected) 0.92f else 0.78f)
    } else if (selected) {
        accentColor.copy(alpha = 0.34f)
    } else {
        accentColor.copy(alpha = 0.14f)
    }
    val borderColor = if (prominent || selected) {
        accentColor.copy(alpha = 0.95f)
    } else {
        accentColor.copy(alpha = 0.58f)
    }
    val textColor = if (prominent) {
        readableTextColorFor(accentColorArgb)
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(buttonColor, shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Context.openFidgetPlayStoreListing() {
    val packageName = BuildConfig.APPLICATION_ID
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        "market://details?id=$packageName".toUri(),
    )
    val webIntent = Intent(
        Intent.ACTION_VIEW,
        "https://play.google.com/store/apps/details?id=$packageName&reviewId=0".toUri(),
    )

    runCatching {
        startActivity(marketIntent)
    }.recoverCatching {
        startActivity(webIntent)
    }
}

private fun Context.isInstalledFromPlay(): Boolean {
    val installerPackageName = runCatching {
        packageManager.getInstallSourceInfo(packageName).installingPackageName
    }.getOrNull()
    return installerPackageName == "com.android.vending"
}

private fun Context.openFidgetPrivacyPolicy() {
    val policyIntent = Intent(
        Intent.ACTION_VIEW,
        FIDGET_PRIVACY_POLICY_URL.toUri(),
    )

    runCatching {
        startActivity(policyIntent)
    }
}

private fun Context.persistFidgetBackgroundImageAccess(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

private fun Context.loadFidgetBackgroundImage(uriString: String?): ImageBitmap? {
    if (uriString.isNullOrBlank()) return null

    return runCatching {
        val source = ImageDecoder.createSource(contentResolver, uriString.toUri())
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longestEdge = maxOf(info.size.width, info.size.height)
            if (longestEdge > 1_600) {
                val scale = 1_600f / longestEdge.toFloat()
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.asImageBitmap()
    }.getOrNull()
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun Context.loadFidgetSettings(): FidgetSettingsState {
    val preferences = getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE)
    val languageIndex = preferences.getInt(
        FIDGET_LANGUAGE_KEY,
        AppLanguages.indexOf(AppLanguage.English),
    )
    return FidgetSettingsState(
        fidgetCount = preferences.getInt(FIDGET_COUNT_KEY, 0).coerceAtLeast(0),
        mainColorArgb = preferences.getInt(FIDGET_MAIN_COLOR_KEY, NEON_GREEN_COLOR),
        backgroundColorArgb = preferences.getInt(FIDGET_BACKGROUND_COLOR_KEY, 0xFF061112.toInt()),
        backgroundImageUri = preferences.getString(FIDGET_BACKGROUND_IMAGE_URI_KEY, null),
        ringColorArgb = preferences.getInt(FIDGET_RING_COLOR_KEY, NEON_GREEN_COLOR),
        spinnerStyleIndex = fidgetSpinnerStyle(
            preferences.getInt(
                FIDGET_SPINNER_STYLE_KEY,
                DEFAULT_FIDGET_SPINNER_STYLE_INDEX,
            ),
        ).index,
        spinnerMultiEnabled = preferences.getBoolean(FIDGET_SPINNER_MULTI_KEY, true),
        hapticFeedbackEnabled = preferences.getBoolean(FIDGET_HAPTIC_ENABLED_KEY, true),
        soundFeedbackEnabled = preferences.getBoolean(FIDGET_SOUND_ENABLED_KEY, false),
        feedbackSoundMode = BeatSoundMode.fromPersistedValue(
            preferences.getInt(FIDGET_SOUND_MODE_KEY, BeatSoundMode.Clicks.persistedValue),
        ),
        accentIntensityMode = AccentIntensityMode.fromPersistedValue(
            preferences.getInt(FIDGET_ACCENT_INTENSITY_KEY, AccentIntensityMode.Big.persistedValue),
        ),
        rewardStyle = FidgetRewardStyle.fromPersistedValue(
            preferences.getInt(FIDGET_REWARD_STYLE_KEY, FidgetRewardStyle.Glow.persistedValue),
        ),
        appLanguage = AppLanguages.getOrElse(languageIndex) { AppLanguage.English },
        keepScreenOn = preferences.getBoolean(FIDGET_KEEP_SCREEN_ON_KEY, false),
        cpuPercentVisible = preferences.getBoolean(FIDGET_CPU_VISIBLE_KEY, false),
        pinnedToyIdsCsv = preferences.getString(FIDGET_PINNED_TOYS_KEY, "") ?: "",
        motionGesturesEnabled = preferences.getBoolean(FIDGET_MOTION_GESTURES_KEY, false),
        gestureControlMode = FidgetControlMode.fromPersistedValue(
            preferences.getInt(FIDGET_GESTURE_CONTROL_MODE_KEY, FidgetControlMode.Touch.persistedValue),
        ),
        phoneMotionEnabled = preferences.getBoolean(FIDGET_PHONE_MOTION_KEY, false),
        tiltGestureEnabled = preferences.getBoolean(FIDGET_TILT_GESTURE_KEY, true),
        shakeGestureEnabled = preferences.getBoolean(FIDGET_SHAKE_GESTURE_KEY, true),
        motionSensitivity = FidgetMotionSensitivity.fromPersistedValue(
            preferences.getInt(
                FIDGET_MOTION_SENSITIVITY_KEY,
                FidgetMotionSensitivity.Medium.persistedValue,
            ),
        ),
        neutralTiltX = preferences.getFloat(FIDGET_NEUTRAL_TILT_X_KEY, 0f),
        neutralTiltY = preferences.getFloat(FIDGET_NEUTRAL_TILT_Y_KEY, 0f),
    )
}

private fun Context.saveFidgetSettings(settings: FidgetSettingsState) {
    getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE).edit {
        putInt(FIDGET_COUNT_KEY, settings.fidgetCount.coerceAtLeast(0))
        putInt(FIDGET_MAIN_COLOR_KEY, settings.mainColorArgb)
        putInt(FIDGET_BACKGROUND_COLOR_KEY, settings.backgroundColorArgb)
        putString(FIDGET_BACKGROUND_IMAGE_URI_KEY, settings.backgroundImageUri)
        putInt(FIDGET_RING_COLOR_KEY, settings.ringColorArgb)
        putInt(FIDGET_SPINNER_STYLE_KEY, fidgetSpinnerStyle(settings.spinnerStyleIndex).index)
        putBoolean(FIDGET_SPINNER_MULTI_KEY, settings.spinnerMultiEnabled)
        putBoolean(FIDGET_HAPTIC_ENABLED_KEY, settings.hapticFeedbackEnabled)
        putBoolean(FIDGET_SOUND_ENABLED_KEY, settings.soundFeedbackEnabled)
        putInt(FIDGET_SOUND_MODE_KEY, settings.feedbackSoundMode.persistedValue)
        putInt(FIDGET_ACCENT_INTENSITY_KEY, settings.accentIntensityMode.persistedValue)
        putInt(FIDGET_REWARD_STYLE_KEY, settings.rewardStyle.persistedValue)
        putInt(FIDGET_LANGUAGE_KEY, AppLanguages.indexOf(settings.appLanguage).coerceAtLeast(0))
        putBoolean(FIDGET_KEEP_SCREEN_ON_KEY, settings.keepScreenOn)
        putBoolean(FIDGET_CPU_VISIBLE_KEY, settings.cpuPercentVisible)
        putString(FIDGET_PINNED_TOYS_KEY, settings.pinnedToyIdsCsv)
        putBoolean(FIDGET_MOTION_GESTURES_KEY, settings.motionGesturesEnabled)
        putInt(FIDGET_GESTURE_CONTROL_MODE_KEY, settings.gestureControlMode.persistedValue)
        putBoolean(FIDGET_PHONE_MOTION_KEY, settings.phoneMotionEnabled)
        putBoolean(FIDGET_TILT_GESTURE_KEY, settings.tiltGestureEnabled)
        putBoolean(FIDGET_SHAKE_GESTURE_KEY, settings.shakeGestureEnabled)
        putInt(FIDGET_MOTION_SENSITIVITY_KEY, settings.motionSensitivity.persistedValue)
        putFloat(FIDGET_NEUTRAL_TILT_X_KEY, settings.neutralTiltX)
        putFloat(FIDGET_NEUTRAL_TILT_Y_KEY, settings.neutralTiltY)
    }
    requestFidgetFavoriteComplicationUpdates()
    refreshFidgetPhoneSurfaces()
}

private fun Context.saveFidgetCount(count: Int) {
    getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE).edit {
        putInt(FIDGET_COUNT_KEY, count.coerceAtLeast(0))
    }
}

private fun Context.saveFidgetPinnedToyIds(pinnedToyIdsCsv: String) {
    getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE).edit {
        putString(FIDGET_PINNED_TOYS_KEY, pinnedToyIdsCsv)
    }
    requestFidgetFavoriteComplicationUpdates()
    refreshFidgetPhoneSurfaces()
}

private fun Context.loadFidgetDonationCounts(): Map<String, Int> {
    val preferences = getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE)
    return FIDGET_DONATION_PRODUCTS.associate { donation ->
        donation.productId to preferences.getInt(donation.donationCountKey(), 0).coerceAtLeast(0)
    }
}

private fun Context.saveFidgetDonationCounts(counts: Map<String, Int>): Map<String, Int> {
    val preferences = getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE)
    preferences.edit {
        FIDGET_DONATION_PRODUCTS.forEach { donation ->
            putInt(
            donation.donationCountKey(),
            counts.getOrDefault(donation.productId, 0).coerceIn(0, 5),
            )
        }
    }
    return loadFidgetDonationCounts()
}

private fun Context.recordFidgetDonation(productId: String): Map<String, Int> {
    if (FIDGET_DONATION_PRODUCTS.none { donation -> donation.productId == productId }) {
        return loadFidgetDonationCounts()
    }
    val preferences = getSharedPreferences(FIDGET_SETTINGS_PREFS, Context.MODE_PRIVATE)
    val donation = FIDGET_DONATION_PRODUCTS.first { it.productId == productId }
    val updatedCount = (preferences.getInt(donation.donationCountKey(), 0) + 1)
        .coerceAtMost(5)
    preferences.edit { putInt(donation.donationCountKey(), updatedCount) }
    return loadFidgetDonationCounts()
}

private fun FidgetDonationProduct.donationCountKey(): String {
    return "$FIDGET_DONATION_COUNT_PREFIX$productId"
}

internal fun String.toPinnedToyIds(): List<Int> {
    val builtToyIds = FIDGET_TOY_INFOS.map { it.id }.toSet()
    return split(",")
        .mapNotNull { value -> value.trim().toIntOrNull() }
        .filter { toyId -> toyId in builtToyIds }
        .distinct()
}

private fun fidgetPageOrderFor(pinnedToyIds: List<Int>): List<Int> {
    val pinnedSet = pinnedToyIds.toSet()
    val unpinnedToyIds = FIDGET_TOY_INFOS
        .map { it.id }
        .filterNot { toyId -> toyId in pinnedSet }
    return pinnedToyIds + unpinnedToyIds
}

internal fun fidgetMenuReturnToyIndex(lastPlayableToyIndex: Int): Int {
    return FIDGET_TOY_INFOS.firstOrNull { toy -> toy.id == lastPlayableToyIndex }
        ?.id
        ?: FIDGET_SPINNER_INDEX
}

internal fun fidgetToyNameFor(
    toyIndex: Int,
    language: AppLanguage,
    text: FidgetText,
): String {
    return when (toyIndex) {
        FIDGET_WALL_INDEX -> text.toyWall
        FIDGET_MENU_INDEX -> text.menu
        else -> FIDGET_TOY_INFOS.firstOrNull { toy -> toy.id == toyIndex }
            ?.nameFor(language)
            ?: text.title
    }
}

private fun Int.wrapFidgetIndex(size: Int): Int {
    if (size <= 0) return 0
    return ((this % size) + size) % size
}

private fun donationThanksTextFor(productId: String, language: AppLanguage): String {
    val label = FIDGET_DONATION_PRODUCTS.firstOrNull { it.productId == productId }?.label
        ?: "that"
    return fidgetTextFor(language).thanksFor(label)
}

private fun randomOpenWhackPosition(
    currentPosition: Int,
    occupiedPositions: Set<Int>,
): Int {
    val choices = (0 until SWITCH_MAZE_CELL_COUNT)
        .filter { position -> position != currentPosition && position !in occupiedPositions }
    if (choices.isEmpty()) return currentPosition
    return choices[Random.nextInt(choices.size)]
}

internal fun generateFidgetMazePuzzle(): FidgetMazePuzzle {
    val openings = MutableList(FIDGET_MAZE_CELL_COUNT) { 0 }
    val visited = BooleanArray(FIDGET_MAZE_CELL_COUNT)
    val playableCells = (0 until FIDGET_MAZE_CELL_COUNT)
        .filterNot { it == FIDGET_MAZE_RESET_CELL }
    val stack = mutableListOf(playableCells[Random.nextInt(playableCells.size)])
    visited[FIDGET_MAZE_RESET_CELL] = true
    visited[stack.last()] = true

    while (stack.isNotEmpty()) {
        val currentCell = stack.last()
        val column = currentCell % FIDGET_MAZE_COLUMNS
        val row = currentCell / FIDGET_MAZE_COLUMNS
        val neighbors = buildList {
            if (row > 0) add(MazeDirection.Up to currentCell - FIDGET_MAZE_COLUMNS)
            if (column < FIDGET_MAZE_COLUMNS - 1) add(MazeDirection.Right to currentCell + 1)
            if (row < FIDGET_MAZE_ROWS - 1) add(MazeDirection.Down to currentCell + FIDGET_MAZE_COLUMNS)
            if (column > 0) add(MazeDirection.Left to currentCell - 1)
        }.filter { (_, nextCell) -> nextCell != FIDGET_MAZE_RESET_CELL && !visited[nextCell] }

        if (neighbors.isEmpty()) {
            stack.removeAt(stack.lastIndex)
        } else {
            val (direction, nextCell) = neighbors[Random.nextInt(neighbors.size)]
            openings[currentCell] = openings[currentCell] or direction.openMask()
            openings[nextCell] = openings[nextCell] or direction.opposite().openMask()
            visited[nextCell] = true
            stack += nextCell
        }
    }

    val startCell = playableCells[Random.nextInt(playableCells.size)]
    var endCell = playableCells[Random.nextInt(playableCells.size)]
    while (endCell == startCell) {
        endCell = playableCells[Random.nextInt(playableCells.size)]
    }
    return FidgetMazePuzzle(
        openings = openings,
        startCell = startCell,
        endCell = endCell,
    )
}

private fun MazeDirection.openMask(): Int {
    return when (this) {
        MazeDirection.Up -> MAZE_OPEN_UP
        MazeDirection.Right -> MAZE_OPEN_RIGHT
        MazeDirection.Down -> MAZE_OPEN_DOWN
        MazeDirection.Left -> MAZE_OPEN_LEFT
    }
}

private fun MazeDirection.opposite(): MazeDirection {
    return when (this) {
        MazeDirection.Up -> MazeDirection.Down
        MazeDirection.Right -> MazeDirection.Left
        MazeDirection.Down -> MazeDirection.Up
        MazeDirection.Left -> MazeDirection.Right
    }
}

private fun Offset.toMazeDirection(): MazeDirection? {
    if (vectorLength() < 12f) return null
    return if (abs(x) > abs(y)) {
        if (x > 0f) MazeDirection.Right else MazeDirection.Left
    } else {
        if (y > 0f) MazeDirection.Down else MazeDirection.Up
    }
}

internal fun formatFidgetCount(value: Int): String {
    if (value < 1_000_000) return value.toString().reversed().chunked(3).joinToString(",").reversed()

    val tenths = value.toLong() * 10L / 1_000_000L
    val whole = tenths / 10L
    val decimal = tenths % 10L
    return if (decimal == 0L) "${whole}M" else "${whole}.${decimal}M"
}

private fun isFibonacciReward(count: Int): Boolean {
    if (count <= 0) return false
    var previous = 1
    var current = 1
    while (current < count) {
        val next = previous + current
        previous = current
        current = next
    }
    return current == count
}

private fun nextFibonacciTarget(count: Int): Int {
    if (count < 1) return 1
    var previous = 1
    var current = 1
    while (current <= count) {
        val next = previous + current
        previous = current
        current = next
    }
    return current
}

private fun previousFibonacciTarget(count: Int): Int {
    if (count < 1) return 0
    var previous = 0
    var current = 1
    while (current <= count) {
        val next = previous + current
        previous = current
        current = next
    }
    return previous
}

private fun positiveRewardMessageFor(count: Int, language: AppLanguage): String {
    val messages = when (language) {
        AppLanguage.English -> listOf("Keep going", "Nice rhythm", "You got this", "Momentum made")
        AppLanguage.Spanish -> listOf("Sigue asi", "Buen ritmo", "Tu puedes", "Buen impulso")
    }
    return messages[count % messages.size]
}

@Composable
private fun rememberFidgetCpuUsagePercent(enabled: Boolean): Float? {
    val sampler = remember { FidgetCpuSampler() }
    var cpuUsagePercent by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(enabled) {
        if (!enabled) {
            cpuUsagePercent = null
            return@LaunchedEffect
        }

        sampler.reset()
        while (true) {
            delay(1_000L)
            cpuUsagePercent = sampler.sample()
        }
    }

    return cpuUsagePercent
}

private class FidgetCpuSampler {
    private var previousWallNanos = 0L
    private var previousCpuMillis = 0L

    fun reset() {
        previousWallNanos = 0L
        previousCpuMillis = 0L
    }

    fun sample(): Float? {
        val wallNanos = System.nanoTime()
        val cpuMillis = android.os.Process.getElapsedCpuTime()
        if (previousWallNanos == 0L) {
            previousWallNanos = wallNanos
            previousCpuMillis = cpuMillis
            return null
        }

        val wallMillis = (wallNanos - previousWallNanos) / 1_000_000f
        val cpuDeltaMillis = (cpuMillis - previousCpuMillis).toFloat()
        previousWallNanos = wallNanos
        previousCpuMillis = cpuMillis
        if (wallMillis <= 0f) return null
        return (cpuDeltaMillis / wallMillis * 100f).coerceIn(0f, 100f)
    }
}

private fun Float?.formatFidgetCpuPercent(): String {
    if (this == null) return "--%"
    return "${roundToInt().coerceIn(0, 100)}%"
}

private fun AccentIntensityMode.feedbackVolume(): Float {
    return when (this) {
        AccentIntensityMode.Big -> 0.95f
        AccentIntensityMode.Medium -> 0.72f
        AccentIntensityMode.Little -> 0.52f
        AccentIntensityMode.Silent -> 0.2f
    }
}

private fun AccentIntensityMode.feedbackDurationMs(): Int {
    return when (this) {
        AccentIntensityMode.Big -> 44
        AccentIntensityMode.Medium -> 34
        AccentIntensityMode.Little -> 24
        AccentIntensityMode.Silent -> 14
    }
}

private fun AccentIntensityMode.feedbackVibrationMs(): Long {
    return when (this) {
        AccentIntensityMode.Big -> 28L
        AccentIntensityMode.Medium -> 18L
        AccentIntensityMode.Little -> 11L
        AccentIntensityMode.Silent -> 6L
    }
}

private fun AccentIntensityMode.feedbackVibrationAmplitude(): Int {
    return when (this) {
        AccentIntensityMode.Big -> VibrationEffect.DEFAULT_AMPLITUDE
        AccentIntensityMode.Medium -> 156
        AccentIntensityMode.Little -> 84
        AccentIntensityMode.Silent -> 32
    }
}

@Composable
private fun FidgetSelectionWallPage(
    pinnedToyIds: List<Int>,
    favoriteToyId: Int,
    language: AppLanguage,
    text: FidgetText,
    accentColor: Color,
    accentColorArgb: Int,
    resetKey: Int,
    onToySelected: (Int) -> Unit,
    onPinToggle: (Int) -> Unit,
    onFavoriteSelected: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    val pinnedSet = pinnedToyIds.toSet()
    val unpinnedByStyle = FIDGET_TOY_INFOS
        .filterNot { toy -> toy.id in pinnedSet }
        .groupBy { it.styleFor(language) }

    LaunchedEffect(resetKey) {
        scrollState.scrollTo(0)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
    ) {
        val wallWidth = if (maxWidth > 620.dp) 620.dp else maxWidth
        val wallPhoneLayout = maxHeight >= 360.dp || maxWidth >= 360.dp

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .width(wallWidth)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(
                        start = 4.dp,
                        top = if (wallPhoneLayout) 18.dp else 3.dp,
                        end = 8.dp,
                        bottom = 42.dp,
                    ),
            ) {
                FidgetWallSectionTitle(text.pinnedFidgets, accentColor)
                if (pinnedToyIds.isNotEmpty()) {
                    pinnedToyIds.mapNotNull { toyId ->
                        FIDGET_TOY_INFOS.firstOrNull { it.id == toyId }
                    }.forEach { toy ->
                        FidgetWallToyRow(
                            toy = toy,
                            language = language,
                            text = text,
                            pinned = true,
                            favorite = toy.id == favoriteToyId,
                            phoneLayout = wallPhoneLayout,
                            enabled = true,
                            accentColor = accentColor,
                            accentColorArgb = accentColorArgb,
                            onToySelected = onToySelected,
                            onPinToggle = onPinToggle,
                            onFavoriteSelected = onFavoriteSelected,
                        )
                    }
                }

                unpinnedByStyle.forEach { (style, toys) ->
                    FidgetWallSectionTitle(style, accentColor)
                    toys.forEach { toy ->
                        FidgetWallToyRow(
                            toy = toy,
                            language = language,
                            text = text,
                            pinned = toy.id in pinnedSet,
                            favorite = toy.id == favoriteToyId,
                            phoneLayout = wallPhoneLayout,
                            enabled = true,
                            accentColor = accentColor,
                            accentColorArgb = accentColorArgb,
                            onToySelected = onToySelected,
                            onPinToggle = onPinToggle,
                            onFavoriteSelected = onFavoriteSelected,
                        )
                    }
                }
            }
        }

        FidgetMenuScrollBar(
            scrollState = scrollState,
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (wallPhoneLayout) 0.dp else 12.dp)
                .width(4.dp)
                .height(88.dp),
        )
    }
}

@Composable
private fun FidgetWallSectionTitle(
    text: String,
    accentColor: Color,
) {
    Text(
        text = text,
        color = accentColor.copy(alpha = 0.9f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    )
}

@Composable
private fun FidgetWallToyRow(
    toy: FidgetToyInfo,
    language: AppLanguage,
    text: FidgetText,
    pinned: Boolean,
    favorite: Boolean,
    phoneLayout: Boolean,
    enabled: Boolean,
    accentColor: Color,
    accentColorArgb: Int,
    onToySelected: (Int) -> Unit,
    onPinToggle: (Int) -> Unit,
    onFavoriteSelected: (Int) -> Unit,
) {
    val rowAlpha = if (enabled) 1f else 0.5f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FidgetThemeButton(
            text = toy.nameFor(language),
            modifier = Modifier
                .weight(1f)
                .height(25.dp),
            fontSize = 9.sp,
            selected = pinned,
            prominent = enabled && pinned,
            accentColor = accentColor.copy(alpha = rowAlpha),
            accentColorArgb = accentColorArgb,
            onClick = {
                if (enabled) {
                    onToySelected(toy.id)
                }
            },
        )
        FidgetThemeButton(
            text = if (enabled) {
                if (pinned) text.pinned else text.pin
            } else {
                text.soon
            },
            modifier = Modifier
                .width(if (enabled && pinned) 44.dp else 36.dp)
                .height(25.dp),
            fontSize = 8.sp,
            selected = pinned,
            prominent = enabled && pinned,
            accentColor = accentColor.copy(alpha = rowAlpha),
            accentColorArgb = accentColorArgb,
            onClick = {
                if (enabled) {
                    onPinToggle(toy.id)
                }
            },
        )
        FidgetFavoriteButton(
            favorite = favorite,
            enabled = enabled,
            phoneLayout = phoneLayout,
            language = language,
            accentColor = accentColor,
            accentColorArgb = accentColorArgb,
            onClick = { onFavoriteSelected(toy.id) },
        )
    }
}

@Composable
private fun FidgetFavoriteButton(
    favorite: Boolean,
    enabled: Boolean,
    phoneLayout: Boolean,
    language: AppLanguage,
    accentColor: Color,
    accentColorArgb: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(7.dp)
    val iconColor = readableTextColorFor(accentColorArgb)
    val description = when (language) {
        AppLanguage.English -> if (favorite) {
            "Favorite toy"
        } else {
            "Set as favorite toy and app icon"
        }
        AppLanguage.Spanish -> if (favorite) {
            "Juguete favorito"
        } else {
            "Usar como juguete favorito e icono"
        }
    }

    Box(
        modifier = Modifier
            .size(width = if (phoneLayout) 34.dp else 28.dp, height = 25.dp)
            .clip(shape)
            .background(
                if (favorite) {
                    accentColor.copy(alpha = 0.92f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                },
            )
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = if (favorite) 0.95f else 0.5f),
                shape = shape,
            )
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FidgetFavoriteStar(
            favorite = favorite,
            iconColor = iconColor,
            accentColor = accentColor,
        )
    }
}

@Composable
private fun FidgetFavoriteStar(
    favorite: Boolean,
    iconColor: Color,
    accentColor: Color,
) {
    Canvas(modifier = Modifier.size(15.dp)) {
            val path = Path()
            val outerRadius = size.minDimension * 0.48f
            val innerRadius = outerRadius * 0.45f
            repeat(10) { pointIndex ->
                val angle = -PI.toFloat() / 2f + pointIndex * PI.toFloat() / 5f
                val radius = if (pointIndex % 2 == 0) outerRadius else innerRadius
                val point = center + Offset(cos(angle), sin(angle)) * radius
                if (pointIndex == 0) {
                    path.moveTo(point.x, point.y)
                } else {
                    path.lineTo(point.x, point.y)
                }
            }
            path.close()
            if (favorite) {
                drawPath(path = path, color = iconColor)
            }
            drawPath(
                path = path,
                color = if (favorite) iconColor else accentColor,
                style = Stroke(width = if (favorite) 1.dp.toPx() else 1.6.dp.toPx()),
            )
    }
    }

@Composable
private fun FidgetOuterRing(
    ringColor: Color,
    rainbow: Boolean,
    rainbowRotationDegrees: Float,
    touchPulse: Float,
    rewardPulse: Float,
    edgeInset: Dp,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val inset = edgeInset.toPx()
        val radius = (size.minDimension / 2f - inset).coerceAtLeast(0f)
        val alpha = (0.34f + touchPulse * 0.10f + rewardPulse * 0.24f).coerceIn(0f, 0.72f)
        drawFidgetThemeRing(
            ringColor = ringColor,
            rainbow = rainbow,
            rainbowRotationDegrees = rainbowRotationDegrees,
            alpha = alpha,
            radius = radius,
            center = center,
            strokeWidth = (3.dp + rewardPulse.dp).toPx(),
        )
    }
}

@Composable
private fun FidgetStageBackdrop(
    ringColor: Color,
    rainbow: Boolean,
    rainbowRotationDegrees: Float,
    touchPulse: Float,
    rewardPulse: Float,
    toyScale: Float,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stageInset = 8.dp.toPx()
        val maximumRadius = (size.minDimension / 2f - stageInset).coerceAtLeast(0f)
        val desiredOuterRadius = 59.dp.toPx() * toyScale * 1.15f
        val outerRadius = minOf(
            desiredOuterRadius * (1f + rewardPulse * 0.103f),
            maximumRadius,
        )
        val desiredInnerRadius = desiredOuterRadius * (0.24f / 0.34f)
        val innerRadius = minOf(
            desiredInnerRadius * (1f + touchPulse * 0.125f + rewardPulse * 0.083f),
            maximumRadius,
        )
        val rewardAlpha = (0.34f + rewardPulse * 0.40f).coerceIn(0f, 0.74f)

        drawFidgetThemeRing(
            ringColor = ringColor,
            rainbow = rainbow,
            rainbowRotationDegrees = rainbowRotationDegrees,
            alpha = rewardAlpha * 0.68f,
            radius = outerRadius,
            center = center,
            strokeWidth = (2.dp + (rewardPulse * 4f).dp).toPx(),
        )
        drawFidgetThemeRing(
            ringColor = ringColor,
            rainbow = rainbow,
            rainbowRotationDegrees = -rainbowRotationDegrees,
            alpha = 0.13f + touchPulse * 0.18f,
            radius = innerRadius,
            center = center,
            strokeWidth = 7.dp.toPx(),
        )
    }
}

private fun DrawScope.drawFidgetThemeRing(
    ringColor: Color,
    rainbow: Boolean,
    rainbowRotationDegrees: Float,
    alpha: Float,
    radius: Float,
    center: Offset,
    strokeWidth: Float,
) {
    if (rainbow) {
        rotate(rainbowRotationDegrees, center) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = FidgetRainbowRingColors,
                    center = center,
                ),
                alpha = alpha.coerceIn(0f, 1f),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth),
            )
        }
    } else {
        drawCircle(
            color = ringColor.copy(alpha = alpha.coerceIn(0f, 1f)),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth),
        )
    }
}

private val FidgetRainbowRingColors = RainbowColors + RainbowColors.first()

@Composable
private fun FidgetSpinner(
    rotation: Float,
    style: FidgetSpinnerStyle,
    mainColor: Color,
    ringColor: Color,
    showSatellites: Boolean,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val mainRadius = size.minDimension * if (showSatellites) 0.39f else 0.43f

        if (showSatellites) {
            val horizontalOffset = size.width * 0.37f
            val verticalOffset = size.height * 0.23f
            val satelliteRadius = size.minDimension * 0.16f
            val satelliteCenters = listOf(
                center + Offset(-horizontalOffset, -verticalOffset),
                center + Offset(-horizontalOffset, verticalOffset),
                center + Offset(horizontalOffset, -verticalOffset),
                center + Offset(horizontalOffset, verticalOffset),
            )
            val rotationMultipliers = floatArrayOf(-1.38f, 1.72f, -1.96f, 1.44f)
            val rotationOffsets = floatArrayOf(18f, 72f, 126f, 216f)

            satelliteCenters.forEachIndexed { index, satelliteCenter ->
                drawStyledSpinner(
                    center = satelliteCenter,
                    radius = satelliteRadius,
                    rotationDegrees = rotation * rotationMultipliers[index] + rotationOffsets[index],
                    style = style,
                    mainColor = mainColor,
                    ringColor = ringColor,
                    alpha = 0.92f,
                )
            }
        }

        drawStyledSpinner(
            center = center,
            radius = mainRadius,
            rotationDegrees = rotation,
            style = style,
            mainColor = mainColor,
            ringColor = ringColor,
        )
    }
}

private fun DrawScope.drawStyledSpinner(
    center: Offset,
    radius: Float,
    rotationDegrees: Float,
    style: FidgetSpinnerStyle,
    mainColor: Color,
    ringColor: Color,
    alpha: Float = 1f,
) {
    val armColor = style.armColorArgb?.let { colorArgb -> Color(colorArgb) } ?: mainColor
    val centerColor = style.centerColorArgb?.let { colorArgb -> Color(colorArgb) } ?: ringColor

    rotate(rotationDegrees, center) {
        val armRadius = radius * 0.62f * style.stemLengthScale
        val ballRadius = radius * 0.22f * style.ballScale
        val armWidth = maxOf(1f, radius * 0.16f * style.stemWidthScale)
        val bearingCenters = List(3) { index ->
            val angle = Math.toRadians((index * 120.0) - 90.0)
            center + Offset(
                x = cos(angle).toFloat() * armRadius,
                y = sin(angle).toFloat() * armRadius,
            )
        }

        bearingCenters.forEach { bearingCenter ->
            drawLine(
                color = armColor.copy(alpha = 0.96f * alpha),
                start = center,
                end = bearingCenter,
                strokeWidth = armWidth,
                cap = StrokeCap.Round,
            )
        }

        val jointPath = Path().apply {
            bearingCenters.forEachIndexed { index, bearingCenter ->
                val jointPoint = center + (bearingCenter - center) * 0.72f
                if (index == 0) moveTo(jointPoint.x, jointPoint.y) else lineTo(jointPoint.x, jointPoint.y)
            }
            close()
        }
        drawPath(jointPath, armColor.copy(alpha = 0.9f * alpha))

        bearingCenters.forEachIndexed { index, bearingCenter ->
            val ballColor = Color(style.ballColor(index))
            drawCircle(
                color = Color.Black.copy(alpha = 0.7f * alpha),
                radius = ballRadius * 1.13f,
                center = bearingCenter,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f * alpha),
                        ballColor.copy(alpha = alpha),
                        Color.Black.copy(alpha = 0.9f * alpha),
                    ),
                    center = bearingCenter - Offset(ballRadius * 0.24f, ballRadius * 0.24f),
                    radius = ballRadius * 1.35f,
                ),
                radius = ballRadius,
                center = bearingCenter,
            )
        }

        drawSpinnerCenterPiece(
            center = center,
            radius = radius * 0.3f * style.centerScale,
            color = centerColor,
            centerPiece = style.centerPiece,
            alpha = alpha,
        )
    }
}

private fun DrawScope.drawSpinnerCenterPiece(
    center: Offset,
    radius: Float,
    color: Color,
    centerPiece: FidgetSpinnerCenterPiece,
    alpha: Float,
) {
    drawCircle(
        color = Color.Black.copy(alpha = 0.76f * alpha),
        radius = radius * 1.12f,
        center = center,
    )

    when (centerPiece) {
        FidgetSpinnerCenterPiece.Disc -> {
            drawCircle(color.copy(alpha = alpha), radius, center)
            drawCircle(
                color = Color.White.copy(alpha = 0.5f * alpha),
                radius = radius * 0.18f,
                center = center - Offset(radius * 0.26f, radius * 0.26f),
            )
        }
        FidgetSpinnerCenterPiece.Ring -> {
            drawCircle(color.copy(alpha = alpha), radius, center)
            drawCircle(Color.Black.copy(alpha = 0.9f * alpha), radius * 0.55f, center)
            drawCircle(color.copy(alpha = 0.78f * alpha), radius * 0.18f, center)
        }
        FidgetSpinnerCenterPiece.Hex -> {
            drawSpinnerPolygon(center, radius, 6, -90f, color.copy(alpha = alpha))
            drawSpinnerPolygon(
                center,
                radius * 0.62f,
                6,
                -90f,
                Color.Black.copy(alpha = 0.28f * alpha),
            )
        }
        FidgetSpinnerCenterPiece.Square -> {
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = center - Offset(radius * 0.78f, radius * 0.78f),
                size = Size(radius * 1.56f, radius * 1.56f),
                cornerRadius = CornerRadius(radius * 0.24f, radius * 0.24f),
            )
            drawCircle(Color.White.copy(alpha = 0.32f * alpha), radius * 0.16f, center)
        }
        FidgetSpinnerCenterPiece.Diamond -> {
            val diamond = Path().apply {
                moveTo(center.x, center.y - radius)
                lineTo(center.x + radius, center.y)
                lineTo(center.x, center.y + radius)
                lineTo(center.x - radius, center.y)
                close()
            }
            drawPath(diamond, color.copy(alpha = alpha))
            drawPath(
                diamond,
                Color.White.copy(alpha = 0.46f * alpha),
                style = Stroke(width = maxOf(1f, radius * 0.13f)),
            )
        }
        FidgetSpinnerCenterPiece.Target -> {
            drawCircle(color.copy(alpha = alpha), radius, center)
            drawCircle(Color.Black.copy(alpha = 0.86f * alpha), radius * 0.66f, center)
            drawCircle(color.copy(alpha = alpha), radius * 0.36f, center)
            drawCircle(Color.White.copy(alpha = 0.72f * alpha), radius * 0.11f, center)
        }
        FidgetSpinnerCenterPiece.Bolt -> {
            drawSpinnerPolygon(center, radius, 6, -90f, color.copy(alpha = alpha))
            drawCircle(Color.Black.copy(alpha = 0.82f * alpha), radius * 0.48f, center)
            drawSpinnerPolygon(
                center,
                radius * 0.28f,
                6,
                -90f,
                Color.White.copy(alpha = 0.65f * alpha),
            )
        }
    }
}

private fun DrawScope.drawSpinnerPolygon(
    center: Offset,
    radius: Float,
    sides: Int,
    rotationDegrees: Float,
    color: Color,
) {
    val path = Path()
    repeat(sides) { index ->
        val angle = Math.toRadians(rotationDegrees.toDouble() + (360.0 * index / sides))
        val point = center + Offset(
            x = cos(angle).toFloat() * radius,
            y = sin(angle).toFloat() * radius,
        )
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color)
}

@Composable
private fun SwitchFidgetToy(
    switchMask: Int,
    onSwitchToggle: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(128.dp)
            .height(118.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) { index ->
                FidgetToggleSwitch(
                    switchedOn = switchMask and (1 shl index) != 0,
                    onClick = { onSwitchToggle(index) },
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            repeat(4) { index ->
                val switchIndex = index + 4
                FidgetToggleSwitch(
                    switchedOn = switchMask and (1 shl switchIndex) != 0,
                    onClick = { onSwitchToggle(switchIndex) },
                )
            }
        }
    }
}

@Composable
private fun SwitchMazeToy(
    mazePosition: Int,
    onMove: (deltaColumn: Int, deltaRow: Int) -> Unit,
) {
    val column = mazePosition % SWITCH_MAZE_COLUMNS
    val row = mazePosition / SWITCH_MAZE_COLUMNS
    val buttonColor = if ((row + column) % 2 == 0) Color(0xFFFFC857) else Color(0xFF56F1C8)

    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingPx = 12.dp.toPx()
            val cellWidth = (size.width - paddingPx * 2f) / SWITCH_MAZE_COLUMNS
            val cellHeight = (size.height - paddingPx * 2f) / SWITCH_MAZE_ROWS

            repeat(SWITCH_MAZE_COLUMNS + 1) { lineIndex ->
                val x = paddingPx + lineIndex * cellWidth
                drawLine(
                    color = Color(0xFF56F1C8).copy(alpha = 0.22f),
                    start = androidx.compose.ui.geometry.Offset(x, paddingPx),
                    end = androidx.compose.ui.geometry.Offset(x, size.height - paddingPx),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            repeat(SWITCH_MAZE_ROWS + 1) { lineIndex ->
                val y = paddingPx + lineIndex * cellHeight
                drawLine(
                    color = Color(0xFFFFC857).copy(alpha = 0.18f),
                    start = androidx.compose.ui.geometry.Offset(paddingPx, y),
                    end = androidx.compose.ui.geometry.Offset(size.width - paddingPx, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        MazeStepButton(
            color = buttonColor,
            modifier = Modifier.offset {
                val cellStepPx = 23.dp.toPx()
                IntOffset(
                    x = ((column - 1.5f) * cellStepPx).roundToInt(),
                    y = ((row - 1.5f) * cellStepPx).roundToInt(),
                )
            },
            onMove = onMove,
        )
    }
}

@Composable
private fun MazeStepButton(
    color: Color,
    modifier: Modifier = Modifier,
    onMove: (deltaColumn: Int, deltaRow: Int) -> Unit,
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.42f),
                        color,
                        color.copy(alpha = 0.46f),
                        Color.Black.copy(alpha = 0.24f),
                    ),
                ),
            )
            .border(2.dp, Color.White.copy(alpha = 0.44f), CircleShape)
            .pointerInput(Unit) {
                var dragX = 0f
                var dragY = 0f
                detectDragGestures(
                    onDragStart = {
                        dragX = 0f
                        dragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                    },
                    onDragEnd = {
                        if (abs(dragX) > abs(dragY)) {
                            when {
                                dragX > 6f -> onMove(1, 0)
                                dragX < -6f -> onMove(-1, 0)
                            }
                        } else {
                            when {
                                dragY > 6f -> onMove(0, 1)
                                dragY < -6f -> onMove(0, -1)
                            }
                        }
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.5f)),
        )
    }
}

@Composable
private fun FidgetToggleSwitch(
    switchedOn: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val trackColor = if (switchedOn) Color(0xFF56F1C8) else Color(0xFF1B2428)
    val knobColor = if (switchedOn) Color(0xFFFFC857) else Color(0xFF8D98A0)
    val knobAlignment = if (switchedOn) Alignment.TopCenter else Alignment.BottomCenter

    Box(
        modifier = modifier
            .width(24.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        trackColor.copy(alpha = if (switchedOn) 0.78f else 0.46f),
                        Color.Black.copy(alpha = 0.34f),
                    ),
                ),
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = 1.dp,
                color = if (switchedOn) Color(0xFFFFC857) else Color(0xFF56F1C8).copy(alpha = 0.42f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(3.dp),
        contentAlignment = knobAlignment,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(knobColor)
                .border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape),
        )
    }
}

@Composable
private fun FidgetHorizontalToggleSwitch(
    switchedOn: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val trackColor = if (switchedOn) Color(0xFF56F1C8) else Color(0xFF1B2428)
    val knobColor = if (switchedOn) Color(0xFFFFC857) else Color(0xFF8D98A0)
    val knobAlignment = if (switchedOn) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = modifier
            .width(48.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.34f),
                        trackColor.copy(alpha = if (switchedOn) 0.78f else 0.46f),
                    ),
                ),
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = 1.dp,
                color = if (switchedOn) Color(0xFFFFC857) else Color(0xFF56F1C8).copy(alpha = 0.42f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(3.dp),
        contentAlignment = knobAlignment,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(knobColor)
                .border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape),
        )
    }
}

@Composable
private fun FreeMoveButtonToy(
    buttonPositions: List<Offset>,
    onButtonDragStart: () -> Unit,
    onButtonMove: (index: Int, delta: Offset) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingPx = 6.dp.toPx()
            repeat(9) { lineIndex ->
                val progress = lineIndex / 8f
                val x = paddingPx + (size.width - paddingPx * 2f) * progress
                val y = paddingPx + (size.height - paddingPx * 2f) * progress
                drawLine(
                    color = Color(0xFF56F1C8).copy(alpha = 0.16f),
                    start = Offset(x, paddingPx),
                    end = Offset(x, size.height - paddingPx),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = Color(0xFFFFC857).copy(alpha = 0.13f),
                    start = Offset(paddingPx, y),
                    end = Offset(size.width - paddingPx, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        buttonPositions.forEachIndexed { index, position ->
            FreeMoveButton(
                index = index,
                position = position,
                onDragStart = onButtonDragStart,
                onDrag = { delta -> onButtonMove(index, delta) },
            )
        }
    }
}

@Composable
private fun FreeMoveButton(
    index: Int,
    position: Offset,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
) {
    val density = LocalDensity.current
    val colors = listOf(
        Color(0xFFFFC857),
        Color(0xFF56F1C8),
        Color(0xFFEF476F),
        Color(0xFF8D6BFF),
    )
    val color = colors[index % colors.size]

    Box(
        modifier = Modifier
            .offset(x = position.x.dp, y = position.y.dp)
            .size(28.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color,
                        color.copy(alpha = 0.52f),
                        Color.Black.copy(alpha = 0.24f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.48f), CircleShape)
            .pointerInput(index) {
                detectDragGestures(
                    onDragStart = {
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(
                            with(density) {
                                Offset(
                                    x = dragAmount.x.toDp().value,
                                    y = dragAmount.y.toDp().value,
                                )
                            }
                        )
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.45f)),
        )
    }
}

@Composable
private fun SquishyFidgetToy(
    pullOffset: Offset,
    pressure: Float,
    accentColor: Color,
    onPress: () -> Unit,
    onPull: (Offset, Float) -> Unit,
    onRelease: () -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, accentColor.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                var dragOffset = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        dragOffset = Offset.Zero
                        onPress()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset = with(density) {
                            Offset(
                                x = dragOffset.x + dragAmount.x.toDp().value,
                                y = dragOffset.y + dragAmount.y.toDp().value,
                            )
                        }.limitedToLength(SQUISHY_PULL_LIMIT_DP)
                        onPull(
                            dragOffset,
                            (0.35f + dragOffset.vectorLength() / SQUISHY_PULL_LIMIT_DP).coerceIn(0f, 1f),
                        )
                    },
                    onDragEnd = {
                        onRelease()
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                        onRelease()
                        dragOffset = Offset.Zero
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val pullPx = Offset(pullOffset.x.dp.toPx(), pullOffset.y.dp.toPx())
            val pressureClamped = pressure.coerceIn(0f, 1f)
            val stretch = (pullOffset.vectorLength() / SQUISHY_PULL_LIMIT_DP).coerceIn(0f, 1f)
            val blobCenter = center + pullPx * 0.28f
            val blobWidth = 62.dp.toPx() + stretch * 18.dp.toPx() - pressureClamped * 7.dp.toPx()
            val blobHeight = 62.dp.toPx() - stretch * 8.dp.toPx() + pressureClamped * 12.dp.toPx()

            drawCircle(
                color = accentColor.copy(alpha = 0.16f),
                radius = 47.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawLine(
                color = Color(0xFFFFC857).copy(alpha = 0.18f + stretch * 0.34f),
                start = center,
                end = blobCenter,
                strokeWidth = (1.dp + (stretch * 3f).dp).toPx(),
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.58f),
                        Color(0xFFFFC857).copy(alpha = 0.92f),
                        accentColor.copy(alpha = 0.8f),
                        Color(0xFFEF476F).copy(alpha = 0.82f),
                        Color.Black.copy(alpha = 0.25f),
                    ),
                    center = blobCenter + Offset(-11.dp.toPx(), -13.dp.toPx()),
                    radius = 58.dp.toPx(),
                ),
                topLeft = Offset(
                    x = blobCenter.x - blobWidth / 2f,
                    y = blobCenter.y - blobHeight / 2f,
                ),
                size = Size(blobWidth, blobHeight),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.34f + pressureClamped * 0.18f),
                radius = 8.dp.toPx() + pressureClamped * 4.dp.toPx(),
                center = blobCenter + Offset(-13.dp.toPx(), -15.dp.toPx()),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.13f),
                radius = 23.dp.toPx() + pressureClamped * 4.dp.toPx(),
                center = blobCenter + Offset(8.dp.toPx(), 11.dp.toPx()),
                style = Stroke(width = 2.dp.toPx() + pressureClamped * 1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun MagSnapFidgetToy(
    position: Int,
    onMove: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .pointerInput(position) {
                var dragX = 0f
                detectDragGestures(
                    onDragStart = { dragX = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                    },
                    onDragEnd = {
                        when {
                            dragX > 9f -> onMove(1)
                            dragX < -9f -> onMove(-1)
                        }
                    },
                    onDragCancel = { dragX = 0f },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawLine(
                color = Color(0xFF56F1C8).copy(alpha = 0.42f),
                start = center + Offset(-38.dp.toPx(), 0f),
                end = center + Offset(38.dp.toPx(), 0f),
                strokeWidth = 5.dp.toPx(),
            )
            repeat(3) { index ->
                val x = center.x + (index - 1) * 34.dp.toPx()
                drawCircle(
                    color = Color(0xFFFFC857).copy(alpha = if (index == position) 0.7f else 0.28f),
                    radius = 10.dp.toPx(),
                    center = Offset(x, center.y),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Box(
            modifier = Modifier
                .offset(x = ((position - 1) * 34).dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.62f), Color(0xFF56F1C8), Color(0xFF122A2A)),
                    ),
                )
                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                .clickable { onMove(if (position < 2) 1 else -2) },
        )
    }
}

@Composable
private fun PopGridFidgetToy(
    popMask: Int,
    onPop: (Int) -> Unit,
    onReset: () -> Unit,
) {
    val boardShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(118.dp)
            .background(Color.White.copy(alpha = 0.06f), boardShape)
            .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), boardShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(4) { column ->
                        val index = row * 4 + column
                        if (index == 3) {
                            FidgetThemeButton(
                                text = "R",
                                modifier = Modifier.size(23.dp),
                                fontSize = 8.sp,
                                selected = true,
                                prominent = true,
                                accentColor = Color(0xFF56F1C8),
                                accentColorArgb = 0xFF56F1C8.toInt(),
                                onClick = onReset,
                            )
                            return@repeat
                        }
                        val popped = popMask and (1 shl index) != 0
                        Box(
                            modifier = Modifier
                                .size(23.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            if (popped) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.5f),
                                            if (popped) Color(0xFF243033) else Color(0xFFFFC857),
                                            if (popped) Color(0xFF56F1C8).copy(alpha = 0.4f) else Color(0xFFEF476F),
                                        ),
                                    ),
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.38f), CircleShape)
                                .clickable { onPop(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfinityFlipFidgetToy(
    fold: Int,
    onFlip: () -> Unit,
    flippedCardMask: Int,
    onCardFlip: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val cardColors = listOf(
        Color(0xFFFFC857),
        Color(0xFF56F1C8),
        Color(0xFFEF476F),
        Color(0xFF8D6BFF),
    )
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .clickable(onClick = onFlip),
        contentAlignment = Alignment.Center,
    ) {
        repeat(4) { index ->
            val column = index % 2
            val row = index / 2
            val open = (fold + index) % 4
            val flipped = flippedCardMask and (1 shl index) != 0
            val flipProgress by animateFloatAsState(
                targetValue = if (flipped) 1f else 0f,
                animationSpec = tween(durationMillis = 280),
                label = "infinityCardFlip$index",
            )
            val baseColor = cardColors[index]
            val cardColor = if (flipProgress < 0.5f) {
                baseColor
            } else {
                Color(1f - baseColor.red, 1f - baseColor.green, 1f - baseColor.blue)
            }
            Box(
                modifier = Modifier
                    .offset(
                        x = ((column - 0.5f) * (34 + open * 3)).dp,
                        y = ((row - 0.5f) * (34 + (3 - open) * 3)).dp,
                    )
                    .rotate((fold * 18f + index * 7f) % 45f)
                    .graphicsLayer {
                        rotationY = flipProgress * 180f
                        cameraDistance = 12f * density.density
                    }
                    .size(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(cardColor)
                    .border(1.dp, Color.White.copy(alpha = 0.44f), RoundedCornerShape(7.dp))
                    .clickable {
                        onCardFlip(index)
                    },
            )
        }
    }
}

@Composable
private fun RatchetRingFidgetToy(
    step: Int,
    onStep: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), CircleShape)
            .pointerInput(step) {
                var drag = Offset.Zero
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        drag += dragAmount
                    },
                    onDragEnd = {
                        if (drag.vectorLength() > 7f) {
                            onStep(if (drag.x + drag.y >= 0f) 1 else -1)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            repeat(RATCHET_STEP_COUNT) { index ->
                val angle = (index * 360f / RATCHET_STEP_COUNT - 90f) * PI.toFloat() / 180f
                val inner = center + Offset(cos(angle), sin(angle)) * 38.dp.toPx()
                val outer = center + Offset(cos(angle), sin(angle)) * 48.dp.toPx()
                drawLine(
                    color = if (index == step) Color(0xFFFFC857) else Color(0xFF56F1C8).copy(alpha = 0.38f),
                    start = inner,
                    end = outer,
                    strokeWidth = if (index == step) 3.dp.toPx() else 1.dp.toPx(),
                )
            }
            val pointerAngle = (step * 360f / RATCHET_STEP_COUNT - 90f) * PI.toFloat() / 180f
            drawLine(
                color = Color(0xFFFFC857),
                start = center,
                end = center + Offset(cos(pointerAngle), sin(pointerAngle)) * 32.dp.toPx(),
                strokeWidth = 3.dp.toPx(),
            )
            drawCircle(Color(0xFF56F1C8), 12.dp.toPx(), center)
        }
    }
}

@Composable
private fun LiquidMazeFidgetToy(
    blobPosition: Offset,
    blobTrail: List<Offset>,
    puzzle: FidgetMazePuzzle,
    onMove: (Offset) -> Unit,
    onRelease: () -> Unit,
    onRefresh: () -> Unit,
) {
    val density = LocalDensity.current
    val boardShape = RoundedCornerShape(16.dp)
    var waterPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                waterPhase = frameNanos / 1_000_000_000f
            }
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .size(118.dp)
            .background(Color.White.copy(alpha = 0.06f), boardShape)
            .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), boardShape),
        contentAlignment = Alignment.Center,
    ) {
        val boardSide = minOf(maxWidth, maxHeight)
        val touchScale = (boardSide.value / 118f).coerceAtLeast(0.1f)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(boardSide)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(puzzle) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onMove(
                                with(density) {
                                    Offset(
                                        dragAmount.x.toDp().value / touchScale,
                                        dragAmount.y.toDp().value / touchScale,
                                    )
                                },
                            )
                        },
                        onDragEnd = onRelease,
                        onDragCancel = onRelease,
                    )
                    },
            ) {
            val coordinateScale = size.minDimension / 118.dp.toPx()
            fun scaledDp(value: Float): Float = value.dp.toPx() * coordinateScale
            fun scaledOffset(x: Float, y: Float): Offset = Offset(scaledDp(x), scaledDp(y))
            val center = Offset(size.width / 2f, size.height / 2f)
            val boardPadding = size.minDimension * (9f / 118f)
            val cellSize = (size.minDimension - boardPadding * 2f) / FIDGET_MAZE_COLUMNS
            val wallColor = Color(0xFF56F1C8).copy(alpha = 0.42f)
            val resetLeft = boardPadding + FIDGET_MAZE_RESET_CELL % FIDGET_MAZE_COLUMNS * cellSize
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.34f),
                topLeft = Offset(resetLeft, boardPadding),
                size = Size(cellSize, cellSize),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            puzzle.openings.forEachIndexed { cellIndex, openMask ->
                val column = cellIndex % FIDGET_MAZE_COLUMNS
                val row = cellIndex / FIDGET_MAZE_COLUMNS
                val left = boardPadding + column * cellSize
                val top = boardPadding + row * cellSize
                val right = left + cellSize
                val bottom = top + cellSize
                val wallWidth = 2.dp.toPx()

                if (openMask and MAZE_OPEN_UP == 0) {
                    drawLine(wallColor, Offset(left, top), Offset(right, top), wallWidth)
                }
                if (openMask and MAZE_OPEN_RIGHT == 0) {
                    drawLine(wallColor, Offset(right, top), Offset(right, bottom), wallWidth)
                }
                if (openMask and MAZE_OPEN_DOWN == 0) {
                    drawLine(wallColor, Offset(left, bottom), Offset(right, bottom), wallWidth)
                }
                if (openMask and MAZE_OPEN_LEFT == 0) {
                    drawLine(wallColor, Offset(left, top), Offset(left, bottom), wallWidth)
                }
            }
            // Map the physics board directly into the measured grid. On small watches this
            // keeps the visual walls, the droplet, and the touch travel area in one space.
            fun boardPoint(point: Offset): Offset = Offset(
                x = boardPadding + ((point.x + LIQUID_MAZE_BOARD_LIMIT_DP) /
                    (LIQUID_MAZE_BOARD_LIMIT_DP * 2f)) * (cellSize * FIDGET_MAZE_COLUMNS),
                y = boardPadding + ((point.y + LIQUID_MAZE_BOARD_LIMIT_DP) /
                    (LIQUID_MAZE_BOARD_LIMIT_DP * 2f)) * (cellSize * FIDGET_MAZE_ROWS),
            )

            val blobCenter = boardPoint(blobPosition)
            blobTrail.forEachIndexed { index, point ->
                val age = (index + 1f) / (blobTrail.size + 1f)
                drawCircle(
                    color = Color(0xFF56F1C8).copy(alpha = 0.18f * age),
                    radius = scaledDp(6f + age * 7f),
                    center = boardPoint(point),
                )
            }
            val wobble = sin(waterPhase * 4.2f) * 2.2f
            drawCircle(
                color = Color(0xFF56F1C8).copy(alpha = 0.22f),
                radius = scaledDp(LIQUID_MAZE_BLOB_RADIUS_DP + 7f + sin(waterPhase * 2.4f) * 2f),
                center = blobCenter,
                style = Stroke(width = scaledDp(1.5f)),
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.78f),
                        Color(0xFF56F1C8).copy(alpha = 0.9f),
                        Color(0xFF147D9B).copy(alpha = 0.78f),
                    ),
                    center = blobCenter + scaledOffset(-3f, -4f),
                    radius = scaledDp(15f),
                ),
                topLeft = blobCenter + scaledOffset(-LIQUID_MAZE_BLOB_RADIUS_DP + wobble, -8f),
                size = Size(scaledDp(LIQUID_MAZE_BLOB_RADIUS_DP * 2f), scaledDp(16f)),
            )
            drawCircle(
                Color.White.copy(alpha = 0.72f),
                scaledDp(3f),
                blobCenter + scaledOffset(-4f, -5f),
            )
        }
            FidgetMazeResetButton(boardSide = boardSide, onClick = onRefresh)
            }
        }
    }
}

@Composable
private fun GearJamFidgetToy(
    rotation: Float,
    onTurn: (Float) -> Unit,
    onResize: () -> Unit,
) {
    var targetSizes by remember { mutableStateOf(randomGearJamSizes()) }
    val primarySize by animateFloatAsState(
        targetValue = targetSizes.primary,
        animationSpec = tween(durationMillis = 260),
        label = "gearPrimarySize",
    )
    val secondarySize by animateFloatAsState(
        targetValue = targetSizes.secondary,
        animationSpec = tween(durationMillis = 260),
        label = "gearSecondarySize",
    )
    val tertiarySize by animateFloatAsState(
        targetValue = targetSizes.tertiary,
        animationSpec = tween(durationMillis = 260),
        label = "gearTertiarySize",
    )
    val animatedSizes = GearJamSizes(primarySize, secondarySize, tertiarySize)
    val layout = gearJamLayoutFor(animatedSizes)

    fun resizeGears() {
        targetSizes = randomGearJamSizes(previous = targetSizes)
        onResize()
    }

    Box(
        modifier = Modifier
            .size(118.dp)
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .pointerInput(targetSizes) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onTurn((dragAmount.x + dragAmount.y) * 0.8f)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        GearShape(
            offset = layout.primaryOffset,
            size = primarySize.dp,
            rotation = rotation,
            color = Color(0xFFFFC857),
        )
        GearShape(
            offset = layout.secondaryOffset,
            size = secondarySize.dp,
            rotation = -rotation * primarySize / secondarySize,
            color = Color(0xFF56F1C8),
        )
        GearShape(
            offset = layout.tertiaryOffset,
            size = tertiarySize.dp,
            rotation = rotation * primarySize / tertiarySize,
            color = Color(0xFFEF476F),
        )
        FidgetCornerResetButton(
            accentColor = Color(0xFFFFC857),
            accentColorArgb = 0xFFFFC857.toInt(),
            onClick = ::resizeGears,
        )
    }
}

internal const val GEAR_JAM_MESH_OVERLAP_DP = 2f

internal data class GearJamSizes(
    val primary: Float,
    val secondary: Float,
    val tertiary: Float,
)

internal data class GearJamLayout(
    val primaryOffset: Offset,
    val secondaryOffset: Offset,
    val tertiaryOffset: Offset,
)

internal fun randomGearJamSizes(
    random: Random = Random.Default,
    previous: GearJamSizes? = null,
): GearJamSizes {
    repeat(6) {
        val candidate = GearJamSizes(
            primary = random.nextInt(34, 49).toFloat(),
            secondary = random.nextInt(28, 45).toFloat(),
            tertiary = random.nextInt(24, 41).toFloat(),
        )
        if (candidate != previous) return candidate
    }

    return GearJamSizes(
        primary = if (previous?.primary == 48f) 34f else 48f,
        secondary = if (previous?.secondary == 44f) 28f else 44f,
        tertiary = if (previous?.tertiary == 40f) 24f else 40f,
    )
}

internal fun gearJamLayoutFor(sizes: GearJamSizes): GearJamLayout {
    val primaryRadius = sizes.primary / 2f
    val secondaryRadius = sizes.secondary / 2f
    val tertiaryRadius = sizes.tertiary / 2f
    val primaryToSecondary = primaryRadius + secondaryRadius - GEAR_JAM_MESH_OVERLAP_DP
    val primaryToTertiary = primaryRadius + tertiaryRadius - GEAR_JAM_MESH_OVERLAP_DP
    val secondaryToTertiary = secondaryRadius + tertiaryRadius - GEAR_JAM_MESH_OVERLAP_DP

    val tertiaryX = (
        primaryToTertiary * primaryToTertiary +
            primaryToSecondary * primaryToSecondary -
            secondaryToTertiary * secondaryToTertiary
        ) / (2f * primaryToSecondary)
    val tertiaryY = sqrt(
        (primaryToTertiary * primaryToTertiary - tertiaryX * tertiaryX)
            .coerceAtLeast(0f),
    )
    val rawPrimary = Offset.Zero
    val rawSecondary = Offset(primaryToSecondary, 0f)
    val rawTertiary = Offset(tertiaryX, tertiaryY)

    val minX = minOf(
        rawPrimary.x - primaryRadius,
        rawSecondary.x - secondaryRadius,
        rawTertiary.x - tertiaryRadius,
    )
    val maxX = maxOf(
        rawPrimary.x + primaryRadius,
        rawSecondary.x + secondaryRadius,
        rawTertiary.x + tertiaryRadius,
    )
    val minY = minOf(
        rawPrimary.y - primaryRadius,
        rawSecondary.y - secondaryRadius,
        rawTertiary.y - tertiaryRadius,
    )
    val maxY = maxOf(
        rawPrimary.y + primaryRadius,
        rawSecondary.y + secondaryRadius,
        rawTertiary.y + tertiaryRadius,
    )
    val clusterCenter = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)

    return GearJamLayout(
        primaryOffset = rawPrimary - clusterCenter,
        secondaryOffset = rawSecondary - clusterCenter,
        tertiaryOffset = rawTertiary - clusterCenter,
    )
}

@Composable
private fun GearShape(
    offset: Offset,
    size: Dp,
    rotation: Float,
    color: Color,
) {
    Box(
        modifier = Modifier
            .offset(x = offset.x.dp, y = offset.y.dp)
            .size(size)
            .rotate(rotation),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outerRadius = this.size.minDimension * 0.48f
            val rootRadius = outerRadius * 0.76f
            val toothPath = Path()
            repeat(32) { index ->
                val angle = -PI.toFloat() / 2f + index * PI.toFloat() / 16f
                val radius = if (index % 4 <= 1) outerRadius else rootRadius
                val point = center + Offset(cos(angle), sin(angle)) * radius
                if (index == 0) {
                    toothPath.moveTo(point.x, point.y)
                } else {
                    toothPath.lineTo(point.x, point.y)
                }
            }
            toothPath.close()
            drawPath(
                path = toothPath,
                color = color.copy(alpha = 0.92f),
            )
            drawPath(
                path = toothPath,
                color = Color.White.copy(alpha = 0.34f),
                style = Stroke(width = 1.dp.toPx()),
            )
            repeat(8) { index ->
                val angle = index * PI.toFloat() / 4f
                drawLine(
                    color = Color.Black.copy(alpha = 0.24f),
                    start = center + Offset(cos(angle), sin(angle)) * this.size.minDimension * 0.15f,
                    end = center + Offset(cos(angle), sin(angle)) * rootRadius * 0.88f,
                    strokeWidth = (this.size.minDimension * 0.075f).coerceAtLeast(1.dp.toPx()),
                )
            }
            drawCircle(color.copy(alpha = 0.98f), this.size.minDimension * 0.25f, center)
            drawCircle(Color.Black.copy(alpha = 0.5f), this.size.minDimension * 0.12f, center)
            drawCircle(
                Color.White.copy(alpha = 0.32f),
                this.size.minDimension * 0.12f,
                center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun WorryStoneFidgetToy(
    rub: Float,
    touchPoint: Offset?,
    accentColor: Color,
    onRub: (Offset, Offset) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, accentColor.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position ->
                        onRub(position, Offset.Zero)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onRub(change.position, dragAmount)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val contactCenter = (touchPoint ?: center).let { point ->
                Offset(
                    x = point.x.coerceIn(16.dp.toPx(), size.width - 16.dp.toPx()),
                    y = point.y.coerceIn(16.dp.toPx(), size.height - 16.dp.toPx()),
                )
            }
            drawOval(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.35f + rub * 0.24f),
                        accentColor.copy(alpha = 0.78f),
                        Color(0xFF101418),
                    ),
                    center = center + Offset(-14.dp.toPx(), -18.dp.toPx()),
                    radius = 70.dp.toPx(),
                ),
                topLeft = center + Offset(-43.dp.toPx(), -34.dp.toPx()),
                size = Size(86.dp.toPx(), 68.dp.toPx()),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.10f + rub * 0.30f),
                radius = 12.dp.toPx() + rub * 10.dp.toPx(),
                center = contactCenter,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

@Composable
private fun KeyClicksFidgetToy(
    keyMask: Int,
    wearEdition: Boolean,
    onKeyPress: (Int) -> Unit,
) {
    val visibleRowCount = if (wearEdition) 2 else 3
    val keyWidth = if (wearEdition) 34.dp else 31.dp
    val keyHeight = if (wearEdition) 28.dp else 25.dp
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(118.dp)
            .height(if (wearEdition) 62.dp else 104.dp),
    ) {
        repeat(visibleRowCount) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { column ->
                    val index = row * 3 + column
                    val down = keyMask and (1 shl index) != 0
                    Box(
                        modifier = Modifier
                            .size(width = keyWidth, height = keyHeight)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (down) Color(0xFF56F1C8) else Color(0xFF20272B))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .clickable { onKeyPress(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = listOf("C", "K", "T", "M", "Z", "B", "P", "D", "R")[index],
                            color = if (down) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SymbolDockFidgetToy(
    padStates: List<Int>,
    accentColor: Color,
    ringColor: Color,
    onPadPress: (Int) -> Unit,
) {
    val padColors = listOf(
        Color(0xFFFFC857),
        Color(0xFFEF476F),
        Color(0xFF56F1C8),
        Color(0xFF8D6BFF),
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(154.dp)
            .height(98.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .border(1.dp, accentColor.copy(alpha = 0.62f), RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 7.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(FIDGET_DOCK_PAD_COUNT) { padIndex ->
                val symbolIndex = padStates.getOrElse(padIndex) { padIndex }
                val symbol = fidgetDockSymbol(symbolIndex)
                val padColor = padColors[padIndex]
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(32.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    padColor.copy(alpha = 0.92f),
                                    padColor.copy(alpha = 0.48f),
                                    Color.Black.copy(alpha = 0.44f),
                                ),
                            ),
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.48f), RoundedCornerShape(8.dp))
                        .clickable { onPadPress(padIndex) },
                ) {
                    Text(
                        text = symbol,
                        color = if (padIndex == 3) Color.White else Color.Black,
                        fontSize = if (symbolIndex == 0 || symbolIndex == 1) 28.sp else 22.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            FIDGET_DOCK_SYMBOLS.forEachIndexed { index, symbol ->
                Text(
                    text = symbol,
                    color = if (index in padStates) {
                        ringColor
                    } else {
                        Color.White.copy(alpha = 0.46f)
                    },
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(9.dp),
                )
            }
        }
    }
}

@Composable
private fun BeatMachineFidgetToy(
    activePad: Int,
    text: FidgetText,
    accentColor: Color,
    accentColorArgb: Int,
    onPadPress: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (activePad >= 0) 0.3f else 0.12f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Black.copy(alpha = 0.28f),
                    ),
                ),
            )
            .border(1.dp, accentColor.copy(alpha = 0.72f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (activePad >= 0) {
                drawCircle(
                    color = Color(0xFFFFC857).copy(alpha = 0.24f),
                    radius = size.minDimension * 0.48f,
                    center = center,
                    style = Stroke(width = 7.dp.toPx()),
                )
            }
            repeat(4) { index ->
                val y = 18.dp.toPx() + index * 27.dp.toPx()
                drawLine(
                    color = accentColor.copy(alpha = 0.11f),
                    start = Offset(16.dp.toPx(), y),
                    end = Offset(size.width - 16.dp.toPx(), y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(2) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    repeat(3) { column ->
                        val index = row * 3 + column
                        val active = activePad == index
                        val padColor = listOf(
                            Color(0xFFFFC857),
                            Color(0xFFEF476F),
                            Color(0xFF56F1C8),
                            Color(0xFF8D6BFF),
                            Color(0xFFFF7A2F),
                            accentColor,
                        )[index]
                        Box(
                            modifier = Modifier
                                .size(width = 31.dp, height = 33.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (active) 0.55f else 0.2f),
                                            padColor.copy(alpha = if (active) 0.95f else 0.64f),
                                            Color.Black.copy(alpha = if (active) 0.08f else 0.32f),
                                        ),
                                    ),
                                )
                                .border(
                                    width = if (active) 2.dp else 1.dp,
                                    color = if (active) Color.White.copy(alpha = 0.8f) else padColor.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { onPadPress(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = text.beatPadLabels[index],
                                color = if (active) readableTextColorFor(accentColorArgb) else Color.White,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = text.flash,
            color = Color(0xFFFFC857).copy(alpha = if (activePad >= 0) 0.92f else 0.42f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 3.dp),
        )
    }
}

@Composable
private fun ZenTraceFidgetToy(
    tracePoints: List<Offset>,
    accentColor: Color,
    onTrace: (Offset) -> Unit,
    onTraceStart: () -> Unit,
    onClear: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, accentColor.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        onTraceStart()
                        onTrace(it)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onTrace(change.position)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            repeat(5) { index ->
                val y = 22.dp.toPx() + index * 17.dp.toPx()
                drawLine(
                    color = Color(0xFFFFC857).copy(alpha = 0.16f),
                    start = Offset(18.dp.toPx(), y),
                    end = Offset(size.width - 18.dp.toPx(), y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            tracePoints.zipWithNext().forEachIndexed { index, (start, end) ->
                drawLine(
                    color = accentColor.copy(alpha = (0.2f + index / tracePoints.size.toFloat()).coerceIn(0.2f, 0.92f)),
                    start = start,
                    end = end,
                    strokeWidth = 3.dp.toPx(),
                )
            }
            tracePoints.lastOrNull()?.let { point ->
                drawCircle(Color.White.copy(alpha = 0.6f), 4.dp.toPx(), point)
            }
        }
        FidgetThemeButton(
            text = "C",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp)
                .size(width = 22.dp, height = 20.dp),
            fontSize = 8.sp,
            selected = true,
            prominent = true,
            accentColor = accentColor,
            accentColorArgb = NEON_GREEN_COLOR,
            onClick = onClear,
        )
    }
}

@Composable
private fun SlingshotFidgetToy(
    ballPosition: Offset,
    pullLimit: Float,
    onPullStart: () -> Unit,
    onPullMove: (Offset) -> Unit,
    onRelease: (Offset) -> Unit,
) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val ballCenter = center + Offset(ballPosition.x.dp.toPx(), ballPosition.y.dp.toPx())
            val pullRatio = (ballPosition.vectorLength() / pullLimit).coerceIn(0f, 1f)

            drawCircle(
                color = Color(0xFF56F1C8).copy(alpha = 0.18f),
                radius = 45.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawLine(
                color = Color(0xFFFFC857).copy(alpha = 0.58f + pullRatio * 0.32f),
                start = center,
                end = ballCenter,
                strokeWidth = (2.dp + (pullRatio * 2f).dp).toPx(),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.18f),
                radius = 9.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        Box(
            modifier = Modifier
                .offset(x = ballPosition.x.dp, y = ballPosition.y.dp)
                .size(27.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFC857),
                            Color(0xFFEF476F),
                            Color.Black.copy(alpha = 0.32f),
                        ),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.58f), CircleShape)
                .pointerInput(pullLimit) {
                    var dragOffset = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            dragOffset = ballPosition
                            onPullStart()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset = with(density) {
                                Offset(
                                    x = dragOffset.x + dragAmount.x.toDp().value,
                                    y = dragOffset.y + dragAmount.y.toDp().value,
                                )
                            }.limitedToLength(pullLimit)
                            onPullMove(dragOffset)
                        },
                        onDragEnd = {
                            onRelease(dragOffset)
                            dragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            onRelease(dragOffset)
                            dragOffset = Offset.Zero
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.46f)),
            )
        }
    }
}

@Composable
private fun MazeFidgetToy(
    puzzle: FidgetMazePuzzle,
    playerCell: Int,
    onMove: (MazeDirection) -> Unit,
    onRefresh: () -> Unit,
) {
    val boardShape = RoundedCornerShape(16.dp)
    BoxWithConstraints(
        modifier = Modifier
            .size(118.dp)
            .background(Color.White.copy(alpha = 0.06f), boardShape)
            .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), boardShape)
            .pointerInput(puzzle) {
                var dragDelta = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        dragDelta = Offset.Zero
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDelta += dragAmount
                    },
                    onDragEnd = {
                        dragDelta.toMazeDirection()?.let(onMove)
                        dragDelta = Offset.Zero
                    },
                    onDragCancel = {
                        dragDelta = Offset.Zero
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val boardSide = minOf(maxWidth, maxHeight)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(boardSide)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
            val boardScale = size.minDimension / 118.dp.toPx()
            val boardPadding = size.minDimension * (9f / 118f)
            val cellSize = (size.minDimension - boardPadding * 2f) / FIDGET_MAZE_COLUMNS
            val markerRadius = cellSize
            val startColumn = puzzle.startCell % FIDGET_MAZE_COLUMNS
            val startRow = puzzle.startCell / FIDGET_MAZE_COLUMNS
            val endColumn = puzzle.endCell % FIDGET_MAZE_COLUMNS
            val endRow = puzzle.endCell / FIDGET_MAZE_COLUMNS
            val playerColumn = playerCell % FIDGET_MAZE_COLUMNS
            val playerRow = playerCell / FIDGET_MAZE_COLUMNS

            fun cellCenter(column: Int, row: Int): Offset {
                return Offset(
                    x = boardPadding + cellSize * (column + 0.5f),
                    y = boardPadding + cellSize * (row + 0.5f),
                )
            }

            val resetColumn = FIDGET_MAZE_RESET_CELL % FIDGET_MAZE_COLUMNS
            val resetLeft = boardPadding + resetColumn * cellSize
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.34f),
                topLeft = Offset(resetLeft, boardPadding),
                size = Size(cellSize, cellSize),
                cornerRadius = CornerRadius(4.dp.toPx() * boardScale),
            )

            drawCircle(
                color = Color(0xFF56F1C8).copy(alpha = 0.58f),
                radius = markerRadius * 0.24f,
                center = cellCenter(startColumn, startRow),
            )
            drawCircle(
                color = Color(0xFFFFC857).copy(alpha = 0.88f),
                radius = markerRadius * 0.25f,
                center = cellCenter(endColumn, endRow),
                style = Stroke(width = 2.dp.toPx() * boardScale),
            )

            puzzle.openings.forEachIndexed { cellIndex, openMask ->
                val column = cellIndex % FIDGET_MAZE_COLUMNS
                val row = cellIndex / FIDGET_MAZE_COLUMNS
                val left = boardPadding + column * cellSize
                val top = boardPadding + row * cellSize
                val right = left + cellSize
                val bottom = top + cellSize
                val wallColor = Color(0xFF56F1C8).copy(alpha = 0.76f)
                val wallWidth = 1.4.dp.toPx() * boardScale

                if (openMask and MAZE_OPEN_UP == 0) {
                    drawLine(wallColor, Offset(left, top), Offset(right, top), wallWidth)
                }
                if (openMask and MAZE_OPEN_RIGHT == 0) {
                    drawLine(wallColor, Offset(right, top), Offset(right, bottom), wallWidth)
                }
                if (openMask and MAZE_OPEN_DOWN == 0) {
                    drawLine(wallColor, Offset(left, bottom), Offset(right, bottom), wallWidth)
                }
                if (openMask and MAZE_OPEN_LEFT == 0) {
                    drawLine(wallColor, Offset(left, top), Offset(left, bottom), wallWidth)
                }
            }

            drawCircle(
                color = Color(0xFFEF476F),
                radius = markerRadius * 0.28f,
                center = cellCenter(playerColumn, playerRow),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.48f),
                radius = markerRadius * 0.1f,
                center = cellCenter(playerColumn, playerRow),
            )
        }

            FidgetMazeResetButton(boardSide = boardSide, onClick = onRefresh)
            }
        }
    }
}

@Composable
private fun BoxScope.FidgetMazeResetButton(
    boardSide: Dp,
    onClick: () -> Unit,
) {
    val buttonWidth = 22.dp
    val buttonHeight = 20.dp
    val buttonOffset = boardSide - buttonWidth - 4.dp
    val buttonTop = 4.dp

    FidgetThemeButton(
        text = "R",
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = buttonOffset, y = buttonTop)
            .size(width = buttonWidth, height = buttonHeight),
        fontSize = 8.sp,
        selected = true,
        prominent = true,
        accentColor = Color(0xFFFFC857),
        accentColorArgb = 0xFFFFC857.toInt(),
        onClick = onClick,
    )
}

internal data class CenterDropMaze(
    val gateAnglesDegrees: List<Float>,
    val startPosition: Offset,
)

internal fun generateCenterDropMaze(random: Random = Random.Default): CenterDropMaze {
    val startAngle = random.nextInt(0, 360).toFloat()
    return CenterDropMaze(
        gateAnglesDegrees = CENTER_DROP_RING_RADII_DP.mapIndexed { index, _ ->
            (startAngle + 68f + index * 97f + random.nextInt(-28, 29)).normalizedDegrees()
        },
        startPosition = offsetAtDegrees(startAngle, CENTER_DROP_START_RADIUS_DP),
    )
}

internal fun moveCenterDropBall(
    position: Offset,
    delta: Offset,
    maze: CenterDropMaze,
): Offset {
    val movement = delta.limitedToLength(CENTER_DROP_MAX_STEP_DP)
    val steps = (movement.vectorLength() / 1.4f).toInt().coerceIn(0, 7) + 1
    val subStep = movement * (1f / steps)
    var current = position.limitedToLength(CENTER_DROP_BOARD_LIMIT_DP)

    repeat(steps) {
        var candidate = (current + subStep).limitedToLength(CENTER_DROP_BOARD_LIMIT_DP)
        val currentRadius = current.vectorLength()
        val candidateRadius = candidate.vectorLength()
        CENTER_DROP_RING_RADII_DP.forEachIndexed { index, ringRadius ->
            val crossed = (currentRadius - ringRadius) * (candidateRadius - ringRadius) <= 0f &&
                abs(candidateRadius - currentRadius) > 0.001f
            if (crossed && !centerDropGateIsOpen(candidate, maze.gateAnglesDegrees[index])) {
                val safeRadius = if (currentRadius >= ringRadius) {
                    ringRadius + CENTER_DROP_BALL_RADIUS_DP
                } else {
                    (ringRadius - CENTER_DROP_BALL_RADIUS_DP).coerceAtLeast(0f)
                }
                candidate = offsetAtDegrees(candidate.angleDegrees(), safeRadius)
            }
        }
        current = candidate
    }
    return current
}

private fun centerDropGateIsOpen(position: Offset, gateAngleDegrees: Float): Boolean =
    angularDistanceDegrees(position.angleDegrees(), gateAngleDegrees) <= CENTER_DROP_GATE_HALF_WIDTH_DEGREES

private fun Float.normalizedDegrees(): Float = ((this % 360f) + 360f) % 360f

private fun Offset.angleDegrees(): Float =
    (atan2(y, x) * 180f / PI.toFloat()).normalizedDegrees()

private fun angularDistanceDegrees(first: Float, second: Float): Float {
    val difference = abs(first.normalizedDegrees() - second.normalizedDegrees())
    return minOf(difference, 360f - difference)
}

private fun offsetAtDegrees(angleDegrees: Float, radius: Float): Offset {
    val angleRadians = angleDegrees * PI.toFloat() / 180f
    return Offset(cos(angleRadians) * radius, sin(angleRadians) * radius)
}

@Composable
private fun CenterDropMazeFidgetToy(
    maze: CenterDropMaze,
    ballPosition: Offset,
    solved: Boolean,
    onMove: (Offset) -> Unit,
    onRelease: () -> Unit,
    onRefresh: () -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .width(118.dp)
            .height(148.dp),
    ) {
        Box(
            modifier = Modifier
                .size(118.dp)
                .align(Alignment.Center)
                .background(Color.White.copy(alpha = 0.06f), CircleShape)
                .border(1.dp, Color(0xFFFFC857).copy(alpha = 0.72f), CircleShape)
                .pointerInput(maze) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onMove(with(density) { Offset(dragAmount.x.toDp().value, dragAmount.y.toDp().value) })
                        },
                        onDragEnd = onRelease,
                        onDragCancel = onRelease,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            CENTER_DROP_RING_RADII_DP.forEachIndexed { index, radiusDp ->
                val radius = radiusDp.dp.toPx()
                val bounds = Size(radius * 2f, radius * 2f)
                drawArc(
                    color = Color(0xFF56F1C8).copy(alpha = 0.78f),
                    startAngle = maze.gateAnglesDegrees[index] + CENTER_DROP_GATE_HALF_WIDTH_DEGREES,
                    sweepAngle = 360f - CENTER_DROP_GATE_HALF_WIDTH_DEGREES * 2f,
                    useCenter = false,
                    topLeft = center - Offset(radius, radius),
                    size = bounds,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            drawCircle(
                color = Color.Black.copy(alpha = 0.86f),
                radius = CENTER_DROP_HOLE_RADIUS_DP.dp.toPx(),
                center = center,
            )
            drawCircle(
                color = Color(0xFFFFC857).copy(alpha = 0.72f),
                radius = CENTER_DROP_HOLE_RADIUS_DP.dp.toPx(),
                center = center,
                style = Stroke(width = 1.4.dp.toPx()),
            )
            val start = center + Offset(
                maze.startPosition.x.dp.toPx(),
                maze.startPosition.y.dp.toPx(),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.24f),
                radius = 5.dp.toPx(),
                center = start,
                style = Stroke(width = 1.dp.toPx()),
            )
            val ballCenter = center + Offset(
                ballPosition.x.dp.toPx(),
                ballPosition.y.dp.toPx(),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFFB9C6D2), Color(0xFF46515B)),
                    center = ballCenter + Offset(-2.dp.toPx(), -2.dp.toPx()),
                    radius = 7.dp.toPx(),
                ),
                radius = (if (solved) 2.5f else CENTER_DROP_BALL_RADIUS_DP).dp.toPx(),
                center = if (solved) center else ballCenter,
                alpha = if (solved) 0.46f else 1f,
            )
        }
        }
        FidgetThemeButton(
            text = "R",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 1.dp)
                .size(width = 22.dp, height = 20.dp),
            fontSize = 7.sp,
            selected = true,
            prominent = true,
            accentColor = Color(0xFFFFC857),
            accentColorArgb = 0xFFFFC857.toInt(),
            onClick = onRefresh,
        )
    }
}

internal data class BallSortMaze(
    val pocketPositions: List<Offset>,
    val startPositions: List<Offset>,
    val obstaclePositions: List<Offset>,
)

internal data class BallSortMoveResult(
    val positions: List<Offset>,
    val locked: List<Boolean>,
)

internal fun generateBallSortMaze(random: Random = Random.Default): BallSortMaze {
    val pocketCandidates = listOf(
        Offset(-34f, -32f),
        Offset(34f, -32f),
        Offset(-34f, 32f),
        Offset(34f, 32f),
    ).shuffled(random)
    val startCandidates = listOf(
        Offset(-24f, -8f),
        Offset(24f, -8f),
        Offset(-18f, 20f),
        Offset(18f, 20f),
        Offset(0f, -28f),
        Offset(0f, 28f),
        Offset(-28f, 0f),
        Offset(28f, 0f),
    ).shuffled(random)
    val resetTarget = offsetAtDegrees(
        angleDegrees = random.nextInt(0, 360).toFloat(),
        radius = BALL_SORT_RESET_ORBIT_RADIUS_DP,
    )
    val safeStarts = startCandidates.filter { candidate ->
        (candidate - resetTarget).vectorLength() >= BALL_SORT_START_CLEARANCE_DP
    }
    return BallSortMaze(
        pocketPositions = pocketCandidates.take(BALL_SORT_BALL_COUNT),
        startPositions = safeStarts.take(BALL_SORT_BALL_COUNT),
        obstaclePositions = listOf(resetTarget),
    )
}

internal fun moveBallSortBalls(
    positions: List<Offset>,
    locked: List<Boolean>,
    maze: BallSortMaze,
    delta: Offset,
): BallSortMoveResult {
    val movement = delta.limitedToLength(BALL_SORT_MAX_STEP_DP)
    val nextLocked = locked.toMutableList()
    val nextPositions = positions.mapIndexed { index, position ->
        if (locked.getOrElse(index) { false }) {
            return@mapIndexed maze.pocketPositions.getOrElse(index) { position }
        }
        var candidate = Offset(
            x = (position.x + movement.x).coerceIn(-BALL_SORT_BOARD_LIMIT_DP, BALL_SORT_BOARD_LIMIT_DP),
            y = (position.y + movement.y).coerceIn(-BALL_SORT_BOARD_LIMIT_DP, BALL_SORT_BOARD_LIMIT_DP),
        )
        maze.obstaclePositions.forEach { obstacle ->
            if ((candidate - obstacle).vectorLength() < BALL_SORT_OBSTACLE_CLEARANCE_DP) {
                val xOnly = Offset(candidate.x, position.y)
                val yOnly = Offset(position.x, candidate.y)
                candidate = when {
                    (xOnly - obstacle).vectorLength() >= BALL_SORT_OBSTACLE_CLEARANCE_DP -> xOnly
                    (yOnly - obstacle).vectorLength() >= BALL_SORT_OBSTACLE_CLEARANCE_DP -> yOnly
                    else -> position
                }
            }
        }
        val pocket = maze.pocketPositions.getOrElse(index) { candidate }
        if ((candidate - pocket).vectorLength() <= BALL_SORT_POCKET_LOCK_DISTANCE_DP) {
            nextLocked[index] = true
            pocket
        } else {
            candidate
        }
    }
    return BallSortMoveResult(nextPositions, nextLocked)
}

@Composable
private fun BallSortMazeFidgetToy(
    maze: BallSortMaze,
    positions: List<Offset>,
    locked: List<Boolean>,
    onMove: (Offset) -> Unit,
    onRelease: () -> Unit,
    onRefresh: () -> Unit,
) {
    val density = LocalDensity.current
    val boardShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(boardShape)
            .background(Color.White.copy(alpha = 0.06f), boardShape)
            .border(1.dp, Color(0xFFEF476F).copy(alpha = 0.72f), boardShape)
            .pointerInput(maze) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onMove(with(density) { Offset(dragAmount.x.toDp().value, dragAmount.y.toDp().value) })
                    },
                    onDragEnd = onRelease,
                    onDragCancel = onRelease,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = Color(0xFFEF476F).copy(alpha = 0.18f),
                radius = BALL_SORT_RESET_ORBIT_RADIUS_DP.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            maze.pocketPositions.forEachIndexed { index, pocket ->
                val pocketCenter = center + Offset(pocket.x.dp.toPx(), pocket.y.dp.toPx())
                drawCircle(
                    color = BALL_SORT_COLORS[index].copy(alpha = 0.16f),
                    radius = 8.dp.toPx(),
                    center = pocketCenter,
                )
                drawCircle(
                    color = BALL_SORT_COLORS[index].copy(alpha = 0.88f),
                    radius = 8.dp.toPx(),
                    center = pocketCenter,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            positions.forEachIndexed { index, position ->
                val ballCenter = center + Offset(position.x.dp.toPx(), position.y.dp.toPx())
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, BALL_SORT_COLORS[index], Color.Black.copy(alpha = 0.5f)),
                        center = ballCenter + Offset(-2.dp.toPx(), -2.dp.toPx()),
                        radius = 8.dp.toPx(),
                    ),
                    radius = (if (locked.getOrElse(index) { false }) 5f else BALL_SORT_BALL_RADIUS_DP).dp.toPx(),
                    center = ballCenter,
                )
            }
        }
        maze.obstaclePositions.firstOrNull()?.let { resetTarget ->
            FidgetThemeButton(
                text = "R",
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = resetTarget.x.dp, y = resetTarget.y.dp)
                    .size(26.dp),
                fontSize = 9.sp,
                selected = true,
                prominent = true,
                accentColor = Color(0xFFEF476F),
                accentColorArgb = 0xFFEF476F.toInt(),
                onClick = onRefresh,
            )
        }
    }
}

private val CENTER_DROP_RING_RADII_DP = listOf(16f, 29f, 42f)
internal const val CENTER_DROP_HOLE_RADIUS_DP = 6f
private const val CENTER_DROP_BALL_RADIUS_DP = 5f
private const val CENTER_DROP_START_RADIUS_DP = 47f
private const val CENTER_DROP_BOARD_LIMIT_DP = 48f
private const val CENTER_DROP_MAX_STEP_DP = 6f
private const val CENTER_DROP_GATE_HALF_WIDTH_DEGREES = 17f
internal const val BALL_SORT_BALL_COUNT = 3
private const val BALL_SORT_BALL_RADIUS_DP = 6f
private const val BALL_SORT_BOARD_LIMIT_DP = 42f
private const val BALL_SORT_MAX_STEP_DP = 6f
private const val BALL_SORT_OBSTACLE_CLEARANCE_DP = 20f
internal const val BALL_SORT_RESET_ORBIT_RADIUS_DP = 18f
internal const val BALL_SORT_START_CLEARANCE_DP = 22f
private const val BALL_SORT_POCKET_LOCK_DISTANCE_DP = 8f
private val BALL_SORT_COLORS = listOf(
    Color(0xFFFFC857),
    Color(0xFFEF476F),
    Color(0xFF56F1C8),
)

@Composable
private fun BoxScope.FidgetCornerResetButton(
    accentColor: Color,
    accentColorArgb: Int,
    onClick: () -> Unit,
) {
    FidgetThemeButton(
        text = "R",
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 4.dp, end = 4.dp)
            .zIndex(3f)
            .size(width = 22.dp, height = 20.dp),
        fontSize = 8.sp,
        selected = true,
        prominent = true,
        accentColor = accentColor,
        accentColorArgb = accentColorArgb,
        onClick = onClick,
    )
}

@Composable
private fun WindowFidgetToy(
    leftOpen: Boolean,
    rightOpen: Boolean,
    text: FidgetText,
    accentColor: Color,
    onPaneToggle: (Int) -> Unit,
) {
    var nightMode by rememberSaveable { mutableStateOf(false) }
    val slideProgress by animateFloatAsState(
        targetValue = if (rightOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "windowSlide",
    )
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, accentColor.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    onPaneToggle(if (position.x < size.width / 2f) 0 else 1)
                }
            }
            .pointerInput(Unit) {
                var dragX = 0f
                detectDragGestures(
                    onDragStart = { dragX = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                    },
                    onDragEnd = {
                        if (abs(dragX) > 10f) onPaneToggle(if (dragX < 0f) 0 else 1)
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameLeft = 10.dp.toPx()
            val frameTop = 10.dp.toPx()
            val frameWidth = size.width - 20.dp.toPx()
            val frameHeight = 66.dp.toPx()
            val innerLeft = frameLeft + 5.dp.toPx()
            val innerTop = frameTop + 5.dp.toPx()
            val innerHeight = frameHeight - 10.dp.toPx()
            val paneWidth = (frameWidth - 15.dp.toPx()) / 2f
            val rightSlide = -paneWidth * 0.92f * slideProgress

            val skyColor = if (nightMode) Color(0xFF172B52) else Color(0xFF6BC7EA)
            val groundColor = if (nightMode) Color(0xFF193C36) else Color(0xFF69B95D)
            val lightColor = if (nightMode) Color(0xFFE8F0FF) else Color(0xFFFFD34E)
            drawRect(skyColor, Offset(innerLeft, innerTop), Size(frameWidth - 10.dp.toPx(), innerHeight))
            drawRect(groundColor, Offset(innerLeft, innerTop + innerHeight - 21.dp.toPx()), Size(frameWidth - 10.dp.toPx(), 21.dp.toPx()))
            val lightCenter = Offset(innerLeft + frameWidth - 28.dp.toPx(), innerTop + 16.dp.toPx())
            drawCircle(lightColor, 7.dp.toPx(), lightCenter)
            if (nightMode) {
                drawCircle(skyColor, 6.dp.toPx(), lightCenter + Offset(3.dp.toPx(), -2.dp.toPx()))
            }
            drawRect(Color(0xFF17242B), Offset(frameLeft, frameTop), Size(frameWidth, frameHeight), style = Stroke(5.dp.toPx()))
            drawRect(
                color = Color(0xFFB9EEFF).copy(alpha = 0.44f),
                topLeft = Offset(innerLeft + paneWidth + 5.dp.toPx() + rightSlide, innerTop),
                size = Size(paneWidth, innerHeight),
            )
            drawRect(
                color = Color(0xFFD8F7FF).copy(alpha = 0.62f),
                topLeft = Offset(innerLeft, innerTop),
                size = Size(paneWidth, innerHeight),
            )
            drawRect(Color(0xFF17242B), Offset(innerLeft + paneWidth + 1.dp.toPx(), innerTop), Size(4.dp.toPx(), innerHeight))
            drawRect(Color(0xFF17242B), Offset(innerLeft, innerTop), Size(frameWidth - 10.dp.toPx(), innerHeight), style = Stroke(2.dp.toPx()))
            drawLine(
                color = Color.White.copy(alpha = 0.52f),
                start = Offset(frameLeft + 3.dp.toPx(), frameTop + frameHeight + 5.dp.toPx()),
                end = Offset(size.width - frameLeft - 3.dp.toPx(), frameTop + frameHeight + 5.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
            )
        }
        FidgetThemeButton(
            text = if (nightMode) text.day else text.night,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp)
                .width(30.dp)
                .height(20.dp),
            fontSize = 6.sp,
            selected = nightMode,
            prominent = true,
            accentColor = accentColor,
            accentColorArgb = if (nightMode) 0xFFE8F0FF.toInt() else 0xFFFFD34E.toInt(),
            onClick = { nightMode = !nightMode },
        )
        Text(
            text = if (leftOpen || rightOpen) text.open else text.closed,
            color = accentColor.copy(alpha = 0.92f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
        )
    }
}

@Composable
private fun DoorFidgetToy(
    open: Boolean,
    text: FidgetText,
    accentColor: Color,
    onToggle: () -> Unit,
) {
    val doorProgress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "doorTrapezoid",
    )
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, accentColor.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            fun px(value: Float) = value.dp.toPx()
            fun interpolate(start: Float, end: Float, amount: Float): Float {
                return start + (end - start) * amount
            }

            val frameLeft = px(16f)
            val frameTop = px(7f)
            val frameWidth = px(78f)
            val frameHeight = px(78f)
            val openingLeft = px(22f)
            val openingTop = px(14f)
            val openingWidth = px(64f)
            val openingHeight = px(65f)

            drawRect(Color(0xFF1A2428), Offset(frameLeft, frameTop), Size(frameWidth, frameHeight))
            drawRect(Color(0xFF05090B), Offset(openingLeft, openingTop), Size(openingWidth, openingHeight))
            drawRect(
                color = accentColor.copy(alpha = 0.72f),
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameWidth, frameHeight),
                style = Stroke(width = px(3f)),
            )
            drawLine(
                color = Color(0xFFE0A16B).copy(alpha = 0.72f),
                start = Offset(px(17f), px(87f)),
                end = Offset(px(93f), px(87f)),
                strokeWidth = px(4f),
            )

            val overlapProgress = 0.76f
            val approachingHinge = doorProgress <= overlapProgress
            val phase = if (approachingHinge) {
                doorProgress / overlapProgress
            } else {
                (doorProgress - overlapProgress) / (1f - overlapProgress)
            }

            // CD is fixed at the hinge. AB is the free edge that passes edge-on
            // over CD, then settles just to its right in the open position.
            val pointC = Offset(px(22f), px(14f))
            val pointD = Offset(px(22f), px(79f))
            val freeEdgeX = if (approachingHinge) {
                interpolate(86f, 22f, phase)
            } else {
                interpolate(22f, 44f, phase)
            }
            val freeEdgeTop = if (approachingHinge) {
                interpolate(14f, 7f, phase)
            } else {
                interpolate(7f, 18f, phase)
            }
            val freeEdgeBottom = if (approachingHinge) {
                interpolate(79f, 88f, phase)
            } else {
                interpolate(88f, 77f, phase)
            }
            val freeEdgeLean = if (approachingHinge) 0f else phase * 2f
            val pointB = Offset(px(freeEdgeX), px(freeEdgeTop))
            val pointA = Offset(px(freeEdgeX + freeEdgeLean), px(freeEdgeBottom))
            val doorPath = Path().apply {
                moveTo(pointC.x, pointC.y)
                lineTo(pointB.x, pointB.y)
                lineTo(pointA.x, pointA.y)
                lineTo(pointD.x, pointD.y)
                close()
            }
            drawPath(
                path = doorPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFB06A43), Color(0xFF5A3122), Color(0xFF2B1712)),
                    start = pointC,
                    end = pointA,
                ),
            )
            drawPath(path = doorPath, color = Color(0xFFE0A16B).copy(alpha = 0.88f), style = Stroke(px(2f)))
            val knobX = interpolate(25f, freeEdgeX, 0.82f)
            val knobY = (freeEdgeTop + freeEdgeBottom) / 2f
            drawCircle(
                color = Color(0xFFFFC857),
                radius = px(3.5f),
                center = Offset(px(knobX), px(knobY)),
            )
        }
        Text(
            text = if (open) text.open else text.shut,
            color = accentColor.copy(alpha = 0.92f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun LightFidgetToy(
    on: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) Color(0xFFFFC857).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, accentColor.copy(alpha = 0.72f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, 38.dp.toPx())
            if (on) {
                repeat(8) { index ->
                    val angle = index * (PI / 4f)
                    val inner = center + Offset(cos(angle).toFloat() * 18.dp.toPx(), sin(angle).toFloat() * 18.dp.toPx())
                    val outer = center + Offset(cos(angle).toFloat() * 29.dp.toPx(), sin(angle).toFloat() * 29.dp.toPx())
                    drawLine(Color(0xFFFFC857).copy(alpha = 0.84f), inner, outer, 2.dp.toPx())
                }
            }
            drawCircle(
                color = if (on) Color(0xFFFFE6A3) else Color(0xFF56636A),
                radius = 13.dp.toPx(),
                center = center,
            )
            drawCircle(
                color = if (on) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.25f),
                radius = 5.dp.toPx(),
                center = center + Offset(-4.dp.toPx(), -4.dp.toPx()),
            )
            drawLine(
                color = Color(0xFF73858D),
                start = Offset(center.x - 12.dp.toPx(), center.y + 16.dp.toPx()),
                end = Offset(center.x + 12.dp.toPx(), center.y + 16.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
            )
        }
        FidgetToggleSwitch(
            switchedOn = on,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp),
            onClick = onToggle,
        )
    }
}

@Composable
private fun FanFidgetToy(
    on: Boolean,
    rotation: Float,
    accentColor: Color,
    accentColorArgb: Int,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, accentColor.copy(alpha = 0.72f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(42.dp.toPx(), 39.dp.toPx())
            drawCircle(
                color = Color(0xFF21343B),
                radius = 28.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            rotate(degrees = rotation % 360f, pivot = center) {
                repeat(4) { index ->
                    val angle = index * (PI / 2f) + PI / 4f
                    val end = center + Offset(cos(angle).toFloat() * 21.dp.toPx(), sin(angle).toFloat() * 21.dp.toPx())
                    drawLine(
                        color = if (on) accentColor.copy(alpha = 0.88f) else Color(0xFF54666D),
                        start = center,
                        end = end,
                        strokeWidth = 8.dp.toPx(),
                    )
                }
            }
            drawCircle(Color(0xFFFFC857), 7.dp.toPx(), center)
            drawCircle(Color.White.copy(alpha = 0.62f), 2.dp.toPx(), center + Offset(-2.dp.toPx(), -2.dp.toPx()))
            drawLine(
                color = Color(0xFF73858D),
                start = Offset(center.x, center.y + 28.dp.toPx()),
                end = Offset(center.x, 84.dp.toPx()),
                strokeWidth = 4.dp.toPx(),
            )
            val streamerColors = listOf(Color(0xFFEF476F), Color(0xFFFFC857), Color(0xFF56F1C8))
            repeat(3) { index ->
                val anchor = Offset(69.dp.toPx(), (25 + index * 14).dp.toPx())
                val sway = if (on) sin(rotation * 0.045f + index * 1.7f) * 6.dp.toPx() else 0f
                val lift = if (on) cos(rotation * 0.038f + index) * 3.dp.toPx() else 10.dp.toPx()
                val streamer = Path().apply {
                    moveTo(anchor.x, anchor.y)
                    quadraticTo(
                        87.dp.toPx(),
                        anchor.y - sway * 0.45f + lift * 0.35f,
                        if (on) 108.dp.toPx() else 91.dp.toPx(),
                        anchor.y + sway + lift,
                    )
                }
                drawPath(
                    path = streamer,
                    color = streamerColors[index].copy(alpha = if (on) 0.92f else 0.58f),
                    style = Stroke(width = 2.2.dp.toPx()),
                )
            }
        }
        FidgetThemeButton(
            text = if (on) "Breeze" else "Start",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 7.dp, bottom = 6.dp)
                .width(40.dp)
                .height(20.dp),
            fontSize = 7.sp,
            selected = on,
            prominent = true,
            accentColor = accentColor,
            accentColorArgb = accentColorArgb,
            onClick = onToggle,
        )
    }
}

@Composable
private fun SinkFidgetToy(
    hotOn: Boolean,
    coldOn: Boolean,
    text: FidgetText,
    accentColor: Color,
    onKnobToggle: (Int) -> Unit,
) {
    val on = hotOn || coldOn
    val waterColor = when {
        hotOn && !coldOn -> Color(0xFFFF8C75)
        coldOn && !hotOn -> Color(0xFF48CFF3)
        hotOn && coldOn -> Color(0xFFA56CFF)
        else -> Color(0xFF70D9E8)
    }
    val flowAmount by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = tween(durationMillis = if (on) 180 else 340),
        label = "sinkFlow",
    )
    var flowPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(on) {
        while (on) {
            withFrameNanos { frameNanos ->
                flowPhase = frameNanos / 1_000_000_000f
            }
        }
    }
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, accentColor.copy(alpha = 0.72f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val basinTop = 64.dp.toPx()
            drawLine(
                color = Color(0xFFB9D4DB),
                start = Offset(centerX, 26.dp.toPx()),
                end = Offset(centerX, basinTop - 9.dp.toPx()),
                strokeWidth = 5.dp.toPx(),
            )
            drawArc(
                color = Color(0xFFB9D4DB),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(centerX - 19.dp.toPx(), 20.dp.toPx()),
                size = Size(38.dp.toPx(), 25.dp.toPx()),
                style = Stroke(width = 5.dp.toPx()),
            )
            if (flowAmount > 0.01f) {
                val streamTop = basinTop - 8.dp.toPx()
                val streamBottom = basinTop + 13.dp.toPx()
                val streamWobble = sin(flowPhase * 8f) * 0.8.dp.toPx()
                drawLine(
                    color = waterColor.copy(alpha = 0.9f * flowAmount),
                    start = Offset(centerX, streamTop),
                    end = Offset(centerX + streamWobble, streamBottom),
                    strokeWidth = (1.5.dp + 3.dp * flowAmount).toPx(),
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.5f * flowAmount),
                    start = Offset(centerX - 1.dp.toPx(), streamTop),
                    end = Offset(centerX + streamWobble - 1.dp.toPx(), streamBottom),
                    strokeWidth = 1.dp.toPx(),
                )
                repeat(3) { index ->
                    val travel = ((flowPhase * 2.4f + index / 3f) % 1f)
                    drawCircle(
                        color = Color.White.copy(alpha = (0.62f - travel * 0.35f) * flowAmount),
                        radius = (1.5f - travel * 0.5f).dp.toPx(),
                        center = Offset(
                            centerX + streamWobble * travel,
                            streamTop + (streamBottom - streamTop) * travel,
                        ),
                    )
                }
            }
            drawRoundRect(
                color = Color(0xFF7898A0).copy(alpha = 0.68f),
                topLeft = Offset(20.dp.toPx(), basinTop),
                size = Size(size.width - 40.dp.toPx(), 25.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()),
            )
            drawLine(
                color = Color(0xFFB9D4DB).copy(alpha = 0.64f),
                start = Offset(27.dp.toPx(), basinTop + 8.dp.toPx()),
                end = Offset(size.width - 27.dp.toPx(), basinTop + 8.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )
            if (flowAmount > 0.01f) {
                val ripple = ((flowPhase * 1.8f) % 1f)
                drawOval(
                    color = waterColor.copy(alpha = 0.34f * flowAmount),
                    topLeft = Offset(centerX - 25.dp.toPx(), basinTop + 5.dp.toPx()),
                    size = Size(50.dp.toPx(), 13.dp.toPx()),
                )
                drawOval(
                    color = Color.White.copy(alpha = (0.5f - ripple * 0.42f) * flowAmount),
                    topLeft = Offset(
                        centerX - (5.dp + 13.dp * ripple).toPx(),
                        basinTop + (9.dp - 2.dp * ripple).toPx(),
                    ),
                    size = Size(
                        (10.dp + 26.dp * ripple).toPx(),
                        (5.dp + 4.dp * ripple).toPx(),
                    ),
                    style = Stroke(width = 1.dp.toPx()),
                )
                repeat(2) { index ->
                    val splash = ((flowPhase * 2.7f + index * 0.5f) % 1f)
                    drawCircle(
                        color = Color(0xFFB9EEFF).copy(alpha = (0.62f - splash * 0.5f) * flowAmount),
                        radius = 1.2.dp.toPx(),
                        center = Offset(
                            centerX + (if (index == 0) -1f else 1f) * (3.dp + 7.dp * splash).toPx(),
                            basinTop + (10.dp - 8.dp * splash).toPx(),
                        ),
                    )
                }
            }
            drawCircle(Color(0xFF25383D), 4.dp.toPx(), Offset(centerX, basinTop + 13.dp.toPx()))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(42.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
        ) {
            SinkKnob(
                label = text.hotInitial,
                color = Color(0xFFEF476F),
                active = hotOn,
                onClick = { onKnobToggle(0) },
            )
            SinkKnob(
                label = text.coldInitial,
                color = Color(0xFF48CFF3),
                active = coldOn,
                onClick = { onKnobToggle(1) },
            )
        }
    }
}

@Composable
private fun SinkKnob(
    label: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (active) color else color.copy(alpha = 0.28f))
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) Color.White.copy(alpha = 0.78f) else color.copy(alpha = 0.72f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun WhackColorButtonToy(
    buttonPositions: List<Int>,
    onButtonTap: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color(0xFF56F1C8).copy(alpha = 0.72f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingPx = 8.dp.toPx()
            val cellWidth = (size.width - paddingPx * 2f) / SWITCH_MAZE_COLUMNS
            val cellHeight = (size.height - paddingPx * 2f) / SWITCH_MAZE_ROWS

            repeat(SWITCH_MAZE_COLUMNS + 1) { lineIndex ->
                val x = paddingPx + lineIndex * cellWidth
                drawLine(
                    color = Color(0xFF56F1C8).copy(alpha = 0.18f),
                    start = Offset(x, paddingPx),
                    end = Offset(x, size.height - paddingPx),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            repeat(SWITCH_MAZE_ROWS + 1) { lineIndex ->
                val y = paddingPx + lineIndex * cellHeight
                drawLine(
                    color = Color(0xFFFFC857).copy(alpha = 0.14f),
                    start = Offset(paddingPx, y),
                    end = Offset(size.width - paddingPx, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        buttonPositions.forEachIndexed { index, position ->
            val column = position % SWITCH_MAZE_COLUMNS
            val row = position / SWITCH_MAZE_COLUMNS
            WhackColorButton(
                index = index,
                modifier = Modifier.offset {
                    val cellStepPx = 25.dp.toPx()
                    IntOffset(
                        x = ((column - 1.5f) * cellStepPx).roundToInt(),
                        y = ((row - 1.5f) * cellStepPx).roundToInt(),
                    )
                },
                onClick = { onButtonTap(index) },
            )
        }
    }
}

@Composable
private fun WhackColorButton(
    index: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = listOf(
        Color(0xFFFFC857),
        Color(0xFF56F1C8),
        Color(0xFFEF476F),
        Color(0xFF8D6BFF),
    )
    val color = colors[index % colors.size]

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.48f),
                        color,
                        color.copy(alpha = 0.55f),
                        Color.Black.copy(alpha = 0.3f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.48f)),
        )
    }
}

@Composable
private fun FidgetTrackNavButton(
    isNext: Boolean,
    modifier: Modifier = Modifier,
    arrowRotationDegrees: Float = 0f,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    accentColorArgb: Int = NEON_GREEN_COLOR,
    width: Dp = 38.dp,
    height: Dp = 64.dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val chevronColor = readableTextColorFor(accentColorArgb)
    val borderColor = chevronColor.copy(alpha = 0.54f)

    Box(
        modifier = modifier
            .zIndex(4f)
            .width(width)
            .height(height)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f),
                        accentColor.copy(alpha = 0.92f),
                        accentColor.copy(alpha = 0.68f),
                    ),
                ),
                shape,
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(minOf(width, height) * 0.72f)
                .rotate(arrowRotationDegrees),
        ) {
            val upper = Offset(
                x = size.width * if (isNext) 0.34f else 0.66f,
                y = size.height * 0.18f,
            )
            val middle = Offset(
                x = size.width * if (isNext) 0.66f else 0.34f,
                y = size.height * 0.5f,
            )
            val lower = Offset(upper.x, size.height * 0.82f)
            drawLine(
                color = chevronColor,
                start = upper,
                end = middle,
                strokeWidth = size.minDimension * 0.16f,
                cap = StrokeCap.Square,
            )
            drawLine(
                color = chevronColor,
                start = middle,
                end = lower,
                strokeWidth = size.minDimension * 0.16f,
                cap = StrokeCap.Square,
            )
        }
    }
}

@Composable
private fun FidgetNavButton(
    text: String,
    wide: Boolean = false,
    primary: Boolean = false,
    phoneLayout: Boolean = false,
    phoneLandscape: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    accentColorArgb: Int = NEON_GREEN_COLOR,
    onClick: () -> Unit,
) {
    if (!wide) {
        FidgetThemeButton(
            text = text,
            modifier = Modifier
                .size(width = 44.dp, height = 30.dp)
                .height(30.dp),
            fontSize = 16.sp,
            selected = false,
            prominent = false,
            accentColor = accentColor,
            accentColorArgb = accentColorArgb,
            onClick = onClick,
        )
        return
    }

    val phonePortrait = phoneLayout && !phoneLandscape
    val shape = RoundedCornerShape(if (phonePortrait) 16.dp else 12.dp)
    val buttonWidth = when {
        phonePortrait -> 136.dp
        phoneLayout -> 104.dp
        else -> 54.dp
    }
    val buttonHeight = when {
        phonePortrait -> 52.dp
        phoneLayout -> 38.dp
        else -> 28.dp
    }
    val textColor = if (primary) {
        readableTextColorFor(accentColorArgb)
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f)
    }
    val buttonBrush = if (primary) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.26f),
                accentColor.copy(alpha = 0.96f),
                accentColor.copy(alpha = 0.72f),
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.10f),
                accentColor.copy(alpha = 0.18f),
                Color.Black.copy(alpha = 0.24f),
            ),
        )
    }
    Box(
        modifier = Modifier
            .width(buttonWidth)
            .height(buttonHeight)
            .clip(shape)
            .background(buttonBrush, shape)
            .border(
                if (primary) 2.dp else 1.dp,
                if (primary) textColor.copy(alpha = 0.68f) else accentColor.copy(alpha = 0.54f),
                shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = when {
                phonePortrait && primary -> 16.sp
                phonePortrait -> 14.sp
                phoneLayout && primary -> 14.sp
                phoneLayout -> 11.sp
                primary -> 9.sp
                else -> 8.sp
            },
            fontWeight = if (primary) FontWeight.Black else FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FidgetRewardChip(
    text: String,
    ringColor: Color,
    rainbow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    phoneLayout: Boolean = false,
    phoneLandscape: Boolean = false,
) {
    val phonePortrait = phoneLayout && !phoneLandscape
    val chipHeight = when {
        phonePortrait -> 30.dp
        phoneLandscape -> 22.dp
        else -> 20.dp
    }
    val dotSize = when {
        phonePortrait -> 7.dp
        phoneLayout -> 5.dp
        else -> 4.dp
    }
    val shape = RoundedCornerShape(50)
    val chipBackground = if (rainbow) {
        Brush.horizontalGradient(RainbowColors.map { color -> color.copy(alpha = 0.22f) })
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                ringColor.copy(alpha = 0.06f),
                ringColor.copy(alpha = 0.22f),
                ringColor.copy(alpha = 0.06f),
            ),
        )
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(chipHeight)
            .clip(shape)
            .background(chipBackground, shape)
            .then(
                if (rainbow) {
                    Modifier.border(
                        width = if (phonePortrait) 1.5.dp else 1.dp,
                        brush = Brush.horizontalGradient(FidgetRainbowRingColors),
                        shape = shape,
                    )
                } else {
                    Modifier.border(
                        width = if (phonePortrait) 1.5.dp else 1.dp,
                        color = ringColor.copy(alpha = 0.56f),
                        shape = shape,
                    )
                },
            )
            .padding(horizontal = if (phonePortrait) 16.dp else 10.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(
                if (phonePortrait) 9.dp else 5.dp,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .then(
                        if (rainbow) {
                            Modifier.background(Brush.sweepGradient(FidgetRainbowRingColors))
                        } else {
                            Modifier.background(ringColor.copy(alpha = 0.9f))
                        },
                    ),
            )
            Text(
                text = text,
                color = if (rainbow) Color.White else ringColor.copy(alpha = 0.98f),
                fontSize = when {
                    phonePortrait -> 11.sp
                    phoneLayout -> 9.sp
                    else -> 8.sp
                },
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .then(
                        if (rainbow) {
                            Modifier.background(Brush.sweepGradient(FidgetRainbowRingColors))
                        } else {
                            Modifier.background(ringColor.copy(alpha = 0.9f))
                        },
                    ),
            )
        }
    }
}

private fun angleDegrees(
    point: androidx.compose.ui.geometry.Offset,
    center: androidx.compose.ui.geometry.Offset,
): Float {
    return (atan2(point.y - center.y, point.x - center.x) * 180f / PI).toFloat()
}

private fun Offset.vectorLength(): Float {
    return sqrt(x * x + y * y)
}

private fun Offset.limitedToLength(maxLength: Float): Offset {
    val length = vectorLength()
    if (length <= maxLength || length == 0f) return this
    val scale = maxLength / length
    return Offset(x * scale, y * scale)
}

private fun Offset.limitedToBox(maxX: Float, maxY: Float): Offset {
    return Offset(
        x = x.coerceIn(-maxX, maxX),
        y = y.coerceIn(-maxY, maxY),
    )
}

private fun shortestAngleDelta(
    previousDegrees: Float,
    currentDegrees: Float,
): Float {
    var delta = currentDegrees - previousDegrees
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return delta
}

private class FidgetFeedbackController(context: Context) {
    private val vibrator: Vibrator? = runCatching {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    }.getOrNull()
    private val clickTone = ToneGenerator(AudioManager.STREAM_MUSIC, 54)
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val loadedSamples = mutableSetOf<Int>()
    private val woodSoundId: Int
    private val bellSoundId: Int
    private val beatPadSoundIds: IntArray

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSamples += sampleId
            }
        }
        woodSoundId = soundPool.load(context, R.raw.wood_mid, 1)
        bellSoundId = soundPool.load(context, R.raw.bell_mid, 1)
        beatPadSoundIds = intArrayOf(
            soundPool.load(context, R.raw.beat_kick, 1),
            soundPool.load(context, R.raw.beat_snare, 1),
            soundPool.load(context, R.raw.beat_hat, 1),
            soundPool.load(context, R.raw.beat_tom, 1),
            soundPool.load(context, R.raw.beat_clap, 1),
            soundPool.load(context, R.raw.beat_bell, 1),
        )
    }

    fun play(
        hapticEnabled: Boolean,
        soundEnabled: Boolean,
        beatSoundMode: BeatSoundMode,
        accentIntensityMode: AccentIntensityMode,
    ) {
        if (hapticEnabled) {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(
                    accentIntensityMode.feedbackVibrationMs(),
                    accentIntensityMode.feedbackVibrationAmplitude(),
                ),
            )
        }

        if (!soundEnabled) return

        when (beatSoundMode) {
            BeatSoundMode.Clicks -> clickTone.startTone(
                ToneGenerator.TONE_PROP_BEEP,
                accentIntensityMode.feedbackDurationMs(),
            )
            BeatSoundMode.Wood -> playSample(woodSoundId, accentIntensityMode)
            BeatSoundMode.Bell -> playSample(bellSoundId, accentIntensityMode)
        }
    }

    fun playReward(
        hapticEnabled: Boolean,
        soundEnabled: Boolean,
        beatSoundMode: BeatSoundMode,
    ) {
        if (hapticEnabled) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 38L, 32L, 72L),
                    intArrayOf(0, 190, 0, VibrationEffect.DEFAULT_AMPLITUDE),
                    -1,
                ),
            )
        }
        if (!soundEnabled) return
        when (beatSoundMode) {
            BeatSoundMode.Clicks -> clickTone.startTone(ToneGenerator.TONE_PROP_ACK, 96)
            BeatSoundMode.Wood -> playSample(woodSoundId, AccentIntensityMode.Big)
            BeatSoundMode.Bell -> playSample(bellSoundId, AccentIntensityMode.Big)
        }
    }

    fun playBeatPad(
        padIndex: Int,
        hapticEnabled: Boolean,
        soundEnabled: Boolean,
        accentIntensityMode: AccentIntensityMode,
    ) {
        if (hapticEnabled) {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(
                    (accentIntensityMode.feedbackVibrationMs() * 0.72f).roundToInt().coerceAtLeast(6).toLong(),
                    accentIntensityMode.feedbackVibrationAmplitude(),
                ),
            )
        }

        if (!soundEnabled) return

        val soundId = beatPadSoundIds.getOrElse(padIndex.wrapFidgetIndex(beatPadSoundIds.size)) {
            beatPadSoundIds.first()
        }
        playSample(soundId, accentIntensityMode)
    }

    fun release() {
        clickTone.release()
        soundPool.release()
    }

    private fun playSample(
        soundId: Int,
        accentIntensityMode: AccentIntensityMode,
    ) {
        if (soundId !in loadedSamples) {
            clickTone.startTone(ToneGenerator.TONE_PROP_BEEP, accentIntensityMode.feedbackDurationMs())
            return
        }
        val volume = accentIntensityMode.feedbackVolume()
        val streamId = soundPool.play(soundId, volume, volume, 1, 0, 1f)
        if (streamId == 0) {
            clickTone.startTone(ToneGenerator.TONE_PROP_BEEP, accentIntensityMode.feedbackDurationMs())
        }
    }
}
