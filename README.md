# ThreeViewer

An offline Android GLB viewer built with Kotlin, Jetpack Compose and Google Filament's **Vulkan** backend.

Licensed under [Apache-2.0](LICENSE). Requires Android 12+ and Vulkan 1.1.

## Build

- JDK 21, Android SDK Platform 37.0, Build Tools 36
- `./gradlew assembleDebug`
- Install `app/build/outputs/apk/debug/app-debug.apk`
- Android 12+ (API 31); Vulkan 1.1 capable device

Set `JAVA_HOME` to JDK 21 (Android Studio's bundled `jbr` also works). `local.properties` is machine specific and excluded from version control.

The optimized release build uses R8 and resource shrinking. Run `./gradlew assembleRelease`. To sign it, set `THREEVIEWER_SIGNING_PROPERTIES` to a local properties file with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, or place that file at `~/.android/threeviewer-signing.properties`. Without a signing file, Gradle produces an unsigned release APK. Keep signing keys and passwords outside the repository.

If this machine's JVM HTTPS/proxy configuration stalls dependency downloads, run `python tools/maven_bridge.py` in another terminal, then add `--init-script tools/local-maven.init.gradle.kts '-Dhttp.nonProxyHosts=*'` to Gradle. This optional localhost bridge fetches directly from the official repositories. Stop it after building; its disposable cache is `.tools/maven-cache/`.

## Use

Open a `.glb` using the system document picker or an Android “Open with” intent. The model must use glTF 2.0 and embed its resources (binary chunks or data URIs). The input limit is 200 MB. Recent entries retain document URI access; removing an entry does not delete the file.

- Drag to orbit, pinch to zoom, two-finger drag to pan, double tap to reset.
- The floating toolbar provides reset, animation playback, view options, model info and full screen.
- View options include background, exposure, animation selection and accessible camera controls.
- Keyboard: arrow keys orbit, `+`/`-` zoom, Home resets.
- App appearance follows system light/dark mode and Monet by default. Both can be adjusted in Settings.
- App language uses the Android 13+ system language picker. Android 12 follows the device language.
- Settings → Open source licenses shows offline license texts and third-party notices.

Includes English and 23 translated locales: Simplified Chinese, Traditional Chinese, Japanese, Korean, French, German, Spanish, Italian, Brazilian Portuguese, Russian, Ukrainian, Polish, Dutch, Swedish, Turkish, Arabic, Hebrew, Persian, Hindi, Bengali, Indonesian, Vietnamese and Thai. `python tools/validate_locales.py` verifies complete keys and format placeholders.

There is no Internet permission, telemetry or external service. File loading is bounded and runs off the main thread; native renderer operations run on the UI thread. A static model stops scheduling frames once rendering settles. GPU resources are released when the viewer leaves the window; rendering pauses when the activity pauses.

## Design and accessibility

Uses `MaterialExpressiveTheme`, the expressive spring motion scheme, flexible app bar, floating toolbar, shape-changing icon buttons and buttons, expressive loading indicator, and Material bottom sheets. All action labels come from Android string resources. Flexible text containers, scrollable sheets, start/end padding and mirrored navigation support font scaling and RTL. Camera gestures have equivalent TalkBack custom actions, visible controls and keyboard controls. Touch targets are at least 48dp.

Navigation Compose drives screen transitions using the Material expressive motion scheme. In-app predictive back follows gesture progress and supports cancellation; home leaves back-to-home handling to Android. Material bottom sheets retain their native predictive dismissal. Full-screen exit has a separate progress-aware callback. System animation-duration preferences apply to Compose animations.

The GPT-image generated icon is packaged as independent adaptive background/foreground layers plus an alpha-only monochrome layer for themed icons. Artwork is centered inside Android's 66dp safe area on a 108dp layer. UI icons are Google's Material Symbols Rounded from the Google Fonts icon family.

## Verification

`./gradlew testDebugUnitTest lintDebug connectedDebugAndroidTest`

Tests cover malformed GLB containers, external resources, primitive counts, bounded camera operations, sample rendering, license navigation, predictive-back cancellation/commit and accessible navigation. Compose accessibility checks use Google's Accessibility Test Framework. Local screenshots and logs are recorded in the ignored `verification/` directory. The release was checked on a connected OnePlus 13 running Android 16; this is not a full manual TalkBack audit or human linguistic review.

## Official references

- [M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)
- [Floating toolbars](https://m3.material.io/components/toolbars/guidelines)
- [Buttons](https://m3.material.io/components/buttons/guidelines)
- [Motion physics](https://m3.material.io/styles/motion/overview/how-it-works)
- [Bottom sheets](https://m3.material.io/components/bottom-sheets/guidelines)
- [Compose dynamic color](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Adaptive and themed icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility)
- [Accessibility testing](https://developer.android.com/develop/ui/compose/accessibility/testing)
- [Scalable content](https://developer.android.com/develop/ui/compose/accessibility/scaling)
- [Predictive back setup](https://developer.android.com/develop/ui/compose/system/predictive-back-setup)
- [Predictive back progress and cancellation](https://developer.android.com/develop/ui/compose/system/predictive-back-progress)
- [Apple Writing](https://developer.apple.com/design/human-interface-guidelines/writing)
- [Filament](https://github.com/google/filament)

## Assets

`tools/prepare_assets.py` (Python 3 and Pillow) reproducibly builds the original knot GLB, studio cubemap, icon layers and downloads Material Symbols. The original AI artwork is retained in `design/app-icon-gpt-image.png`. The monochrome icon uses the same alpha silhouette; the system supplies its colors. Material Symbols and Filament are Apache 2.0 licensed. The sample knot and studio environment are original procedural assets.

See [NOTICE](NOTICE) and [third-party notices](THIRD_PARTY_NOTICES.md) for attribution, the full dependency inventory, and regenerating the offline license catalog.
