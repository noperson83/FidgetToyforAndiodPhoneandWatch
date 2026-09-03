# Munkz Fidget Toy

Standalone Android project for the Munkz Fidget Toy phone and Wear OS apps.
Both variants use the existing Google Play application ID so phone and watch
updates remain compatible with installed releases.

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
