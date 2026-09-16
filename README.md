# Munkz Fidget Toy

Standalone Android project for the Munkz Fidget Toy phone and Wear OS apps.
Both variants use the existing Google Play application ID so phone and watch
updates remain compatible with installed releases.

## Store listing

### Short description

Spin, tap, tilt, and play colorful fidgets on your phone and Wear OS watch.

### Full description

Munkz Fidget Toy turns your phone and Wear OS watch into a pocket-sized
playground for curious hands.

Flick a responsive spinner, tilt through shifting mazes, pop colorful buttons,
build beats, and explore a growing collection of playful interactive toys.
Every fidget has its own movement, personality, and satisfying response.

#### The toy collection

- **Spin Storm:** Flick, drag, reverse, or hold a customizable spinner built to carry real momentum.
- **Flip Stack:** A bank of eight crisp switches waiting for the perfect on-and-off pattern.
- **Grid Stepper:** Nudge a glowing button through a compact grid, one satisfying step at a time.
- **Button Drift:** Slide four colorful buttons freely and arrange the board exactly your way.
- **Color Pop Hunt:** Catch bright targets as they jump to fresh positions.
- **Bounce Shot:** Pull in any direction, release, and ricochet around the walls at a speed set by your draw.
- **Maze Shuffle:** Navigate a newly generated maze with changing start and finish points.
- **Squish Pop:** Press, pull, and stretch a soft shape that leans into every touch.
- **Mag Snap:** Slide between magnetic stops and feel every clean snap into place.
- **Pop Grid:** Press through a field of bubbles, complete the grid, then reset and pop again.
- **Infinity Flip:** Cycle folding color panels through an endlessly shifting sequence.
- **Ratchet Ring:** Turn the ring notch by notch for crisp mechanical clicks.
- **Liquid Maze:** Guide a flowing blob around resistant walls and leave a shimmering trail.
- **Gear Jam:** Turn a meshed gear train, build momentum, and remix the gear sizes.
- **Worry Stone:** Rub a smooth digital stone and polish its surface with every pass.
- **Key Clicks:** Tap a compact set of tactile keys and build your own click pattern.
- **Zen Trace:** Draw a luminous trail that lingers, flows, and fades behind your fingertip.
- **Beat Machine:** Trigger kick, snare, hats, tom, clap, and bell with light, sound, and rumble.
- **Window Slide:** Open the glass and switch a tiny landscape between sunlight and moonlight.
- **Door Swing:** Tap a hinged door and watch it fold through a dimensional open-and-close motion.
- **Light Flick:** Flip the switch and fill the toy with a warm electric glow.
- **Fan Breeze:** Start the fan and watch three streamers dance in the airflow.
- **Sink Flow:** Turn the hot and cold knobs and mix colorful animated water.
- **Symbol Dock:** Tap four chunky pads to cycle through a playful deck of symbols.
- **Center Drop:** Guide a silver ball through circular gates toward the center hole.
- **Ball Sort:** Steer three colored balls through obstacles and settle each into its matching pocket.

Make the experience yours with themes, color and Big Ring choices, photo
backgrounds, Single or Multi spinner layouts, sound, screen flashes, and
vibration feedback. Pin one favorite toy, collect tap rewards, keep paired phone
and watch preferences in step, place the Fidget Dock on your home screen, or
bring an interactive spinner to your wallpaper.

On Wear OS, add Favorite Fidget to a compatible watch face for a quick launch.
Wear OS calls this type of watch-face shortcut a complication. It shows your
starred toy in its current theme; tap it to open that toy in the app.

#### Optional motion play

Touch is always ready. When you want a hands-on twist, turn on Motion in Menu,
choose a sensitivity, and calibrate your neutral position. Tilt guides Liquid
Maze, Maze Shuffle, Center Drop, and Ball Sort; a gentle shake refreshes the
three maze boards. Motion is optional, and every supported toy still has touch
controls.

Spin it. Tap it. Tilt it. Make it yours.

## Rewards and support

- Tap totals and pinned favorites persist locally and sync between compatible
  paired phone and Wear OS devices.
- Fibonacci milestones can trigger a reward moment. Choose Calm, Glow, or
  Celebrate feedback in Menu; Celebrate adds a short positive message, flash,
  and stronger haptic feedback when supported by the device.
- The reward chip opens progress toward the next milestone, and rewards can be
  reset from Menu.
- Optional, consumable $1, $3, $5, and $10 donations are handled by Google
  Play Billing. Donation badges are stored locally, support repeat donations,
  and can sync to a paired device.
- The review dialog links to the app's Fidget-specific privacy policy at
  `https://www.labmunkz.com/MunkzFidgetToy/privacy`.

## Release history

### 1.6.5 - Production submission (2026-09-16)

- Phone version code 21 and Wear OS version code 22 were submitted to Google
  Play Production review.
- This release covers the expanded toy collection and optional Motion controls.
- Status: In review. Google Play approval and device delivery are pending.

## Variants

- `fidgetphone`: Android phone app, minimum API 31
- `fidgettoy`: Wear OS app, minimum API 33
- Google Play application ID: `bpm.munkz.pulse_wear.os.fidgettoy`

## Release bundles

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat clean bundleFidgettoyRelease bundleFidgetphoneRelease
```

- Wear: `build/outputs/bundle/fidgettoyRelease/fidget-fidgettoy-release.aab`
- Phone: `build/outputs/bundle/fidgetphoneRelease/fidget-fidgetphone-release.aab`

Release builds require an ignored `keystore.properties` file with
`storeFile`, `storePassword`, `keyAlias`, and `keyPassword` values.

## Verification

```powershell
.\gradlew.bat testFidgettoyDebugUnitTest testFidgetphoneDebugUnitTest
.\gradlew.bat lintFidgettoyRelease lintFidgetphoneRelease verifyFidgetReleasePackages
```

`verifyFidgetReleasePackages` rejects bundles containing source files,
unexpected modules, legacy BPM product classes/resources, or unnecessary
permissions.

The historical application ID and compatible code namespace are intentional.
Changing either without an explicit migration can break Play updates, launcher
aliases, deep links, or persisted user state; they do not pull BPM product code
into these bundles.

This repository does not include the former shared `:app` module.
